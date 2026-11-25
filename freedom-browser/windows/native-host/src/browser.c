#include "freedom.h"
#include <commdlg.h>
#include <shlobj.h>

#include "include/capi/cef_auth_callback_capi.h"
#include "include/capi/cef_registration_capi.h"

static int is_turkish(void) { return PRIMARYLANGID(GetUserDefaultUILanguage()) == LANG_TURKISH; }

/* =============================================================== tab list */

tab* tab_find(int id) {
  for (tab* t = g.tabs; t; t = t->next) if (t->id == id) return t;
  return NULL;
}

tab* tab_find_browser(cef_browser_t* b) {
  if (!b) return NULL;
  int bid = b->get_identifier(b);
  for (tab* t = g.tabs; t; t = t->next) if (t->browser && t->browser->get_identifier(t->browser) == bid) return t;
  return NULL;
}

static void tab_link(tab* t) {
  AcquireSRWLockExclusive(&g.lock);
  tab** p = &g.tabs;
  while (*p) p = &(*p)->next;
  *p = t;
  ReleaseSRWLockExclusive(&g.lock);
}

static void tab_unlink(tab* t) {
  AcquireSRWLockExclusive(&g.lock);
  for (tab** p = &g.tabs; *p; p = &(*p)->next) if (*p == t) { *p = t->next; break; }
  ReleaseSRWLockExclusive(&g.lock);
}

void page_host_set(tab* t, const char* url) {
  char* host = url_host(url);
  AcquireSRWLockExclusive(&g.lock);
  free(t->page_host);
  t->page_host = host;
  ReleaseSRWLockExclusive(&g.lock);
}

static int any_private_tab(void) {
  for (tab* t = g.tabs; t; t = t->next) if (t->is_private) return 1;
  return 0;
}

/* Private tabs share one in-memory ("incognito") request context; it is dropped when the
 * last private tab closes, which discards its cookies, storage and cache. */
static cef_request_context_t* private_context(void) {
  if (!g.private_context) {
    cef_request_context_settings_t s = {0};
    s.size = sizeof s; /* empty cache_path => in-memory profile */
    g.private_context = cef_request_context_create_context(&s, NULL);
  }
  CEF_ADDREF(g.private_context);
  return g.private_context;
}

static RECT content_rect(void) {
  RECT rc;
  GetClientRect(g.main_hwnd, &rc);
  return rc;
}

int tab_create(int id, int is_private, const char* url, int select) {
  if (id == 0 || tab_find(id) || g.closing) return 0;
  RECT rc = content_rect();
  cef_window_info_t wi = {0};
  wi.size = sizeof wi;
  wi.style = WS_CHILD | WS_CLIPCHILDREN | WS_CLIPSIBLINGS | WS_TABSTOP;
  wi.parent_window = g.main_hwnd;
  wi.bounds.width = rc.right;
  wi.bounds.height = rc.bottom;
  wi.runtime_style = CEF_RUNTIME_STYLE_ALLOY;
  cef_browser_settings_t bs = {0};
  bs.size = sizeof bs;
  bs.background_color = 0xFFFFFFFF; /* web pages without a background are white, as in every browser */
  cef_string_t u = {0};
  cs_set(&u, (url && *url) ? url : "about:blank");
  cef_client_t* client = content_client();
  client->base.add_ref(&client->base);
  cef_request_context_t* ctx = is_private ? private_context() : NULL;
  tab* t = (tab*)calloc(1, sizeof(tab));
  t->id = id;
  t->is_private = is_private;
  if (url) page_host_set(t, url);
  tab_link(t); /* linked before creation so early IO-thread callbacks can find it */
  t->browser = cef_browser_host_create_browser_sync(&wi, client, &u, &bs, NULL, ctx);
  cs_clear(&u);
  if (!t->browser) {
    tab_unlink(t);
    free(t->page_host);
    free(t);
    return 0;
  }
  g.browser_count++;
  if (select || !g.selected_tab) g.selected_tab = id;
  layout_browsers();
  return 1;
}

void tab_select(int id) {
  if (!tab_find(id)) return;
  g.selected_tab = id;
  layout_browsers();
}

void tab_close(int id) {
  tab* t = tab_find(id);
  if (!t || !t->browser) return;
  cef_browser_host_t* host = t->browser->get_host(t->browser);
  host->close_browser(host, 0); /* runs beforeunload handlers */
  CEF_RELEASE(host);
}

void tabs_close_all(void) {
  for (tab* t = g.tabs; t; t = t->next) {
    cef_browser_host_t* host = t->browser->get_host(t->browser);
    host->close_browser(host, 1);
    CEF_RELEASE(host);
  }
}

/* ========================================================== shared helpers */

static char* site_of(const char* origin_or_url) {
  char* h = url_host(origin_or_url);
  if (h && !strncmp(h, "www.", 4)) memmove(h, h + 4, strlen(h + 4) + 1);
  return h;
}

/* "allow" / "block" / "ask" for site+permission from the UI-provided config. */
static int site_decision(const char* site, const char* perm) {
  int r = 0; /* 0 ask, 1 allow, -1 block */
  AcquireSRWLockShared(&g.lock);
  if (g.site_permissions && site) {
    cef_dictionary_value_t* sp = dict_dict(g.site_permissions, site);
    if (sp) {
      char* v = dict_str(sp, perm);
      if (v) r = !strcmp(v, "allow") ? 1 : !strcmp(v, "block") ? -1 : 0;
      free(v);
      CEF_RELEASE(sp);
    }
  }
  ReleaseSRWLockShared(&g.lock);
  return r;
}

static void copy_to_clipboard(const char* utf8) {
  wchar_t* w = utf8_to_wide(utf8);
  size_t bytes = (wcslen(w) + 1) * sizeof(wchar_t);
  if (OpenClipboard(g.main_hwnd)) {
    EmptyClipboard();
    HGLOBAL mem = GlobalAlloc(GMEM_MOVEABLE, bytes);
    if (mem) {
      memcpy(GlobalLock(mem), w, bytes);
      GlobalUnlock(mem);
      SetClipboardData(CF_UNICODETEXT, mem);
    }
    CloseClipboard();
  }
  free(w);
}

static void emit_tab_str(const char* type, int id, const char* key, const char* value) {
  sbuf b;
  sb_init(&b);
  sb_printf(&b, "{\"type\":\"%s\",\"id\":%d,\"%s\":", type, id, key);
  sb_json_str(&b, value);
  sb_append(&b, "}");
  ui_emit(b.data);
  sb_free(&b);
}

/* ======================================================== display handler */

typedef struct { cef_display_handler_t h; } disp_t;

static void CEF_CALLBACK on_address_change(cef_display_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, const cef_string_t* url) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t && frame->is_main(frame)) {
    char* u = cs_to_utf8(url);
    page_host_set(t, u);
    t->blocked = 0;
    emit_tab_str("url", t->id, "url", u);
    free(u);
  }
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
}

static void CEF_CALLBACK on_title_change(cef_display_handler_t* self, cef_browser_t* browser, const cef_string_t* title) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t) {
    char* s = cs_to_utf8(title);
    emit_tab_str("title", t->id, "title", s);
    free(s);
  }
  CEF_RELEASE(browser);
}

static void CEF_CALLBACK on_favicon(cef_display_handler_t* self, cef_browser_t* browser, cef_string_list_t urls) {
  (void)self;
  tab* t = tab_find_browser(browser);
  /* Private tabs never show favicons: the UI would fetch them with the normal profile. */
  if (t && !t->is_private && cef_string_list_size(urls) > 0) {
    cef_string_t first = {0};
    if (cef_string_list_value(urls, 0, &first)) {
      char* u = cs_to_utf8(&first);
      if (str_starts_with(u, "https://")) emit_tab_str("favicon", t->id, "url", u);
      free(u);
      cs_clear(&first);
    }
  }
  CEF_RELEASE(browser);
}

