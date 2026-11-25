// Lumo Browser – Windows UI controller.
// Runs inside CEF at freedom://ui/ and talks to the native host through freedom://ui/ipc (POST JSON).
// Domain logic (URL resolution, search, file names, history ranges, design tokens...) comes from the
// shared Kotlin module compiled to JavaScript, so Android and Windows behave the same.
import { FreedomCore } from './shared/freedom-shared.mjs';
import { T } from './i18n.js';

const core = FreedomCore.getInstance();
const $ = sel => document.querySelector(sel);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const ICON = {
  lock: '<path d="M7 11V8a5 5 0 0 1 10 0v3"/><rect x="5" y="11" width="14" height="9" rx="2"/>',
  warn: '<path d="M12 4l9 16H3z"/><path d="M12 10v4"/><path d="M12 17v.5"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5"/><path d="M12 8v.5"/>',
  eye: '<path d="M3 12s3.5-6 9-6 9 6 9 6-3.5 6-9 6-9-6-9-6z"/><path d="M4 4l16 16"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18"/><path d="M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
  star: '<path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z"/>',
  file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
  dl: '<path d="M12 4v11"/><path d="M7 10l5 5 5-5"/><path d="M5 20h14"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  stop: '<path d="M6 6l12 12M18 6L6 18"/>',
  reload: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>',
  brand: '<path d="M72,38 A24,24 0 1,0 78,54" stroke="var(--accent)" stroke-width="6"/><path d="M44,62 L54,46 L62,56 L76,34" stroke="var(--text)" stroke-width="6"/>',
};
const svg = (name, extra = '') => `<svg class="i" viewBox="0 0 24 24" ${extra}>${ICON[name]}</svg>`;

// ------------------------------------------------------------------------------------------- IPC
async function ipc(cmd, args = {}) {
  try {
    const r = await fetch('freedom://ui/ipc', { method: 'POST', body: JSON.stringify({ cmd, ...args }) });
    return await r.json();
  } catch (e) {
    console.error('ipc failed', cmd, e);
    return { ok: false };
  }
}
const storage = {
  async get(key, fallback) {
    const r = await ipc('storage.get', { key });
    if (!r.ok || r.value == null) return fallback;
    try { return JSON.parse(r.value); } catch { return fallback; }
  },
  timers: {},
  set(key, value, delay = 400) {
    clearTimeout(this.timers[key]);
    this.timers[key] = setTimeout(() => ipc('storage.set', { key, value: JSON.stringify(value) }), delay);
  },
};

// ----------------------------------------------------------------------------------------- state
const DEFAULT_SETTINGS = {
  engine: core.defaultSearchEngine(), homeMode: 'start', homeUrl: '', restoreTabs: true, theme: 'dark',
  adblock: true, blockPopups: true, block3p: true, askDownload: false, downloadDir: '',
  blockListUrl: 'https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts', blockListUpdated: 0,
  translateTemplate: 'https://translate.google.com/translate?sl=auto&tl={lang}&u={url}',
};
const S = {
  info: {}, settings: { ...DEFAULT_SETTINGS }, tabs: [], selected: 0, nextId: 1, closed: [],
  bookmarks: [], history: [], sites: {}, downloads: [], panel: null, menu: false, dialog: null,
  suggest: null, find: null, infobars: [], editing: false,
};
const ZOOM_STEPS = [25, 33, 50, 67, 75, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300, 400, 500];
const MAX_HISTORY = 20000;

const tab = id => S.tabs.find(t => t.id === id);
const cur = () => tab(S.selected);
function newTabState(id, priv, url = '') {
  return { id, private: !!priv, url, title: '', loading: false, progress: 0, canBack: false, canForward: false, favicon: '',
    error: null, blocked: 0, zoom: 100, hasBrowser: false, pendingUrl: url };
}

// ------------------------------------------------------------------------------------- settings
function pushConfig() {
  const s = S.settings;
  const permissions = {};
  const exceptions = [];
  const popupAllow = [];
  for (const [site, v] of Object.entries(S.sites)) {
    permissions[site] = { camera: v.camera || 'ask', microphone: v.microphone || 'ask', location: v.location || 'ask' };
    if (v.adblockException) exceptions.push(site);
    if (v.popups === 'allow') popupAllow.push(site);
  }
  ipc('config', { adblock: s.adblock, adblockExceptions: exceptions, blockPopups: s.blockPopups, popupAllow,
    blockThirdPartyCookies: s.block3p, askDownload: s.askDownload, downloadDir: s.downloadDir, permissions });
}
function saveSettings() { storage.set('settings', S.settings); pushConfig(); }
function saveSites() { storage.set('sites', S.sites); pushConfig(); }
function applyTheme() {
  const t = S.settings.theme === 'system' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : S.settings.theme;
  let style = document.getElementById('tokens');
  if (!style) { style = document.createElement('style'); style.id = 'tokens'; document.head.prepend(style); }
  style.textContent = `:root{${core.tokensCss(t)}color-scheme:${t}}`;
}

// ------------------------------------------------------------------------------------- session
function saveSession() {
  const tabs = S.tabs.filter(t => !t.private && (t.url || t.pendingUrl)).map(t => ({ url: t.url || t.pendingUrl, title: t.title }));
  const sel = S.tabs.filter(t => !t.private && (t.url || t.pendingUrl)).findIndex(t => t.id === S.selected);
  storage.set('session', { tabs, selected: sel });
}

// ----------------------------------------------------------------------------------------- tabs
function openTab({ url = '', priv = false, background = false, after = null } = {}) {
  const t = newTabState(S.nextId++, priv, url);
  const idx = after != null ? S.tabs.findIndex(x => x.id === after) : -1;
  if (idx >= 0) S.tabs.splice(idx + 1, 0, t); else S.tabs.push(t);
  if (url) ensureBrowser(t, !background);
  if (!background) selectTab(t.id); else render();
  saveSession();
  return t;
}
function ensureBrowser(t, select) {
  if (t.hasBrowser) return;
  t.hasBrowser = true;
  t.url = t.pendingUrl || t.url;
  t.loading = true;
  ipc('tab.create', { id: t.id, private: t.private, url: t.url, select });
}
function selectTab(id) {
  const t = tab(id);
  if (!t) return;
  S.selected = id;
  if (!t.hasBrowser && t.pendingUrl) ensureBrowser(t, true);
  if (t.hasBrowser) { ipc('tab.select', { id }); ipc('tab.focus', { id }); }
  closeFind(false);
  S.panel = null; S.menu = false; S.suggest = null;
  render();
  saveSession();
}
function closeTab(id) {
  const i = S.tabs.findIndex(t => t.id === id);
  if (i < 0) return;
  const t = S.tabs[i];
  if (!t.private && (t.url || t.pendingUrl)) S.closed.unshift({ url: t.url || t.pendingUrl, title: t.title });
  S.closed = S.closed.slice(0, 25);
  S.tabs.splice(i, 1);
  if (t.hasBrowser) ipc('tab.close', { id });
  S.infobars = S.infobars.filter(b => b.tabId !== id);
  if (S.selected === id) {
    const same = S.tabs.filter(x => x.private === t.private);
    const pool = same.length ? same : S.tabs;
    const next = pool.find(x => S.tabs.indexOf(x) >= i) || pool[pool.length - 1];
    if (next) selectTab(next.id);
  }
  if (!S.tabs.length) openTab();
  render();
  saveSession();
}
function reopenClosed() {
  const c = S.closed.shift();
  if (c) openTab({ url: c.url });
}
function cycleTab(dir) {
  if (S.tabs.length < 2) return;
  const i = S.tabs.findIndex(t => t.id === S.selected);
  selectTab(S.tabs[(i + dir + S.tabs.length) % S.tabs.length].id);
}

