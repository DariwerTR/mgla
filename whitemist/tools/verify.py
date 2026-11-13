#!/usr/bin/env python3
"""
Static verifier for the White Mist project. I cannot run Gradle/Minecraft here, so this is the next best thing.

What it checks
  1. Java syntax of every .java file (tree-sitter, Java 25 grammar-compatible).
  2. Missing / unused imports (catches the typos a compiler would catch).
  3. Every import is CONFIRMED against real 26.3 code (Fabric API, NeoForge 26.3.x, Fabric Docs reference).
  4. Every method / field name used on Minecraft-facing code appears somewhere in those corpora (catches renames).
  5. JSON syntax, timeline format and attribute ids vs. the vanilla registry, lang keys, clock consistency.
  6. Texture size / UV extents / overlaps / opaque faces vs. the model.
  7. Sound ids vs. the vanilla sound_event registry.
  8. Forbidden legacy patterns (Yarn names, modImplementation, ...).
  9. Build files vs. the official example mod.
Usage: verify.py CORPUS_DIR [--report docs/VERIFICATION_REPORT.md]
"""
import json
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

import tree_sitter_java
from PIL import Image
from tree_sitter import Language, Parser

ROOT = Path(__file__).resolve().parent.parent
CORPUS = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path("/home/claude")
REPORT = None
if "--report" in sys.argv:
    REPORT = ROOT / sys.argv[sys.argv.index("--report") + 1]

results = []  # (level, area, message)


def add(level, area, msg):
    results.append((level, area, msg))


JAVA = Language(tree_sitter_java.language())
parser = Parser(JAVA)

SRC_FILES = sorted(p for p in (ROOT / "src").rglob("*.java"))
TOOL_FILES = sorted(p for p in (ROOT / "tools").rglob("*.java") if "stubs" not in p.parts)
ALL_JAVA = SRC_FILES + TOOL_FILES


def parse(path):
    data = path.read_bytes()
    return parser.parse(data), data


def walk(node):
    yield node
    for c in node.children:
        yield from walk(c)


def text(node, data):
    return data[node.start_byte:node.end_byte].decode("utf8")


# ------------------------------------------------------------------------------------------- 1. syntax
for p in ALL_JAVA:
    tree, data = parse(p)
    errs = [n for n in walk(tree.root_node) if n.type == "ERROR" or n.is_missing]
    if errs:
        for e in errs[:3]:
            add("FAIL", "syntax", f"{p.relative_to(ROOT)}:{e.start_point[0]+1}: {'MISSING ' + e.type if e.is_missing else 'syntax error'}")
    else:
        add("PASS", "syntax", f"{p.relative_to(ROOT)}")

# ------------------------------------------------------------------------------------------- corpus index
CORPORA = [d for d in ("fabric-api", "neoforge", "fabric-docs/reference/latest/src") if (CORPUS / d).exists()]
import_seen = set()           # FQNs imported somewhere in real code
fabric_classes = set()         # FQNs of classes defined in the Fabric API repo
patch_classes = set()          # vanilla classes that NeoForge patches (=> they exist in 26.3)
call_tokens = set()            # identifiers that are followed by "("
member_tokens = set()          # identifiers that follow a "."
imp_re = re.compile(r"^\s*(?:\+|-)?\s*import\s+(?:static\s+)?([\w.]+?)(?:\.\*)?;", re.M)
call_re = re.compile(r"\b([A-Za-z_]\w*)\s*\(")
mem_re = re.compile(r"\.\s*([A-Za-z_]\w*)")
n_files = 0
for c in CORPORA:
    base = CORPUS / c
    for f in base.rglob("*"):
        if f.suffix not in (".java", ".patch") or not f.is_file():
            continue
        n_files += 1
        try:
            s = f.read_text(errors="ignore")
        except Exception:
            continue
        for m in imp_re.finditer(s):
            import_seen.add(m.group(1))
        for m in call_re.finditer(s):
            call_tokens.add(m.group(1))
        for m in mem_re.finditer(s):
            member_tokens.add(m.group(1))
        if c == "fabric-api" and f.suffix == ".java":
            rel = str(f.relative_to(base))
            mm = re.search(r"src/[^/]+/java/(.+)\.java$", rel)
            if mm:
                fabric_classes.add(mm.group(1).replace("/", "."))
        if c == "neoforge" and f.suffix == ".patch":
            rel = str(f.relative_to(base))
            mm = re.search(r"patches/(net/minecraft/.+)\.java\.patch$", rel)
            if mm:
                patch_classes.add(mm.group(1).replace("/", "."))
