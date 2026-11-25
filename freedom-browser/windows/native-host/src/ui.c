#include "freedom.h"
#include <shlobj.h>
#include <shellapi.h>
#include <stdarg.h>
#include "include/capi/cef_drag_handler_capi.h"

/* --------------------------------------------------------------- schemes */

void ui_register_schemes(cef_scheme_registrar_t* registrar) {
  cef_string_t name = {0};
  cs_set(&name, "freedom");
  /* DISPLAY_ISOLATED: web pages cannot navigate to or embed freedom:// URLs. */
  registrar->add_custom_scheme(registrar, &name,
                               CEF_SCHEME_OPTION_STANDARD | CEF_SCHEME_OPTION_SECURE | CEF_SCHEME_OPTION_CORS_ENABLED |
                                   CEF_SCHEME_OPTION_FETCH_ENABLED | CEF_SCHEME_OPTION_DISPLAY_ISOLATED);
  cs_clear(&name);
}

/* ------------------------------------------------------------- UI client */

typedef struct { cef_life_span_handler_t h; } ui_lsh_t;
typedef struct { cef_request_handler_t h; } ui_req_t;
typedef struct { cef_context_menu_handler_t h; } ui_menu_t;
typedef struct { cef_drag_handler_t h; } ui_drag_t;
typedef struct {
  cef_client_t c;
  ui_lsh_t* lsh;
  ui_req_t* req;
  ui_menu_t* menu;
  ui_drag_t* drag;
} ui_client_t;

static ui_client_t* g_ui_client;

static int CEF_CALLBACK ui_on_before_popup(cef_life_span_handler_t* self, cef_browser_t* browser, cef_frame_t* frame, int popup_id,
                                          const cef_string_t* target_url, const cef_string_t* target_frame_name,
                                          cef_window_open_disposition_t disp, int user_gesture, const cef_popup_features_t* f,
                                          cef_window_info_t* wi, cef_client_t** client, cef_browser_settings_t* settings,
                                          cef_dictionary_value_t** extra, int* no_js) {
  (void)self; (void)popup_id; (void)target_url; (void)target_frame_name; (void)disp; (void)user_gesture; (void)f; (void)wi;
  (void)client; (void)settings; (void)extra; (void)no_js;
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  return 1; /* the UI never opens windows; it uses tab.create */
}

static void CEF_CALLBACK ui_on_before_close(cef_life_span_handler_t* self, cef_browser_t* browser) {
  (void)self;
  if (g.ui_browser && browser->is_same(browser, g.ui_browser)) {
    CEF_RELEASE(g.ui_browser);
    g.ui_browser = NULL;
    g.browser_count--;
  }
  CEF_RELEASE(browser);
  maybe_quit();
}

static int CEF_CALLBACK ui_on_before_browse(cef_request_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                            cef_request_t* request, int user_gesture, int is_redirect) {
  (void)self; (void)user_gesture; (void)is_redirect;
  char* url = csu_to_utf8_free(request->get_url(request));
  int is_main = frame->is_main(frame);
  /* The UI browser may only show Lumo's own UI (blocks drag & drop navigation, link clicks...). */
  int cancel = is_main && !str_starts_with(url, "freedom://ui/");
  free(url);
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(request);
  return cancel;
}

static void CEF_CALLBACK ui_on_before_context_menu(cef_context_menu_handler_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                                   cef_context_menu_params_t* params, cef_menu_model_t* model) {
  (void)self;
  /* Keep cut/copy/paste in text fields; hide developer/navigation items. */
  if (!params->is_editable(params)) model->clear(model);
  else {
    model->remove(model, MENU_ID_BACK);
    model->remove(model, MENU_ID_FORWARD);
    model->remove(model, MENU_ID_RELOAD);
    model->remove(model, MENU_ID_VIEW_SOURCE);
    model->remove(model, MENU_ID_PRINT);
  }
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(params);
  CEF_RELEASE(model);
}

