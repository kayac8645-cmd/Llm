#include "freedom.h"
#include <shlobj.h>


app_state g;
static HMODULE g_module; /* our module (DLL in bootstrap mode, EXE otherwise) */

/* --------------------------------------------------------------------- app */

typedef struct { cef_browser_process_handler_t h; } bph_t;
typedef struct { cef_app_t app; bph_t* bph; } app_t;

static void CEF_CALLBACK on_context_initialized(cef_browser_process_handler_t* self) {
  (void)self;
  ui_register_handlers();
  RECT rc;
  GetClientRect(g.main_hwnd, &rc);
  cef_window_info_t wi = {0};
  wi.size = sizeof wi;
  wi.style = WS_CHILD | WS_CLIPCHILDREN | WS_CLIPSIBLINGS | WS_TABSTOP | WS_VISIBLE;
  wi.parent_window = g.main_hwnd;
  wi.bounds.x = 0; wi.bounds.y = 0; wi.bounds.width = rc.right; wi.bounds.height = rc.bottom;
  wi.runtime_style = CEF_RUNTIME_STYLE_ALLOY;
  cef_browser_settings_t bs = {0};
  bs.size = sizeof bs;
  bs.background_color = 0xFF0B0C0E;
  cef_string_t url = {0};
  cs_set(&url, "freedom://ui/index.html");
  cef_client_t* client = ui_client();
  client->base.add_ref(&client->base);
  g.ui_browser = cef_browser_host_create_browser_sync(&wi, client, &url, &bs, NULL, NULL);
  cs_clear(&url);
  if (g.ui_browser) {
    g.ui_browser_id = g.ui_browser->get_identifier(g.ui_browser);
    g.browser_count++;
  } else {
    flog("failed to create UI browser");
    MessageBoxW(g.main_hwnd, L"Lumo Browser could not start its interface.", FREEDOM_APP_NAME, MB_ICONERROR);
    cef_quit_message_loop();
  }
  layout_browsers();
}

static void CEF_CALLBACK on_register_custom_schemes(cef_app_t* self, cef_scheme_registrar_t* registrar) {
  (void)self;
  ui_register_schemes(registrar); /* scoped object: not ref-counted, do not release */
}

static void CEF_CALLBACK on_before_command_line_processing(cef_app_t* self, const cef_string_t* process_type, cef_command_line_t* cl) {
  (void)self; (void)process_type;
  CEF_RELEASE(cl);
}

static cef_browser_process_handler_t* CEF_CALLBACK get_bph(cef_app_t* self) {
  app_t* a = (app_t*)self;
  fr_add_ref(a->bph);
  return &a->bph->h;
}

static cef_app_t* create_app(void) {
  app_t* a = FR_NEW(app_t, cef_app_t, NULL);
  a->app.on_register_custom_schemes = on_register_custom_schemes;
  a->app.on_before_command_line_processing = on_before_command_line_processing;
  a->app.get_browser_process_handler = get_bph;
  a->bph = FR_NEW(bph_t, cef_browser_process_handler_t, NULL);
  a->bph->h.on_context_initialized = on_context_initialized;
  return &a->app;
}

/* ------------------------------------------------------------------ layout */

static double dpi_scale(void) {
  UINT dpi = 96;
  HMODULE user32 = GetModuleHandleW(L"user32.dll");
  typedef UINT(WINAPI * GetDpiForWindow_t)(HWND);
  GetDpiForWindow_t fn = user32 ? (GetDpiForWindow_t)(void*)GetProcAddress(user32, "GetDpiForWindow") : NULL;
  if (fn && g.main_hwnd) dpi = fn(g.main_hwnd);
  return dpi / 96.0;
}

static HWND browser_hwnd(cef_browser_t* b) {
  if (!b) return NULL;
  cef_browser_host_t* host = b->get_host(b);
  HWND h = host->get_window_handle(host);
  CEF_RELEASE(host);
  return h;
}