// ----------------------------------------------------------------------------------- navigation
function navigate(t, url) {
  t.error = null;
  t.url = url;
  t.pendingUrl = url;
  t.blocked = 0;
  if (!t.hasBrowser) ensureBrowser(t, t.id === S.selected);
  else ipc('tab.navigate', { id: t.id, url });
  t.loading = true;
  S.panel = null; S.suggest = null;
  render();
  saveSession();
}
function submit(text, t = cur()) {
  const [kind, url] = core.resolveInput(text, S.settings.engine);
  if (kind === 'empty') return;
  navigate(t || openTab(), url);
}
function goHome() {
  const t = cur();
  if (!t) return;
  if (S.settings.homeMode === 'custom' && S.settings.homeUrl) return submit(S.settings.homeUrl, t);
  // Start page: drop the browser for this tab (frees memory) and show Lumo's start page.
  if (t.hasBrowser) ipc('tab.close', { id: t.id });
  const fresh = newTabState(S.nextId++, t.private);
  S.tabs[S.tabs.indexOf(t)] = fresh;
  S.selected = fresh.id;
  render();
  saveSession();
}
function reload() {
  const t = cur();
  if (!t) return;
  if (t.error) { const u = t.error.url || t.url; t.error = null; return navigate(t, u); }
  if (t.hasBrowser) ipc(t.loading ? 'tab.stop' : 'tab.reload', { id: t.id });
}
function back() {
  const t = cur();
  if (!t) return;
  if (t.error && !t.canBack) return goHome();
  if (t.canBack) { t.error = null; ipc('tab.back', { id: t.id }); }
  else if (t.url) goHome();
}
function setZoom(pct) {
  const t = cur();
  if (!t || !t.hasBrowser) return;
  t.zoom = pct;
  ipc('tab.zoom', { id: t.id, level: Math.log(pct / 100) / Math.log(1.2) });
  render();
}
function zoomStep(dir) {
  const t = cur();
  if (!t) return;
  const i = ZOOM_STEPS.indexOf(t.zoom);
  const next = ZOOM_STEPS[Math.min(ZOOM_STEPS.length - 1, Math.max(0, (i < 0 ? 7 : i) + dir))];
  setZoom(dir === 0 ? 100 : next);
}

