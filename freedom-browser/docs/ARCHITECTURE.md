# Architecture

Freedom Browser is one repository with three layers:

```
                ┌───────────────────── shared (Kotlin Multiplatform) ─────────────────────┐
                │ UrlResolver · SearchEngine · IDN · DisplayUrl · BlockList · FileNames   │
                │ Formatters · HistoryRange · BookmarkTree · PageErrors · SiteSettings    │
                │ DesignTokens · AiAssist                                                 │
                └──────────────┬──────────────────────────────────────┬───────────────────┘
                     JVM bytecode                              ES module (Kotlin/JS)
                ┌──────────────▼───────────────┐      ┌────────────────▼──────────────────┐
                │ androidApp (Kotlin, Compose) │      │ windows/ui (HTML/CSS/JS in CEF)   │
                │ MVVM · Room · DataStore      │      │      ▲ fetch("freedom://ui/ipc")  │
                │ Android System WebView       │      │      ▼ execute_java_script events │
                └──────────────────────────────┘      │ windows/native-host (C, CEF C API)│
                                                      │ CEF 158 x86 (Chromium 158)        │
                                                      └───────────────────────────────────┘
```

Shared logic is written once in Kotlin and tested on the JVM (`shared/src/commonTest`). Android consumes the JVM
artifact; the Windows UI imports the same code compiled to JavaScript (`FreedomCore` facade). The only rule that
must exist twice – ad-block matching, which runs on CEF's IO thread in C – is checked against the same conformance
vectors (`shared/testdata/blocklist-vectors.txt`) by both the Kotlin and the C test.

## Android

| Concern | Implementation |
|---|---|
| UI | Jetpack Compose, Material 3 colour scheme generated from `DesignTokens` (dark default, light, system) |
| State | `BrowserViewModel` (tabs, prompts, screens) + pure `TabList` reducer; `LibraryViewModel` (bookmarks/history/downloads) |
| Engine | `WebViewManager` implements `TabEngine`; one `WebView` per live tab, created with a `MutableContextWrapper` so it follows the Activity |
| Memory | LRU budget of live WebViews derived from `ActivityManager.memoryClass` (2–8); background tabs are discarded with `saveState()` and restored on demand; `onTrimMemory` discards aggressively. Tab count itself is not capped. |
| Private tabs | separate WebView **profile** (`androidx.webkit` `MULTI_PROFILE`): own cookies, storage, cache; profile deleted when the last private tab closes; history never written (checked twice: ViewModel and `HistoryRepository`) |
| Persistence | Room (`bookmarks`, `history`, `downloads`, `site_settings`, `session_tabs`), DataStore (settings), Android Keystore AES-GCM (`SecretStore`, AI key). Cloud backup and device transfer are disabled. |
| Downloads | `DownloadController`: OkHttp, `Range`/`If-Range` resume when the server supports it, MediaStore `Download/` or a SAF folder, foreground service (`dataSync`) with one progress notification; cookies are read from the tab's profile at request time and never stored |
| Ad blocking | `shouldInterceptRequest` → shared `BlockList.shouldBlock` (sub-resources only, per-site exceptions) |
| Security | no `addJavascriptInterface`; file/content access off; `javascript:`, `file:`, `content:`, top-level `data:`/`blob:` navigations refused; SSL errors always cancelled; Safe Browsing on; external `intent:` links stripped of component/selector and confirmed by the user |

## Windows x86

### Process & window model

```
FreedomBrowser.exe  (CEF bootstrap.exe, MSVC-built by the CEF project)
  └─ loads FreedomBrowser.dll (our code, MinGW i686) → RunWinMain(sandbox_info)
       ├─ main Win32 window
       │    ├─ UI browser  (freedom://ui/index.html)  – toolbar, tabs, panels, dialogs
       │    └─ content browsers (child HWNDs, one per tab, Alloy runtime style)
       └─ Chromium sub-processes (renderer, GPU, network…) – sandboxed
```

`freedom-compat.exe` contains the same code linked as a normal executable without the sandbox, for troubleshooting.

When the UI needs the whole window (start page, error page, panels, menus, dialogs, suggestions) it asks the host
to expand the UI browser and hide the active content browser; otherwise only the toolbar strip is shown above the
page. Info bars (permission requests, downloads, pop-ups, sign-in) live inside the toolbar so the page stays visible.

### IPC and its security model

* The custom scheme `freedom://` is registered as *standard, secure, CORS/fetch enabled* and **display-isolated** –
  web pages can neither navigate to nor embed it.
* The scheme handler factory serves files and the IPC endpoint **only** when the requesting browser's client is the
  UI client object. Content browsers get `NULL` (request fails). No V8 bindings or process messages exist, so web
  content has no bridge at all.
* The UI browser itself refuses any main-frame navigation outside `freedom://ui/`, refuses dropped files, and uses a
  strict Content-Security-Policy (`script-src 'self'`, no inline handlers). All strings that come from the web
  (titles, file names, hosts) are HTML-escaped before rendering.
* IPC requests run on the CEF UI thread; responses are JSON. Native → UI events use `execute_java_script` on the UI
  frame only.

### Threading

* UI thread: window management, tabs, downloads, permissions, IPC commands.
* IO thread: `on_before_resource_load` ad-block decision. Shared state (block list, exceptions, per-tab page host,
  tab list) is protected by an `SRWLOCK`; the block list is swapped atomically.

### Feature mapping to CEF

| Feature | CEF API |
|---|---|
| Ad/tracker blocking | `cef_resource_request_handler_t.on_before_resource_load` → `RV_CANCEL` |
| Private tabs | `cef_request_context_create_context` with empty cache path (in-memory), released after the last private tab |
| Downloads | `cef_download_handler_t` (+ pause/resume/cancel via `cef_download_item_callback_t`) |
| Permissions | `cef_permission_handler_t` (media access + permission prompts); notifications denied |
| Pop-ups | `on_before_popup` (link targets → new tab; `window.open` with features → embedded popup tab keeping `opener`) and Chromium content setting `POPUPS` for the global switch and per-site exceptions |
| Third-party cookies | Chromium preference `profile.cookie_controls_mode` |
| Clear data | cookie manager, `clear_http_cache`, DevTools `Storage.clearDataForOrigin` per known origin |
| Screenshot | DevTools `Page.captureScreenshot` |
| PDF / print | `print_to_pdf`, `print` |
| Certificate errors | `on_certificate_error` returns 0 (cancel, never bypass) → error page |
| Shortcuts | `cef_keyboard_handler_t.on_pre_key_event` forwarded to the UI |

### Reference counting (C API)

Every struct handed to CEF is allocated with `FR_NEW` (hidden atomic ref-count header, `base.size` = size of the CEF
struct). Rules followed throughout: objects passed *to* CEF transfer one reference; getters add a reference before
returning; every ref-counted parameter received in a callback is released (or kept deliberately); scoped types such
as `cef_scheme_registrar_t` are never released.
