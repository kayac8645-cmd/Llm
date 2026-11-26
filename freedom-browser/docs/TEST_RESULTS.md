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

---

# 1.1 UI upgrade – results (2026-11-25)

| Suite | Result |
|---|---|
| Shared (JVM) | **27 / 27 passed** (+ `TopSitesTest`: frecency ranking, exclusions, inline completion, suggestion ranking) |
| Shared JS facade | passed |
| Android unit + Robolectric | **42 / 42 passed** (+ shortcuts add/move/remove, recently-visited + suggestions + hide, new-tab-page rendering) |
| Native block-list (C) | 17 / 17 |
| x86 verification | 8 / 8 PE files PE32/i386 (`docs/build-logs/windows-x86-verification.txt`) |
| Windows E2E under Wine 11 (virtual desktop) | all previous checks still pass; new screenshots: title bar + window buttons, new-tab page, inline autocomplete with engine chips, tab context menu over the page, maximize/restore, profile menu, settings |
| Manual Wine checks | tab drag-and-drop reordering, bookmarks bar, F11 full screen on/off, maximize/restore icon |
| Installer 1.1.0 | silent install (21 entries) → uninstall OK |

Defect found by the new tests and fixed: rapid consecutive shortcut edits on Android overwrote each other (now an
atomic read-modify-write inside the DataStore transaction).

### DuckDuckGo header hiding (1.1.0 update)

| Check | Result |
|---|---|
| `:shared:jvmTest` (adds `SiteTweaksTest`: URL detection, script guards) | 29 / 29 passed |
| `:androidApp:testDebugUnitTest` | 42 / 42 passed |
| Playwright (Chromium) on a mock built from the live DOM structure, phone + desktop | logo, Duck.ai, menu hidden; search form, tabs incl. "Ask Duck.ai", result favicons, results visible; home page untouched |
| Lumo Windows x86 under Wine on live `duckduckgo.com/?q=GitHub` | logo and menu hidden; search box, tabs and results visible |
| Android on a real device | not verified (no device); uses `addDocumentStartJavaScript`, fallback `evaluateJavascript` on page finish |

### Windows 1.1.1 fixes

| Defect | Cause | Fix | Verified (Wine 11, x86 build) |
|---|---|---|---|
| App did not exit: window stayed open, processes kept running | UI-browser close detection used `is_same()`, which consumes a reference and did not match, so the open-browser count never reached 0; CEF's close notification (WM_CLOSE to the main window) was ignored, so browser windows were never destroyed | compare browser ids; let the main window be destroyed on CEF's notification; hide the window at once; 5 s safety quit | ✕ button and Alt+F4 with 3 tabs (1 private): all Lumo processes gone in 1–2 s |
| Data written in the last 0.4–1.5 s before closing was lost | debounced UI writes never ran | `beforeClose` event: UI flushes pending writes, then closes (1.5 s timeout); writes during close are synchronous | history/session present after close |
| Closing one tab could start closing the app | default CEF close notification targets the top-level window | `do_close` destroys only the tab's own window while the app runs | Ctrl+W then Ctrl+Shift+T in full E2E run |
| Sluggish UI on busy pages | every native event (progress, each blocked request, downloads) re-rendered the whole UI; history file written with a flush on the UI thread | renders coalesced to one per frame; tab strip rebuilt only when changed; top sites cached; blocked counters batched (400 ms); storage writes on CEF's file thread | full E2E run (19 screenshots) passes; menu open incl. page snapshot ≈ 65 ms |

Open items are unchanged: no physical Android device and no real 32-bit Windows 10 PC were available.