static void CEF_CALLBACK on_fullscreen(cef_display_handler_t* self, cef_browser_t* browser, int fullscreen) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t && t->id == g.selected_tab) set_html_fullscreen(fullscreen);
  CEF_RELEASE(browser);
}

static void CEF_CALLBACK on_progress(cef_display_handler_t* self, cef_browser_t* browser, double progress) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t) ui_emitf("{\"type\":\"progress\",\"id\":%d,\"progress\":%d}", t->id, (int)(progress * 100));
  CEF_RELEASE(browser);
}

/* =========================================================== load handler */

typedef struct { cef_load_handler_t h; } load_t;

static void CEF_CALLBACK on_loading_state(cef_load_handler_t* self, cef_browser_t* browser, int loading, int back, int fwd) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t) ui_emitf("{\"type\":\"loading\",\"id\":%d,\"loading\":%s,\"canBack\":%s,\"canForward\":%s}", t->id, loading ? "true" : "false",
                  back ? "true" : "false", fwd ? "true" : "false");
  CEF_RELEASE(browser);
}

static void CEF_CALLBACK on_load_start(cef_load_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, cef_transition_type_t tt) {
  (void)self; (void)tt;
  tab* t = tab_find_browser(browser);
  if (t && frame->is_main(frame)) ui_emitf("{\"type\":\"loadStart\",\"id\":%d}", t->id);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
}

static void CEF_CALLBACK on_load_error(cef_load_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, cef_errorcode_t code,
                                       const cef_string_t* text, const cef_string_t* failed_url) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t && frame->is_main(frame) && code != ERR_ABORTED) {
    char* txt = cs_to_utf8(text);
    char* url = cs_to_utf8(failed_url);
    sbuf b;
    sb_init(&b);
    sb_printf(&b, "{\"type\":\"loadError\",\"id\":%d,\"code\":%d,\"text\":", t->id, (int)code);
    sb_json_str(&b, txt);
    sb_append(&b, ",\"url\":");
    sb_json_str(&b, url);
    sb_append(&b, "}");
    ui_emit(b.data);
    sb_free(&b);
    free(txt);
    free(url);
  }
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
}

/* ===================================================== ad/tracker blocking */

typedef struct { cef_resource_request_handler_t h; } rrh_t;
static rrh_t* g_rrh;

static void blocked_task(void* arg) {
  int id = (int)(intptr_t)arg;
  tab* t = tab_find(id);
  if (!t) return;
  t->blocked++;
  ui_emitf("{\"type\":\"blocked\",\"id\":%d,\"count\":%d}", id, t->blocked);
}

/* Runs on the IO thread. */
static cef_return_value_t CEF_CALLBACK on_before_resource_load(cef_resource_request_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                                               cef_request_t* request, cef_callback_t* callback) {
  (void)self;
  cef_return_value_t rv = RV_CONTINUE;
  int is_main = request->get_resource_type(request) == RT_MAIN_FRAME;
  if (!is_main && browser) {
    char* url = csu_to_utf8_free(request->get_url(request));
    int bid = browser->get_identifier(browser);
    int tab_id = 0, block = 0;
    AcquireSRWLockShared(&g.lock);
    for (tab* t = g.tabs; t; t = t->next) {
      if (t->browser && t->browser->get_identifier(t->browser) == bid) {
        tab_id = t->id;
        block = blocklist_should_block(g.blocklist, url, t->page_host, 0, g.adblock_enabled,
                                       (const char* const*)g.adblock_exceptions, g.adblock_exception_count);
        break;
      }
    }
    ReleaseSRWLockShared(&g.lock);
    free(url);
    if (block) {
      rv = RV_CANCEL;
      post_ui_task(blocked_task, (void*)(intptr_t)tab_id);
    }
  }
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(request);
  CEF_RELEASE(callback);
  return rv;
}

typedef struct { char* url; } external_arg;

static void external_task(void* arg) {
  external_arg* a = (external_arg*)arg;
  sbuf b;
  sb_init(&b);
  sb_append(&b, "{\"type\":\"external\",\"url\":");
  sb_json_str(&b, a->url);
  sb_append(&b, "}");
  ui_emit(b.data);
  sb_free(&b);
  free(a->url);
  free(a);
}

static void CEF_CALLBACK on_protocol_execution(cef_resource_request_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                               cef_request_t* request, int* allow_os_execution) {
  (void)self;
  *allow_os_execution = 0; /* never launched silently */
  char* url = csu_to_utf8_free(request->get_url(request));
  if (str_starts_with(url, "mailto:") || str_starts_with(url, "tel:")) {
    external_arg* a = (external_arg*)malloc(sizeof *a);
    a->url = url;
    url = NULL;
    post_ui_task(external_task, a);
  }
  free(url);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(request);
}

/* ======================================================== request handler */

typedef struct { cef_request_handler_t h; } req_t;

static cef_resource_request_handler_t* CEF_CALLBACK get_rrh(cef_request_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                                            cef_request_t* request, int is_nav, int is_download,
                                                            const cef_string_t* initiator, int* disable_default) {
  (void)self; (void)is_nav; (void)is_download; (void)initiator; (void)disable_default;
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(request);
  fr_add_ref(g_rrh);
  return &g_rrh->h;
}

static int CEF_CALLBACK on_before_browse(cef_request_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, cef_request_t* request,
                                         int user_gesture, int is_redirect) {
  (void)self; (void)user_gesture; (void)is_redirect;
  char* url = csu_to_utf8_free(request->get_url(request));
  int is_main = frame->is_main(frame);
  /* Content tabs never show local files, internal schemes or Lumo's UI. */
  int cancel = is_main && (str_starts_with(url, "file:") || str_starts_with(url, "freedom:") || str_starts_with(url, "chrome:") ||
                           str_starts_with(url, "devtools:") || str_starts_with(url, "javascript:"));
  free(url);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(request);
  return cancel;
}

static int CEF_CALLBACK on_certificate_error(cef_request_handler_t* self, cef_browser_t* browser, cef_errorcode_t err, const cef_string_t* url,
                                             cef_sslinfo_t* ssl, cef_callback_t* callback) {
  (void)self; (void)err; (void)url;
  CEF_RELEASE(browser);
  CEF_RELEASE(ssl);
  CEF_RELEASE(callback);
  return 0; /* cancel: certificate errors are never bypassed; on_load_error reports them */
}

static void CEF_CALLBACK on_render_terminated(cef_request_handler_t* self, cef_browser_t* browser, cef_termination_status_t status, int code,
                                              const cef_string_t* text) {
  (void)self; (void)code; (void)text;
  tab* t = tab_find_browser(browser);
  if (t) ui_emitf("{\"type\":\"crashed\",\"id\":%d,\"status\":%d}", t->id, (int)status);
  flog("renderer terminated status=%d", (int)status);
  CEF_RELEASE(browser);
}

/* HTTP authentication: asks the UI for credentials. */
typedef struct auth_pending { int id; cef_auth_callback_t* cb; struct auth_pending* next; } auth_pending;
static auth_pending* g_auth;
static int g_auth_next = 1;

static int CEF_CALLBACK get_auth_credentials(cef_request_handler_t* self, cef_browser_t* browser, const cef_string_t* origin, int is_proxy,
                                             const cef_string_t* host, int port, const cef_string_t* realm, const cef_string_t* scheme,
                                             cef_auth_callback_t* callback) {
  (void)self; (void)origin; (void)port; (void)scheme;
  tab* t = tab_find_browser(browser);
  CEF_RELEASE(browser);
  if (!t || is_proxy) { CEF_RELEASE(callback); return 0; }
  auth_pending* p = (auth_pending*)calloc(1, sizeof *p);
  p->id = g_auth_next++;
  p->cb = callback;
  p->next = g_auth;
  g_auth = p;
  char* h = cs_to_utf8(host);
  char* r = cs_to_utf8(realm);
  sbuf b;
  sb_init(&b);
  sb_printf(&b, "{\"type\":\"auth\",\"authId\":%d,\"id\":%d,\"host\":", p->id, t->id);
  sb_json_str(&b, h);
  sb_append(&b, ",\"realm\":");
  sb_json_str(&b, r);
  sb_append(&b, "}");
  ui_emit(b.data);
  sb_free(&b);
  free(h);
  free(r);
  return 1;
}