// -------------------------------------------------------------------------------------- history
function recordHistory(t) {
  if (t.private || !/^https?:\/\//.test(t.url)) return;
  const last = S.history[S.history.length - 1];
  const now = core.now();
  if (last && last.url === t.url && now - last.t < 30000) { if (t.title) last.title = t.title; }
  else S.history.push({ url: t.url, title: t.title || '', t: now });
  if (S.history.length > MAX_HISTORY) S.history.splice(0, S.history.length - MAX_HISTORY);
  storage.set('history', S.history, 1500);
}
function updateHistoryTitle(t) {
  if (t.private || !t.title) return;
  for (let i = S.history.length - 1; i >= Math.max(0, S.history.length - 5); i--) {
    if (S.history[i].url === t.url) { S.history[i].title = t.title; storage.set('history', S.history, 1500); return; }
  }
}

// ------------------------------------------------------------------------------------ bookmarks
const isBookmarked = url => !!url && S.bookmarks.some(b => b.url === url);
function toggleBookmark() {
  const t = cur();
  if (!t || !t.url || t.error) return;
  const existing = S.bookmarks.find(b => b.url === t.url);
  if (existing) S.bookmarks = S.bookmarks.filter(b => b !== existing);
  else S.bookmarks.push({ id: Date.now(), parentId: 0, title: t.title || core.displayUrl(t.url), url: t.url, created: Date.now() });
  storage.set('bookmarks', S.bookmarks);
  render();
}
function deleteBookmark(id) {
  const ids = new Set([id]);
  let grew = true;
  while (grew) { grew = false; for (const b of S.bookmarks) if (ids.has(b.parentId) && !ids.has(b.id)) { ids.add(b.id); grew = true; } }
  S.bookmarks = S.bookmarks.filter(b => !ids.has(b.id));
  storage.set('bookmarks', S.bookmarks);
  render();
}

// ------------------------------------------------------------------------------------ infobars
function infobar(bar) {
  bar.key = bar.key || Math.random().toString(36).slice(2);
  S.infobars = S.infobars.filter(b => b.key !== bar.key);
  S.infobars.push(bar);
  if (bar.timeout) setTimeout(() => dismissBar(bar.key), bar.timeout);
  render();
  return bar.key;
}
function dismissBar(key) { S.infobars = S.infobars.filter(b => b.key !== key); render(); }

// ------------------------------------------------------------------------------- native events
window.freedom = {
  onEvent(e) {
    const t = e.id != null ? tab(e.id) : null;
    switch (e.type) {
      case 'url':
        if (!t) break;
        t.url = e.url; t.pendingUrl = e.url; t.blocked = 0;
        recordHistory(t);
        saveSession();
        break;
      case 'title': if (t) { t.title = e.title; updateHistoryTitle(t); saveSession(); } break;
      case 'favicon': if (t) t.favicon = e.url; break;
      case 'progress': if (t) t.progress = e.progress; break;
      case 'loading': if (t) { t.loading = e.loading; t.canBack = e.canBack; t.canForward = e.canForward; } break;
      case 'loadStart': if (t) { t.error = null; t.loading = true; } break;
      case 'loadError': if (t) { t.error = { code: e.code, text: e.text, url: e.url, kind: core.errorKindForNetError(e.code) }; t.loading = false; } break;
      case 'crashed': if (t) { t.error = { code: e.status, text: 'renderer terminated', url: t.url, kind: 'crash' }; t.loading = false; } break;
      case 'blocked': if (t) t.blocked = e.count; break;
      case 'findResult': if (S.find && t && t.id === S.selected) { S.find.count = e.count; S.find.active = e.active; } break;
      case 'tabCreated': {
        const nt = newTabState(e.id, e.private);
        nt.hasBrowser = true;
        const idx = S.tabs.findIndex(x => x.id === e.openerId);
        if (idx >= 0) S.tabs.splice(idx + 1, 0, nt); else S.tabs.push(nt);
        if (!e.background) { S.selected = nt.id; S.panel = null; }
        break;
      }
      case 'tabClosed': {
        if (!t) break;
        const i = S.tabs.indexOf(t);
        S.tabs.splice(i, 1);
        if (S.selected === t.id) { const n = S.tabs[i] || S.tabs[i - 1]; if (n) selectTab(n.id); }
        if (!S.tabs.length) openTab();
        saveSession();
        break;
      }
      case 'openTab': openTab({ url: e.url, priv: e.private, background: e.background, after: e.openerId }); break;
      case 'popupBlocked': {
        const site = e.site || core.hostOf(e.url);
        infobar({ key: 'popup-' + e.id, tabId: e.id, kind: 'warn', html: T.popupBlocked(esc(site)), actions: [
          { label: T.openOnce, run: () => openTab({ url: e.url, priv: t && t.private, after: e.id }) },
          { label: T.allowAlways, run: () => { (S.sites[site] ||= {}).popups = 'allow'; saveSites(); } },
        ] });
        break;
      }
      case 'permission': permissionBar(e); break;
      case 'permissionDismissed': dismissBar('perm-' + e.promptId); break;
      case 'downloadAsk': downloadAsk(e); break;
      case 'download': downloadUpdate(e); break;
      case 'external':
        infobar({ kind: 'warn', html: T.externalAsk(esc(e.url.slice(0, 120))), actions: [
          { label: T.open, primary: true, run: () => ipc('external.open', { url: e.url }) }] });
        break;
      case 'auth': authBar(e); break;
      case 'screenshot':
      case 'pdf':
        infobar({ kind: e.ok ? 'info' : 'danger', timeout: 8000, html: esc(e.ok ? (e.type === 'pdf' ? T.pdfSaved : T.screenshotSaved) + ': ' + e.path : T.saveFailed),
          actions: e.ok ? [{ label: T.showInFolder, run: () => ipc('download.show', { path: e.path }) }] : [] });
        break;
      case 'shortcut': shortcut(e.name); break;
      case 'privateSessionEnded':
        S.downloads = S.downloads.filter(d => !d.private || d.state === 'progress');
        break;
      case 'fullscreen': break;
    }
    render();
  },
};

function permissionBar(e) {
  const names = e.permissions.map(p => T[p] || p).join(', ');
  const remember = e.permissions.some(p => ['camera', 'microphone', 'location'].includes(p));
  const key = 'perm-' + e.promptId;
  const answer = allow => {
    const box = document.getElementById(key + '-rem');
    if (remember && box && box.checked) {
      const s = (S.sites[e.site] ||= {});
      for (const p of e.permissions) if (['camera', 'microphone', 'location'].includes(p)) s[p] = allow ? 'allow' : 'block';
      saveSites();
    }
    ipc('permission.answer', { promptId: e.promptId, allow });
    dismissBar(key);
  };
  infobar({ key, tabId: e.id, kind: 'warn', html: T.permAsk(esc(e.site), esc(names)) +
      (remember ? ` &nbsp;<label><input type="checkbox" id="${key}-rem" checked> ${esc(T.remember)}</label>` : ''),
    noDismiss: true, actions: [{ label: T.block, run: () => answer(false) }, { label: T.allow, primary: true, run: () => answer(true) }] });
}

function authBar(e) {
  const key = 'auth-' + e.authId;
  const send = cancel => {
    const user = document.getElementById(key + '-u')?.value || '';
    const pass = document.getElementById(key + '-p')?.value || '';
    ipc('auth.answer', { authId: e.authId, user, pass, cancel });
    dismissBar(key);
  };
  infobar({ key, tabId: e.id, kind: 'info', noDismiss: true,
    html: T.authAsk(esc(e.host), esc(e.realm)) + ` <input type="text" id="${key}-u" placeholder="${esc(T.username)}"> <input type="password" id="${key}-p" placeholder="${esc(T.password)}">`,
    actions: [{ label: T.cancel, run: () => send(true) }, { label: T.signIn, primary: true, run: () => send(false) }] });
}

// ------------------------------------------------------------------------------------ downloads
// Downloads of this session, by CEF download id (ids restart at 1 after every launch).
const liveDownloads = new Map();
function liveDownload(id) {
  let d = liveDownloads.get(id);
  if (!d) {
    d = { downloadId: id, name: '', url: '', state: 'waiting', received: 0, total: -1, speed: 0, private: false, path: '', started: Date.now(), accepted: false };
    liveDownloads.set(id, d);
    S.downloads.unshift(d);
  }
  return d;
}
function downloadAsk(e) {
  const d = liveDownload(e.downloadId);
  Object.assign(d, { name: core.sanitizeFileName(e.name || 'download'), url: e.url, total: e.total, private: !!e.private, state: 'waiting' });
  const dangerous = core.isPotentiallyDangerous(d.name, e.mime || '');
  const accept = () => { d.accepted = true; d.state = 'progress'; ipc('download.answer', { downloadId: e.downloadId, accept: true }); };
  if (!dangerous) return accept();
  const key = 'dl-' + e.downloadId;
  infobar({ key, kind: 'danger', noDismiss: true, html: T.dangerousDl(esc(d.name)), actions: [
    { label: T.discard, run: () => { ipc('download.answer', { downloadId: e.downloadId, accept: false }); d.state = 'canceled'; dismissBar(key); saveDownloads(); } },
    { label: T.keep, run: () => { accept(); dismissBar(key); } },
  ] });
}
function downloadUpdate(e) {
  const d = liveDownload(e.downloadId);
  const before = d.state;
  const final = e.state === 'complete' || e.state === 'canceled' || e.state === 'interrupted';
  // Before the user accepted, CEF already reports an in-progress item: keep showing "waiting".
  if (!d.accepted && !final) return;
  Object.assign(d, { state: e.state, received: e.received, total: e.total, speed: e.speed, path: e.path || d.path, url: e.url || d.url });
  if (e.path) d.name = e.path.split(/[\\/]/).pop();
  if (before !== e.state && e.state === 'complete') {
    infobar({ key: 'dldone-' + e.downloadId, kind: 'info', timeout: 10000, html: T.dlDone(esc(d.name)), actions: [
      { label: T.open, run: () => openDownload(d) }, { label: T.showInFolder, run: () => ipc('download.show', { path: d.path }) }] });
  }
  if (before !== e.state && e.state === 'interrupted') infobar({ kind: 'danger', timeout: 10000, html: T.dlFailed(esc(d.name)) });
  if (final) { dismissBar('dl-' + e.downloadId); liveDownloads.delete(e.downloadId); }
  if (e.state !== 'progress' || before !== 'progress') saveDownloads();
}
function saveDownloads() {
  storage.set('downloads', S.downloads.filter(d => !d.private && d.state !== 'waiting').slice(0, 500).map(({ accepted, ...d }) => ({ ...d, speed: 0 })));
}
function openDownload(d) {
  if (core.isPotentiallyDangerous(d.name, '')) {
    S.dialog = { kind: 'confirmOpen', download: d };
    render();
    return;
  }
  ipc('download.open', { path: d.path });
}

// ---------------------------------------------------------------------------------- shortcuts
function shortcut(name) {
  const t = cur();
  switch (name) {
    case 'newTab': openTab(); focusAddress(); break;
    case 'newPrivateTab': openTab({ priv: true }); focusAddress(); break;
    case 'closeTab': if (t) closeTab(t.id); break;
    case 'reopenTab': reopenClosed(); break;
    case 'nextTab': cycleTab(1); break;
    case 'prevTab': cycleTab(-1); break;
    case 'focusAddress': focusAddress(); break;
    case 'find': openFind(); break;
    case 'findNext': if (S.find) findGo(true, true); else openFind(); break;
    case 'findPrev': if (S.find) findGo(false, true); break;
    case 'bookmark': toggleBookmark(); break;
    case 'reload': reload(); break;
    case 'print': if (t?.hasBrowser && !t.error) ipc('tab.print', { id: t.id }); break;
    case 'savePdf': if (t?.hasBrowser && !t.error) ipc('tab.pdf', { id: t.id }); break;
    case 'downloads': showPanel('downloads'); break;
    case 'history': showPanel('history'); break;
    case 'zoomIn': zoomStep(1); break;
    case 'zoomOut': zoomStep(-1); break;
    case 'zoomReset': zoomStep(0); break;
    case 'back': back(); break;
    case 'forward': if (t?.canForward) ipc('tab.forward', { id: t.id }); break;
    case 'home': goHome(); break;
  }
}
function focusAddress() {
  S.panel = null; S.menu = false;
  render();
  ipc('ui.focus'); // keyboard focus may still be in a page's window
  const a = $('#address'); a.focus(); a.select();
}
document.addEventListener('keydown', e => {
  const k = e.key;
  let name = null;
  if (e.ctrlKey && !e.altKey) {
    const map = { t: 'newTab', w: 'closeTab', l: 'focusAddress', f: 'find', d: 'bookmark', r: 'reload', p: 'print', j: 'downloads', h: 'history', s: 'savePdf', '+': 'zoomIn', '=': 'zoomIn', '-': 'zoomOut', '0': 'zoomReset' };
    if (e.shiftKey && k.toLowerCase() === 't') name = 'reopenTab';
    else if (e.shiftKey && k.toLowerCase() === 'n') name = 'newPrivateTab';
    else if (k === 'Tab') name = e.shiftKey ? 'prevTab' : 'nextTab';
    else if (!e.shiftKey) name = map[k.toLowerCase()];
  } else if (e.altKey && !e.ctrlKey) {
    if (k === 'ArrowLeft') name = 'back'; else if (k === 'ArrowRight') name = 'forward'; else if (k === 'Home') name = 'home';
  } else if (k === 'F5') name = 'reload'; else if (k === 'F6') name = 'focusAddress';
  else if (k === 'Escape') {
    if (S.dialog) S.dialog = null; else if (S.menu) S.menu = false; else if (S.suggest) { S.suggest = null; $('#address').blur(); }
    else if (S.find) closeFind(); else if (S.panel) S.panel = null;
    render();
    return;
  }
  if (name) { e.preventDefault(); shortcut(name); }
});

// -------------------------------------------------------------------------------------- find
function openFind() {
  const t = cur();
  if (!t || !t.hasBrowser || t.error) return;
  S.find = S.find || { text: '', count: 0, active: 0 };
  S.panel = null; S.menu = false;
  render();
  ipc('ui.focus');
  $('#findInput').focus();
  $('#findInput').select();
}
function findGo(forward, next) {
  const t = cur();
  if (!t || !S.find) return;
  ipc('tab.find', { id: t.id, text: S.find.text, forward, next });
}
function closeFind(rerender = true) {
  const t = cur();
  if (S.find && t?.hasBrowser) ipc('tab.findStop', { id: t.id });
  S.find = null;
  if (rerender) render();
}

// ------------------------------------------------------------------------------------- render
// Some compositors (seen with Wine's software path) present a frame only when the next one is produced.
// After each render we change one invisible pixel over the next two animation frames so the final
// state is always presented. Cost: two tiny repaints.
const nudge = document.createElement('div');
nudge.style.cssText = 'position:fixed;right:0;bottom:0;width:1px;height:1px;pointer-events:none;opacity:.02;background:#000';
document.body.appendChild(nudge);
function paintNudge() {
  requestAnimationFrame(() => { nudge.style.background = '#010101'; requestAnimationFrame(() => { nudge.style.background = '#000'; }); });
}
let lastLayout = '';
function wantsFullUi() {
  const t = cur();
  return !!(S.panel || S.menu || S.dialog || S.suggest || !t || t.error || !t.hasBrowser || (!t.url && !t.loading));
}
function render() {
  renderTabs();
  renderNav();
  renderBars();
  const full = wantsFullUi();
  const overlay = $('#overlay');
  overlay.classList.toggle('hidden', !full);
  const toolbarH = Math.ceil($('#toolbar').getBoundingClientRect().height);
  overlay.style.top = toolbarH + 'px';
  if (full) renderOverlay(); else overlay.innerHTML = '';
  const key = `${full}|${toolbarH}`;
  if (key !== lastLayout) { lastLayout = key; ipc('layout', { full, toolbar: toolbarH }); }
  const t = cur();
  document.title = t ? (t.title || (t.url ? core.displayUrl(t.url) : T.startPage)) + ' – Lumo' : 'Lumo';
  ipcTitle(document.title);
  paintNudge();
}
let lastTitle = '';
function ipcTitle(title) { if (title !== lastTitle) { lastTitle = title; ipc('title', { title }); } }

function renderTabs() {
  const strip = $('#tabstrip');
  strip.innerHTML = S.tabs.map(t => {
    const fav = t.loading && t.hasBrowser ? '<span class="spin"></span>'
      : t.favicon ? `<img class="fav" src="${esc(t.favicon)}" alt="">`
      : svg(t.private ? 'eye' : 'globe', 'style="width:16px;height:16px"');
    const title = t.title || (t.url ? core.displayUrl(t.url) : (t.private ? T.privateTitle : T.startPage));
    return `<div class="tab${t.id === S.selected ? ' active' : ''}${t.private ? ' private' : ''}" role="tab" data-id="${t.id}" title="${esc(title)}" aria-selected="${t.id === S.selected}">
      ${fav}<span class="title">${esc(title)}</span>
      <button class="close" data-close="${t.id}" title="${esc(T.closeTab)}">${svg('x', 'style="width:14px;height:14px"')}</button></div>`;
  }).join('') + `<button id="newtab" title="${esc(T.newTab)} (Ctrl+T)">${svg('x', 'style="transform:rotate(45deg)"')}</button>`;
}

function renderNav() {
  const t = cur();
  $('#back').disabled = !t || (!t.canBack && !t.url && !t.error);
  $('#forward').disabled = !t?.canForward;
  $('#reloadIcon').innerHTML = t?.loading && t?.hasBrowser ? ICON.stop : ICON.reload;
  $('#reload').disabled = !t || (!t.hasBrowser && !t.error);
  const addr = $('#address');
  if (document.activeElement !== addr && !S.editing) addr.value = t?.url ? core.displayUrl(t.url) : '';
  addr.placeholder = t?.private ? T.addressHintPrivate : T.addressHint;
  $('#omnibox').classList.toggle('private', !!t?.private);
  const level = t ? core.securityLevel(t.url || '', t.error?.kind === 'certificate') : 'none';
  const sec = $('#security');
  sec.className = level === 'insecure' ? 'insecure' : level === 'cert_error' ? 'cert' : '';
  $('#secIcon').innerHTML = level === 'secure' ? ICON.lock : level === 'insecure' || level === 'cert_error' ? ICON.warn : t?.private ? ICON.eye : ICON.info;
  $('#shield').classList.toggle('hidden', !t?.blocked);
  $('#shieldCount').textContent = t?.blocked || 0;
  $('#star').classList.toggle('on', isBookmarked(t?.url));
  $('#star').disabled = !t?.url || !!t?.error;
  $('#progress > div').style.width = t?.loading && t?.hasBrowser ? Math.max(5, t.progress) + '%' : '0';
  const active = S.downloads.filter(d => d.state === 'progress').length;
  $('#dlBadge').textContent = active;
  $('#dlBadge').classList.toggle('hidden', !active);
  for (const el of document.querySelectorAll('[data-tip]')) el.title = T[el.dataset.tip] || '';
}

function renderBars() {
  const fb = $('#findbar');
  fb.classList.toggle('hidden', !S.find);
  if (S.find) {
    if (document.activeElement !== $('#findInput')) $('#findInput').value = S.find.text;
    $('#findInput').placeholder = T.findInPage;
    $('#findcount').textContent = S.find.text ? `${S.find.active}/${S.find.count}` : '';
  }
  const bars = S.infobars.filter(b => !b.tabId || b.tabId === S.selected);
  const host = $('#infobars');
  // Keep existing DOM (inputs) stable: rebuild only when the set changes.
  const sig = bars.map(b => b.key).join(',');
  if (host.dataset.sig === sig) return;
  host.dataset.sig = sig;
  host.innerHTML = bars.map(b => `<div class="bar ${b.kind || 'info'}" data-key="${b.key}"><span class="msg">${b.html}</span>
    ${(b.actions || []).map((a, i) => `<button class="btn${a.primary ? ' primary' : ''}" data-bar="${b.key}" data-act="${i}">${esc(a.label)}</button>`).join('')}
    ${b.noDismiss ? '' : `<button class="ib" data-bar="${b.key}" data-act="x" title="${esc(T.dismiss)}">${svg('x')}</button>`}</div>`).join('');
}

// ------------------------------------------------------------------------------------ overlay
function renderOverlay() {
  const o = $('#overlay');
  const t = cur();
  let html = '';
  const pageBehind = t && t.hasBrowser && !t.error && (t.url || t.loading);
  if (S.panel) html = renderPanel(S.panel);
  else if (t?.error) html = renderError(t);
  else if (!pageBehind) html = renderStart(t);
  if (S.suggest) html += renderSuggest();
  if (S.menu) html += `<div class="scrim" data-act="closeMenu"></div>` + renderMenu();
  if (S.dialog) html += `<div class="scrim" data-act="closeDialog"></div>` + renderDialog();
  // Avoid resetting focus/scroll when nothing changed.
  if (o.dataset.html === html) return;
  const focusedId = document.activeElement?.id;
  const sel = document.activeElement?.selectionStart;
  const scroll = o.scrollTop;
  o.innerHTML = html;
  o.dataset.html = html;
  o.scrollTop = scroll;
  if (focusedId && o.querySelector('#' + focusedId)) { const el = document.getElementById(focusedId); el.focus(); try { el.setSelectionRange(sel, sel); } catch {} }
}

function renderStart(t) {
  const priv = t?.private;
  return `<div class="page start">
    <div class="brand">${priv ? svg('eye', 'style="width:40px;height:40px;color:var(--private)"') : `<img src="logo.png" alt="" width="48" height="48">`}
      <span class="${priv ? 'private-title' : ''}">${esc(priv ? T.privateTitle : T.appName)}</span></div>
    <form class="search" data-form="start">${svg('search')}<input id="startInput" type="text" placeholder="${esc(T.startHint)}" spellcheck="false" autocomplete="off"></form>
    <div class="chips">
      <button class="chip" data-panel="downloads">${svg('dl')} ${esc(T.downloads)}</button>
      <button class="chip" data-panel="bookmarks">${svg('star')} ${esc(T.bookmarks)}</button>
      <button class="chip" data-panel="history">${svg('clock')} ${esc(T.history)}</button>
    </div>
    ${priv ? `<p class="private-note">${esc(T.privateNote)}</p>` : ''}
  </div>`;
}

function renderError(t) {
  const e = t.error;
  const [title, body] = T.err[e.kind] || T.err.unknown;
  const unsafe = e.kind === 'certificate' || e.kind === 'unsafe';
  return `<div class="page error ${e.kind === 'certificate' ? 'cert' : ''}">
    <div class="icon">${svg(unsafe ? 'warn' : 'info')}</div>
    <h1>${esc(title)}</h1><p class="muted">${esc(body)}</p>
    <p class="small muted url">${esc(core.redactUrl(e.url || t.url))}</p>
    <p class="small muted">${esc(e.text || '')} (${esc(e.code)})</p>
    <div class="actions">
      ${unsafe ? `<button class="btn primary" data-act="back">${esc(T.backToSafety)}</button>` : `<button class="btn primary" data-act="reload">${esc(T.retry)}</button>`}
      <button class="btn" data-act="copyError">${esc(T.copyDetails)}</button>
    </div></div>`;
}

function renderSuggest() {
  const q = S.suggest.text.trim();
  const items = [];
  if (q) {
    const [kind, url] = core.resolveInput(q, S.settings.engine);
    items.push({ icon: kind === 'navigate' ? 'globe' : 'search', title: q, sub: kind === 'navigate' ? core.displayUrl(url) : S.settings.engine, url });
    const seen = new Set([url]);
    const add = (title, u, icon) => { if (!seen.has(u) && items.length < 9) { seen.add(u); items.push({ icon, title: title || core.displayUrl(u), sub: core.displayUrl(u), url: u }); } };
    for (const b of S.bookmarks) if (b.url && core.textMatches(q, b.title, b.url)) add(b.title, b.url, 'star');
    for (let i = S.history.length - 1; i >= 0 && items.length < 9; i--) { const h = S.history[i]; if (core.textMatches(q, h.title, h.url)) add(h.title, h.url, 'clock'); }
  }
  S.suggest.items = items;
  const sel = S.suggest.sel ?? 0;
  return `<div class="suggest" role="listbox">${items.map((it, i) => `<div class="sg${i === sel ? ' sel' : ''}" data-sg="${i}" role="option">${svg(it.icon)}
    <div style="min-width:0"><div class="t">${esc(it.title)}</div><div class="u">${esc(it.sub)}</div></div></div>`).join('')}</div>`;
}

function renderMenu() {
  const t = cur();
  const page = t && t.hasBrowser && t.url && !t.error;
  const mi = (act, label, key = '', dis = false) => `<button class="mi" data-act="${act}" ${dis ? 'disabled' : ''}>${esc(label)}<span class="k">${key}</span></button>`;
  return `<div class="menu" role="menu">
    ${mi('newTab', T.newTab, 'Ctrl+T')}${mi('newPrivate', T.newPrivateTab, 'Ctrl+Shift+N')}${mi('reopen', T.reopenTab, 'Ctrl+Shift+T', !S.closed.length)}
    <div class="sep"></div>
    ${mi('p:bookmarks', T.bookmarks)}${mi('p:history', T.history, 'Ctrl+H')}${mi('p:downloads', T.downloads, 'Ctrl+J')}
    <div class="sep"></div>
    <div class="zoomrow"><span class="lab">${esc(T.zoom)}</span>
      <button class="ib" data-act="zoomOut" ${page ? '' : 'disabled'}>−</button><span style="width:48px;text-align:center">${t?.zoom || 100}%</span>
      <button class="ib" data-act="zoomIn" ${page ? '' : 'disabled'}>+</button></div>
    ${mi('find', T.findInPage, 'Ctrl+F', !page)}${mi('print', T.print, 'Ctrl+P', !page)}${mi('pdf', T.savePdf, 'Ctrl+S', !page)}
    ${mi('screenshot', T.screenshot, '', !page)}${mi('copyLink', T.copyLink, '', !page)}${mi('translate', T.translate, '', !page)}
    <div class="sep"></div>
    ${mi('p:settings', T.settings)}
  </div>`;
}

function renderDialog() {
  const d = S.dialog;
  if (d.kind === 'confirmOpen') {
    return `<div class="dialog"><h3>${esc(d.download.name)}</h3><p>${T.dangerousDl(esc(d.download.name))}</p>
      <div class="actions"><button class="btn" data-act="closeDialog">${esc(T.cancel)}</button>
      <button class="btn danger" data-act="openAnyway">${esc(T.open)}</button></div></div>`;
  }
  if (d.kind === 'bookmark') {
    const folders = S.bookmarks.filter(b => !b.url && b.id !== d.id);
    return `<div class="dialog"><h3>${esc(d.id ? T.edit : (d.folder ? T.newFolder : T.addBookmark))}</h3>
      <form data-form="bookmark">
      <label>${esc(T.title)}</label><input class="field" id="bmTitle" value="${esc(d.title)}">
      ${d.folder ? '' : `<label>${esc(T.url)}</label><input class="field" id="bmUrl" value="${esc(d.url)}">`}
      <label>${esc(T.folder)}</label><select class="field" id="bmParent"><option value="0">${esc(T.rootFolder)}</option>
      ${folders.map(f => `<option value="${f.id}" ${f.id === d.parentId ? 'selected' : ''}>${esc(f.title)}</option>`).join('')}</select>
      ${d.err ? `<div class="err">${esc(d.err)}</div>` : ''}
      <div class="actions"><button type="button" class="btn" data-act="closeDialog">${esc(T.cancel)}</button><button class="btn primary">${esc(T.save)}</button></div>
      </form></div>`;
  }
  if (d.kind === 'translate') {
    return `<div class="dialog"><h3>${esc(T.translate)}</h3><p>${T.translateAsk(esc(d.host))}</p>
      <div class="actions"><button class="btn" data-act="closeDialog">${esc(T.cancel)}</button><button class="btn primary" data-act="translateGo">${esc(T.translateGo)}</button></div></div>`;
  }
  if (d.kind === 'siteinfo') return renderSiteInfo();
  return '';
}

const dialogSite = () => S.dialog?.site || (cur()?.url ? core.siteKey(cur().url) : '');
function renderSiteInfo() {
  const fromSettings = !!S.dialog.site;
  const t = fromSettings ? { url: 'https://' + S.dialog.site + '/', blocked: 0, private: false } : cur();
  const site = dialogSite();
  const level = fromSettings ? 'none' : t ? core.securityLevel(t.url || '', t.error?.kind === 'certificate') : 'none';
  const s = S.sites[site] || {};
  const seg = (perm, val) => `<span class="seg">${['ask', 'allow', 'block'].map(v => `<button data-perm="${perm}" data-val="${v}" class="${(val || 'ask') === v ? 'on' : ''}">${esc(T[v])}</button>`).join('')}</span>`;
  return `<div class="dialog"><h3>${esc(site || (t?.private ? T.privateTitle : T.appName))}</h3>
    ${fromSettings ? '' : `<p class="${level === 'secure' ? 'muted' : ''}" style="${level === 'insecure' ? 'color:var(--warning)' : level === 'cert_error' ? 'color:var(--danger)' : ''}">
      ${esc(level === 'secure' ? T.secure : level === 'insecure' ? T.insecure : level === 'cert_error' ? T.certError : /^https?:/.test(t?.url || '') ? T.localNote : T.internal)}</p>`}
    ${site ? `
    <div class="set"><div class="lbl">${esc(T.blockHere)}<div class="s">${esc(S.settings.adblock ? T.blockedCount(t.blocked || 0) : T.adblockOff)}</div></div>
      <label class="switch"><input type="checkbox" data-toggle="siteAdblock" ${s.adblockException ? '' : 'checked'} ${S.settings.adblock ? '' : 'disabled'}><span></span></label></div>
    <h2 style="margin-top:16px">${esc(T.sitePermissions)}</h2>
    ${['camera', 'microphone', 'location', 'popups'].map(p => `<div class="set"><div class="lbl">${esc(T[p])}</div>${seg(p, s[p])}</div>`).join('')}
    <p class="small muted">${esc(T.notificationsUnsupported)}</p>
    <div class="actions"><button class="btn" data-act="resetSite">${esc(T.resetSite)}</button><button class="btn primary" data-act="closeDialog">OK</button></div>` : ''}
  </div>`;
}

// ------------------------------------------------------------------------------------- panels
function showPanel(name) { S.panel = name; S.menu = false; S.suggest = null; S.dialog = null; S.panelState = S.panelState || {}; render(); if (name) ipc('ui.focus'); }
function fmtTime(t) { return new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }); }
function fmtDay(t) { return new Date(t).toLocaleDateString([], { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }); }

