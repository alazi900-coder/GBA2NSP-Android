#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export DEVKITPRO="${DEVKITPRO:-/opt/devkitpro}"
export DEVKITA64="${DEVKITA64:-$DEVKITPRO/devkitA64}"
export PATH="$DEVKITPRO/tools/bin:$DEVKITA64/bin:$PATH"

for tool in git cmake python3 elf2nso npdmtool; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: missing $tool" >&2; exit 1; }
done
[ -d "$DEVKITA64" ] || { echo "ERROR: devkitA64 not found: $DEVKITA64" >&2; exit 1; }

mkdir -p "$ROOT/vendor" "$ROOT/build/exefs" "$ROOT/dist"
if [ ! -d "$ROOT/vendor/mgba/.git" ]; then
  git clone --depth 1 --branch 0.10.5 https://github.com/mgba-emu/mgba.git "$ROOT/vendor/mgba"
fi

python3 "$ROOT/patch_mgba_switch.py"
rm -rf "$ROOT/vendor/mgba/build-gba2nsp" "$ROOT/build/exefs"
mkdir -p "$ROOT/build/exefs" "$ROOT/dist"

cmake -S "$ROOT/vendor/mgba" -B "$ROOT/vendor/mgba/build-gba2nsp" \
  -DCMAKE_TOOLCHAIN_FILE="$ROOT/vendor/mgba/src/platform/switch/CMakeToolchain.txt" \
  -DBUILD_QT=OFF -DBUILD_SDL=OFF -DBUILD_TEST=OFF -DBUILD_SUITE=OFF \
  -DBUILD_GLES3=ON -DUSE_ZLIB=ON -DUSE_PNG=ON -DENABLE_DEBUGGERS=OFF
cmake --build "$ROOT/vendor/mgba/build-gba2nsp" --parallel

ELF="$(find "$ROOT/vendor/mgba/build-gba2nsp" -type f -name '*.elf' | head -n 1)"
[ -n "$ELF" ] || { echo "ERROR: mGBA ELF not found" >&2; exit 1; }
elf2nso "$ELF" "$ROOT/build/exefs/main"
npdmtool "$ROOT/runtime-npdm.json" "$ROOT/build/exefs/main.npdm"

python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys, zipfile, hashlib
root = Path(sys.argv[1])
out = root / "dist" / "GBA-Switch-Runtime.zip"
npdm = root / "build" / "exefs" / "main.npdm"
if not npdm.is_file() or npdm.stat().st_size < 0x300:
    raise SystemExit("ERROR: generated main.npdm is missing or too small")
npdm_bytes = npdm.read_bytes()
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.write(root / "build" / "exefs" / "main", "exefs/main")
    z.write(npdm, "exefs/main.npdm")
    lic = root / "vendor" / "mgba" / "LICENSE"
    if lic.is_file():
        z.write(lic, "LICENSES/mGBA-LICENSE.txt")
    readme = root / "README_AR.md"
    if readme.is_file():
        z.write(readme, "README_AR.md")
sha = hashlib.sha256(out.read_bytes()).hexdigest()
(root / "dist" / "GBA-Switch-Runtime.sha256").write_text(f"{sha}  {out.name}\n", encoding="ascii")
(root / "dist" / "BUILD-REPORT.txt").write_text(
    "\n".join([
        "Artifact: GBA Switch Runtime",
        "mGBA version: 0.10.5",
        "Runtime ROM path: romfs:/game.gba",
        "SaveData: uses current ProgramId from svcGetInfo(InfoType_ProgramId)",
        "main.npdm bytes: " + str(len(npdm_bytes)),
        "Runtime ZIP bytes: " + str(out.stat().st_size),
        "SVC 0x26: declared in runtime-npdm.json",
        "SVC 0x27: declared in runtime-npdm.json",
        "SVC 0x28: declared in runtime-npdm.json",
        "SVC 0x29: declared in runtime-npdm.json",
        "SHA256: " + sha,
        ""
    ]),
    encoding="utf-8"
)
print("[OK]", out)
print("[OK] sha256", sha)
PY