void auth_answer(int auth_id, const char* user, const char* pass) {
  for (auth_pending** pp = &g_auth; *pp; pp = &(*pp)->next) {
    auth_pending* p = *pp;
    if (p->id != auth_id) continue;
    *pp = p->next;
    if (user) {
      cef_string_t u = {0}, pw = {0};
      cs_set(&u, user);
      cs_set(&pw, pass ? pass : "");
      p->cb->cont(p->cb, &u, &pw);
      cs_clear(&u);
      cs_clear(&pw);
    } else {
      p->cb->cancel(p->cb);
    }
    CEF_RELEASE(p->cb);
    free(p);
    return;
  }
}

/* ======================================================= life span handler */

typedef struct pending_popup { int opener; int is_private; int background; struct pending_popup* next; } pending_popup;
static pending_popup* g_popups;

typedef struct { cef_life_span_handler_t h; } lsh_t;

static int CEF_CALLBACK on_before_popup(cef_life_span_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, int popup_id,
                                        const cef_string_t* target_url, const cef_string_t* target_frame_name,
                                        cef_window_open_disposition_t disp, int user_gesture, const cef_popup_features_t* features,
                                        cef_window_info_t* wi, cef_client_t** client, cef_browser_settings_t* settings,
                                        cef_dictionary_value_t** extra, int* no_js) {
  (void)self; (void)popup_id; (void)features; (void)client; (void)settings; (void)extra; (void)no_js;
  tab* opener = tab_find_browser(browser);
  char* url = cs_to_utf8(target_url);
  int cancel = 1;
  if (opener && (str_starts_with(url, "http://") || str_starts_with(url, "https://") || !strcmp(url, "about:blank") || !*url)) {
    char* page_site = NULL;
    AcquireSRWLockShared(&g.lock);
    page_site = opener->page_host ? str_dup(opener->page_host) : NULL;
    int allowed_site = host_in_list(page_site, (const char* const*)g.popup_allow, g.popup_allow_count);
    int block_popups = g.block_popups;
    ReleaseSRWLockShared(&g.lock);
    int named = target_frame_name && target_frame_name->length > 0;
    if (!user_gesture && block_popups && !allowed_site) {
      sbuf b;
      sb_init(&b);
      sb_printf(&b, "{\"type\":\"popupBlocked\",\"id\":%d,\"site\":", opener->id);
      sb_json_str(&b, page_site ? page_site : "");
      sb_append(&b, ",\"url\":");
      sb_json_str(&b, url);
      sb_append(&b, "}");
      ui_emit(b.data);
      sb_free(&b);
    } else if ((disp == CEF_WOD_NEW_FOREGROUND_TAB || disp == CEF_WOD_NEW_BACKGROUND_TAB) && !named && *url) {
      /* Link-style "open in new tab": the UI creates and tracks the tab. */
      sbuf b;
      sb_init(&b);
      sb_printf(&b, "{\"type\":\"openTab\",\"openerId\":%d,\"private\":%s,\"background\":%s,\"url\":", opener->id,
                opener->is_private ? "true" : "false", disp == CEF_WOD_NEW_BACKGROUND_TAB ? "true" : "false");
      sb_json_str(&b, url);
      sb_append(&b, "}");
      ui_emit(b.data);
      sb_free(&b);
    } else {
      /* Real popup (window.open with features / named target): keep window.opener working by letting
       * CEF create it, embedded as a new tab in our window. */
      RECT rc = content_rect();
      wi->parent_window = g.main_hwnd;
      wi->style = WS_CHILD | WS_CLIPCHILDREN | WS_CLIPSIBLINGS | WS_TABSTOP;
      wi->ex_style = 0;
      wi->bounds.x = 0;
      wi->bounds.y = 0;
      wi->bounds.width = rc.right;
      wi->bounds.height = rc.bottom;
      wi->runtime_style = CEF_RUNTIME_STYLE_ALLOY;
      pending_popup* p = (pending_popup*)calloc(1, sizeof *p);
      p->opener = opener->id;
      p->is_private = opener->is_private;
      p->background = disp == CEF_WOD_NEW_BACKGROUND_TAB;
      pending_popup** tail = &g_popups;
      while (*tail) tail = &(*tail)->next;
      *tail = p;
      cancel = 0;
    }
    free(page_site);
  }
  free(url);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  return cancel;
}

static void CEF_CALLBACK on_before_popup_aborted(cef_life_span_handler_t* self, cef_browser_t* browser, int popup_id) {
  (void)self; (void)popup_id;
  if (g_popups) { pending_popup* p = g_popups; g_popups = p->next; free(p); }
  CEF_RELEASE(browser);
}

static void CEF_CALLBACK on_after_created(cef_life_span_handler_t* self, cef_browser_t* browser) {
  (void)self;
  if (browser->is_popup(browser) && !tab_find_browser(browser) && g_popups) {
    pending_popup* p = g_popups;
    g_popups = p->next;
    tab* t = (tab*)calloc(1, sizeof(tab));
    t->id = g.next_native_tab_id--;
    t->is_private = p->is_private;
    t->browser = browser; /* keep the reference we were given */
    browser = NULL;
    tab_link(t);
    g.browser_count++;
    if (!p->background) g.selected_tab = t->id;
    ui_emitf("{\"type\":\"tabCreated\",\"id\":%d,\"openerId\":%d,\"private\":%s,\"background\":%s}", t->id, p->opener,
             p->is_private ? "true" : "false", p->background ? "true" : "false");
    free(p);
    layout_browsers();
  }
  CEF_RELEASE(browser);
}

static void CEF_CALLBACK on_before_close(cef_life_span_handler_t* self, cef_browser_t* browser) {
  (void)self;
  tab* t = tab_find_browser(browser);
  if (t) {
    int id = t->id, was_private = t->is_private;
    tab_unlink(t);
    CEF_RELEASE(t->browser);
    free(t->page_host);
    free(t);
    g.browser_count--;
    if (g.selected_tab == id) g.selected_tab = 0;
    if (!g.closing) ui_emitf("{\"type\":\"tabClosed\",\"id\":%d}", id);
    if (was_private && !any_private_tab() && g.private_context) {
      CEF_RELEASE(g.private_context);
      g.private_context = NULL;
      if (!g.closing) ui_emit("{\"type\":\"privateSessionEnded\"}");
    }
  }
  CEF_RELEASE(browser);
  maybe_quit();
}

/* ======================================================== download handler */

typedef struct dl_entry {
  uint32_t id;
  cef_before_download_callback_t* before; /* until the user answers */
  cef_download_item_callback_t* control;  /* latest pause/resume/cancel handle */
  char* name;
  DWORD last_emit;
  struct dl_entry* next;
} dl_entry;
static dl_entry* g_downloads;

static dl_entry* dl_find(uint32_t id, int create) {
  for (dl_entry* d = g_downloads; d; d = d->next) if (d->id == id) return d;
  if (!create) return NULL;
  dl_entry* d = (dl_entry*)calloc(1, sizeof *d);
  d->id = id;
  d->next = g_downloads;
  g_downloads = d;
  return d;
}

static void dl_remove(uint32_t id) {
  for (dl_entry** pp = &g_downloads; *pp; pp = &(*pp)->next) {
    dl_entry* d = *pp;
    if (d->id != id) continue;
    *pp = d->next;
    CEF_RELEASE(d->before);
    CEF_RELEASE(d->control);
    free(d->name);
    free(d);
    return;
  }
}