add("INFO", "corpus", f"indexed {n_files} files from {', '.join(CORPORA)}; "
                      f"{len(import_seen)} distinct imports, {len(fabric_classes)} Fabric classes, {len(patch_classes)} patched vanilla classes")

# ------------------------------------------------------------------------------------------- 2/3. imports
JAVA_LANG = {"String", "Math", "Object", "Double", "Integer", "Long", "Boolean", "Float", "Override", "Exception",
             "Runnable", "RuntimeException", "IllegalArgumentException", "Character", "Number", "Iterable",
             "Thread", "System", "Class", "Enum", "Record", "Void", "FunctionalInterface", "Deprecated",
             "SuppressWarnings", "Byte", "Short", "StringBuilder", "Comparable", "CharSequence", "Throwable",
             "Error", "AssertionError", "UnsupportedOperationException", "IllegalStateException", "Override"}
ALLOWED_PREFIX = ("java.", "javax.")
BUNDLED_CONFIRM = ("org.slf4j.", "com.google.gson.", "com.mojang.")

same_pkg = defaultdict(set)
for p in SRC_FILES:
    tree, data = parse(p)
    pkg = ""
    for n in tree.root_node.children:
        if n.type == "package_declaration":
            pkg = text(n, data).replace("package", "").replace(";", "").strip()
    same_pkg[pkg].add(p.stem)

unconfirmed_imports = []
for p in SRC_FILES:
    tree, data = parse(p)
    root = tree.root_node
    pkg = ""
    imports = {}  # simple -> fqn
    for n in root.children:
        if n.type == "package_declaration":
            pkg = text(n, data).replace("package", "").replace(";", "").strip()
        if n.type == "import_declaration":
            raw = text(n, data).replace("import", "", 1).replace(";", "").strip()
            if raw.startswith("static"):
                continue
            imports[raw.split(".")[-1]] = raw

    # declared types in this file
    declared = set()
    for n in walk(root):
        if n.type in ("class_declaration", "interface_declaration", "enum_declaration", "record_declaration"):
            nm = n.child_by_field_name("name")
            if nm:
                declared.add(text(nm, data))
    # type parameters (generics) are also declared names
    for n in walk(root):
        if n.type == "type_parameter":
            for c in n.children:
                if c.type == "type_identifier":
                    declared.add(text(c, data))

    # used type names: type_identifier + capitalised scope of static calls/field access
    used = set()
    for n in walk(root):
        if n.type == "type_identifier":
            par = n.parent
            if par is not None and par.type == "scoped_type_identifier" and par.children[0] != n:
                continue  # Outer.Inner: only Outer must be imported
            used.add(text(n, data))
        elif n.type in ("method_invocation", "field_access", "method_reference"):
            first = n.children[0] if n.children else None
            if first is not None and first.type == "identifier":
                t = text(first, data)
                if t[:1].isupper() and not re.fullmatch(r"[A-Z][A-Z0-9_]*", t):
                    used.add(t)  # ALL_CAPS identifiers are constants (fields), not types
    # identifiers directly after "new"
    for n in walk(root):
        if n.type == "object_creation_expression":
            t = n.child_by_field_name("type")
            if t is not None:
                first = t
                while first.children and first.type in ("scoped_type_identifier", "generic_type"):
                    first = first.children[0]
                used.add(text(first, data))

    rel = p.relative_to(ROOT)
    missing = []
    for name in sorted(used):
        if name in imports or name in declared or name in JAVA_LANG or name in same_pkg[pkg]:
            continue
        if len(name) <= 2 and name.isupper():  # generic parameters like T, E
            continue
        # nested class referenced as Outer.Inner is covered by Outer; names that are scopes of imported types
        missing.append(name)
    if missing:
        add("FAIL", "imports", f"{rel}: type(s) used but NOT imported/declared: {', '.join(missing)}")
    unused = [s for s in imports if s not in used and not re.search(r"\b" + re.escape(s) + r"\b", re.sub(r"^import .*$", "", data.decode('utf8'), flags=re.M))]
    if unused:
        add("WARN", "imports", f"{rel}: unused import(s): {', '.join(unused)}")
    if not missing and not unused:
        add("PASS", "imports", f"{rel}")

    for s, fqn in imports.items():
        if fqn.startswith("com.whitemist."):
            parts = fqn.split(".")
            resolved = False
            for cut in (1, 2):  # class, or Outer.Inner
                pkg_name = ".".join(parts[:-cut])
                outer = parts[-cut]
                if outer in same_pkg.get(pkg_name, set()):
                    resolved = True
            if not resolved:
                add("FAIL", "imports", f"{rel}: import {fqn} does not resolve to a project class")
            continue
        if fqn.startswith(ALLOWED_PREFIX):
            continue
        if fqn.startswith("net.fabricmc.fabric."):
            if fqn in fabric_classes:
                continue
            unconfirmed_imports.append((str(rel), fqn, "not found in Fabric API source tree"))
            continue
        if fqn in import_seen or fqn in patch_classes:
            continue
        if fqn.startswith("net.fabricmc.api.") or fqn.startswith("net.fabricmc.loader."):
            unconfirmed_imports.append((str(rel), fqn, "Fabric core class not seen in corpus"))
            continue
        unconfirmed_imports.append((str(rel), fqn, "never imported anywhere in the 26.3 corpora"))