function renderPanel(name) {
  const ps = (S.panelState ||= {});
  if (name === 'bookmarks') {
    const q = ps.bmQuery || '';
    const folder = ps.folder || 0;
    const list = q ? S.bookmarks.filter(b => b.url && core.textMatches(q, b.title, b.url))
      : S.bookmarks.filter(b => (b.parentId || 0) === folder).sort((a, b) => (!!a.url - !!b.url) || a.title.localeCompare(b.title));
    const crumbs = []; let f = folder;
    while (f) { const n = S.bookmarks.find(b => b.id === f); if (!n) break; crumbs.unshift(n); f = n.parentId || 0; }
    return `<div class="page"><h1>${esc(T.bookmarks)}</h1>
      <div class="toolbar-row"><input class="field grow" id="bmQuery" placeholder="${esc(T.searchBookmarks)}" value="${esc(q)}">
      <button class="btn" data-act="addBookmark">${esc(T.addBookmark)}</button><button class="btn" data-act="addFolder">${esc(T.newFolder)}</button></div>
      ${folder && !q ? `<div class="crumbs"><a data-folder="0">${esc(T.rootFolder)}</a>${crumbs.map(c => ` / <a data-folder="${c.id}">${esc(c.title)}</a>`).join('')}</div>` : ''}
      <div class="list">${list.length ? list.map(b => `<div class="row" ${b.url ? `data-open="${esc(b.url)}"` : `data-folder="${b.id}"`}>
        <span class="fico">${svg(b.url ? 'star' : 'folder')}</span><div class="main"><div class="t">${esc(b.title)}</div>${b.url ? `<div class="s">${esc(core.displayUrl(b.url))}</div>` : ''}</div>
        <div class="acts"><button class="btn" data-edit="${b.id}">${esc(T.edit)}</button><button class="btn" data-del="${b.id}">${esc(T.delete)}</button></div></div>`).join('')
        : `<div class="empty">${esc(T.noBookmarks)}</div>`}</div></div>`;
  }
  if (name === 'history') {
    const q = ps.hQuery || '';
    const out = [];
    let day = '';
    let n = 0;
    for (let i = S.history.length - 1; i >= 0 && n < 600; i--) {
      const h = S.history[i];
      if (q && !core.textMatches(q, h.title, h.url)) continue;
      const d = fmtDay(h.t);
      if (d !== day) { day = d; out.push(`<div class="daylabel">${esc(d)}</div>`); }
      out.push(`<div class="row" data-open="${esc(h.url)}"><span class="time">${fmtTime(h.t)}</span><div class="main"><div class="t">${esc(h.title || core.displayUrl(h.url))}</div>
        <div class="s">${esc(core.displayUrl(h.url))}</div></div><div class="acts"><button class="ib" data-hdel="${i}" title="${esc(T.delete)}">${svg('x')}</button></div></div>`);
      n++;
    }
    return `<div class="page"><h1>${esc(T.history)}</h1>
      <div class="toolbar-row"><input class="field grow" id="hQuery" placeholder="${esc(T.searchHistory)}" value="${esc(q)}">
      <button class="btn" data-act="clearDataDialog">${esc(T.clearData)}</button></div>
      <div class="list">${out.length ? out.join('') : `<div class="empty">${esc(T.noHistory)}</div>`}</div>
      ${ps.clearOpen ? renderClearData() : ''}</div>`;
  }
  if (name === 'downloads') {
    return `<div class="page"><h1>${esc(T.downloads)}</h1>
      <div class="toolbar-row"><span class="grow muted small">${esc(T.dlInterruptedHint)}</span><button class="btn" data-act="clearDownloads">${esc(T.clearList)}</button></div>
      <div class="list">${S.downloads.length ? S.downloads.map((d, i) => {
        const pct = d.total > 0 ? Math.round(d.received * 100 / d.total) : -1;
        const parts = [T.dl[d.state] || d.state];
        if (d.state === 'progress' || d.state === 'paused') parts.push(`${core.formatBytes(d.received || 0)} / ${core.formatBytes(d.total ?? -1)}`);
        if (d.state === 'progress') { parts.push(core.formatSpeed(d.speed || 0)); parts.push(core.formatEta((d.total || 0) - (d.received || 0), d.speed || 0)); }
        if (d.state === 'complete') parts.push(core.formatBytes(d.total || d.received || 0));
        if (d.private) parts.push(T.dlPrivate);
        const acts = [];
        if (d.state === 'progress') acts.push(['pause', T.pause], ['cancel', T.cancel]);
        if (d.state === 'paused') acts.push(['resume', T.resume], ['cancel', T.cancel]);
        if (d.state === 'complete') acts.push(['open', T.open], ['show', T.showInFolder]);
        if ((d.state === 'interrupted' || d.state === 'canceled') && /^https?:/.test(d.url || '')) acts.push(['retry', T.retryDl]);
        if (d.state !== 'progress' && d.state !== 'paused' && d.state !== 'waiting') acts.push(['remove', T.removeFromList]);
        return `<div class="row"><span class="fico">${svg(core.isPotentiallyDangerous(d.name, '') ? 'warn' : 'file')}</span><div class="main">
          <div class="t">${esc(d.name)}</div><div class="s">${esc(parts.filter(Boolean).join(' · '))}</div>
          ${(d.state === 'progress' || d.state === 'paused') && pct >= 0 ? `<div class="bar-progress"><div style="width:${pct}%"></div></div>` : ''}</div>
          <div class="acts">${acts.map(([a, l]) => `<button class="btn" data-dl="${i}" data-dla="${a}">${esc(l)}</button>`).join('')}</div></div>`;
      }).join('') : `<div class="empty">${esc(T.noDownloads)}</div>`}</div></div>`;
  }
  if (name === 'settings') return renderSettings();
  return '';
}