/* Windows-safe file name (mirrors shared FileNames.sanitize). */
static wchar_t* sanitize_name(const char* utf8) {
  wchar_t* w = utf8_to_wide(utf8 && *utf8 ? utf8 : "download");
  for (wchar_t* c = w; *c; c++) if (wcschr(L"\\/:*?\"<>|", *c) || *c < 32) *c = L'_';
  size_t n = wcslen(w);
  while (n && (w[n - 1] == L'.' || w[n - 1] == L' ')) w[--n] = 0;
  wchar_t* start = w;
  while (*start == L'.' || *start == L' ') start++;
  if (!*start) { free(w); return _wcsdup(L"download"); }
  static const wchar_t* reserved[] = {L"con", L"prn", L"aux", L"nul", L"com1", L"com2", L"com3", L"com4", L"com5", L"com6", L"com7", L"com8",
                                      L"com9", L"lpt1", L"lpt2", L"lpt3", L"lpt4", L"lpt5", L"lpt6", L"lpt7", L"lpt8", L"lpt9"};
  wchar_t base[16] = {0};
  wcsncpy(base, start, 15);
  wchar_t* dot = wcschr(base, L'.');
  if (dot) *dot = 0;
  int is_reserved = 0;
  for (size_t i = 0; i < sizeof reserved / sizeof reserved[0]; i++) if (!_wcsicmp(base, reserved[i])) is_reserved = 1;
  size_t len = wcslen(start);
  if (len > 120) {
    wchar_t* ext = wcsrchr(start, L'.');
    size_t extlen = (ext && wcslen(ext) < 12) ? wcslen(ext) : 0;
    wmemmove(start + 120 - extlen, start + len - extlen, extlen + 1);
    start[120] = 0;
  }
  wchar_t* r = (wchar_t*)malloc((wcslen(start) + 2) * sizeof(wchar_t));
  r[0] = 0;
  if (is_reserved) wcscpy(r, L"_");
  wcscat(r, start);
  free(w);
  return r;
}

/* dir\name, or dir\name (1).ext ... for the first free name. */
static wchar_t* unique_path(const wchar_t* dir, const wchar_t* name) {
  wchar_t* p = path_join(dir, name);
  if (!file_exists_w(p)) return p;
  free(p);
  const wchar_t* dot = wcsrchr(name, L'.');
  size_t base_len = (dot && dot != name) ? (size_t)(dot - name) : wcslen(name);
  const wchar_t* ext = (dot && dot != name) ? dot : L"";
  for (int i = 1; i < 10000; i++) {
    wchar_t buf[400];
    swprintf(buf, 400, L"%.*ls (%d)%ls", (int)base_len, name, i, ext);
    p = path_join(dir, buf);
    if (!file_exists_w(p)) return p;
    free(p);
  }
  return path_join(dir, name);
}

typedef struct { cef_download_handler_t h; } dlh_t;

static int CEF_CALLBACK can_download(cef_download_handler_t* self, cef_browser_t* browser, const cef_string_t* url, const cef_string_t* method) {
  (void)self; (void)url; (void)method;
  CEF_RELEASE(browser);
  return 1;
}

static int CEF_CALLBACK on_before_download(cef_download_handler_t* self, cef_browser_t* browser, cef_download_item_t* item,
                                           const cef_string_t* suggested, cef_before_download_callback_t* callback) {
  (void)self;
  tab* t = tab_find_browser(browser);
  uint32_t id = item->get_id(item);
  dl_entry* d = dl_find(id, 1);
  CEF_RELEASE(d->before);
  d->before = callback; /* kept until download_answer() */
  free(d->name);
  d->name = cs_to_utf8(suggested);
  char* url = csu_to_utf8_free(item->get_original_url(item));
  char* mime = csu_to_utf8_free(item->get_mime_type(item));
  sbuf b;
  sb_init(&b);
  sb_printf(&b, "{\"type\":\"downloadAsk\",\"downloadId\":%u,\"tabId\":%d,\"private\":%s,\"total\":%lld,\"name\":", id, t ? t->id : 0,
            (t && t->is_private) ? "true" : "false", (long long)item->get_total_bytes(item));
  sb_json_str(&b, d->name);
  sb_append(&b, ",\"mime\":");
  sb_json_str(&b, mime);
  sb_append(&b, ",\"url\":");
  sb_json_str(&b, str_starts_with(url, "data:") ? "data:" : url);
  sb_append(&b, "}");
  ui_emit(b.data);
  sb_free(&b);
  free(url);
  free(mime);
  CEF_RELEASE(browser);
  CEF_RELEASE(item);
  return 1;
}

void download_answer(uint32_t id, int accept) {
  dl_entry* d = dl_find(id, 0);
  if (!d || !d->before) return;
  if (accept) {
    AcquireSRWLockShared(&g.lock);
    wchar_t* dir = _wcsdup(g.downloads_dir);
    int ask = g.ask_download;
    ReleaseSRWLockShared(&g.lock);
    CreateDirectoryW(dir, NULL);
    wchar_t* name = sanitize_name(d->name);
    wchar_t* path = unique_path(dir, name);
    char* path8 = wide_to_utf8(path);
    cef_string_t p = {0};
    cs_set(&p, path8);
    d->before->cont(d->before, &p, ask);
    cs_clear(&p);
    free(path8);
    free(path);
    free(name);
    free(dir);
    CEF_RELEASE(d->before);
    d->before = NULL;
  } else {
    dl_remove(id); /* releasing the callback without continuing cancels the download */
  }
}

void download_control(uint32_t id, const char* action) {
  dl_entry* d = dl_find(id, 0);
  if (!d || !d->control) return;
  if (!strcmp(action, "pause")) d->control->pause(d->control);
  else if (!strcmp(action, "resume")) d->control->resume(d->control);
  else if (!strcmp(action, "cancel")) d->control->cancel(d->control);
}

static void CEF_CALLBACK on_download_updated(cef_download_handler_t* self, cef_browser_t* browser, cef_download_item_t* item,
                                             cef_download_item_callback_t* callback) {
  (void)self;
  uint32_t id = item->get_id(item);
  dl_entry* d = dl_find(id, 1);
  CEF_RELEASE(d->control);
  d->control = callback;
  int complete = item->is_complete(item), canceled = item->is_canceled(item), interrupted = item->is_interrupted(item);
  int paused = item->is_paused(item);
  DWORD now = GetTickCount();
  int final_state = complete || canceled || interrupted;
  if (final_state || paused || now - d->last_emit >= 250) {
    d->last_emit = now;
    char* path = csu_to_utf8_free(item->get_full_path(item));
    char* url = csu_to_utf8_free(item->get_original_url(item));
    const char* state = complete ? "complete" : canceled ? "canceled" : interrupted ? "interrupted" : paused ? "paused" : "progress";
    sbuf b;
    sb_init(&b);
    sb_printf(&b, "{\"type\":\"download\",\"downloadId\":%u,\"state\":\"%s\",\"received\":%lld,\"total\":%lld,\"speed\":%lld,\"percent\":%d,\"reason\":%d,\"path\":",
              id, state, (long long)item->get_received_bytes(item), (long long)item->get_total_bytes(item),
              (long long)item->get_current_speed(item), item->get_percent_complete(item), interrupted ? (int)item->get_interrupt_reason(item) : 0);
    sb_json_str(&b, path);
    sb_append(&b, ",\"url\":");
    sb_json_str(&b, str_starts_with(url, "data:") ? "data:" : url);
    sb_append(&b, "}");
    ui_emit(b.data);
    sb_free(&b);
    free(path);
    free(url);
  }
  if (complete || canceled) dl_remove(id);
  CEF_RELEASE(browser);
  CEF_RELEASE(item);
}

