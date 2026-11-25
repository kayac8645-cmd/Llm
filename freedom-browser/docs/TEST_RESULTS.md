# Test and build results (2026-11-25)

All results below were produced in the Linux build container used for this delivery. Logs and reports are in
`docs/build-logs/`. Nothing here is estimated: every line was observed.

## Environment

* Ubuntu 24.04 x86_64, OpenJDK 21, Gradle 9.9.2, Android SDK (platform 37.1, build-tools 36.0.0)
* MinGW-w64 i686 GCC 13, NSIS 3.09, Node 22, Wine 11.0 (WoW64) + Xvfb for Windows execution tests
* No KVM → **no Android emulator**; no physical Android device; no physical/virtual 32-bit Windows.

## Automated tests

| Suite | Command | Result |
|---|---|---|
| Shared domain logic (JVM) | `./gradlew :shared:jvmTest` | **23 / 23 passed** (URL resolver, IDN, display/redaction, file names, Content-Disposition, formatters, history ranges, bookmark cycles, error mapping, site settings, design tokens, AI request building, block-list conformance vectors) |
| Shared logic compiled to JS | `node scripts/verify-shared-js.mjs` | **passed** (facade used by the Windows UI) |
| Android unit + Robolectric | `./gradlew :androidApp:testDebugUnitTest` | **39 / 39 passed**: `TabListTest` 8, `BrowserViewModelTest` 11, `DownloadControllerTest` 8 (MockWebServer: completion, cookies/UA, duplicate names, pause → resume with `Range`/`If-Range`, server ignoring ranges, HTTP error + retry, cancel deletes partial file, `data:` URLs, private entries cleared), `RepositoriesTest` 6 (Room: private visits never stored, range clearing, search, bookmark folders/cycle/recursive delete, site settings), `UiScreenshotTest` 6 (Compose rendered with Robolectric native graphics) |
| Native block-list (C) vs shared vectors | `make -C windows/native-host test` | **17 / 17 queries, 0 failures** + 100k-domain list check |

Two real defects were found by these tests and fixed before delivery: download pause did not persist the exact
byte offset (a resume would have corrupted the file), and history recording depended on load timing.

## Builds

| Artifact | Result |
|---|---|
| `androidApp-debug.apk` | built (71.5 MB, debug-signed, `apksigner verify` OK) |
| `androidApp-release` | built with R8 (3.7 MB); delivered as `LumoBrowser-1.0.0-release.apk`, signed with a sideload test key |
| `LumoBrowser.dll` / `freedom-compat.exe` | built with `i686-w64-mingw32-gcc`, warnings-free with `-Wall -Wextra` |
| Windows portable ZIP / NSIS installer | built; SHA-256 in `docs/build-logs/SHA256SUMS-windows.txt` |
| x86 verification | **8 / 8 PE files are PE32 / i386**; DLL imports only system DLLs + `libcef.dll`; exports `RunWinMain` (`docs/build-logs/windows-x86-verification.txt`) |

## Windows x86 execution under Wine 11 (automated, `windows/tests/e2e/run-wine-e2e.sh`)

| Check | Observed |
|---|---|
| Sandboxed start (`LumoBrowser.exe` → CEF bootstrap → our DLL) | ✅ UI shown, `freedom.log`: `sandbox=on` |
| Real HTTPS website (`example.com`) | ✅ loaded, lock icon, title, history entry |
| Ad/tracker blocking | ✅ 2 of 2 tracker requests blocked, shield counter = 2 |
| Bookmark (Ctrl+D) persisted | ✅ `bookmarks.json` |
| Unreachable host → error page with Retry / Copy details | ✅ |
| Private tab: start page, browsing | ✅; private URL **absent** from `history.json` |
| Download completes with Mark-of-the-Web | ✅ `report.txt` (27,000 bytes) + `Zone.Identifier` |
| Dangerous file (`setup.exe`) | ✅ held as `.tmp` until the user chooses Keep/Discard; warning bar shown |
| Downloads / history panels, menu, site-info dialog, settings | ✅ screenshots |
| Pop-up without user gesture | ✅ blocked (target page never requested) |
| Close tab + Ctrl+Shift+T | ✅ reopened |
| Installer `/S` → files, Start-menu shortcuts, uninstall key | ✅; installed edition stores data in `%LOCALAPPDATA%\LumoBrowser` |
| Launch installed app | ✅ (`sandbox=on`) |
| Uninstaller `/S` | ✅ program folder and registry key removed (user data kept unless chosen) |

Defects found and fixed through this run: missing NULL-initialisation of CEF's post-data element array (crash on the
first IPC call), content pages defaulting to a dark background, keyboard focus not moving to the address bar after
Ctrl+T from a page, duplicate download rows (CEF reports progress before `on_before_download`), and a compositor
presentation delay for panels opened by keyboard shortcuts.

## Not verified (open items for the acceptance tests)

* Installing and running the APK on a physical Android 10+ device (WebView behaviour, permissions, downloads to
  MediaStore, printing, private profile deletion).
* Running the portable ZIP and the installer on a real 32-bit Windows 10 PC (see the checklist in
  `WINDOWS_X86_COMPATIBILITY.md`); GPU paths, Windows sandbox enforcement and SmartScreen behaviour differ from Wine.
* Third-party-cookie preference and per-site pop-up exceptions on Windows (code paths exist; not exercised by the
  automated scenario).