function renderClearData() {
  const c = (S.panelState.clear ||= { range: 'hour', history: true, cookies: false, cache: true, downloads: false, perms: false });
  const ranges = [['hour', T.rHour], ['day', T.rDay], ['week', T.rWeek], ['month', T.rMonth], ['all', T.rAll]];
  const box = (k, l) => `<label class="set" style="border:0;padding:4px 0"><input type="checkbox" data-clear="${k}" ${c[k] ? 'checked' : ''}> ${esc(l)}</label>`;
  return `<div class="dialog" style="position:fixed"><h3>${esc(T.clearData)}</h3>
    <label>${esc(T.clearRange)}</label><select class="field" id="clearRange">${ranges.map(([v, l]) => `<option value="${v}" ${c.range === v ? 'selected' : ''}>${esc(l)}</option>`).join('')}</select>
    ${box('history', T.cHistory)}${box('cookies', T.cCookies)}${box('cache', T.cCache)}${box('downloads', T.cDownloads)}${box('perms', T.cPerms)}
    <p class="small muted">${esc(T.cookiesNote)}</p>
    <div class="actions"><button class="btn" data-act="clearCancel">${esc(T.cancel)}</button><button class="btn primary" data-act="clearRun">${esc(T.clear)}</button></div></div>`;
}