static int CEF_CALLBACK ui_on_drag_enter(cef_drag_handler_t* self, cef_browser_t* browser, cef_drag_data_t* data, cef_drag_operations_mask_t mask) {
  (void)self; (void)mask;
  int is_link = data->is_link(data);
  CEF_RELEASE(browser);
  CEF_RELEASE(data);
  return !is_link; /* allow dragging links onto the UI (address bar / tab strip), refuse files */
}

#define UI_GETTER(name, field, type)                                    \
  static type* CEF_CALLBACK name(cef_client_t* self) {                 \
    ui_client_t* c = (ui_client_t*)self;                               \
    fr_add_ref(c->field);                                              \
    return &c->field->h;                                               \
  }
UI_GETTER(ui_get_lsh, lsh, cef_life_span_handler_t)
UI_GETTER(ui_get_req, req, cef_request_handler_t)
UI_GETTER(ui_get_menu, menu, cef_context_menu_handler_t)
UI_GETTER(ui_get_drag, drag, cef_drag_handler_t)

static cef_download_handler_t* CEF_CALLBACK ui_get_dl(cef_client_t* self) {
  ui_client_t* c = (ui_client_t*)self;
  cef_client_t* cc = content_client();
  cef_download_handler_t* h = cc->get_download_handler(cc); /* returns an added reference */
  (void)c;
  return h;
}

cef_client_t* ui_client(void) {
  if (g_ui_client) return &g_ui_client->c;
  ui_client_t* c = FR_NEW(ui_client_t, cef_client_t, NULL);
  c->lsh = FR_NEW(ui_lsh_t, cef_life_span_handler_t, NULL);
  c->lsh->h.on_before_popup = ui_on_before_popup;
  c->lsh->h.on_before_close = ui_on_before_close;
  c->req = FR_NEW(ui_req_t, cef_request_handler_t, NULL);
  c->req->h.on_before_browse = ui_on_before_browse;
  c->menu = FR_NEW(ui_menu_t, cef_context_menu_handler_t, NULL);
  c->menu->h.on_before_context_menu = ui_on_before_context_menu;
  c->drag = FR_NEW(ui_drag_t, cef_drag_handler_t, NULL);
  c->drag->h.on_drag_enter = ui_on_drag_enter;
  c->c.get_life_span_handler = ui_get_lsh;
  c->c.get_request_handler = ui_get_req;
  c->c.get_context_menu_handler = ui_get_menu;
  c->c.get_drag_handler = ui_get_drag;
  c->c.get_download_handler = ui_get_dl;
  g_ui_client = c; /* kept for the process lifetime */
  return &c->c;
}

/* ---------------------------------------------------------------- events */

void ui_emit(const char* json) {
  if (!g.ui_browser) return;
  if (g.debug) {
    const char* t = strstr(json, "\"type\":\"");
    flog("event %.24s", t ? t + 8 : "?");
  }
  cef_frame_t* frame = g.ui_browser->get_main_frame(g.ui_browser);
  if (!frame) return;
  sbuf b;
  sb_init(&b);
  sb_append(&b, "window.freedom&&window.freedom.onEvent(");
  sb_append(&b, json);
  sb_append(&b, ");");
  cef_string_t code = {0}, src = {0};
  cs_set(&code, b.data);
  cs_set(&src, "freedom://ui/native-event");
  frame->execute_java_script(frame, &code, &src, 0);
  cs_clear(&code);
  cs_clear(&src);
  sb_free(&b);
  CEF_RELEASE(frame);
}

void ui_emitf(const char* fmt, ...) {
  char buf[2048];
  va_list ap;
  va_start(ap, fmt);
  vsnprintf(buf, sizeof buf, fmt, ap);
  va_end(ap);
  ui_emit(buf);
}

/* ------------------------------------------------------------- blocklist */