/* ============================================================ find handler */

typedef struct { cef_find_handler_t h; } find_t;

static void CEF_CALLBACK on_find_result(cef_find_handler_t* self, cef_browser_t* browser, int ident, int count, const cef_rect_t* rect,
                                        int active, int final_update) {
  (void)self; (void)ident; (void)rect;
  tab* t = tab_find_browser(browser);
  if (t) ui_emitf("{\"type\":\"findResult\",\"id\":%d,\"count\":%d,\"active\":%d,\"final\":%s}", t->id, count, active, final_update ? "true" : "false");
  CEF_RELEASE(browser);
}

/* ======================================================== keyboard handler */

typedef struct { cef_keyboard_handler_t h; } kbd_t;

static const char* shortcut_for(const cef_key_event_t* e) {
  int ctrl = (e->modifiers & EVENTFLAG_CONTROL_DOWN) != 0;
  int shift = (e->modifiers & EVENTFLAG_SHIFT_DOWN) != 0;
  int alt = (e->modifiers & EVENTFLAG_ALT_DOWN) != 0;
  int k = e->windows_key_code;
  if (ctrl && !alt) {
    if (shift && k == 'T') return "reopenTab";
    if (shift && k == 'N') return "newPrivateTab";
    if (shift && k == 'B') return "toggleBookmarksBar";
    if (shift && k == VK_DELETE) return "clearData";
    if (k == VK_TAB) return shift ? "prevTab" : "nextTab";
    if (shift) return NULL;
    switch (k) {
      case 'T': return "newTab";
      case 'W': case VK_F4: return "closeTab";
      case 'L': return "focusAddress";
      case 'K': case 'E': return "focusAddress";
      case 'F': return "find";
      case 'D': return "bookmark";
      case 'R': return "reload";
      case 'P': return "print";
      case 'J': return "downloads";
      case 'H': return "history";
      case 'S': return "savePdf";
      case VK_OEM_PLUS: case VK_ADD: return "zoomIn";
      case VK_OEM_MINUS: case VK_SUBTRACT: return "zoomOut";
      case '0': case VK_NUMPAD0: return "zoomReset";
    }
  }
  if (alt && !ctrl) {
    if (k == VK_LEFT) return "back";
    if (k == VK_RIGHT) return "forward";
    if (k == VK_HOME) return "home";
    if (k == 'D') return "focusAddress";
  }
  if (!ctrl && !alt) {
    if (k == VK_F11) return "fullscreen";
    if (k == VK_ESCAPE && g.window_fullscreen) return "exitFullscreen";
    if (k == VK_F5) return "reload";
    if (k == VK_F6) return "focusAddress";
    if (k == VK_BROWSER_BACK) return "back";
    if (k == VK_BROWSER_FORWARD) return "forward";
    if (k == VK_F3) return shift ? "findPrev" : "findNext";
  }
  return NULL;
}

static int CEF_CALLBACK on_pre_key_event(cef_keyboard_handler_t* self, cef_browser_t* browser, const cef_key_event_t* event,
                                         cef_event_handle_t os_event, int* is_shortcut) {
  (void)self; (void)os_event; (void)is_shortcut;
  int handled = 0;
  if (event->type == KEYEVENT_RAWKEYDOWN) {
    const char* name = shortcut_for(event);
    if (name) {
      ui_emitf("{\"type\":\"shortcut\",\"name\":\"%s\"}", name);
      handled = 1;
    }
  }
  CEF_RELEASE(browser);
  return handled;
}

/* ====================================================== permission handler */

typedef struct perm_pending {
  int id;
  uint64_t cef_prompt_id;
  uint32_t requested;
  cef_media_access_callback_t* media;
  cef_permission_prompt_callback_t* prompt;
  struct perm_pending* next;
} perm_pending;
static perm_pending* g_perms;
static int g_perm_next = 1;

static void emit_perm_prompt(int prompt_id, tab* t, const char* site, const char* perms_json) {
  sbuf b;
  sb_init(&b);
  sb_printf(&b, "{\"type\":\"permission\",\"promptId\":%d,\"id\":%d,\"site\":", prompt_id, t ? t->id : 0);
  sb_json_str(&b, site ? site : "");
  sb_printf(&b, ",\"permissions\":%s}", perms_json);
  ui_emit(b.data);
  sb_free(&b);
}

typedef struct { cef_permission_handler_t h; } perm_t;

static int CEF_CALLBACK on_media_access(cef_permission_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, const cef_string_t* origin,
                                        uint32_t requested, cef_media_access_callback_t* callback) {
  (void)self;
  tab* t = tab_find_browser(browser);
  char* o = cs_to_utf8(origin);
  char* site = site_of(o);
  int cam = requested & CEF_MEDIA_PERMISSION_DEVICE_VIDEO_CAPTURE;
  int mic = requested & CEF_MEDIA_PERMISSION_DEVICE_AUDIO_CAPTURE;
  int screen = requested & (CEF_MEDIA_PERMISSION_DESKTOP_AUDIO_CAPTURE | CEF_MEDIA_PERMISSION_DESKTOP_VIDEO_CAPTURE);
  int dc = cam ? site_decision(site, "camera") : 1;
  int dm = mic ? site_decision(site, "microphone") : 1;
  if (!t || (cam && dc < 0) || (mic && dm < 0)) {
    callback->cont(callback, CEF_MEDIA_PERMISSION_NONE);
    CEF_RELEASE(callback);
  } else if (!screen && dc > 0 && dm > 0) {
    callback->cont(callback, requested);
    CEF_RELEASE(callback);
  } else {
    perm_pending* p = (perm_pending*)calloc(1, sizeof *p);
    p->id = g_perm_next++;
    p->requested = requested;
    p->media = callback;
    p->next = g_perms;
    g_perms = p;
    char list[96] = "[";
    if (cam) strcat(list, "\"camera\",");
    if (mic) strcat(list, "\"microphone\",");
    if (screen) strcat(list, "\"screen\",");
    size_t n = strlen(list);
    if (list[n - 1] == ',') list[n - 1] = 0;
    strcat(list, "]");
    emit_perm_prompt(p->id, t, site, list);
  }
  free(site);
  free(o);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  return 1;
}

static int CEF_CALLBACK on_show_prompt(cef_permission_handler_t* self, cef_browser_t* browser, uint64_t prompt_id, const cef_string_t* origin,
                                       uint32_t requested, cef_permission_prompt_callback_t* callback) {
  (void)self;
  tab* t = tab_find_browser(browser);
  char* o = cs_to_utf8(origin);
  char* site = site_of(o);
  /* Web notifications are not supported by the Alloy runtime: always denied. */
  uint32_t supported = requested & ~(uint32_t)CEF_PERMISSION_TYPE_NOTIFICATIONS;
  int geo = requested & CEF_PERMISSION_TYPE_GEOLOCATION;
  int decision = geo ? site_decision(site, "location") : 0;
  if (!t || !supported || decision < 0) {
    callback->cont(callback, CEF_PERMISSION_RESULT_DENY);
    CEF_RELEASE(callback);
  } else if (geo && supported == (uint32_t)CEF_PERMISSION_TYPE_GEOLOCATION && decision > 0) {
    callback->cont(callback, CEF_PERMISSION_RESULT_ACCEPT);
    CEF_RELEASE(callback);
  } else {
    perm_pending* p = (perm_pending*)calloc(1, sizeof *p);
    p->id = g_perm_next++;
    p->cef_prompt_id = prompt_id;
    p->requested = requested;
    p->prompt = callback;
    p->next = g_perms;
    g_perms = p;
    char list[160] = "[";
    if (geo) strcat(list, "\"location\",");
    if (requested & CEF_PERMISSION_TYPE_CLIPBOARD) strcat(list, "\"clipboard\",");
    if (requested & (CEF_PERMISSION_TYPE_CAMERA_STREAM | CEF_PERMISSION_TYPE_CAMERA_PAN_TILT_ZOOM)) strcat(list, "\"camera\",");
    if (requested & CEF_PERMISSION_TYPE_MIC_STREAM) strcat(list, "\"microphone\",");
    if (requested & CEF_PERMISSION_TYPE_MULTIPLE_DOWNLOADS) strcat(list, "\"downloads\",");
    if (strlen(list) == 1) strcat(list, "\"other\",");
    size_t n = strlen(list);
    if (list[n - 1] == ',') list[n - 1] = 0;
    strcat(list, "]");
    emit_perm_prompt(p->id, t, site, list);
  }
  free(site);
  free(o);
  CEF_RELEASE(browser);
  return 1;
}