function renderSettings() {
  const s = S.settings;
  const sw = (key, on, dis = false) => `<label class="switch"><input type="checkbox" data-setting="${key}" ${on ? 'checked' : ''} ${dis ? 'disabled' : ''}><span></span></label>`;
  const opt = (v, l, cur) => `<option value="${v}" ${v === cur ? 'selected' : ''}>${esc(l)}</option>`;
  const sites = Object.entries(S.sites).filter(([, v]) => Object.keys(v).length);
  const listWhen = s.blockListUpdated ? new Date(s.blockListUpdated).toLocaleString() : T.never;
  const info = S.info;
  return `<div class="page"><h1>${esc(T.settings)}</h1>
    <h2>${esc(T.sSearch)}</h2>
    <div class="set"><div class="lbl">${esc(T.sSearch)}</div><select class="field" data-select="engine">${core.searchEngines().map(([id, n]) => opt(id, n, s.engine)).join('')}</select></div>
    <h2>${esc(T.sHome)}</h2>
    <div class="set"><div class="lbl">${esc(T.home)}</div><select class="field" data-select="homeMode">${opt('start', T.homeStart, s.homeMode)}${opt('custom', T.homeCustom, s.homeMode)}</select></div>
    ${s.homeMode === 'custom' ? `<div class="set"><div class="lbl">${esc(T.url)}</div><input class="field grow" data-text="homeUrl" value="${esc(s.homeUrl)}" placeholder="https://"></div>` : ''}
    <div class="set"><div class="lbl">${esc(T.restoreTabs)}</div>${sw('restoreTabs', s.restoreTabs)}</div>
    <h2>${esc(T.sAppearance)}</h2>
    <div class="set"><div class="lbl">${esc(T.theme)}</div><select class="field" data-select="theme">${opt('dark', T.themeDark, s.theme)}${opt('light', T.themeLight, s.theme)}${opt('system', T.themeSystem, s.theme)}</select></div>
    <h2>${esc(T.sDownloads)}</h2>
    <div class="set"><div class="lbl">${esc(T.dlLocation)}<div class="s">${esc(s.downloadDir || info.downloadsDir || '')}</div></div><button class="btn" data-act="pickFolder">${esc(T.change)}</button></div>
    <div class="set"><div class="lbl">${esc(T.askWhere)}</div>${sw('askDownload', s.askDownload)}</div>
    <h2>${esc(T.sPrivacy)}</h2>
    <div class="set"><div class="lbl">${esc(T.adblock)}<div class="s">${esc(T.adblockHint)}</div><div class="s">${esc(T.listStatus(info.blocklistSize ?? '…', listWhen))}</div></div>${sw('adblock', s.adblock)}</div>
    <div class="set"><div class="lbl">${esc(T.listSource)}</div><input class="field grow" data-text="blockListUrl" value="${esc(s.blockListUrl)}"></div>
    <div class="toolbar-row" style="margin-top:8px"><button class="btn" data-act="updateList">${esc(T.updateList)}</button>${s.blockListUpdated ? `<button class="btn" data-act="removeList">${esc(T.removeList)}</button>` : ''}</div>
    <div class="set"><div class="lbl">${esc(T.blockPopups)}</div>${sw('blockPopups', s.blockPopups)}</div>
    <div class="set"><div class="lbl">${esc(T.block3p)}</div>${sw('block3p', s.block3p)}</div>
    <div class="set"><div class="lbl muted small">${esc(T.safeBrowsingNA)}</div></div>
    <div class="set"><div class="lbl">${esc(T.clearData)}</div><button class="btn" data-act="clearDataDialog">${esc(T.clearData)}</button></div>
    <h2>${esc(T.siteSettings)}</h2>
    ${sites.length ? sites.map(([site, v]) => `<div class="set"><div class="lbl">${esc(site)}<div class="s">${esc(['camera', 'microphone', 'location', 'popups'].filter(p => v[p] && v[p] !== 'ask').map(p => `${T[p]}: ${T[v[p]]}`).concat(v.adblockException ? [T.exceptions] : []).join(' · '))}</div></div>
      <button class="btn" data-siteinfo="${esc(site)}">${esc(T.edit)}</button><button class="btn" data-sitereset="${esc(site)}">${esc(T.resetSite)}</button></div>`).join('') : `<p class="muted small">${esc(T.noSites)}</p>`}
    <h2>${esc(T.sTranslate)}</h2>
    <div class="set"><div class="lbl">${esc(T.translateHint)}</div><input class="field grow" data-text="translateTemplate" value="${esc(s.translateTemplate)}"></div>
    <h2>${esc(T.sShortcuts)}</h2>
    ${T.shortcuts.map(([k, l]) => `<div class="set"><div class="lbl">${esc(l)}</div><kbd>${esc(k)}</kbd></div>`).join('')}
    <h2>${esc(T.sAbout)}</h2>
    <p>${esc(T.version(info.version || '', info.cef || '', info.chromium || ''))}</p>
    <p class="muted small">${esc(T.noTelemetry)}</p>
    <p class="muted small">${esc(info.portable ? T.portable : `${T.installed} ${info.profileDir || ''}`)}</p>
    <p class="muted small">${esc(T.compatNote)}</p>
    <h2>${esc(T.privacyPolicy)}</h2><div class="text-block small">${esc(T.privacyText)}</div>
    <h2>${esc(T.licenses)}</h2><div class="text-block small">${esc(T.licensesText)}</div>
    ${S.panelState.clearOpen ? renderClearData() : ''}
  </div>`;
}

