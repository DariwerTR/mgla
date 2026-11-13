#!/usr/bin/env bash
# Compiles and runs the Minecraft-free self test (needs a JDK 17+). Uses tiny Gson stubs ONLY for compile checking.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
javac --release 17 -Xlint:all -d "$OUT" \
  "$ROOT"/tools/selftest/stubs/com/google/gson/*.java \
  "$ROOT"/src/main/java/com/whitemist/core/*.java \
  "$ROOT"/src/main/java/com/whitemist/config/*.java \
  "$ROOT"/tools/selftest/SelfTest.java
java -cp "$OUT" SelfTest
