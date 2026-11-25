/*
 * Lumo Browser - Windows x86 native host (CEF C API).
 *
 * Architecture: one top-level Win32 window hosts
 *   - the UI browser: Lumo's own HTML/CSS/JS interface served from freedom://ui/
 *     (toolbar, tabs, panels) which talks to this host through freedom://ipc/
 *   - one content browser (child HWND) per open tab.
 * Only the UI browser can reach freedom:// URLs; websites never get a bridge.
 */
#ifndef FREEDOM_H
#define FREEDOM_H

#ifndef CEF_API_VERSION
#define CEF_API_VERSION 15800
#endif

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "include/cef_api_hash.h"
#include "include/cef_version.h"
#include "include/cef_version_info.h"
#include "include/capi/cef_app_capi.h"
#include "include/capi/cef_browser_capi.h"
#include "include/capi/cef_browser_process_handler_capi.h"
#include "include/capi/cef_client_capi.h"
#include "include/capi/cef_context_menu_handler_capi.h"
#include "include/capi/cef_cookie_capi.h"
#include "include/capi/cef_devtools_message_observer_capi.h"
#include "include/capi/cef_display_handler_capi.h"
#include "include/capi/cef_download_handler_capi.h"
#include "include/capi/cef_find_handler_capi.h"
#include "include/capi/cef_keyboard_handler_capi.h"
#include "include/capi/cef_life_span_handler_capi.h"
#include "include/capi/cef_load_handler_capi.h"
#include "include/capi/cef_parser_capi.h"
#include "include/capi/cef_permission_handler_capi.h"
#include "include/capi/cef_request_context_capi.h"
#include "include/capi/cef_request_handler_capi.h"
#include "include/capi/cef_resource_handler_capi.h"
#include "include/capi/cef_resource_request_handler_capi.h"
#include "include/capi/cef_scheme_capi.h"
#include "include/capi/cef_task_capi.h"
#include "include/capi/cef_values_capi.h"

#define FREEDOM_VERSION "1.1.0"
#define FREEDOM_APP_NAME L"Lumo Browser"

/* ---------------------------------------------------------------- util.c */

/* Ref-counted allocation for every struct we hand to CEF. The returned object
 * starts with one reference owned by the caller. */
void* fr_new_sized(size_t alloc_size, size_t cef_struct_size, void (*dtor)(void* self));
/* Allocates our struct `Type` whose first member is the CEF struct `CefType`. */
#define FR_NEW(Type, CefType, dtor) ((Type*)fr_new_sized(sizeof(Type), sizeof(CefType), (dtor)))
void fr_add_ref(void* obj);
void fr_release(void* obj);
/* Releases a CEF-owned object pointer (browser, frame, request...) if non-NULL. */
/* Every CEF ref-counted struct starts (possibly nested) with cef_base_ref_counted_t. */
#define CEF_RELEASE(p) do { if (p) ((cef_base_ref_counted_t*)(p))->release((cef_base_ref_counted_t*)(p)); } while (0)
#define CEF_ADDREF(p) do { if (p) ((cef_base_ref_counted_t*)(p))->add_ref((cef_base_ref_counted_t*)(p)); } while (0)

/* Strings: internal UTF-8, CEF UTF-16. Returned char* must be free()d. */
char* cs_to_utf8(const cef_string_t* s);
char* csu_to_utf8_free(cef_string_userfree_t s); /* frees s */
void cs_set(cef_string_t* out, const char* utf8);
void cs_clear(cef_string_t* s);
wchar_t* utf8_to_wide(const char* s);
char* wide_to_utf8(const wchar_t* w);
char* str_dup(const char* s);
int str_starts_with(const char* s, const char* prefix);

/* Growable string buffer, used to build JSON events. */
typedef struct { char* data; size_t len, cap; } sbuf;
void sb_init(sbuf* b);
void sb_free(sbuf* b);
void sb_append(sbuf* b, const char* s);
void sb_appendn(sbuf* b, const char* s, size_t n);
void sb_printf(sbuf* b, const char* fmt, ...);
void sb_json_str(sbuf* b, const char* s); /* appends a quoted, escaped JSON string */