static void CEF_CALLBACK on_dismiss_prompt(cef_permission_handler_t* self, cef_browser_t* browser, uint64_t prompt_id, cef_permission_request_result_t r) {
  (void)self; (void)r;
  for (perm_pending** pp = &g_perms; *pp; pp = &(*pp)->next) {
    perm_pending* p = *pp;
    if (p->prompt && p->cef_prompt_id == prompt_id) {
      *pp = p->next;
      ui_emitf("{\"type\":\"permissionDismissed\",\"promptId\":%d}", p->id);
      CEF_RELEASE(p->prompt);
      free(p);
      break;
    }
  }
  CEF_RELEASE(browser);
}

void permission_answer(int prompt_id, int allow) {
  for (perm_pending** pp = &g_perms; *pp; pp = &(*pp)->next) {
    perm_pending* p = *pp;
    if (p->id != prompt_id) continue;
    *pp = p->next;
    if (p->media) {
      p->media->cont(p->media, allow ? p->requested : CEF_MEDIA_PERMISSION_NONE);
      CEF_RELEASE(p->media);
    }
    if (p->prompt) {
      p->prompt->cont(p->prompt, allow ? CEF_PERMISSION_RESULT_ACCEPT : CEF_PERMISSION_RESULT_DENY);
      CEF_RELEASE(p->prompt);
    }
    free(p);
    return;
  }
}

/* ===================================================== context menu (pages) */

typedef struct { cef_context_menu_handler_t h; } menu_t;
enum { CMD_OPEN_TAB = MENU_ID_USER_FIRST + 1, CMD_OPEN_PRIVATE, CMD_COPY_LINK, CMD_OPEN_IMAGE, CMD_SAVE_IMAGE, CMD_SAVE_LINK, CMD_SEARCH_SELECTION };

static void add_item(cef_menu_model_t* m, size_t index, int id, const char* en, const char* tr) {
  cef_string_t s = {0};
  cs_set(&s, is_turkish() ? tr : en);
  m->insert_item_at(m, index, id, &s);
  cs_clear(&s);
}

static void CEF_CALLBACK on_before_context_menu(cef_context_menu_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                                cef_context_menu_params_t* params, cef_menu_model_t* model) {
  (void)self;
  int flags = params->get_type_flags(params);
  size_t i = 0;
  tab* t = tab_find_browser(browser);
  if (flags & CM_TYPEFLAG_LINK) {
    add_item(model, i++, CMD_OPEN_TAB, "Open link in new tab", "Bağlantıyı yeni sekmede aç");
    if (t && !t->is_private) add_item(model, i++, CMD_OPEN_PRIVATE, "Open link in private tab", "Bağlantıyı gizli sekmede aç");
    add_item(model, i++, CMD_COPY_LINK, "Copy link address", "Bağlantı adresini kopyala");
    add_item(model, i++, CMD_SAVE_LINK, "Save link as...", "Bağlantıyı farklı kaydet...");
  }
  if ((flags & CM_TYPEFLAG_MEDIA) && params->get_media_type(params) == CM_MEDIATYPE_IMAGE) {
    add_item(model, i++, CMD_OPEN_IMAGE, "Open image in new tab", "Resmi yeni sekmede aç");
    add_item(model, i++, CMD_SAVE_IMAGE, "Save image as...", "Resmi farklı kaydet...");
  }
  if ((flags & CM_TYPEFLAG_SELECTION) && !(flags & CM_TYPEFLAG_EDITABLE)) {
    add_item(model, i++, CMD_SEARCH_SELECTION, "Search the web for selection", "Seçili metni web'de ara");
  }
  if (i) model->insert_separator_at(model, i);
  model->remove(model, MENU_ID_VIEW_SOURCE);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(params);
  CEF_RELEASE(model);
}

static int CEF_CALLBACK on_context_command(cef_context_menu_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                           cef_context_menu_params_t* params, int command, cef_event_flags_t flags) {
  (void)self; (void)flags;
  int handled = 1;
  tab* t = tab_find_browser(browser);
  char* link = csu_to_utf8_free(params->get_link_url(params));
  char* src = csu_to_utf8_free(params->get_source_url(params));
  const char* target = (command == CMD_OPEN_IMAGE || command == CMD_SAVE_IMAGE) ? src : link;
  switch (command) {
    case CMD_OPEN_TAB:
    case CMD_OPEN_PRIVATE:
    case CMD_OPEN_IMAGE: {
      if (!str_starts_with(target, "http")) break;
      sbuf b;
      sb_init(&b);
      sb_printf(&b, "{\"type\":\"openTab\",\"openerId\":%d,\"background\":%s,\"private\":%s,\"url\":", t ? t->id : 0,
                command == CMD_OPEN_IMAGE ? "false" : "true", (command == CMD_OPEN_PRIVATE || (t && t->is_private)) ? "true" : "false");
      sb_json_str(&b, target);
      sb_append(&b, "}");
      ui_emit(b.data);
      sb_free(&b);
      break;
    }
    case CMD_COPY_LINK:
      copy_to_clipboard(link);
      break;
    case CMD_SEARCH_SELECTION: {
      char* sel = csu_to_utf8_free(params->get_selection_text(params));
      sbuf b;
      sb_init(&b);
      sb_printf(&b, "{\"type\":\"searchText\",\"openerId\":%d,\"private\":%s,\"text\":", t ? t->id : 0, (t && t->is_private) ? "true" : "false");
      sel[sel[0] ? (strlen(sel) > 500 ? 500 : strlen(sel)) : 0] = 0;
      sb_json_str(&b, sel);
      sb_append(&b, "}");
      ui_emit(b.data);
      sb_free(&b);
      free(sel);
      break;
    }
    case CMD_SAVE_IMAGE:
    case CMD_SAVE_LINK: {
      cef_browser_host_t* host = browser->get_host(browser);
      cef_string_t s = {0};
      cs_set(&s, target);
      host->start_download(host, &s);
      cs_clear(&s);
      CEF_RELEASE(host);
      break;
    }
    default:
      handled = 0;
  }
  free(link);
  free(src);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(params);
  return handled;
}

/* ========================================================== content client */

typedef struct {
  cef_client_t c;
  disp_t* disp;
  load_t* load;
  req_t* req;
  lsh_t* lsh;
  dlh_t* dl;
  find_t* find;
  kbd_t* kbd;
  perm_t* perm;
  menu_t* menu;
} content_client_t;
static content_client_t* g_content;

#define CC_GETTER(name, field, type)                       \
  static type* CEF_CALLBACK name(cef_client_t* self) {    \
    content_client_t* c = (content_client_t*)self;        \
    fr_add_ref(c->field);                                 \
    return &c->field->h;                                  \
  }
CC_GETTER(cc_disp, disp, cef_display_handler_t)
CC_GETTER(cc_load, load, cef_load_handler_t)
CC_GETTER(cc_req, req, cef_request_handler_t)
CC_GETTER(cc_lsh, lsh, cef_life_span_handler_t)
CC_GETTER(cc_dl, dl, cef_download_handler_t)
CC_GETTER(cc_find, find, cef_find_handler_t)
CC_GETTER(cc_kbd, kbd, cef_keyboard_handler_t)
CC_GETTER(cc_perm, perm, cef_permission_handler_t)
CC_GETTER(cc_menu, menu, cef_context_menu_handler_t)