void blocklist_reload(void) {
  blocklist* bl = blocklist_create();
  wchar_t* dir = path_join(g.ui_dir, L"blocklist");
  wchar_t* bundled = path_join(dir, L"freedom-default.txt");
  wchar_t* custom = path_join(g.data_dir, L"blocklist-custom.txt");
  size_t len = 0;
  char* text = file_read_all(bundled, &len);
  if (text) { blocklist_add_text(bl, text, len); free(text); }
  text = file_read_all(custom, &len);
  if (text) { blocklist_add_text(bl, text, len); free(text); }
  free(dir);
  free(bundled);
  free(custom);
  AcquireSRWLockExclusive(&g.lock);
  blocklist* old = g.blocklist;
  g.blocklist = bl;
  ReleaseSRWLockExclusive(&g.lock);
  blocklist_free(old);
}

/* ------------------------------------------------------------------- IPC */

static int valid_storage_key(const char* k) {
  size_t n = k ? strlen(k) : 0;
  if (n == 0 || n > 64) return 0;
  for (size_t i = 0; i < n; i++) {
    char c = k[i];
    if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.')) return 0;
  }
  return k[0] != '.';
}

static wchar_t* storage_path(const char* key, const wchar_t* ext) {
  wchar_t* wkey = utf8_to_wide(key);
  size_t n = wcslen(wkey) + wcslen(ext) + 1;
  wchar_t* name = (wchar_t*)malloc(n * sizeof(wchar_t));
  wcscpy(name, wkey);
  wcscat(name, ext);
  wchar_t* p = path_join(g.data_dir, name);
  free(wkey);
  free(name);
  return p;
}

static char** list_to_strings(cef_list_value_t* list, int* count) {
  *count = 0;
  if (!list) return NULL;
  int n = (int)list->get_size(list);
  char** arr = (char**)calloc((size_t)(n > 0 ? n : 1), sizeof(char*));
  for (int i = 0; i < n; i++) {
    if (list->get_type(list, (size_t)i) != VTYPE_STRING) continue;
    arr[(*count)++] = csu_to_utf8_free(list->get_string(list, (size_t)i));
  }
  return arr;
}

static void free_strings(char** arr, int count) {
  for (int i = 0; i < count; i++) free(arr[i]);
  free(arr);
}

static void apply_config(cef_dictionary_value_t* d) {
  int exc_n = 0, pop_n = 0;
  cef_list_value_t* exc = dict_list(d, "adblockExceptions");
  cef_list_value_t* pop = dict_list(d, "popupAllow");
  char** exc_arr = list_to_strings(exc, &exc_n);
  char** pop_arr = list_to_strings(pop, &pop_n);
  CEF_RELEASE(exc);
  CEF_RELEASE(pop);
  cef_dictionary_value_t* perms = dict_dict(d, "permissions");
  char* dl = dict_str(d, "downloadDir");

  AcquireSRWLockExclusive(&g.lock);
  g.adblock_enabled = dict_bool(d, "adblock", 1);
  g.block_popups = dict_bool(d, "blockPopups", 1);
  g.ask_download = dict_bool(d, "askDownload", 0);
  free_strings(g.adblock_exceptions, g.adblock_exception_count);
  g.adblock_exceptions = exc_arr;
  g.adblock_exception_count = exc_n;
  free_strings(g.popup_allow, g.popup_allow_count);
  g.popup_allow = pop_arr;
  g.popup_allow_count = pop_n;
  CEF_RELEASE(g.site_permissions);
  g.site_permissions = perms;
  if (dl && *dl) { free(g.downloads_dir); g.downloads_dir = utf8_to_wide(dl); }
  ReleaseSRWLockExclusive(&g.lock);
  free(dl);
  g.hide_ddg_header = dict_bool(d, "hideDdgHeader", 1);
  char* script = dict_str(d, "ddgScript");
  if (script) { free(g.ddg_script); g.ddg_script = script; }
  browser_apply_cookie_policy(dict_bool(d, "blockThirdPartyCookies", 1));
  AcquireSRWLockShared(&g.lock);
  browser_apply_popup_policy(g.block_popups, g.popup_allow, g.popup_allow_count);
  ReleaseSRWLockShared(&g.lock);
}