for rel, fqn, why in unconfirmed_imports:
    add("FAIL", "api-import", f"{rel}: {fqn} ({why})")
ok_imports = sum(len([1 for n in walk(parse(p)[0].root_node) if n.type == 'import_declaration']) for p in SRC_FILES)
add("INFO", "api-import", f"{ok_imports} import statements checked, {len(unconfirmed_imports)} unconfirmed")

# ------------------------------------------------------------------------------------------- 4. method/field names
MC_FILES = [p for p in SRC_FILES if re.search(r"import (net\.minecraft|net\.fabricmc|com\.mojang)", p.read_text())]
own_methods = set()
for p in SRC_FILES + TOOL_FILES:
    tree, data = parse(p)
    for n in walk(tree.root_node):
        if n.type in ("method_declaration", "constructor_declaration", "record_declaration"):
            nm = n.child_by_field_name("name")
            if nm:
                own_methods.add(text(nm, data))
        if n.type == "record_declaration":
            for c in walk(n):
                if c.type == "formal_parameter":
                    nm = c.child_by_field_name("name")
                    if nm:
                        own_methods.add(text(nm, data))
own_fields = set()
for p in SRC_FILES:
    tree, data = parse(p)
    for n in walk(tree.root_node):
        if n.type == "field_declaration":
            for d in n.children:
                if d.type == "variable_declarator":
                    nm = d.child_by_field_name("name")
                    if nm:
                        own_fields.add(text(nm, data))
        if n.type == "enum_constant":
            nm = n.child_by_field_name("name")
            if nm:
                own_fields.add(text(nm, data))

JDK_METHODS = set("""add get put size remove removeIf computeIfAbsent isEmpty contains sqrt cos sin floor ceil round min max abs pow
nextInt nextDouble nextFloat nextBoolean length toLowerCase name ordinal values valueOf equals hashCode toString info warn error debug
accept apply run resolve getParent createDirectories exists newBufferedReader newBufferedWriter write append getFileName getMessage
getInstance fromJson toJson create setPrettyPrinting asLong isNaN of listOf format forEach stream list asList copyOf
getConfigDir register invoker interact""".split())
JDK_METHODS |= {"getConfigDir"}

unseen_calls = defaultdict(set)
unseen_members = defaultdict(set)
for p in MC_FILES:
    tree, data = parse(p)
    for n in walk(tree.root_node):
        if n.type == "method_invocation":
            nm = n.child_by_field_name("name")
            if nm is None:
                continue
            name = text(nm, data)
            if name in own_methods or name in JDK_METHODS:
                continue
            if name not in call_tokens:
                unseen_calls[name].add(f"{p.relative_to(ROOT)}:{nm.start_point[0]+1}")
        elif n.type == "method_reference":
            nm = n.children[-1]
            name = text(nm, data)
            if name in ("new",) or name in own_methods or name in JDK_METHODS:
                continue
            if name not in call_tokens and name not in member_tokens:
                unseen_calls[name].add(f"{p.relative_to(ROOT)}:{nm.start_point[0]+1}")
        elif n.type == "field_access":
            f = n.child_by_field_name("field")
            if f is None:
                continue
            name = text(f, data)
            if name in own_fields or name in own_methods:
                continue
            if name not in member_tokens:
                unseen_members[name].add(f"{p.relative_to(ROOT)}:{f.start_point[0]+1}")