static void show_browser(tab* t, int show, const RECT* r) {
  if (!t->hwnd) t->hwnd = browser_hwnd(t->browser);
  if (!t->hwnd) return;
  cef_browser_host_t* host = t->browser->get_host(t->browser);
  if (show) {
    SetWindowPos(t->hwnd, HWND_BOTTOM, r->left, r->top, r->right - r->left, r->bottom - r->top, SWP_NOACTIVATE | SWP_SHOWWINDOW);
    host->was_hidden(host, 0);
  } else if (IsWindowVisible(t->hwnd)) {
    ShowWindow(t->hwnd, SW_HIDE);
    host->was_hidden(host, 1);
  }
  CEF_RELEASE(host);
}

void layout_browsers(void) {
  if (!g.main_hwnd) return;
  RECT rc;
  GetClientRect(g.main_hwnd, &rc);
  HWND ui = browser_hwnd(g.ui_browser);
  int toolbar = (int)(g.toolbar_css_px * dpi_scale() + 0.5);
  if (toolbar > rc.bottom) toolbar = rc.bottom;
  RECT content = {0, toolbar, rc.right, rc.bottom};
  if (g.html_fullscreen) content.top = 0;
  for (tab* t = g.tabs; t; t = t->next) {
    int visible = t->id == g.selected_tab && (g.html_fullscreen || !g.ui_full);
    show_browser(t, visible, &content);
  }
  if (ui) {
    if (g.html_fullscreen) ShowWindow(ui, SW_HIDE);
    else {
      int h = g.ui_full ? rc.bottom : toolbar;
      RECT old;
      GetWindowRect(ui, &old);
      SetWindowPos(ui, HWND_TOP, 0, 0, rc.right, h, SWP_NOACTIVATE | SWP_SHOWWINDOW);
      if (old.bottom - old.top != h) {
        /* Make Chromium produce a frame for the new size right away instead of on the next input event. */
        cef_browser_host_t* host = g.ui_browser->get_host(g.ui_browser);
        host->was_hidden(host, 0);
        host->notify_move_or_resize_started(host);
        CEF_RELEASE(host);
        RedrawWindow(ui, NULL, NULL, RDW_INVALIDATE | RDW_ALLCHILDREN | RDW_UPDATENOW);
      }
    }
  }
}

void set_html_fullscreen(int on) {
  if (on == g.html_fullscreen) return;
  g.html_fullscreen = on;
  HWND w = g.main_hwnd;
  if (on) {
    g.placement.length = sizeof g.placement;
    GetWindowPlacement(w, &g.placement);
    g.saved_style = GetWindowLongW(w, GWL_STYLE);
    MONITORINFO mi = {sizeof mi};
    GetMonitorInfoW(MonitorFromWindow(w, MONITOR_DEFAULTTONEAREST), &mi);
    SetWindowLongW(w, GWL_STYLE, g.saved_style & ~WS_OVERLAPPEDWINDOW);
    SetWindowPos(w, HWND_TOP, mi.rcMonitor.left, mi.rcMonitor.top, mi.rcMonitor.right - mi.rcMonitor.left,
                 mi.rcMonitor.bottom - mi.rcMonitor.top, SWP_NOOWNERZORDER | SWP_FRAMECHANGED);
  } else {
    SetWindowLongW(w, GWL_STYLE, g.saved_style);
    SetWindowPlacement(w, &g.placement);
    SetWindowPos(w, NULL, 0, 0, 0, 0, SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOOWNERZORDER | SWP_FRAMECHANGED);
  }
  layout_browsers();
  ui_emitf("{\"type\":\"fullscreen\",\"on\":%s}", on ? "true" : "false");
}

void maybe_quit(void) {
  if (g.closing && g.browser_count <= 0) {
    cef_quit_message_loop();
  }
}

/* ------------------------------------------------------------------ window */