static char* pick_folder(void) {
  BROWSEINFOW bi = {0};
  bi.hwndOwner = g.main_hwnd;
  bi.lpszTitle = L"Choose download folder";
  bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
  PIDLIST_ABSOLUTE pidl = SHBrowseForFolderW(&bi);
  if (!pidl) return NULL;
  wchar_t path[MAX_PATH];
  char* r = NULL;
  if (SHGetPathFromIDListW(pidl, path)) r = wide_to_utf8(path);
  CoTaskMemFree(pidl);
  return r;
}

static void with_browser(int tab_id, void (*fn)(cef_browser_t*, cef_browser_host_t*, cef_dictionary_value_t*), cef_dictionary_value_t* d) {
  tab* t = tab_find(tab_id);
  if (!t || !t->browser) return;
  cef_browser_host_t* host = t->browser->get_host(t->browser);
  fn(t->browser, host, d);
  CEF_RELEASE(host);
}

static void op_back(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)h; (void)d; b->go_back(b); }
static void op_forward(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)h; (void)d; b->go_forward(b); }
static void op_reload(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)h; (void)d; b->reload(b); }
static void op_stop(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)h; (void)d; b->stop_load(b); }
static void op_print(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)b; (void)d; h->print(h); }
static void op_zoom(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)b; h->set_zoom_level(h, dict_double(d, "level", 0)); }
static void op_find_stop(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)b; (void)d; h->stop_finding(h, 1); }
static void op_focus(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) { (void)b; (void)d; h->set_focus(h, 1); }
static void op_find(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) {
  (void)b;
  char* text = dict_str(d, "text");
  cef_string_t s = {0};
  cs_set(&s, text);
  if (text && *text) h->find(h, &s, dict_bool(d, "forward", 1), 0, dict_bool(d, "next", 0));
  else h->stop_finding(h, 1);
  cs_clear(&s);
  free(text);
}
static void op_navigate(cef_browser_t* b, cef_browser_host_t* h, cef_dictionary_value_t* d) {
  (void)h;
  char* url = dict_str(d, "url");
  /* Only web URLs reach content tabs (the shared resolver already guarantees this). */
  if (url && (str_starts_with(url, "https://") || str_starts_with(url, "http://") || strcmp(url, "about:blank") == 0)) {
    cef_frame_t* f = b->get_main_frame(b);
    cef_string_t s = {0};
    cs_set(&s, url);
    f->load_url(f, &s);
    cs_clear(&s);
    CEF_RELEASE(f);
  }
  free(url);
}

