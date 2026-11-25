#!/usr/bin/env bash
# End-to-end smoke test of the staged Windows x86 build under Wine (Linux CI/dev machines).
# This is NOT a substitute for testing on real 32-bit Windows 10; it exercises the same binaries
# through Wine's WoW64 layer with a virtual X display.
# Requirements: wine (>= 9, with 32-bit support), Xvfb, xdotool, ImageMagick (import), python3.
# Usage: windows/tests/e2e/run-wine-e2e.sh <staged LumoBrowser dir> <output dir> [extra browser flags...]
set -uo pipefail
APP="$(realpath "$1")"; OUT="$(realpath -m "$2")"; shift 2
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"
WORK="$(mktemp -d)"
cp -r "$APP" "$WORK/app" && touch "$WORK/app/portable.flag"
export DISPLAY="${DISPLAY:-:99}" WINEDEBUG=-all WINEDLLOVERRIDES="mscoree,mshtml="
pgrep -f "Xvfb $DISPLAY" >/dev/null || (Xvfb "$DISPLAY" -screen 0 1280x800x24 >/dev/null 2>&1 &)
python3 "$HERE/server.py" > "$OUT/server.log" 2>&1 & SERVER=$!
WINE="${WINE:-wine}"
DL="$(winepath -u 'C:\users\'"$USER"'\Downloads' 2>/dev/null || echo "$WINEPREFIX/drive_c/users/$USER/Downloads")"
mkdir -p "$DL"; rm -f "$DL"/report*.txt "$DL"/setup*.exe
(cd "$WORK/app" && timeout 600 "$WINE" LumoBrowser.exe --disable-gpu "$@" > "$OUT/browser.log" 2>&1) & BROWSER=$!
sleep 30
addr() { xdotool mousemove 600 90 click 1; sleep 0.7; xdotool key ctrl+a; xdotool type --delay 40 "$1"; xdotool key Return; }
shot() { import -window root "$OUT/$1.png"; echo "screenshot $1"; }
addr "127.0.0.1:8765/adtest.html"; sleep 12; shot 01-adblock-shield
xdotool key ctrl+d; sleep 2; shot 02-bookmark-star
xdotool key ctrl+t; sleep 3; xdotool type --delay 40 "nonexistent-freedom-test.invalid"; xdotool key Return; sleep 15; shot 03-dns-error-page
xdotool key ctrl+shift+n; sleep 3; shot 04-private-start
xdotool type --delay 40 "127.0.0.1:8765/private.html"; xdotool key Return; sleep 8; shot 05-private-page
xdotool key ctrl+t; sleep 3; xdotool type --delay 40 "127.0.0.1:8765/report.txt"; xdotool key Return; sleep 8; shot 06-download-complete
xdotool key ctrl+t; sleep 3; xdotool type --delay 40 "127.0.0.1:8765/setup.exe"; xdotool key Return; sleep 6; shot 07-dangerous-download-bar
xdotool key ctrl+j; sleep 3; shot 08-downloads-panel
xdotool key ctrl+h; sleep 3; shot 09-history-panel
xdotool key Escape; sleep 1; xdotool mousemove 1250 90 click 1; sleep 2; shot 10-menu
xdotool key Escape; sleep 1
xdotool key ctrl+t; sleep 3; xdotool type --delay 40 "127.0.0.1:8765/popup.html"; xdotool key Return; sleep 8; shot 11-popup-blocked
xdotool key ctrl+w; sleep 2; xdotool key ctrl+shift+t; sleep 6; shot 12-reopened-tab
xdotool mousemove 190 90 click 1; sleep 2; shot 13-site-info
xdotool key Escape; sleep 1; xdotool mousemove 1250 90 click 1; sleep 1; xdotool mousemove 1180 706 click 1; sleep 3; shot 14-settings
# Persistence check: data written by the UI
PROFILE="$WORK/app/profile/freedom-data"
{
  echo "== history.json contains adtest: $(grep -c adtest "$PROFILE/history.json" 2>/dev/null)"
  echo "== history.json contains private.html (must be 0): $(grep -c private.html "$PROFILE/history.json" 2>/dev/null || echo 0)"
  echo "== bookmarks.json: $(cat "$PROFILE/bookmarks.json" 2>/dev/null | head -c 300)"
  echo "== downloads folder:"; ls -la "$DL"
  echo "== popup without user gesture blocked (target must not be requested): $(grep -c popup-target "$OUT/server.log")"
  echo "== server requests:"; grep REQ "$OUT/server.log"
} | tee "$OUT/checks.txt"
kill $BROWSER 2>/dev/null; wineserver -k 2>/dev/null; kill $SERVER 2>/dev/null
rm -rf "$WORK"