cef_client_t* content_client(void) {
  if (g_content) return &g_content->c;
  content_client_t* c = FR_NEW(content_client_t, cef_client_t, NULL);
  c->disp = FR_NEW(disp_t, cef_display_handler_t, NULL);
  c->disp->h.on_address_change = on_address_change;
  c->disp->h.on_title_change = on_title_change;
  c->disp->h.on_favicon_urlchange = on_favicon;
  c->disp->h.on_fullscreen_mode_change = on_fullscreen;
  c->disp->h.on_loading_progress_change = on_progress;
  c->load = FR_NEW(load_t, cef_load_handler_t, NULL);
  c->load->h.on_loading_state_change = on_loading_state;
  c->load->h.on_load_start = on_load_start;
  c->load->h.on_load_error = on_load_error;
  c->req = FR_NEW(req_t, cef_request_handler_t, NULL);
  c->req->h.on_before_browse = on_before_browse;
  c->req->h.get_resource_request_handler = get_rrh;
  c->req->h.on_certificate_error = on_certificate_error;
  c->req->h.on_render_process_terminated = on_render_terminated;
  c->req->h.get_auth_credentials = get_auth_credentials;
  c->lsh = FR_NEW(lsh_t, cef_life_span_handler_t, NULL);
  c->lsh->h.on_before_popup = on_before_popup;
  c->lsh->h.on_before_popup_aborted = on_before_popup_aborted;
  c->lsh->h.on_after_created = on_after_created;
  c->lsh->h.on_before_close = on_before_close;
  c->dl = FR_NEW(dlh_t, cef_download_handler_t, NULL);
  c->dl->h.can_download = can_download;
  c->dl->h.on_before_download = on_before_download;
  c->dl->h.on_download_updated = on_download_updated;
  c->find = FR_NEW(find_t, cef_find_handler_t, NULL);
  c->find->h.on_find_result = on_find_result;
  c->kbd = FR_NEW(kbd_t, cef_keyboard_handler_t, NULL);
  c->kbd->h.on_pre_key_event = on_pre_key_event;
  c->perm = FR_NEW(perm_t, cef_permission_handler_t, NULL);
  c->perm->h.on_request_media_access_permission = on_media_access;
  c->perm->h.on_show_permission_prompt = on_show_prompt;
  c->perm->h.on_dismiss_permission_prompt = on_dismiss_prompt;
  c->menu = FR_NEW(menu_t, cef_context_menu_handler_t, NULL);
  c->menu->h.on_before_context_menu = on_before_context_menu;
  c->menu->h.on_context_menu_command = on_context_command;
  c->c.get_display_handler = cc_disp;
  c->c.get_load_handler = cc_load;
  c->c.get_request_handler = cc_req;
  c->c.get_life_span_handler = cc_lsh;
  c->c.get_download_handler = cc_dl;
  c->c.get_find_handler = cc_find;
  c->c.get_keyboard_handler = cc_kbd;
  c->c.get_permission_handler = cc_perm;
  c->c.get_context_menu_handler = cc_menu;
  g_rrh = FR_NEW(rrh_t, cef_resource_request_handler_t, NULL);
  g_rrh->h.on_before_resource_load = on_before_resource_load;
  g_rrh->h.on_protocol_execution = on_protocol_execution;
  g_content = c; /* kept for the process lifetime */
  return &c->c;
}

/* ================================================================ features */

static void set_int_pref(cef_request_context_t* ctx, const char* name, int value) {
  if (!ctx) return;
  cef_value_t* v = cef_value_create();
  v->set_int(v, value);
  cef_string_t n = {0}, err = {0};
  cs_set(&n, name);
  if (ctx->base.can_set_preference(&ctx->base, &n)) {
    v->base.add_ref(&v->base);
    if (!ctx->base.set_preference(&ctx->base, &n, v, &err)) flog("set_preference failed");
  }
  cs_clear(&n);
  cs_clear(&err);
  CEF_RELEASE(v);
}

void browser_apply_cookie_policy(int block_third_party) {
  /* Chromium cookie controls: 0 = allow all, 1 = block third-party cookies. */
  cef_request_context_t* global = cef_request_context_get_global_context();
  set_int_pref(global, "profile.cookie_controls_mode", block_third_party ? 1 : 0);
  CEF_RELEASE(global);
  if (g.private_context) set_int_pref(g.private_context, "profile.cookie_controls_mode", 1);
}

/* Pop-ups without a user gesture are blocked by Chromium itself (no callback reaches us), so the
 * global switch and per-site exceptions are applied as Chromium content settings. */
static char** g_popup_sites;
static int g_popup_site_count;

static void set_popup_setting(cef_request_context_t* ctx, const char* site, cef_content_setting_values_t v) {
  cef_string_t u = {0};
  if (site) {
    char buf[300];
    snprintf(buf, sizeof buf, "https://%s/", site);
    cs_set(&u, buf);
    ctx->set_content_setting(ctx, &u, NULL, CEF_CONTENT_SETTING_TYPE_POPUPS, v);
    snprintf(buf, sizeof buf, "http://%s/", site);
    cs_set(&u, buf);
    ctx->set_content_setting(ctx, &u, NULL, CEF_CONTENT_SETTING_TYPE_POPUPS, v);
    cs_clear(&u);
  } else {
    ctx->set_content_setting(ctx, NULL, NULL, CEF_CONTENT_SETTING_TYPE_POPUPS, v);
  }
}

void browser_apply_popup_policy(int block, char** allow, int allow_count) {
  cef_request_context_t* global = cef_request_context_get_global_context();
  set_popup_setting(global, NULL, block ? CEF_CONTENT_SETTING_VALUE_BLOCK : CEF_CONTENT_SETTING_VALUE_ALLOW);
  for (int i = 0; i < g_popup_site_count; i++) set_popup_setting(global, g_popup_sites[i], CEF_CONTENT_SETTING_VALUE_DEFAULT);
  for (int i = 0; i < g_popup_site_count; i++) free(g_popup_sites[i]);
  free(g_popup_sites);
  g_popup_sites = (char**)calloc((size_t)(allow_count > 0 ? allow_count : 1), sizeof(char*));
  g_popup_site_count = 0;
  for (int i = 0; i < allow_count; i++) {
    if (!allow[i] || strchr(allow[i], '/') || strchr(allow[i], ':')) continue;
    g_popup_sites[g_popup_site_count++] = str_dup(allow[i]);
    set_popup_setting(global, allow[i], CEF_CONTENT_SETTING_VALUE_ALLOW);
  }
  CEF_RELEASE(global);
}

void browser_clear_data(int cookies, int cache, cef_list_value_t* origins) {
  cef_request_context_t* global = cef_request_context_get_global_context();
  if (cookies) {
    cef_cookie_manager_t* cm = global->get_cookie_manager(global, NULL);
    if (cm) {
      cm->delete_cookies(cm, NULL, NULL, NULL);
      cm->flush_store(cm, NULL);
      CEF_RELEASE(cm);
    }
  }
  if (cache) global->clear_http_cache(global, NULL);
  if (cookies && origins && g.ui_browser) {
    /* Site storage (localStorage, IndexedDB, service workers...) for each known origin. */
    cef_browser_host_t* host = g.ui_browser->get_host(g.ui_browser);
    size_t n = origins->get_size(origins);
    for (size_t i = 0; i < n && i < 5000; i++) {
      char* origin = csu_to_utf8_free(origins->get_string(origins, i));
      if (str_starts_with(origin, "http")) {
        cef_dictionary_value_t* params = cef_dictionary_value_create();
        cef_string_t k = {0}, v = {0}, method = {0};
        cs_set(&k, "origin");
        cs_set(&v, origin);
        params->set_string(params, &k, &v);
        cs_set(&k, "storageTypes");
        cs_set(&v, "all");
        params->set_string(params, &k, &v);
        cs_set(&method, "Storage.clearDataForOrigin");
        host->execute_dev_tools_method(host, 0, &method, params);
        cs_clear(&k);
        cs_clear(&v);
        cs_clear(&method);
      }
      free(origin);
    }
    CEF_RELEASE(host);
  }
  CEF_RELEASE(global);
}