/* Executes one IPC command on the UI thread and returns a JSON response (malloc'd). */
static char* run_command(const char* body) {
  cef_dictionary_value_t* d = json_parse_object(body);
  sbuf out;
  sb_init(&out);
  if (!d) { sb_append(&out, "{\"ok\":false,\"error\":\"bad json\"}"); return out.data; }
  char* cmd = dict_str(d, "cmd");
  int id = dict_int(d, "id", 0);
  if (!cmd) cmd = str_dup("");
  if (g.debug) flog("ipc %s full=%d", cmd, dict_bool(d, "full", -1));

  if (!strcmp(cmd, "init")) {
    char* profile = wide_to_utf8(g.profile_dir);
    char* dl = wide_to_utf8(g.downloads_dir);
    sb_printf(&out, "{\"ok\":true,\"version\":\"%s\",\"cef\":\"%s\",\"chromium\":\"%d.%d.%d.%d\",\"platform\":\"Windows x86\",\"portable\":%s,",
              FREEDOM_VERSION, CEF_VERSION, CHROME_VERSION_MAJOR, CHROME_VERSION_MINOR, CHROME_VERSION_BUILD, CHROME_VERSION_PATCH,
              g.portable ? "true" : "false");
    sb_append(&out, "\"profileDir\":");
    sb_json_str(&out, profile);
    sb_append(&out, ",\"downloadsDir\":");
    sb_json_str(&out, dl);
    sb_printf(&out, ",\"blocklistSize\":%u}", (unsigned)blocklist_size(g.blocklist));
    free(profile);
    free(dl);
  } else if (!strcmp(cmd, "storage.get")) {
    char* key = dict_str(d, "key");
    if (valid_storage_key(key)) {
      wchar_t* p = storage_path(key, L".json");
      char* data = file_read_all(p, NULL);
      sb_append(&out, "{\"ok\":true,\"value\":");
      if (data) sb_json_str(&out, data); else sb_append(&out, "null");
      sb_append(&out, "}");
      free(data);
      free(p);
    } else sb_append(&out, "{\"ok\":false}");
    free(key);
  } else if (!strcmp(cmd, "storage.set")) {
    char* key = dict_str(d, "key");
    char* value = dict_str(d, "value");
    int ok = 0;
    if (valid_storage_key(key) && value) {
      wchar_t* p = storage_path(key, L".json");
      ok = file_write_atomic(p, value, strlen(value));
      free(p);
    }
    sb_printf(&out, "{\"ok\":%s}", ok ? "true" : "false");
    free(key);
    free(value);
  } else if (!strcmp(cmd, "config")) {
    apply_config(d);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "layout")) {
    g.ui_full = dict_bool(d, "full", 1);
    g.toolbar_css_px = dict_int(d, "toolbar", g.toolbar_css_px);
    layout_browsers();
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "ui.focus")) {
    /* Moves Win32 keyboard focus to the UI browser (e.g. address bar after Ctrl+T in a page). */
    if (g.ui_browser) {
      cef_browser_host_t* host = g.ui_browser->get_host(g.ui_browser);
      host->set_focus(host, 1);
      CEF_RELEASE(host);
    }
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "log")) {
    /* UI script errors, written only with --freedom-debug-log */
    char* m = dict_str(d, "message");
    if (g.debug && m) flog("ui: %.300s", m);
    free(m);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "window.minimize")) {
    ShowWindow(g.main_hwnd, SW_MINIMIZE);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "window.maximize")) {
    ShowWindow(g.main_hwnd, IsZoomed(g.main_hwnd) ? SW_RESTORE : SW_MAXIMIZE);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "window.close")) {
    PostMessageW(g.main_hwnd, WM_CLOSE, 0, 0);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "window.drag")) {
    window_begin_drag();
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "window.fullscreen")) {
    set_window_fullscreen(dict_bool(d, "on", !g.window_fullscreen));
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "window.state")) {
    sb_printf(&out, "{\"ok\":true,\"maximized\":%s,\"fullscreen\":%s}", IsZoomed(g.main_hwnd) ? "true" : "false", g.window_fullscreen ? "true" : "false");
  } else if (!strcmp(cmd, "tab.snapshot")) {
    browser_snapshot(id);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "title")) {
    char* t = dict_str(d, "title");
    wchar_t* w = utf8_to_wide(t);
    SetWindowTextW(g.main_hwnd, w);
    free(w);
    free(t);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "tab.create")) {
    char* url = dict_str(d, "url");
    int ok = tab_create(id, dict_bool(d, "private", 0), url, dict_bool(d, "select", 1));
    free(url);
    sb_printf(&out, "{\"ok\":%s}", ok ? "true" : "false");
  } else if (!strcmp(cmd, "tab.select")) {
    tab_select(id);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "tab.close")) {
    tab_close(id);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "tab.navigate")) { with_browser(id, op_navigate, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.back")) { with_browser(id, op_back, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.forward")) { with_browser(id, op_forward, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.reload")) { with_browser(id, op_reload, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.stop")) { with_browser(id, op_stop, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.zoom")) { with_browser(id, op_zoom, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.find")) { with_browser(id, op_find, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.findStop")) { with_browser(id, op_find_stop, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.focus")) { with_browser(id, op_focus, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.print")) { with_browser(id, op_print, d); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.pdf")) { browser_print_pdf(id); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "tab.screenshot")) { browser_screenshot(id); sb_append(&out, "{\"ok\":true}"); }
  else if (!strcmp(cmd, "download.answer")) {
    download_answer((uint32_t)dict_double(d, "downloadId", 0), dict_bool(d, "accept", 0));
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "download.control")) {
    char* action = dict_str(d, "action");
    download_control((uint32_t)dict_double(d, "downloadId", 0), action ? action : "");
    free(action);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "download.open") || !strcmp(cmd, "download.show")) {
    char* path = dict_str(d, "path");
    wchar_t* w = utf8_to_wide(path);
    int ok = 0;
    if (path && *path && file_exists_w(w)) {
      if (!strcmp(cmd, "download.open")) ok = (INT_PTR)ShellExecuteW(g.main_hwnd, L"open", w, NULL, NULL, SW_SHOWNORMAL) > 32;
      else {
        size_t n = wcslen(w) + 16;
        wchar_t* args = (wchar_t*)malloc(n * sizeof(wchar_t));
        swprintf(args, n, L"/select,\"%ls\"", w);
        ok = (INT_PTR)ShellExecuteW(g.main_hwnd, L"open", L"explorer.exe", args, NULL, SW_SHOWNORMAL) > 32;
        free(args);
      }
    }
    free(w);
    free(path);
    sb_printf(&out, "{\"ok\":%s}", ok ? "true" : "false");
  } else if (!strcmp(cmd, "download.start")) {
    char* url = dict_str(d, "url");
    if (url && (str_starts_with(url, "https://") || str_starts_with(url, "http://")) && g.ui_browser) {
      cef_browser_host_t* host = g.ui_browser->get_host(g.ui_browser);
      cef_string_t s = {0};
      cs_set(&s, url);
      host->start_download(host, &s);
      cs_clear(&s);
      CEF_RELEASE(host);
    }
    free(url);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "permission.answer")) {
    permission_answer(dict_int(d, "promptId", 0), dict_bool(d, "allow", 0));
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "auth.answer")) {
    char* user = dict_str(d, "user");
    char* pass = dict_str(d, "pass");
    auth_answer(dict_int(d, "authId", 0), dict_bool(d, "cancel", 0) ? NULL : (user ? user : ""), pass);
    free(user);
    free(pass);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "external.open")) {
    char* url = dict_str(d, "url");
    int ok = 0;
    /* Only mail and phone links may launch another application. */
    if (url && (str_starts_with(url, "mailto:") || str_starts_with(url, "tel:"))) {
      wchar_t* w = utf8_to_wide(url);
      ok = (INT_PTR)ShellExecuteW(g.main_hwnd, L"open", w, NULL, NULL, SW_SHOWNORMAL) > 32;
      free(w);
    }
    free(url);
    sb_printf(&out, "{\"ok\":%s}", ok ? "true" : "false");
  } else if (!strcmp(cmd, "data.clear")) {
    cef_list_value_t* origins = dict_list(d, "origins");
    browser_clear_data(dict_bool(d, "cookies", 0), dict_bool(d, "cache", 0), origins);
    CEF_RELEASE(origins);
    sb_append(&out, "{\"ok\":true}");
  } else if (!strcmp(cmd, "folder.pick")) {
    char* p = pick_folder();
    sb_append(&out, "{\"ok\":true,\"path\":");
    if (p) sb_json_str(&out, p); else sb_append(&out, "null");
    sb_append(&out, "}");
    free(p);
  } else if (!strcmp(cmd, "blocklist.save")) {
    char* text = dict_str(d, "text");
    wchar_t* p = path_join(g.data_dir, L"blocklist-custom.txt");
    int ok;
    if (text && *text) ok = file_write_atomic(p, text, strlen(text));
    else ok = DeleteFileW(p) || GetLastError() == ERROR_FILE_NOT_FOUND;
    free(p);
    free(text);
    blocklist_reload();
    sb_printf(&out, "{\"ok\":%s,\"size\":%u}", ok ? "true" : "false", (unsigned)blocklist_size(g.blocklist));
  } else {
    sb_append(&out, "{\"ok\":false,\"error\":\"unknown command\"}");
  }
  free(cmd);
  CEF_RELEASE(d);
  return out.data;
}