for name, where in sorted(unseen_calls.items()):
    add("WARN", "api-method", f"method '{name}(' never appears in the 26.3 corpora (rename risk): {', '.join(sorted(where)[:2])}")
for name, where in sorted(unseen_members.items()):
    add("WARN", "api-member", f"member '.{name}' never appears in the 26.3 corpora: {', '.join(sorted(where)[:2])}")

# ------------------------------------------------------------------------------------------- 5. JSON
def load_json(path):
    try:
        return json.loads(path.read_text(encoding="utf8"))
    except Exception as e:
        add("FAIL", "json", f"{path.relative_to(ROOT)}: {e}")
        return None


json_files = sorted((ROOT / "src").rglob("*.json"))
parsed = {}
for jf in json_files:
    d = load_json(jf)
    if d is not None:
        parsed[jf] = d
        add("PASS", "json", f"{jf.relative_to(ROOT)} parses")

reg_path = CORPUS / "mcmeta-summary/registries/data.json"
registries = json.loads(reg_path.read_text()) if reg_path.exists() else {}
env_attrs = set(registries.get("environment_attribute", []))
sound_ids = set(registries.get("sound_event", []))
vanilla_tl = CORPUS / "mcmeta-data/data/minecraft/timeline"

KNOWN_FLOAT_MODS = {"override", "add", "subtract", "multiply", "minimum", "maximum", "alpha_blend"}
KNOWN_RGB_MODS = {"override", "add", "subtract", "multiply"}
KNOWN_EASE = {"constant", "linear"} | {f"{a}_{b}" if a else b for a in ("in", "out", "in_out") for b in
              ("back", "bounce", "circ", "cubic", "elastic", "expo", "quad", "quart", "quint", "sine")}
RGB_ATTRS = {"minecraft:visual/fog_color", "minecraft:visual/sky_color"}
FLOAT_ATTRS = {"minecraft:visual/fog_start_distance", "minecraft:visual/fog_end_distance"}
HEX_RGB = re.compile(r"^#[0-9a-fA-F]{6}$")