/* ------------------------------------------------- screenshot via DevTools */

typedef struct {
  cef_dev_tools_message_observer_t o;
  int message_id;
  int tab_id;
  int snapshot; /* 1 = emit JPEG data URL for the UI backdrop, 0 = save PNG screenshot */
  cef_registration_t* registration;
} shot_t;

static void shot_done(void* arg) {
  shot_t* s = (shot_t*)arg;
  CEF_RELEASE(s->registration); /* unregisters the observer */
  s->registration = NULL;
  fr_release(s);
}

static void CEF_CALLBACK shot_result(cef_dev_tools_message_observer_t* self, cef_browser_t* browser, int message_id, int success,
                                     const void* result, size_t size) {
  shot_t* s = (shot_t*)self;
  CEF_RELEASE(browser);
  if (message_id != s->message_id) return;
  if (s->snapshot) {
    /* {"data":"<base64>"} -> data URL event; the base64 alphabet needs no JSON escaping */
    const char* r = (const char*)result;
    const char* key = success ? strstr(r, "\"data\":\"") : NULL;
    sbuf b;
    sb_init(&b);
    sb_printf(&b, "{\"type\":\"snapshot\",\"id\":%d,\"data\":\"", s->tab_id);
    if (key) {
      const char* start = key + 8;
      const char* end = memchr(start, '"', size - (size_t)(start - r));
      if (end) { sb_append(&b, "data:image/jpeg;base64,"); sb_appendn(&b, start, (size_t)(end - start)); }
    }
    sb_append(&b, "\"}");
    ui_emit(b.data);
    sb_free(&b);
    post_ui_task(shot_done, s);
    return;
  }
  int ok = 0;
  wchar_t* file = NULL;
  if (success) {
    cef_value_t* v = cef_parse_json_buffer(result, size, JSON_PARSER_RFC);
    cef_dictionary_value_t* d = v ? v->get_dictionary(v) : NULL;
    char* data = d ? dict_str(d, "data") : NULL;
    if (data) {
      cef_binary_value_t* bin = NULL;
      cef_string_t b64 = {0};
      cs_set(&b64, data);
      bin = cef_base64_decode(&b64);
      cs_clear(&b64);
      if (bin) {
        size_t n = bin->get_size(bin);
        char* bytes = (char*)malloc(n);
        bin->get_data(bin, bytes, n, 0);
        PWSTR pics = NULL;
        if (SUCCEEDED(SHGetKnownFolderPath(&FOLDERID_Pictures, 0, NULL, &pics))) {
          wchar_t* dir = path_join(pics, L"Lumo");
          CreateDirectoryW(dir, NULL);
          SYSTEMTIME t;
          GetLocalTime(&t);
          wchar_t name[64];
          swprintf(name, 64, L"Freedom_%04d%02d%02d_%02d%02d%02d.png", t.wYear, t.wMonth, t.wDay, t.wHour, t.wMinute, t.wSecond);
          file = unique_path(dir, name);
          ok = file_write_atomic(file, bytes, n);
          free(dir);
          CoTaskMemFree(pics);
        }
        free(bytes);
        CEF_RELEASE(bin);
      }
      free(data);
    }
    CEF_RELEASE(d);
    CEF_RELEASE(v);
  }
  char* path8 = file ? wide_to_utf8(file) : str_dup("");
  sbuf b;
  sb_init(&b);
  sb_printf(&b, "{\"type\":\"screenshot\",\"ok\":%s,\"path\":", ok ? "true" : "false");
  sb_json_str(&b, path8);
  sb_append(&b, "}");
  ui_emit(b.data);
  sb_free(&b);
  free(path8);
  free(file);
  post_ui_task(shot_done, s);
}

static void capture(int tab_id, int snapshot) {
  tab* t = tab_find(tab_id);
  if (!t) {
    if (snapshot) ui_emitf("{\"type\":\"snapshot\",\"id\":%d,\"data\":\"\"}", tab_id);
    return;
  }
  static int seq;
  cef_browser_host_t* host = t->browser->get_host(t->browser);
  shot_t* s = FR_NEW(shot_t, cef_dev_tools_message_observer_t, NULL);
  s->o.on_dev_tools_method_result = shot_result;
  s->tab_id = tab_id;
  s->snapshot = snapshot;
  s->message_id = 0x5C0000 + (++seq & 0xFFFF);
  fr_add_ref(s);
  s->registration = host->add_dev_tools_message_observer(host, &s->o);
  cef_dictionary_value_t* params = cef_dictionary_value_create();
  cef_string_t k = {0}, v = {0}, method = {0};
  cs_set(&k, "format");
  cs_set(&v, snapshot ? "jpeg" : "png");
  params->set_string(params, &k, &v);
  if (snapshot) {
    cs_set(&k, "quality");
    params->set_int(params, &k, 60);
  }
  cs_set(&method, "Page.captureScreenshot");
  host->execute_dev_tools_method(host, s->message_id, &method, params);
  cs_clear(&k);
  cs_clear(&v);
  cs_clear(&method);
  CEF_RELEASE(host);
}

void browser_screenshot(int tab_id) { capture(tab_id, 0); }
void browser_snapshot(int tab_id) { capture(tab_id, 1); }

/* ------------------------------------------------------------------- PDF */

typedef struct { cef_pdf_print_callback_t c; } pdf_cb_t;

static void CEF_CALLBACK on_pdf_done(cef_pdf_print_callback_t* self, const cef_string_t* path, int ok) {
  (void)self;
  char* p = cs_to_utf8(path);
  sbuf b;
  sb_init(&b);
  sb_printf(&b, "{\"type\":\"pdf\",\"ok\":%s,\"path\":", ok ? "true" : "false");
  sb_json_str(&b, p);
  sb_append(&b, "}");
  ui_emit(b.data);
  sb_free(&b);
  free(p);
}

void browser_print_pdf(int tab_id) {
  tab* t = tab_find(tab_id);
  if (!t) return;
  wchar_t file[MAX_PATH] = L"page.pdf";
  OPENFILENAMEW ofn = {0};
  ofn.lStructSize = sizeof ofn;
  ofn.hwndOwner = g.main_hwnd;
  ofn.lpstrFilter = L"PDF (*.pdf)\0*.pdf\0";
  ofn.lpstrFile = file;
  ofn.nMaxFile = MAX_PATH;
  ofn.lpstrDefExt = L"pdf";
  ofn.lpstrInitialDir = g.downloads_dir;
  ofn.Flags = OFN_OVERWRITEPROMPT | OFN_PATHMUSTEXIST | OFN_NOCHANGEDIR;
  if (!GetSaveFileNameW(&ofn)) return;
  char* path8 = wide_to_utf8(file);
  cef_string_t p = {0};
  cs_set(&p, path8);
  cef_pdf_print_settings_t s = {0};
  s.size = sizeof s;
  s.print_background = 1;
  pdf_cb_t* cb = FR_NEW(pdf_cb_t, cef_pdf_print_callback_t, NULL);
  cb->c.on_pdf_print_finished = on_pdf_done;
  cef_browser_host_t* host = t->browser->get_host(t->browser);
  host->print_to_pdf(host, &p, &s, &cb->c); /* reference transferred */
  CEF_RELEASE(host);
  cs_clear(&p);
  free(path8);
}
