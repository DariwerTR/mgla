#!/usr/bin/env python3
"""Tests the verifier itself: breaks a copy of the project in known ways and checks every break is detected."""
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CORPUS = sys.argv[1] if len(sys.argv) > 1 else "/home/claude"

M = "src/main/java/com/whitemist/"
MUTATIONS = [
    ("missing import",           M + "noise/NoiseManager.java", "import net.minecraft.util.Mth;\n", "", "imports"),
    ("bogus vanilla import",     M + "entity/ListenerEntity.java", "import net.minecraft.world.entity.Mob;", "import net.minecraft.world.entity.Mob;\nimport net.minecraft.world.entity.monster.ZombieFoo;", "api-import"),
    ("renamed vanilla method",   M + "noise/PlayerNoiseTracker.java", "isShiftKeyDown()", "isSneakingNow()", "api-method"),
    ("syntax error",             M + "core/NoiseMath.java", "return 0.0;\n\t\t}\n\t\tdouble base", "return 0.0\n\t\t}\n\t\tdouble base", "syntax"),
    ("wrong clock range",        M + "mist/ClockBridge.java", "CLOCK_MAX = 2400", "CLOCK_MAX = 2000", "clock"),
    ("missing lang key",         "src/main/resources/assets/whitemist/lang/ru_ru.json", '\t"entity.whitemist.listener": "Слушатель",\n', "", "lang"),
    ("overlapping UV nets",      "src/client/java/com/whitemist/client/render/ListenerModel.java", "texOffs(32, 0).addBox(4.0F", "texOffs(28, 0).addBox(4.0F", "texture"),
    ("typo in attribute id",     "src/main/resources/data/whitemist/timeline/mist.json", "visual/fog_end_distance", "visual/fog_end_distanse", "timeline"),
    ("non-noop first keyframe",  "src/main/resources/data/whitemist/timeline/mist.json", '"#000000"', '"#101010"', "timeline"),
    ("unknown sound id",         M + "entity/ListenerEntity.java", "entity.warden.heartbeat", "entity.warden.heartbeet", "sound"),
    ("legacy Yarn name",         M + "noise/NoiseListeners.java", "ServerPlayer && !player.isSpectator()) {\n\t\t\t\tdouble loudness", "ServerPlayer && !player.isSpectator()) {\n\t\t\t\tServerWorld w = null;\n\t\t\t\tdouble loudness", "legacy"),
]

detected = 0
for name, rel, old, new, area in MUTATIONS:
    tmp = Path(tempfile.mkdtemp())
    dst = tmp / "p"
    shutil.copytree(ROOT, dst, ignore=shutil.ignore_patterns(".git", "build", ".gradle"))
    f = dst / rel
    text = f.read_text(encoding="utf8")
    if old not in text:
        print(f"[ SKIP ] {name}: pattern not found in {rel}")
        continue
    f.write_text(text.replace(old, new, 1), encoding="utf8")
    out = subprocess.run([sys.executable, str(dst / "tools/verify.py"), CORPUS], capture_output=True, text=True)
    hit = [l for l in out.stdout.splitlines() if l.startswith(("[FAIL]", "[WARN]")) and area in l]
    ok = bool(hit)
    detected += ok
    print(f"[{'CAUGHT' if ok else 'MISSED'}] {name:26} -> {hit[0][:110] if hit else 'nothing reported in area ' + area}")
    shutil.rmtree(tmp, ignore_errors=True)
print(f"\n{detected}/{len(MUTATIONS)} mutations detected")
sys.exit(0 if detected == len(MUTATIONS) else 1)
