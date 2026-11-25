# Privacy policy – Freedom Browser 1.0

**Short version:** no analytics, no telemetry, no accounts. Your browsing data stays on your device.

## What stays on the device

| Data | Android | Windows |
|---|---|---|
| History, bookmarks, open tabs, download list, site settings, preferences | app-private storage (Room / DataStore) | profile folder (`profile\` in portable mode, `%LOCALAPPDATA%\FreedomBrowser\User Data` when installed) |
| Cookies, cache, site storage | Android System WebView profile | Chromium profile |
| Optional AI API key | encrypted with an Android Keystore key | — |
| Diagnostic log | opt-in, URLs reduced to scheme/host/path, no page content | `freedom.log`: start/stop and errors, no URLs |

Android cloud backup and device-to-device transfer are disabled for all app data.

## What leaves the device – only through your actions

* **Pages you open** go to those websites, like in any browser.
* **Searches** go to the search engine you selected (default DuckDuckGo).
* **Safe Browsing** (Android only, on by default, can be switched off): URL checks performed by Google through
  Android System WebView.
* **Filter-list update** – only when you press *Update*: the list is downloaded from the URL shown in Settings
  (default: StevenBlack/hosts on GitHub).
* **Translation** – only after a confirmation dialog: the page address is opened through the provider you configure.
* **AI assistance** (Android, off by default) – only after a confirmation dialog for each request: the page title and up
  to 12,000 characters of visible text are sent to the endpoint you configured. No key is bundled.
* **Chromium components (Windows)** – the embedded Chromium may download security data such as certificate
  revocation lists from Google's component update service. No browsing data is sent.

## Private tabs

* History is never written.
* Android: a separate WebView profile holds cookies, storage and cache; it is deleted when the last private tab closes.
  If the installed WebView lacks profile support, private tabs are disabled rather than offered with shared cookies.
* Windows: an in-memory Chromium profile; it is discarded when the last private tab closes.
* Files you download in a private tab remain in your downloads folder; bookmarks you create remain.
* Your network administrator, internet provider and the sites you visit can still see your activity.

## Network

Freedom uses the device's ordinary connection. It contains no VPN, proxy, tunnelling or traffic-disguise features and
cannot bypass network-administrator, school (e.g. MEB) or website restrictions.

## Contact / changes

Changes to this policy are made in this file in the source repository.