/* ------------------------------------------------------ resource handler */

typedef struct {
  cef_resource_handler_t h;
  char* data;
  size_t len, pos;
  const char* mime;
  int status;
  char* body;               /* IPC request body */
  cef_callback_t* callback; /* pending IPC continuation */
} res_t;

static void res_dtor(void* self) {
  res_t* r = (res_t*)self;
  free(r->data);
  free(r->body);
}

static const char* mime_for(const char* path) {
  const char* dot = strrchr(path, '.');
  if (!dot) return "application/octet-stream";
  if (!_stricmp(dot, ".html")) return "text/html";
  if (!_stricmp(dot, ".css")) return "text/css";
  if (!_stricmp(dot, ".js") || !_stricmp(dot, ".mjs")) return "text/javascript";
  if (!_stricmp(dot, ".json") || !_stricmp(dot, ".map")) return "application/json";
  if (!_stricmp(dot, ".svg")) return "image/svg+xml";
  if (!_stricmp(dot, ".png")) return "image/png";
  if (!_stricmp(dot, ".ico")) return "image/x-icon";
  if (!_stricmp(dot, ".txt")) return "text/plain";
  if (!_stricmp(dot, ".woff2")) return "font/woff2";
  return "application/octet-stream";
}

static void ipc_task(void* arg) {
  res_t* r = (res_t*)arg;
  char* resp = run_command(r->body ? r->body : "{}");
  r->data = resp;
  r->len = strlen(resp);
  r->mime = "application/json";
  r->status = 200;
  cef_callback_t* cb = r->callback;
  r->callback = NULL;
  cb->cont(cb);
  CEF_RELEASE(cb);
  fr_release(r);
}

