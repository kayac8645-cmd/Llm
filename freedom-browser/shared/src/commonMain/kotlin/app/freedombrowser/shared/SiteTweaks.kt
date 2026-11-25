package app.freedombrowser.shared

/**
 * Optional cosmetic tweaks applied to specific sites. They only hide elements with CSS; no data is
 * read from or sent to the page, and nothing is exposed to the page.
 */
object SiteTweaks {
    /** True for DuckDuckGo result pages (a `q=` query on duckduckgo.com or a subdomain). */
    fun isDuckDuckGoResults(url: String): Boolean {
        val u = WebUrl.parse(url) ?: return false
        if (!u.isHttps) return false
        if (u.host != "duckduckgo.com" && !u.host.endsWith(".duckduckgo.com")) return false
        val q = u.query ?: return false
        return q.split('&').any { it.startsWith("q=") && it.length > 2 }
    }

    /** Origins allowed to receive the DuckDuckGo script (Android `addDocumentStartJavaScript` rules). */
    val DUCKDUCKGO_ORIGINS = setOf("https://duckduckgo.com", "https://*.duckduckgo.com")

    /**
     * Hides DuckDuckGo's top row on result pages: the logo, the Duck.ai entry and the menu button.
     *
     * 1. CSS for DuckDuckGo's stable behaviour hooks (`#header-logo-wrapper`, `.js-header-logo`,
     *    `.js-header-aside`, `.js-side-menu-open`), checked against the live result page on 2026-11-25.
     * 2. A fallback limited to the header container (`#header_wrapper`, `#header`, `<header>`): logo
     *    links, Duck.ai links/buttons and menu buttons found there are hidden individually. The search
     *    form, the result tabs (`#react-duckbar`) and the results are never touched.
     * Applies only when the URL has a `q=` query; idempotent; re-applies when the page re-renders.
     */
    const val DUCKDUCKGO_HIDE_HEADER_JS = """(function () {
  if (window.__lumoDdgHeader) return; window.__lumoDdgHeader = true;
  var CSS = 'html.lumo-ddg-serp #header-logo-wrapper, html.lumo-ddg-serp .js-header-logo, html.lumo-ddg-serp .header__logo-wrap,' +
    'html.lumo-ddg-serp .js-header-aside, html.lumo-ddg-serp .header--aside, html.lumo-ddg-serp #header-hamburger-menu-btn,' +
    'html.lumo-ddg-serp .js-side-menu-open, html.lumo-ddg-serp [data-lumo-hidden] { display: none !important; }';
  function isSerp() { return /[?&]q=[^&]/.test(location.search); }
  function ensureStyle() {
    if (document.getElementById('lumo-ddg-style') || !document.head) return;
    var st = document.createElement('style'); st.id = 'lumo-ddg-style'; st.textContent = CSS; document.head.appendChild(st);
  }
  function txt(e) { return (e.textContent || '').replace(/\s+/g, ' ').trim(); }
  function run() {
    var root = document.documentElement;
    if (!root) return;
    root.classList.toggle('lumo-ddg-serp', isSerp());
    if (!isSerp()) return;
    ensureStyle();
    var header = document.getElementById('header_wrapper') || document.getElementById('header') || document.querySelector('header');
    if (!header) return;
    var count = 0;
    header.querySelectorAll('a, button').forEach(function (e) {
      if (e.closest('form, #react-duckbar, nav')) return;
      var href = e.getAttribute('href') || '';
      var label = (e.getAttribute('aria-label') || '') + ' ' + (e.getAttribute('title') || '') + ' ' + (e.id || '') + ' ' + String(e.className || '');
      var logo = (/^\/(\?t=|\?$|$)/.test(href) || /^https:\/\/(start\.)?duckduckgo\.com\/?$/.test(href)) && !!e.querySelector('svg, img');
      var duckAi = /duck\.ai|ia=chat|duckchat/i.test(href) || /duck\.?ai/i.test(label) || /^duck\.ai$/i.test(txt(e));
      var menu = /hamburger|side-menu-open|open menu|men\u00fc/i.test(label);
      if (logo || duckAi || menu) { e.setAttribute('data-lumo-hidden', '1'); count++; }
    });
    if (window.__lumoDebug !== count) { window.__lumoDebug = count; try { console.debug('[lumo] ddg-header fallback=' + count); } catch (x) {} }
  }
  function schedule() { clearTimeout(window.__lumoDdgTimer); window.__lumoDdgTimer = setTimeout(run, 50); }
  function start() {
    run();
    new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('popstate', schedule);
  }
  if (document.documentElement) start(); else document.addEventListener('readystatechange', start, { once: true });
})();"""
}
