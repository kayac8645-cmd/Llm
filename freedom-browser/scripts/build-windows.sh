#!/usr/bin/env bash
# Builds Lumo Browser for Windows x86 (32-bit): portable ZIP + installer EXE.
# Requirements (Linux): JDK 17+, i686-w64-mingw32-gcc/windres, gcc, nsis (makensis), zip, curl, python3, node (optional check).
# Usage: scripts/build-windows.sh [--skip-gradle]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="1.1.0"
CEF_VERSION="158.0.6+g23f1736+chromium-158.0.8127.89"
CEF_NAME="cef_binary_${CEF_VERSION}_windows32_minimal"
CEF_SHA1="d8baa8eb95b4e4c4a913be7cdfc8f9f1b874489c"   # from cef-builds.spotifycdn.com/<file>.sha1
CACHE="${FREEDOM_CACHE:-$ROOT/build-cache}"
DIST="$ROOT/dist"
STAGE="$DIST/stage/LumoBrowser"
LOG="$DIST/windows-build.log"

mkdir -p "$CACHE" "$DIST"
: > "$LOG"
log() { echo "[build-windows] $*" | tee -a "$LOG"; }

# 1. CEF x86 binary distribution (official Spotify CDN), verified by SHA-1 -------------------------
ARCHIVE="$CACHE/$CEF_NAME.tar.bz2"
if [[ ! -f "$ARCHIVE" ]]; then
  log "downloading $CEF_NAME"
  curl -fL --retry 4 -o "$ARCHIVE.part" "https://cef-builds.spotifycdn.com/$(python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$CEF_NAME.tar.bz2")"
  mv "$ARCHIVE.part" "$ARCHIVE"
fi
echo "$CEF_SHA1  $ARCHIVE" | sha1sum -c - | tee -a "$LOG"
CEF_DIR="$CACHE/$CEF_NAME"
[[ -d "$CEF_DIR" ]] || tar -xjf "$ARCHIVE" -C "$CACHE"

# 2. Shared Kotlin logic compiled to JavaScript ------------------------------------------------------
if [[ "${1:-}" != "--skip-gradle" ]]; then
  log "building shared Kotlin/JS module"
  (cd "$ROOT" && ./gradlew --console=plain -q :shared:jvmTest :shared:jsBrowserProductionLibraryDistribution) 2>&1 | tee -a "$LOG"
fi
SHARED_JS="$ROOT/shared/build/dist/js/productionLibrary"
[[ -f "$SHARED_JS/freedom-shared.mjs" ]] || { echo "shared JS missing; run without --skip-gradle"; exit 1; }
if command -v node >/dev/null; then node "$ROOT/scripts/verify-shared-js.mjs" "$SHARED_JS" | tee -a "$LOG"; fi

# 3. Native host (MinGW-w64 i686) + native unit test ------------------------------------------------
log "compiling native host (i686)"
make -C "$ROOT/windows/native-host" clean >/dev/null
make -C "$ROOT/windows/native-host" CEF_DIR="$CEF_DIR" all test 2>&1 | tee -a "$LOG"

# 4. Stage the application folder -------------------------------------------------------------------
log "staging"
rm -rf "$DIST/stage"
mkdir -p "$STAGE/ui/shared" "$STAGE/ui/blocklist" "$STAGE/locales" "$STAGE/licenses"
cp "$CEF_DIR/Release/bootstrap.exe" "$STAGE/LumoBrowser.exe"          # CEF's sandbox bootstrap, loads LumoBrowser.dll
cp "$ROOT/windows/native-host/build/LumoBrowser.dll" "$STAGE/"
cp "$ROOT/windows/native-host/build/freedom-compat.exe" "$STAGE/"
for f in libcef.dll chrome_elf.dll d3dcompiler_47.dll vk_swiftshader.dll vk_swiftshader_icd.json vulkan-1.dll v8_context_snapshot.bin; do
  cp "$CEF_DIR/Release/$f" "$STAGE/"