static LRESULT CALLBACK wnd_proc(HWND hwnd, UINT msg, WPARAM wp, LPARAM lp) {
  switch (msg) {
    case WM_SIZE:
      layout_browsers();
      return 0;
    case WM_ERASEBKGND:
      return 1;
    case WM_SETFOCUS: {
      tab* t = tab_find(g.selected_tab);
      cef_browser_t* b = (t && !g.ui_full) ? t->browser : g.ui_browser;
      if (b) {
        cef_browser_host_t* host = b->get_host(b);
        host->set_focus(host, 1);
        CEF_RELEASE(host);
      }
      return 0;
    }
    case WM_GETMINMAXINFO: {
      MINMAXINFO* mmi = (MINMAXINFO*)lp;
      mmi->ptMinTrackSize.x = 480;
      mmi->ptMinTrackSize.y = 360;
      return 0;
    }
    case 0x02E0: /* WM_DPICHANGED */ {
      RECT* r = (RECT*)lp;
      SetWindowPos(hwnd, NULL, r->left, r->top, r->right - r->left, r->bottom - r->top, SWP_NOZORDER | SWP_NOACTIVATE);
      layout_browsers();
      return 0;
    }
    case WM_CLOSE:
      if (!g.closing) {
        g.closing = 1;
        tabs_close_all();
        if (g.ui_browser) {
          cef_browser_host_t* host = g.ui_browser->get_host(g.ui_browser);
          host->close_browser(host, 1);
          CEF_RELEASE(host);
        }
        maybe_quit();
      }
      return 0;
    case WM_DESTROY:
      return 0;
  }
  return DefWindowProcW(hwnd, msg, wp, lp);
}

static HWND create_main_window(HINSTANCE inst, int show) {
  WNDCLASSEXW wc = {0};
  wc.cbSize = sizeof wc;
  wc.lpfnWndProc = wnd_proc;
  wc.hInstance = inst;
  wc.hIcon = LoadIconW(g_module, MAKEINTRESOURCEW(101));
  wc.hIconSm = wc.hIcon;
  wc.hCursor = LoadCursor(NULL, IDC_ARROW);
  wc.hbrBackground = CreateSolidBrush(RGB(0x0B, 0x0C, 0x0E));
  wc.lpszClassName = L"LumoBrowserMainWindow";
  RegisterClassExW(&wc);
  HWND hwnd = CreateWindowExW(0, wc.lpszClassName, FREEDOM_APP_NAME, WS_OVERLAPPEDWINDOW | WS_CLIPCHILDREN,
                              CW_USEDEFAULT, CW_USEDEFAULT, 1280, 820, NULL, NULL, inst, NULL);
  ShowWindow(hwnd, show);
  UpdateWindow(hwnd);
  return hwnd;
}

/* ------------------------------------------------------------------- paths */

static wchar_t* known_folder(REFKNOWNFOLDERID id) {
  PWSTR p = NULL;
  wchar_t* r = NULL;
  if (SUCCEEDED(SHGetKnownFolderPath(id, 0, NULL, &p))) {
    r = _wcsdup(p);
    CoTaskMemFree(p);
  }
  return r;
}

static void init_paths(void) {
  wchar_t buf[MAX_PATH * 2];
  GetModuleFileNameW(g_module, buf, MAX_PATH * 2);
  wchar_t* slash = wcsrchr(buf, L'\\');
  if (slash) *slash = 0;
  g.exe_dir = _wcsdup(buf);
  g.ui_dir = path_join(g.exe_dir, L"ui");
  wchar_t* flag = path_join(g.exe_dir, L"portable.flag");
  g.portable = file_exists_w(flag);
  free(flag);
  if (g.portable) {
    g.profile_dir = path_join(g.exe_dir, L"profile");
  } else {
    wchar_t* local = known_folder(&FOLDERID_LocalAppData);
    wchar_t* app = path_join(local ? local : g.exe_dir, L"LumoBrowser");
    CreateDirectoryW(app, NULL);
    g.profile_dir = path_join(app, L"User Data");
    free(app);
    free(local);
  }
  CreateDirectoryW(g.profile_dir, NULL);
  g.data_dir = path_join(g.profile_dir, L"freedom-data");
  CreateDirectoryW(g.data_dir, NULL);
  g.downloads_dir = known_folder(&FOLDERID_Downloads);
  if (!g.downloads_dir) g.downloads_dir = _wcsdup(g.profile_dir);
}

/* ------------------------------------------------------------------- entry */

