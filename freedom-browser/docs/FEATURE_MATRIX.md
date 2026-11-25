# Feature matrix

✅ implemented · 🟡 partial (limitation explained) · ❌ not supported / not implemented · — not applicable

"Verified" names how the behaviour was checked in this delivery: **U** unit/JVM test, **R** Robolectric (Android
framework + Compose rendering), **E** Windows end-to-end run under Wine (x86 binaries), **B** build/inspection only.
Nothing was run on a physical Android device or on a physical 32-bit Windows installation (see TEST_RESULTS.md).

| Feature | Android | Windows x86 | Verified | Notes |
|---|---|---|---|---|
| **Address & search bar** |||||
| URL or search detection, IDN (punycode), URL encoding | ✅ | ✅ | U, E | shared `UrlResolver`; `javascript:`/`file:`/`data:` input is treated as a search |
| Google / Bing / DuckDuckGo / Brave Search | ✅ | ✅ | U | default DuckDuckGo |
| Suggestions from history & bookmarks | — | ✅ | E | Android shows plain input |
| Loading progress, secure-connection indicator | ✅ | ✅ | R, E | homograph-safe Unicode display (Android); Windows shows punycode |
| Back / forward / reload / stop / home | ✅ | ✅ | R, E | |
| Copy URL / share | ✅ / ✅ | ✅ / — | E | Windows: copy link (no share sheet) |
| **Tabs** |||||
| Multiple tabs, fast switching, close, close all | ✅ | ✅ | U, R, E | no fixed cap; Android keeps 2–8 live WebViews by RAM class and discards the rest with saved state |
| Background tabs ("open in background") | ✅ | ✅ | U, E | long-press menu / middle-click, Ctrl+click |
| Private tabs | 🟡 | ✅ | U, R, E | Android needs a WebView with the multi-profile feature; on older WebView private tabs are disabled with an explanation instead of leaking cookies |
| Reopen recently closed (25) | ✅ | ✅ | U, E | private tabs never remembered |
| Restore normal tabs after restart / crash | 🟡 | 🟡 | U, E | URLs and titles restored; per-tab back/forward history is not restored across restarts |
| **Downloads** |||||
| List with name, size, progress, speed, ETA | ✅ | ✅ | U, E | |
| Pause / resume | ✅ | ✅ | U | Android: only offered when the server supports byte ranges; Windows: CEF item pause/resume during the session |
| Cancel / retry | ✅ | ✅ | U, E | Windows retry restarts the transfer |
| Open / share completed files | ✅ | 🟡 | B, E | Windows: open + show in folder (no share) |
| Choose download location | ✅ | ✅ | B | Android: SAF folder; Windows: folder picker or "ask where to save" |
| Download history, clear history | ✅ | ✅ | U, E | private downloads leave the list when the private session ends |
| Safe duplicate file names | ✅ | ✅ | U, E | `name (1).ext` |
| Dangerous file types: warning, no auto-open | ✅ | ✅ | U, E | Windows files also receive Mark-of-the-Web (Zone.Identifier) |
| `blob:` downloads | ❌ | ✅ | E | Android WebView cannot hand blob URLs to a download manager |
| **Bookmarks & history** |||||
| Add / edit / delete / folders / move (cycle-safe) | ✅ | ✅ | U, E | |
| Search history, delete entries, clear by time range | ✅ | ✅ | U, E | |
| Private browsing writes no history | ✅ | ✅ | U, E | checked in unit tests and in the Windows E2E run |
| **Page tools** |||||
| Desktop-site mode | ✅ | — | U | desktop UA + UA client hints |
| Zoom | ✅ | ✅ | B | Android text zoom + pinch; Windows Chrome zoom steps |
| Find in page | ✅ | ✅ | B | |
| Reader mode | ❌ | ❌ | — | future work; not shipped rather than shipped unreliable |
| Print / save as PDF | ✅ | ✅ | B | Android system print dialog ("Save as PDF") |
| Screenshot | 🟡 | 🟡 | B | visible area only |
| Translation | 🟡 | 🟡 | B | opens the page through a user-configurable provider URL after a disclosure; no built-in/offline translation |
| **Privacy & security** |||||
| Ad/tracker blocking on/off, per-site exceptions, counter | 🟡 | 🟡 | U, E | domain-based (bundled list + optional maintained list); no cosmetic filtering; ads served from first-party domains or inside video streams are not blocked |
| Pop-up blocking + per-site allow | ✅ | 🟡 | U, E | Windows: Chromium blocks gesture-less pop-ups silently, so there is no "pop-up blocked" notice |
| Third-party cookie control | ✅ | ✅ | B | Windows via Chromium cookie-controls preference (not covered by the E2E run) |
| Clear cookies, cache, history, site data | 🟡 | 🟡 | U | Android WebView and CEF clear cookies/cache for all time only; Windows clears site storage for every origin in history/open tabs |
| Camera / microphone / location permissions | ✅ | ✅ | R, B | per-site Ask/Allow/Block, plus the OS permission on Android |
| Web notifications | ❌ | ❌ | — | not supported by Android WebView or the CEF Alloy runtime; requests are denied |
| HTTPS indicator; certificate errors never bypassed | ✅ | ✅ | R, E | |
| Safe Browsing | ✅ | ❌ | B | Android: WebView Safe Browsing (switchable); CEF has no Safe Browsing service |
| No JavaScript bridge exposed to websites | ✅ | ✅ | B | Windows IPC is reachable only from the UI browser |
| Chromium renderer sandbox | ✅ | ✅ | E | Windows via CEF `bootstrap.exe`; `freedom-compat.exe` runs without sandbox (troubleshooting only) |
| **Settings** |||||
| Search engine, homepage, download location, privacy, blocking, clear data, theme, about | ✅ | ✅ | R, E | light/dark/system, dark default |
| **Reliability** |||||
| Offline / DNS / timeout / TLS / invalid URL / unavailable site messages, retry, copy details | ✅ | ✅ | U, R, E | details contain no query string |
| Renderer crash recovery | ✅ | ✅ | B | Android `onRenderProcessGone`; Windows `on_render_process_terminated` |
| Low-memory handling | ✅ | 🟡 | U | Windows relies on lazy tab creation and Chromium's own memory management |
| Diagnostic log without browsing contents | ✅ | ✅ | E | Android opt-in; Windows `freedom.log` contains no URLs |
| **Other** |||||
| HTTP authentication dialog | ✅ | ✅ | B | |
| File upload (`<input type=file>`) | ✅ | ✅ | B | Windows uses CEF's default dialog |
| Fullscreen video | ✅ | ✅ | B | |
| External apps (`intent:`, `mailto:`, `tel:`) | ✅ | 🟡 | B | always confirmed; Windows only `mailto:`/`tel:` |
| Optional AI: summarize / ask about page | ✅ | ❌ | U | Android only, off by default, user endpoint & key (Keystore-encrypted), disclosure before every request |
| AI: explain selected text, local models | ❌ | ❌ | — | not implemented |
| VPN / proxy / tunnelling / policy bypass | ❌ | ❌ | — | intentionally absent |