done
cp "$CEF_DIR/Resources/"*.pak "$CEF_DIR/Resources/icudtl.dat" "$STAGE/"
cp "$CEF_DIR/Resources/locales/en-US.pak" "$CEF_DIR/Resources/locales/tr.pak" "$STAGE/locales/"
cp "$ROOT/windows/ui/"*.html "$ROOT/windows/ui/"*.css "$ROOT/windows/ui/"*.js "$ROOT/windows/ui/"*.png "$STAGE/ui/"
cp "$SHARED_JS/"*.mjs "$STAGE/ui/shared/"
cp "$ROOT/shared/assets/blocklist/freedom-default.txt" "$STAGE/ui/blocklist/"
cp "$ROOT/LICENSE" "$STAGE/LICENSE.txt"
cp "$CEF_DIR/LICENSE.txt" "$STAGE/licenses/CEF-LICENSE.txt"
cp "$CEF_DIR/CREDITS.html" "$STAGE/CREDITS.html" 2>/dev/null || true
cp "$ROOT/windows/README-Windows.txt" "$STAGE/README.txt"

# 5. Architecture verification: every PE file must be 32-bit x86 --------------------------------------
REPORT="$DIST/windows-x86-verification.txt"
{
  echo "Lumo Browser $VERSION - Windows x86 artifact verification ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  echo "CEF $CEF_VERSION (windows32), archive SHA-1 $CEF_SHA1 verified"
  echo
  fail=0
  while IFS= read -r -d '' f; do
    desc="$(file -b "$f")"
    machine="$(i686-w64-mingw32-objdump -f "$f" | awk -F'architecture: ' '/architecture/{split($2,a,",");print a[1]}')"
    if [[ "$desc" == PE32\ * && "$desc" == *"Intel 80386"* && "$machine" == "i386" ]]; then status=OK; else status=FAIL; fail=1; fi
    printf '%-4s %-28s %s | objdump: %s\n' "$status" "$(basename "$f")" "$desc" "$machine"
  done < <(find "$STAGE" -type f \( -iname '*.exe' -o -iname '*.dll' \) -print0 | sort -z)
  echo
  echo "Imports of LumoBrowser.dll:"; i686-w64-mingw32-objdump -p "$STAGE/LumoBrowser.dll" | awk '/DLL Name/{print "  "$3}'
  echo "Exports of LumoBrowser.dll:"; i686-w64-mingw32-objdump -p "$STAGE/LumoBrowser.dll" | sed -n '/\[Ordinal\/Name Pointer\] Table/,/^$/p' | grep -E '\[ *[0-9]+\]' | awk '{print "  "$NF}'
  echo
  [[ $fail == 0 ]] && echo "RESULT: all PE files are 32-bit x86 (PE32, i386)" || echo "RESULT: FAILED - non-x86 binary found"
} | tee "$REPORT" | tee -a "$LOG"
grep -q "RESULT: all PE files" "$REPORT"

# 6. Portable ZIP --------------------------------------------------------------------------------------
PORTABLE="$DIST/LumoBrowser-$VERSION-windows-x86-portable.zip"
rm -f "$PORTABLE"
touch "$STAGE/portable.flag"
(cd "$DIST/stage" && zip -qr9 "$PORTABLE" LumoBrowser)
rm "$STAGE/portable.flag"
log "portable: $PORTABLE"

# 7. Installer EXE (NSIS, x86 stub) --------------------------------------------------------------------
SETUP="$DIST/LumoBrowser-$VERSION-windows-x86-setup.exe"
makensis -V2 -DSTAGE="$STAGE" -DOUTFILE="$SETUP" -DVERSION="$VERSION" "$ROOT/windows/installer/freedom.nsi" | tee -a "$LOG"
echo "installer: $(file -b "$SETUP")" | tee -a "$REPORT" | tee -a "$LOG"

(cd "$DIST" && sha256sum "$(basename "$PORTABLE")" "$(basename "$SETUP")" > SHA256SUMS-windows.txt)
cat "$DIST/SHA256SUMS-windows.txt" | tee -a "$LOG"
log "done"