timeline_file = ROOT / "src/main/resources/data/whitemist/timeline/mist.json"
clock_ticks = set()
tl = parsed.get(timeline_file)
if tl is not None:
    allowed_top = {"clock", "period_ticks", "time_markers", "tracks"}
    if set(tl) - allowed_top:
        add("FAIL", "timeline", f"unknown top-level keys: {set(tl) - allowed_top}")
    if tl.get("clock") != "whitemist:mist":
        add("FAIL", "timeline", f"clock is {tl.get('clock')!r}, expected 'whitemist:mist'")
    for attr, track in tl.get("tracks", {}).items():
        short = attr.replace("minecraft:", "")
        if env_attrs and short not in env_attrs:
            add("FAIL", "timeline", f"attribute {attr} is not in the vanilla environment_attribute registry")
        if set(track) - {"keyframes", "modifier", "ease"}:
            add("FAIL", "timeline", f"{attr}: unknown track keys {set(track) - {'keyframes', 'modifier', 'ease'}}")
        mod = track.get("modifier", "override")
        if attr in FLOAT_ATTRS and mod not in KNOWN_FLOAT_MODS:
            add("FAIL", "timeline", f"{attr}: modifier {mod} not valid for float attributes")
        if attr in RGB_ATTRS and mod not in KNOWN_RGB_MODS:
            add("FAIL", "timeline", f"{attr}: modifier {mod} not valid for RGB attributes")
        if track.get("ease", "linear") not in KNOWN_EASE:
            add("FAIL", "timeline", f"{attr}: unknown ease {track.get('ease')}")
        kfs = track.get("keyframes", [])
        ticks = [k.get("ticks") for k in kfs]
        if len(kfs) < 2 or ticks != sorted(ticks):
            add("FAIL", "timeline", f"{attr}: keyframes must be >=2 and ordered by ticks")
        clock_ticks.add(max(ticks) if ticks else None)
        for k in kfs:
            v = k.get("value")
            if mod == "alpha_blend":
                if not (isinstance(v, dict) and set(v) == {"value", "alpha"} and 0.0 <= v["alpha"] <= 1.0):
                    add("FAIL", "timeline", f"{attr}: alpha_blend value must be {{value, alpha in [0,1]}}, got {v}")
            elif attr in RGB_ATTRS:
                if not (isinstance(v, str) and HEX_RGB.match(v)):
                    add("FAIL", "timeline", f"{attr}: RGB value must be '#rrggbb', got {v!r}")
            elif not isinstance(v, (int, float)):
                add("FAIL", "timeline", f"{attr}: float value expected, got {v!r}")
        # first keyframe must be a NO-OP so that clear weather is untouched
        first = kfs[0].get("value")
        noop = ((mod == "alpha_blend" and first.get("alpha") == 0.0) or
                (mod == "add" and first == "#000000") or
                (mod == "multiply" and str(first).lower() == "#ffffff"))
        if noop:
            add("PASS", "timeline", f"{attr}: first keyframe is a no-op (clear weather stays vanilla)")
        else:
            add("FAIL", "timeline", f"{attr}: first keyframe is NOT a no-op, it would change clear weather")
    if vanilla_tl.exists():
        van = json.loads((vanilla_tl / "early_game.json").read_text())
        if set(van) - {"tracks", "clock", "period_ticks", "time_markers"}:
            add("WARN", "timeline", "vanilla timeline format has new top-level keys, re-check")
        else:
            add("PASS", "timeline", "top-level format matches vanilla timelines (clock/tracks/...)")
    if len(clock_ticks) == 1:
        add("PASS", "timeline", f"all tracks end at the same tick {clock_ticks}")
    else:
        add("FAIL", "timeline", f"tracks end at different ticks: {clock_ticks}")

clock_bridge = (ROOT / "src/main/java/com/whitemist/mist/ClockBridge.java").read_text()
m = re.search(r"CLOCK_MAX\s*=\s*(\d+)", clock_bridge)
m2 = re.search(r'CLOCK_ID\s*=\s*"([^"]+)"', clock_bridge)
if m and clock_ticks and int(m.group(1)) in clock_ticks:
    add("PASS", "clock", f"ClockBridge.CLOCK_MAX={m.group(1)} equals the last keyframe tick")
else:
    add("FAIL", "clock", f"ClockBridge.CLOCK_MAX {m.group(1) if m else None} != timeline end {clock_ticks}")
if m2 and m2.group(1) == "whitemist:mist" and (ROOT / "src/main/resources/data/whitemist/world_clock/mist.json").exists():
    add("PASS", "clock", "clock id matches world_clock/mist.json and the timeline")
else:
    add("FAIL", "clock", "clock id / world_clock file mismatch")
wc = parsed.get(ROOT / "src/main/resources/data/whitemist/world_clock/mist.json")
if wc == {}:
    add("PASS", "clock", "world_clock JSON is {} like vanilla")
else:
    add("FAIL", "clock", f"world_clock JSON should be {{}}, got {wc}")

tag = parsed.get(ROOT / "src/main/resources/data/minecraft/tags/timeline/in_overworld.json")
if tag and tag.get("values") == ["whitemist:mist"] and "replace" not in tag:
    add("PASS", "tag", "in_overworld tag adds whitemist:mist and does not replace vanilla timelines")
else:
    add("FAIL", "tag", f"unexpected in_overworld tag content: {tag}")
if (CORPUS / "mcmeta-data/data/minecraft/tags/timeline/in_overworld.json").exists():
    add("PASS", "tag", "tag path data/minecraft/tags/timeline/ exists in vanilla data")