static int run(HINSTANCE inst, int show, void* sandbox_info) {
  /* Must be the first CEF call: selects the API version we were compiled against. */
  cef_api_hash(CEF_API_VERSION, 0);
  cef_main_args_t args = {inst};
  cef_app_t* app = create_app();
  app->base.add_ref(&app->base);
  int code = cef_execute_process(&args, app, sandbox_info);
  if (code >= 0) {
    app->base.release(&app->base);
    return code; /* sub-process finished */
  }

  memset(&g, 0, sizeof g);
  g.instance = inst;
  g.toolbar_css_px = 88;
  g.ui_full = 1;
  g.next_native_tab_id = -1;
  g.adblock_enabled = 1;
  g.block_popups = 1;
  InitializeSRWLock(&g.lock);
  CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
  init_paths();
  flog("start version=%s cef=%s sandbox=%s portable=%d", FREEDOM_VERSION, CEF_VERSION, sandbox_info ? "on" : "off", g.portable);
  blocklist_reload();

  cef_settings_t s = {0};
  s.size = sizeof s;
  s.no_sandbox = sandbox_info ? 0 : 1;
  s.persist_session_cookies = 0;
  s.background_color = 0xFF0B0C0E;
  char* profile8 = wide_to_utf8(g.profile_dir);
  cs_set(&s.root_cache_path, profile8);
  wchar_t* cache_w = path_join(g.profile_dir, L"Default");
  char* cache8 = wide_to_utf8(cache_w);
  cs_set(&s.cache_path, cache8);
  free(cache_w);
  free(cache8);
  free(profile8);
  LANGID lang = GetUserDefaultUILanguage();
  cs_set(&s.locale, PRIMARYLANGID(lang) == LANG_TURKISH ? "tr" : "en-US");
  /* Chromium's own log may contain URLs; it is off unless --freedom-debug-log is given. */
  g.debug = wcsstr(GetCommandLineW(), L"--freedom-debug-log") != NULL;
  s.log_severity = g.debug ? LOGSEVERITY_WARNING : LOGSEVERITY_DISABLE;
  if (s.log_severity != LOGSEVERITY_DISABLE) {
    wchar_t* lf = path_join(g.profile_dir, L"cef-debug.log");
    char* lf8 = wide_to_utf8(lf);
    cs_set(&s.log_file, lf8);
    free(lf);
    free(lf8);
  }

  g.main_hwnd = create_main_window(inst, show);
  app->base.add_ref(&app->base);
  if (!cef_initialize(&args, &s, app, sandbox_info)) {
    flog("cef_initialize failed: %d", cef_get_exit_code());
    MessageBoxW(g.main_hwnd, L"Chromium Embedded Framework failed to initialize.\nAnother Lumo Browser instance may already be using this profile.",
                FREEDOM_APP_NAME, MB_ICONERROR);
    return 1;
  }
  cef_run_message_loop();
  if (g.private_context) CEF_RELEASE(g.private_context);
  cef_shutdown();
  DestroyWindow(g.main_hwnd);
  app->base.release(&app->base);
  flog("exit");
  return 0;
}

static void detect_module(void) {
  GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT, (LPCWSTR)(void*)&detect_module, &g_module);
}

#ifdef FREEDOM_BOOTSTRAP
/* Entry point called by CEF's bootstrap.exe (renamed LumoBrowser.exe), which enables the
 * Chromium sandbox for all sub-processes and passes |sandbox_info|. */
__declspec(dllexport) int RunWinMain(HINSTANCE hInstance, LPWSTR lpCmdLine, int nCmdShow, void* sandbox_info, cef_version_info_t* version_info) {
  (void)lpCmdLine;
  (void)version_info;
  detect_module();
  return run(hInstance, nCmdShow, sandbox_info);
}
#else
/* Compatibility executable: same browser without the Chromium sandbox (troubleshooting only). */
int WINAPI wWinMain(HINSTANCE hInstance, HINSTANCE prev, PWSTR cmd, int nCmdShow) {
  (void)prev;
  (void)cmd;
  detect_module();
  return run(hInstance, nCmdShow, NULL);
}
#endif