/* Files */
char* file_read_all(const wchar_t* path, size_t* len_out);
int file_write_atomic(const wchar_t* path, const char* data, size_t len);
int file_exists_w(const wchar_t* path);
wchar_t* path_join(const wchar_t* a, const wchar_t* b);

/* JSON input (parsed with CEF's parser). */
cef_dictionary_value_t* json_parse_object(const char* utf8);
char* dict_str(cef_dictionary_value_t* d, const char* key);   /* NULL if missing */
int dict_int(cef_dictionary_value_t* d, const char* key, int def);
double dict_double(cef_dictionary_value_t* d, const char* key, double def);
int dict_bool(cef_dictionary_value_t* d, const char* key, int def);
cef_list_value_t* dict_list(cef_dictionary_value_t* d, const char* key);       /* caller releases */
cef_dictionary_value_t* dict_dict(cef_dictionary_value_t* d, const char* key); /* caller releases */

void flog(const char* fmt, ...); /* local diagnostic log; never pass URLs or page data */

/* Posts fn(arg) to the CEF UI thread. */
void post_ui_task(void (*fn)(void* arg), void* arg);

#include "blocklist.h"

/* ----------------------------------------------------------------- app state */
typedef struct tab {
  int id;
  int is_private;
  cef_browser_t* browser;   /* owned reference */
  HWND hwnd;
  char* page_host;          /* guarded by g.lock (read on IO thread) */
  int blocked;
  struct tab* next;
} tab;

typedef struct {
  HINSTANCE instance;
  HWND main_hwnd;
  cef_browser_t* ui_browser;
  int ui_browser_id;
  tab* tabs;
  int selected_tab;
  int next_native_tab_id;  /* negative ids for tabs created by pages (popups) */
  int ui_full;             /* UI covers the whole window (panel/start page/error) */
  int toolbar_css_px;
  int html_fullscreen;
  int window_fullscreen; /* F11 */
  int closing;
  int browser_count;
  wchar_t* exe_dir;
  wchar_t* profile_dir;
  wchar_t* data_dir;
  wchar_t* ui_dir;
  wchar_t* downloads_dir;
  int portable;
  int debug; /* --freedom-debug-log: verbose local diagnostics (command names only, never URLs) */
  cef_request_context_t* private_context;
  /* settings pushed by the UI ("config"); guarded by lock */
  SRWLOCK lock;
  blocklist* blocklist;
  int adblock_enabled;
  char** adblock_exceptions; int adblock_exception_count;
  int block_popups;
  char** popup_allow; int popup_allow_count;
  int ask_download;
  cef_dictionary_value_t* site_permissions; /* site -> {camera:"allow"...} */
  /* saved window placement for HTML fullscreen */
  WINDOWPLACEMENT placement; LONG saved_style;
} app_state;

extern app_state g;

/* main.c */
void layout_browsers(void);
void set_html_fullscreen(int on);
void set_window_fullscreen(int on);
void emit_window_state(void);
void window_begin_drag(void);
void maybe_quit(void);

/* browser.c */
cef_client_t* content_client(void);
tab* tab_find(int id);
tab* tab_find_browser(cef_browser_t* b);
int tab_create(int id, int is_private, const char* url, int select);
void tab_select(int id);
void tab_close(int id);
void tabs_close_all(void);
void browser_apply_cookie_policy(int block_third_party);
void browser_apply_popup_policy(int block, char** allow, int allow_count);
void browser_clear_data(int cookies, int cache, cef_list_value_t* origins);
void browser_screenshot(int tab_id);
void browser_snapshot(int tab_id); /* emits {type:"snapshot"} with a JPEG data URL for menu backdrops */
void browser_print_pdf(int tab_id);
void download_answer(uint32_t id, int accept);
void download_control(uint32_t id, const char* action);
void permission_answer(int prompt_id, int allow);
void auth_answer(int auth_id, const char* user, const char* pass); /* user NULL = cancel */
void page_host_set(tab* t, const char* url);

/* ui.c */
cef_client_t* ui_client(void);
void ui_register_schemes(cef_scheme_registrar_t* registrar);
void ui_register_handlers(void);
void ui_emit(const char* json);      /* window.freedom.onEvent(json) in the UI browser */
void ui_emitf(const char* fmt, ...); /* printf-style (caller escapes strings!) */
void blocklist_reload(void);

#endif