static char* read_post_body(cef_request_t* request) {
  cef_post_data_t* pd = request->get_post_data(request);
  if (!pd) return NULL;
  size_t count = pd->get_element_count(pd);
  sbuf b;
  sb_init(&b);
  if (count > 0 && count < 64) {
    cef_post_data_element_t* elems[64];
    memset(elems, 0, sizeof elems); /* CEF reads the array as input too: must be NULL-initialised */
    pd->get_elements(pd, &count, elems);
    for (size_t i = 0; i < count; i++) {
      size_t n = elems[i]->get_bytes_count(elems[i]);
      if (n > 0 && n < 64u * 1024 * 1024) {
        char* tmp = (char*)malloc(n);
        elems[i]->get_bytes(elems[i], n, tmp);
        sb_appendn(&b, tmp, n);
        free(tmp);
      }
      CEF_RELEASE(elems[i]);
    }
  }
  CEF_RELEASE(pd);
  return b.data;
}

static int CEF_CALLBACK res_open(cef_resource_handler_t* self, cef_request_t* request, int* handle_request, cef_callback_t* callback) {
  res_t* r = (res_t*)self;
  char* url = csu_to_utf8_free(request->get_url(request));
  int result = 1;
  const char* path = url + strlen("freedom://ui/");
  if (!str_starts_with(url, "freedom://ui/")) { r->status = 404; *handle_request = 1; goto done; }
  if (!strncmp(path, "ipc", 3) && (path[3] == 0 || path[3] == '?')) {
    r->body = read_post_body(request);
    r->callback = callback;
    callback = NULL; /* kept until ipc_task continues it */
    fr_add_ref(r);
    post_ui_task(ipc_task, r);
    *handle_request = 0;
    goto done;
  }
  /* Static UI file: reject traversal and anything but simple relative paths. */
  {
    char clean[260];
    size_t n = strcspn(path, "?#");
    if (n == 0) { path = "index.html"; n = strlen(path); }
    if (n >= sizeof clean || strstr(path, "..") || memchr(path, '\\', n) || memchr(path, ':', n)) { r->status = 404; *handle_request = 1; goto done; }
    memcpy(clean, path, n);
    clean[n] = 0;
    for (char* c = clean; *c; c++) if (*c == '/') *c = '\\';
    wchar_t* rel = utf8_to_wide(clean);
    wchar_t* full = path_join(g.ui_dir, rel);
    r->data = file_read_all(full, &r->len);
    free(rel);
    free(full);
    r->status = r->data ? 200 : 404;
    r->mime = mime_for(clean);
    *handle_request = 1;
  }
done:
  free(url);
  CEF_RELEASE(request);
  CEF_RELEASE(callback);
  return result;
}