// ------------------------------------------------------------------------------------- actions
async function updateBlockList() {
  try {
    const url = S.settings.blockListUrl;
    if (!/^https:\/\//.test(url)) throw new Error('HTTPS URL required');
    const r = await fetch(url, { credentials: 'omit', cache: 'no-store' });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const text = await r.text();
    if (text.length > 20 * 1024 * 1024) throw new Error('list too large');
    const res = await ipc('blocklist.save', { text });
    if (!res.ok) throw new Error('save failed');
    S.info.blocklistSize = res.size;
    S.settings.blockListUpdated = Date.now();
    saveSettings();
    infobar({ kind: 'info', timeout: 6000, html: esc(T.blocklistUpdated(res.size)) });
  } catch (e) {
    infobar({ kind: 'danger', timeout: 8000, html: esc(T.blocklistFailed(e.message || e)) });
  }
}

async function clearData() {
  const c = S.panelState.clear;
  const cutoff = core.historyCutoff(c.range, core.now());
  const origins = new Set();
  for (const h of S.history) { try { origins.add(new URL(h.url).origin); } catch {} }
  for (const t of S.tabs) { try { if (t.url) origins.add(new URL(t.url).origin); } catch {} }
  if (c.history) { S.history = S.history.filter(h => h.t < cutoff); storage.set('history', S.history, 0); S.closed = []; }
  if (c.downloads) { S.downloads = S.downloads.filter(d => d.state === 'progress' || d.state === 'paused'); saveDownloads(); }
  if (c.perms) { S.sites = {}; saveSites(); }
  if (c.cookies || c.cache) await ipc('data.clear', { cookies: c.cookies, cache: c.cache, origins: c.cookies ? [...origins] : [] });
  S.panelState.clearOpen = false;
  infobar({ kind: 'info', timeout: 5000, html: esc(T.dataCleared) });
}

function saveBookmarkDialog() {
  const d = S.dialog;
  const title = $('#bmTitle').value.trim();
  const parentId = Number($('#bmParent').value) || 0;
  if (!title) { d.err = T.titleError; return render(); }
  let url = null;
  if (!d.folder) { url = core.validateBookmarkUrl($('#bmUrl').value); if (!url) { d.err = T.urlError; return render(); } }
  if (d.id && d.folder) {
    const pairs = S.bookmarks.flatMap(b => [String(b.id), String(b.parentId || '')]);
    if (core.bookmarkMoveCreatesCycle(String(d.id), String(parentId || ''), pairs)) { d.err = T.cycleError; return render(); }
  }
  if (d.id) Object.assign(S.bookmarks.find(b => b.id === d.id), { title, url, parentId });
  else S.bookmarks.push({ id: Date.now(), parentId, title, url, created: Date.now() });
  storage.set('bookmarks', S.bookmarks);
  S.dialog = null;
  render();
}

function menuAction(act) {
  const t = cur();
  S.menu = false;
  if (act.startsWith('p:')) return showPanel(act.slice(2));
  switch (act) {
    case 'newTab': openTab(); focusAddress(); break;
    case 'newPrivate': openTab({ priv: true }); focusAddress(); break;
    case 'reopen': reopenClosed(); break;
    case 'zoomIn': S.menu = true; zoomStep(1); break;
    case 'zoomOut': S.menu = true; zoomStep(-1); break;
    case 'find': openFind(); break;
    case 'print': ipc('tab.print', { id: t.id }); break;
    case 'pdf': ipc('tab.pdf', { id: t.id }); break;
    case 'screenshot': ipc('tab.screenshot', { id: t.id }); break;
    case 'copyLink': navigator.clipboard.writeText(t.url).then(() => infobar({ kind: 'info', timeout: 2500, html: esc(T.copied) })); break;
    case 'translate': {
      const url = S.settings.translateTemplate.replace('{lang}', encodeURIComponent((navigator.language || 'en').split('-')[0])).replace('{url}', encodeURIComponent(t.url));
      const host = core.hostOf(url);
      if (host) S.dialog = { kind: 'translate', url, host };
      break;
    }
  }
  render();
}

// --------------------------------------------------------------------------------- DOM events
document.addEventListener('click', e => {
  const el = e.target.closest('[data-id],[data-close],[data-act],[data-panel],[data-bar],[data-open],[data-folder],[data-edit],[data-del],[data-hdel],[data-dl],[data-sg],[data-perm],[data-siteinfo],[data-sitereset],#newtab');
  if (!el) return;
  const d = el.dataset;
  if (d.close) { e.stopPropagation(); return closeTab(Number(d.close)); }
  if (el.id === 'newtab') { openTab(); return focusAddress(); }
  if (d.id && el.classList.contains('tab')) return selectTab(Number(d.id));
  if (d.bar) {
    const bar = S.infobars.find(b => b.key === d.bar);
    if (!bar) return;
    if (d.act === 'x') return dismissBar(bar.key);
    const a = bar.actions[Number(d.act)];
    a?.run();
    if (!bar.noDismiss || !a) dismissBar(bar.key);
    return;
  }
  if (d.panel) return showPanel(d.panel);
  if (d.sg != null) { const it = S.suggest?.items?.[Number(d.sg)]; S.suggest = null; S.editing = false; if (it) navigate(cur(), it.url); return; }
  if (d.perm) {
    const site = dialogSite();
    const s = (S.sites[site] ||= {});
    if (d.val === 'ask') delete s[d.perm]; else s[d.perm] = d.val;
    saveSites();
    return render();
  }
  if (d.siteinfo) { S.dialog = { kind: 'siteinfo', site: d.siteinfo }; return render(); }
  if (d.sitereset) { delete S.sites[d.sitereset]; saveSites(); return render(); }
  if (d.open) { const t = cur(); S.panel = null; if (t && !t.url && !t.hasBrowser) navigate(t, d.open); else openTab({ url: d.open }); return; }
  if (d.folder != null && !e.target.closest('button')) { S.panelState.folder = Number(d.folder); return render(); }
  if (d.edit) { const b = S.bookmarks.find(x => x.id === Number(d.edit)); S.dialog = { kind: 'bookmark', id: b.id, folder: !b.url, title: b.title, url: b.url || '', parentId: b.parentId || 0 }; return render(); }
  if (d.del) return deleteBookmark(Number(d.del));
  if (d.hdel) { e.stopPropagation(); S.history.splice(Number(d.hdel), 1); storage.set('history', S.history, 300); return render(); }
  if (d.dl != null) {
    const dl = S.downloads[Number(d.dl)];
    if (!dl) return;
    switch (d.dla) {
      case 'pause': case 'resume': case 'cancel': ipc('download.control', { downloadId: dl.downloadId, action: d.dla }); break;
      case 'open': openDownload(dl); break;
      case 'show': ipc('download.show', { path: dl.path }); break;
      case 'retry': S.downloads.splice(Number(d.dl), 1); ipc('download.start', { url: dl.url }); break;
      case 'remove': S.downloads.splice(Number(d.dl), 1); saveDownloads(); break;
    }
    return render();
  }
  switch (d.act) {
    case 'reload': return reload();
    case 'back': return back();
    case 'copyError': {
      const t = cur();
      const er = t.error;
      navigator.clipboard.writeText(core.errorDetails(er.kind, er.code, er.text || '', er.url || t.url, S.info.version || '')).then(() => infobar({ kind: 'info', timeout: 2500, html: esc(T.copied) }));
      return;
    }
    case 'closeMenu': S.menu = false; return render();
    case 'closeDialog': S.dialog = null; return render();
    case 'openAnyway': ipc('download.open', { path: S.dialog.download.path }); S.dialog = null; return render();
    case 'translateGo': { const url = S.dialog.url; S.dialog = null; openTab({ url, priv: cur()?.private, after: S.selected }); return; }
    case 'resetSite': { delete S.sites[dialogSite()]; saveSites(); return render(); }
    case 'addBookmark': S.dialog = { kind: 'bookmark', folder: false, title: '', url: '', parentId: S.panelState.folder || 0 }; return render();
    case 'addFolder': S.dialog = { kind: 'bookmark', folder: true, title: '', url: '', parentId: S.panelState.folder || 0 }; return render();
    case 'clearDownloads': S.downloads = S.downloads.filter(x => x.state === 'progress' || x.state === 'paused' || x.state === 'waiting'); saveDownloads(); return render();
    case 'clearDataDialog': S.panelState.clearOpen = true; return render();
    case 'clearCancel': S.panelState.clearOpen = false; return render();
    case 'clearRun': return clearData().then(render);
    case 'pickFolder': return ipc('folder.pick').then(r => { if (r.path) { S.settings.downloadDir = r.path; saveSettings(); render(); } });
    case 'updateList': return updateBlockList().then(render);
    case 'removeList': return ipc('blocklist.save', { text: '' }).then(r => { S.info.blocklistSize = r.size; S.settings.blockListUpdated = 0; saveSettings(); render(); });
  }
  if (d.act && el.classList.contains('mi')) return menuAction(d.act);
  if (d.act && el.closest('.menu')) return menuAction(d.act);
});
document.addEventListener('change', e => {
  const el = e.target;
  if (el.dataset.setting) { S.settings[el.dataset.setting] = el.checked; saveSettings(); render(); }
  else if (el.dataset.select) { S.settings[el.dataset.select] = el.value; if (el.dataset.select === 'theme') applyTheme(); saveSettings(); render(); }
  else if (el.dataset.text) { S.settings[el.dataset.text] = el.value.trim(); saveSettings(); }
  else if (el.dataset.toggle === 'siteAdblock') {
    const site = dialogSite();
    (S.sites[site] ||= {}).adblockException = !el.checked;
    saveSites();
    if (!S.dialog?.site && cur()?.hasBrowser) ipc('tab.reload', { id: S.selected });
    render();
  }
  else if (el.dataset.clear) { S.panelState.clear[el.dataset.clear] = el.checked; }
  else if (el.id === 'clearRange') { S.panelState.clear.range = el.value; }
});
document.addEventListener('input', e => {
  const el = e.target;
  if (el.id === 'bmQuery') { S.panelState.bmQuery = el.value; render(); }
  if (el.id === 'hQuery') { S.panelState.hQuery = el.value; render(); }
  if (el.id === 'findInput') { S.find.text = el.value; findGo(true, false); render(); }
});
document.addEventListener('submit', e => {
  e.preventDefault();
  const f = e.target.dataset.form;
  if (f === 'start') submit($('#startInput').value);
  if (f === 'bookmark') saveBookmarkDialog();
});

// address bar
const address = $('#address');
address.addEventListener('focus', () => { const t = cur(); S.editing = true; address.value = t?.url || ''; address.select(); });
address.addEventListener('blur', () => setTimeout(() => { S.editing = false; if (S.suggest) { S.suggest = null; render(); } else renderNav(); }, 150));
address.addEventListener('input', () => { S.suggest = { text: address.value, sel: 0 }; render(); });
address.addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    const sel = S.suggest?.sel ?? 0;
    const it = S.suggest?.items?.[sel];
    const text = address.value;
    S.suggest = null; S.editing = false;
    address.blur();
    if (it && sel > 0) navigate(cur(), it.url); else submit(text);
  } else if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && S.suggest?.items?.length) {
    e.preventDefault();
    const n = S.suggest.items.length;
    S.suggest.sel = ((S.suggest.sel ?? 0) + (e.key === 'ArrowDown' ? 1 : -1) + n) % n;
    address.value = S.suggest.sel === 0 ? S.suggest.text : S.suggest.items[S.suggest.sel].url;
    render();
  }
});
$('#back').onclick = back;
$('#forward').onclick = () => shortcut('forward');
$('#reload').onclick = reload;
$('#home').onclick = goHome;
$('#star').onclick = toggleBookmark;
$('#downloadsBtn').onclick = () => showPanel(S.panel === 'downloads' ? null : 'downloads');
$('#menuBtn').onclick = () => { S.menu = !S.menu; render(); };
$('#security').onclick = () => { S.dialog = { kind: 'siteinfo' }; render(); };
$('#shield').onclick = () => { S.dialog = { kind: 'siteinfo' }; render(); };
$('#findInput').addEventListener('keydown', e => { if (e.key === 'Enter') findGo(!e.shiftKey, true); });
$('#findPrev').onclick = () => findGo(false, true);
$('#findNext').onclick = () => findGo(true, true);
$('#findClose').onclick = () => closeFind();
// Broken favicons are removed (inline handlers are forbidden by the UI's CSP).
document.addEventListener('error', e => { if (e.target?.classList?.contains('fav')) e.target.remove(); }, true);
document.addEventListener('auxclick', e => { const el = e.target.closest('.tab'); if (el && e.button === 1) closeTab(Number(el.dataset.id)); });
matchMedia('(prefers-color-scheme: light)').addEventListener('change', () => { if (S.settings.theme === 'system') applyTheme(); });
window.addEventListener('resize', () => { lastLayout = ''; render(); });

// ----------------------------------------------------------------------------------------- boot
(async function boot() {
  applyTheme();
  S.info = await ipc('init');
  S.settings = { ...DEFAULT_SETTINGS, ...(await storage.get('settings', {})) };
  applyTheme();
  [S.bookmarks, S.history, S.sites, S.downloads] = await Promise.all([
    storage.get('bookmarks', []), storage.get('history', []), storage.get('sites', {}), storage.get('downloads', []),
  ]);
  // Downloads that were running when the app closed cannot be continued by CEF after a restart.
  for (const d of S.downloads) if (d.state === 'progress' || d.state === 'paused' || d.state === 'waiting') d.state = 'interrupted';
  pushConfig();
  const session = S.settings.restoreTabs ? await storage.get('session', null) : null;
  if (session?.tabs?.length) {
    for (const st of session.tabs) { const t = newTabState(S.nextId++, false, st.url); t.title = st.title || ''; S.tabs.push(t); }
    selectTab(S.tabs[Math.max(0, Math.min(session.selected, S.tabs.length - 1))].id);
  } else {
    openTab();
  }
  render();
})();