# ---- lang keys
lang_en = parsed.get(ROOT / "src/main/resources/assets/whitemist/lang/en_us.json", {})
lang_ru = parsed.get(ROOT / "src/main/resources/assets/whitemist/lang/ru_ru.json", {})
src_text = "\n".join(p.read_text() for p in SRC_FILES)
used_keys = {k for k in re.findall(r'translatable\(\s*"([^"]+)"', src_text) if not k.endswith(".")}
literals = set(re.findall(r'"([a-z0-9_.]+)"', src_text))
used_keys |= {k for k in literals if k in lang_en}
for pref in re.findall(r'translatable\(\s*"([^"]+\.)"\s*\+', src_text):
    used_keys |= {k for k in lang_en if k.startswith(pref)}
entity_ids = re.findall(r'WhiteMist\.id\("([a-z_]+)"\)\);\s*\n?\s*$', (ROOT / "src/main/java/com/whitemist/entity/ModEntities.java").read_text(), re.M)
entity_ids = re.findall(r'ResourceKey\.create\(Registries\.ENTITY_TYPE,\s*WhiteMist\.id\("([a-z_]+)"\)\)', (ROOT / "src/main/java/com/whitemist/entity/ModEntities.java").read_text())
for eid in entity_ids:
    used_keys.add(f"entity.whitemist.{eid}")
for name, lang in (("en_us", lang_en), ("ru_ru", lang_ru)):
    miss = sorted(k for k in used_keys if k not in lang)
    extra = sorted(k for k in lang if k not in used_keys)
    if miss:
        add("FAIL", "lang", f"{name}: missing keys {miss}")
    else:
        add("PASS", "lang", f"{name}: all {len(used_keys)} keys used by the code are present")
    if extra:
        add("WARN", "lang", f"{name}: keys never used by the code: {extra}")
if set(lang_en) != set(lang_ru):
    add("FAIL", "lang", f"en/ru key sets differ: {sorted(set(lang_en) ^ set(lang_ru))}")
fmt_re = re.compile(r"%s")
for k in lang_en:
    if len(fmt_re.findall(lang_en[k])) != len(fmt_re.findall(lang_ru.get(k, ""))):
        add("FAIL", "lang", f"{k}: placeholder count differs between en and ru")
# status command argument count vs placeholders
def call_args(src, marker):
    i = src.find(marker)
    if i < 0:
        return None
    j = src.index("(", i)
    depth, k, args, cur = 0, j, [], ""
    while k < len(src):
        ch = src[k]
        if ch == "(":
            depth += 1
            if depth > 1:
                cur += ch
        elif ch == ")":
            depth -= 1
            if depth == 0:
                args.append(cur.strip())
                break
            cur += ch
        elif ch == "," and depth == 1:
            args.append(cur.strip())
            cur = ""
        else:
            cur += ch
        k += 1
    return args

args = call_args(src_text, 'Component.translatable("command.whitemist.status"')
if args:
    n_ph = len(fmt_re.findall(lang_en["command.whitemist.status"]))
    add("PASS" if len(args) - 1 == n_ph else "FAIL", "lang", f"status command passes {len(args) - 1} args for {n_ph} placeholders")

# ---- fabric.mod.json / build files
fmj = parsed.get(ROOT / "src/main/resources/fabric.mod.json", {})
for kind, classes in fmj.get("entrypoints", {}).items():
    for cls in classes:
        f = ROOT / ("src/client/java" if kind == "client" else "src/main/java") / (cls.replace(".", "/") + ".java")
        add("PASS" if f.exists() else "FAIL", "mod.json", f"{kind} entrypoint {cls}")
mod_id_java = re.search(r'MOD_ID\s*=\s*"([^"]+)"', (ROOT / "src/main/java/com/whitemist/WhiteMist.java").read_text()).group(1)
checks = [("fabric.mod.json id", fmj.get("id")), ("settings.gradle rootProject.name",
          re.search(r"rootProject.name\s*=\s*'([^']+)'", (ROOT / "settings.gradle").read_text()).group(1)),
          ("build.gradle loom mod", re.search(r'mods \{\s*"([^"]+)"', (ROOT / "build.gradle").read_text()).group(1))]
for what, val in checks:
    add("PASS" if val == mod_id_java else "FAIL", "mod.json", f"{what} == MOD_ID ({val!r} vs {mod_id_java!r})")