static void CEF_CALLBACK res_headers(cef_resource_handler_t* self, cef_response_t* response, int64_t* length, cef_string_t* redirect) {
  (void)redirect;
  res_t* r = (res_t*)self;
  response->set_status(response, r->status ? r->status : 404);
  cef_string_t s = {0}, k = {0};
  cs_set(&s, r->mime ? r->mime : "text/plain");
  response->set_mime_type(response, &s);
  cs_set(&s, "utf-8");
  response->set_charset(response, &s);
  cs_set(&k, "Cache-Control");
  cs_set(&s, "no-store");
  response->set_header_by_name(response, &k, &s, 1);
  cs_set(&k, "X-Content-Type-Options");
  cs_set(&s, "nosniff");
  response->set_header_by_name(response, &k, &s, 1);
  cs_clear(&s);
  cs_clear(&k);
  *length = r->data ? (int64_t)r->len : 0;
  CEF_RELEASE(response);
}

static int CEF_CALLBACK res_read(cef_resource_handler_t* self, void* out, int to_read, int* bytes_read, cef_resource_read_callback_t* cb) {
  res_t* r = (res_t*)self;
  CEF_RELEASE(cb);
  if (!r->data || r->pos >= r->len) { *bytes_read = 0; return 0; }
  size_t n = r->len - r->pos;
  if (n > (size_t)to_read) n = (size_t)to_read;
  memcpy(out, r->data + r->pos, n);
  r->pos += n;
  *bytes_read = (int)n;
  return 1;
}

static void CEF_CALLBACK res_cancel(cef_resource_handler_t* self) { (void)self; }

/* -------------------------------------------------------- scheme factory */

typedef struct { cef_scheme_handler_factory_t f; } factory_t;

static cef_resource_handler_t* CEF_CALLBACK factory_create(cef_scheme_handler_factory_t* self, cef_browser_t* browser, cef_frame_t* frame,
                                                          const cef_string_t* scheme, cef_request_t* request) {
  (void)self; (void)scheme;
  int allowed = 0;
  /* Only Lumo's own UI browser may load freedom:// resources or call the IPC endpoint. */
  if (browser) {
    cef_browser_host_t* host = browser->get_host(browser);
    cef_client_t* client = host ? host->get_client(host) : NULL;
    allowed = client && g_ui_client && client == &g_ui_client->c;
    CEF_RELEASE(client);
    CEF_RELEASE(host);
  }
  CEF_RELEASE(browser);
  CEF_RELEASE(frame);
  CEF_RELEASE(request);
  if (!allowed) return NULL;
  res_t* r = FR_NEW(res_t, cef_resource_handler_t, res_dtor);
  r->h.open = res_open;
  r->h.get_response_headers = res_headers;
  r->h.read = res_read;
  r->h.cancel = res_cancel;
  return &r->h; /* our single reference is transferred to CEF */
}

void ui_register_handlers(void) {
  factory_t* f = FR_NEW(factory_t, cef_scheme_handler_factory_t, NULL);
  f->f.create = factory_create;
  cef_string_t scheme = {0}, domain = {0};
  cs_set(&scheme, "freedom");
  cs_set(&domain, "ui");
  cef_register_scheme_handler_factory(&scheme, &domain, &f->f); /* ownership transferred */
  cs_clear(&scheme);
  cs_clear(&domain);
}
