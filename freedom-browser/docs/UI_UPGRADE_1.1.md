# Lumo Browser 1.1 – professional UI upgrade

Chrome is the usability reference; Lumo keeps its own identity (logo, name, colours, icons). No Google logos,
artwork or assets are used.

## Design system (shared `DesignTokens`)

| Token | Value |
|---|---|
| Primary background | `#0B0C0E` |
| Secondary background | `#15171B` |
| Toolbar background | `#191C21` |
| Accent | `#2EC4B6` |
| Primary / secondary text | `#F5F5F5` / `#A0A5AD` |
| Borders | `#292D33` |

Android receives the tokens as a Material 3 colour scheme, Windows as CSS custom properties (same Kotlin source).

## Windows (x86)

* **Frameless window with Chrome-style title area**: tabs live in the title bar; native minimize / maximize-restore /
  close buttons; drag the empty title area to move, double-click to maximize; native resize borders, snapping and
  shadows are kept (`WM_NCCALCSIZE`). F11 toggles full screen; HTML5 video full screen hides Lumo's UI.
* **Tabs**: rounded active tab joined to the toolbar, separators, favicons, loading spinner, close buttons,
  new-tab button, background tabs, **drag-and-drop reordering**, **pin/unpin**, **duplicate**, right-click menu
  (new tab to the right, reload, duplicate, pin, bookmark, close, close others, close to the right, reopen closed).
* **Toolbar**: back, forward, reload/stop, home, omnibox (security indicator, ad-block counter, zoom badge,
  bookmark star), downloads (activity ring), **profile area** (local profile menu), main menu.
* **Omnibox**: URL/search detection, **inline autocompletion** from history and bookmarks, suggestion list,
  **search-provider chips** ("search with Google / Bing / DuckDuckGo / Brave"), keyboard navigation, select-all on focus.
* **Bookmarks bar** (Ctrl+Shift+B or menu): bookmarks and folder drop-downs, drag-and-drop reordering and moving
  into folders, right-click menus (open, new tab, private tab, edit, delete; open all for folders).
* **Main menu**: new tab, new private tab, history, downloads, bookmarks, bookmarks-bar toggle, zoom (− % + and
  full screen), print, save as PDF, find, screenshot, copy link, translate, settings, **About Lumo**, exit.
* **Menus over the page**: page content is a native window, so Lumo grabs a snapshot of the visible page
  (DevTools `Page.captureScreenshot`, JPEG) and shows menus, dialogs and suggestions on top of it.
* **New tab page**: logo, large search field, user **shortcuts (add / remove / drag to reorder)**, **recently
  visited** sites (frecency ranking from history, can be hidden per site or switched off), downloads/bookmarks/history.
* **Context menus in pages**: links (new tab, private tab, copy, save), images (open, save), selected text
  (**search the web**), plus Chromium's editing items.
* Restrained animations: tab open/close, menu/panel/info-bar transitions, progress bar; `prefers-reduced-motion`
  respected.

## Android

* Toolbar colour from the new palette; address bar with security indicator, tab counter and menu; bottom bar
  with back, forward, home, reload/stop and menu.
* **Address suggestions** (history + bookmarks), **inline completion**, search-provider chips.
* **New tab page** with shortcuts (add, long-press to move/remove) and recently visited sites (long-press to pin or hide).
* **Visual tab switcher**: adaptive grid of cards with page previews (captured when leaving a tab, RGB_565 at 35 %
  scale), favicons and close buttons; private tabs tab; recently closed.
* **Splash screen** (Android 12+) with the Lumo mark; launcher icon with adaptive + monochrome layers.
* **Tablet / landscape (≥ 600 dp)**: navigation moves into the top bar and the bottom bar is removed.
* **About Lumo** dialog.

## Not implemented (and why)

| Item | Reason |
|---|---|
| Extensions panel | Android System WebView has no extension support; the CEF Alloy runtime used on Windows cannot install or run Chrome Web Store extensions. A panel would be a fake control, so it is not shown. |
| "New private window" | Lumo has one window per process; private browsing is provided as private **tabs** (separate in-memory profile). |
| Profile accounts / sync | The profile area shows the local profile and its actions; there are no accounts or cloud sync by design (privacy). |
| Remote search suggestions | Would send every keystroke to the search engine; Lumo suggests only from local history/bookmarks. |
| Tab previews on Windows, tab groups, tab audio mute | Not part of 1.1. |
| Android pinning of tabs | Not implemented on Android (Windows only). |
| Windows splash screen | Start-up shows the window immediately; a splash would only add delay. |