gp = dict(re.findall(r"^(\w+)=(.*)$", (ROOT / "gradle.properties").read_text(), re.M))
ex = CORPUS / "fabric-example-mod/gradle.properties"
if ex.exists():
    exp = dict(re.findall(r"^(\w+)=(.*)$", ex.read_text(), re.M))
    for k in ("minecraft_version", "loader_version", "loom_version", "fabric_api_version"):
        add("PASS" if gp.get(k) == exp.get(k) else "FAIL", "build", f"{k} = {gp.get(k)} (official template: {exp.get(k)})")
    wrap = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text() == (CORPUS / "fabric-example-mod/gradle/wrapper/gradle-wrapper.properties").read_text()
    add("PASS" if wrap else "FAIL", "build", "gradle wrapper identical to the official template")
dep = fmj.get("depends", {})
if dep.get("minecraft") == f"~{gp.get('minecraft_version')}" and dep.get("fabricloader") == f">={gp.get('loader_version')}":
    add("PASS", "mod.json", f"depends match gradle.properties (minecraft {dep.get('minecraft')}, loader {dep.get('fabricloader')})")
else:
    add("FAIL", "mod.json", f"depends {dep} do not match gradle.properties")
if "mixins" in fmj:
    add("FAIL", "mod.json", "mixins declared but the project promises zero mixins")
build_text = (ROOT / "build.gradle").read_text()
add("FAIL" if re.search(r"modImplementation|mappings\s", build_text) else "PASS", "build", "no modImplementation / mappings (26.x is unobfuscated)")

# ------------------------------------------------------------------------------------------- 6. texture vs model
model_src = (ROOT / "src/client/java/com/whitemist/client/render/ListenerModel.java").read_text()
tw = int(re.search(r"TEXTURE_WIDTH\s*=\s*(\d+)", model_src).group(1))
th = int(re.search(r"TEXTURE_HEIGHT\s*=\s*(\d+)", model_src).group(1))
tex_path = ROOT / "src/main/resources/assets/whitemist/textures/entity/listener.png"
if not tex_path.exists():
    add("FAIL", "texture", "listener.png missing")
else:
    tex = Image.open(tex_path).convert("RGBA")
    add("PASS" if tex.size == (tw, th) else "FAIL", "texture", f"png size {tex.size} vs model {tw}x{th}")
    boxes = re.findall(r"texOffs\((\d+),\s*(\d+)\)\s*\.addBox\(([-\d.F, ]+)\)", model_src)
    rects = []
    for u, v, args in boxes:
        nums = [float(x.replace("F", "")) for x in args.split(",")]
        w, h, d = nums[3], nums[4], nums[5]
        u, v = int(u), int(v)
        ww, hh = int(2 * (w + d)), int(d + h)
        rects.append(((u, v, u + ww, v + hh), (u, v, w, h, d)))
        if u + ww > tw or v + hh > th:
            add("FAIL", "texture", f"cuboid at ({u},{v}) net {ww}x{hh} exceeds the texture")
        # every face of the net must be opaque
        faces = [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h), (u + d + w, v + d, d, h),
                 (u + 2 * d + w, v + d, w, h)]
        transparent = 0
        for fx, fy, fw, fh in faces:
            for yy in range(int(fy), int(fy + fh)):
                for xx in range(int(fx), int(fx + fw)):
                    if tex.getpixel((xx, yy))[3] < 255:
                        transparent += 1
        if transparent:
            add("FAIL", "texture", f"cuboid at ({u},{v}): {transparent} face pixels are not opaque")
    seen = {}
    for rect, key in rects:
        if rect in seen:
            if seen[rect] != key:
                add("FAIL", "texture", f"two different cuboids share UV rect {rect}")
            continue
        seen[rect] = key
    uniq = list(seen)
    overlap = False
    for i in range(len(uniq)):
        for j in range(i + 1, len(uniq)):
            a, b = uniq[i], uniq[j]
            if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                overlap = True
                add("FAIL", "texture", f"UV nets overlap: {a} and {b}")
    if not overlap:
        add("PASS", "texture", f"{len(uniq)} UV nets fit in {tw}x{th} without overlapping, all faces opaque")
icon = ROOT / "src/main/resources/assets/whitemist/icon.png"
add("PASS" if icon.exists() else "FAIL", "texture", "icon.png present")

# ------------------------------------------------------------------------------------------- 7. sounds
ent_src = (ROOT / "src/main/java/com/whitemist/entity/ListenerEntity.java").read_text()
for sid in re.findall(r'sound\("([a-z0-9_.]+)"\)', ent_src):
    if not sound_ids:
        add("WARN", "sound", f"{sid}: vanilla registry not available")
    else:
        add("PASS" if sid in sound_ids else "FAIL", "sound", f"minecraft:{sid} exists in the vanilla sound_event registry")

# ------------------------------------------------------------------------------------------- 8. forbidden patterns
FORBIDDEN = [r"\bMinecraftClient\b", r"\bPlayerEntity\b", r"\bServerWorld\b", r"\bServerPlayerEntity\b",
             r"\bText\.(literal|translatable)\(", r"net\.minecraft\.util\.Identifier", r"Identifier\.of\(",
             r"\bmodImplementation\b", r"\blocalStorage\b", r"\.moveTo\([^,()]+,[^,()]+,[^,()]+,[^,()]+,[^,()]+\)",
             r"\bEntityType\.[A-Z_]{3,}\b(?<!BUILDER)", r"\bBlocks\.[A-Z_]+_(WOOL|CARPET)\b"]
bad = False
for p in SRC_FILES:
    code = re.sub(r"/\*.*?\*/", "", p.read_text(), flags=re.S)   # block comments (may span lines)
    code = re.sub(r"//[^\n]*", "", code)                            # line comments (must NOT span lines)
    for pat in FORBIDDEN:
        mm = re.search(pat, code)
        if mm and not (pat.startswith(r"\bEntityType\.") and mm.group(0) in ("EntityType.Builder",)):
            bad = True
            add("FAIL", "legacy", f"{p.relative_to(ROOT)}: forbidden/legacy pattern {pat!r}: {mm.group(0)}")
if not bad:
    add("PASS", "legacy", "no Yarn names, no EntityType.<CONSTANT> (moved to EntityTypes in 26.3), no 5-arg moveTo")

# ------------------------------------------------------------------------------------------- 10. pure logic
try:
    out = subprocess.run([str(ROOT / "tools/run_selftest.sh")], capture_output=True, text=True, timeout=240)
    last = [l for l in out.stdout.splitlines() if l.startswith("passed=")]
    if out.returncode == 0 and last:
        add("PASS", "selftest", f"pure logic compiled with -Xlint and tested: {last[-1]}")
    else:
        add("FAIL", "selftest", (out.stdout + out.stderr)[-500:])
except Exception as e:
    add("FAIL", "selftest", str(e))

# ------------------------------------------------------------------------------------------- report
counts = defaultdict(int)
for lvl, _, _ in results:
    counts[lvl] += 1
order = {"FAIL": 0, "WARN": 1, "INFO": 2, "PASS": 3}
print("=" * 78)
for lvl, area, msg in sorted(results, key=lambda r: (order[r[0]], r[1])):
    if lvl != "PASS":
        print(f"[{lvl:4}] {area:10} {msg}")
print("=" * 78)
print(f"PASS={counts['PASS']} WARN={counts['WARN']} FAIL={counts['FAIL']} INFO={counts['INFO']}")

if REPORT:
    lines = ["# Отчёт автоматической верификации", "",
             "Сгенерирован `tools/verify.py`. **Это статическая проверка, а не сборка:** Gradle и Minecraft в моей среде "
             "запустить нельзя (нет доступа к maven.fabricmc.net и серверам Mojang).", "",
             f"**Итого:** PASS={counts['PASS']}, WARN={counts['WARN']}, FAIL={counts['FAIL']}", ""]
    for lvl in ("FAIL", "WARN", "INFO"):
        items = [(a, m) for l, a, m in results if l == lvl]
        if items:
            lines += [f"## {lvl}", ""] + [f"- `{a}`: {m}" for a, m in items] + [""]
    by_area = defaultdict(list)
    for l, a, m in results:
        if l == "PASS":
            by_area[a].append(m)
    lines += ["## PASS (по областям)", ""]
    for a, ms in sorted(by_area.items()):
        lines.append(f"- **{a}**: {len(ms)} проверок")
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines) + "\n", encoding="utf8")
sys.exit(1 if counts["FAIL"] else 0)
