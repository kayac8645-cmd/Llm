#include <stdarg.h>
#include "freedom.h"

/* ------------------------------------------------------------- refcounting */

typedef struct {
  volatile LONG refs;
  void (*dtor)(void*);
} __attribute__((aligned(8))) rc_header;

#define HDR(obj) (((rc_header*)(obj)) - 1)

static void CEF_CALLBACK rc_add_ref(cef_base_ref_counted_t* self) { InterlockedIncrement(&HDR(self)->refs); }

static int CEF_CALLBACK rc_release(cef_base_ref_counted_t* self) {
  rc_header* h = HDR(self);
  if (InterlockedDecrement(&h->refs) == 0) {
    if (h->dtor) h->dtor(self);
    free(h);
    return 1;
  }
  return 0;
}

static int CEF_CALLBACK rc_has_one(cef_base_ref_counted_t* self) { return HDR(self)->refs == 1; }
static int CEF_CALLBACK rc_has_any(cef_base_ref_counted_t* self) { return HDR(self)->refs >= 1; }

void* fr_new_sized(size_t alloc_size, size_t cef_struct_size, void (*dtor)(void* self)) {
  rc_header* h = (rc_header*)calloc(1, sizeof(rc_header) + alloc_size);
  h->refs = 1;
  h->dtor = dtor;
  cef_base_ref_counted_t* base = (cef_base_ref_counted_t*)(h + 1);
  base->size = cef_struct_size; /* CEF uses this for API versioning: size of the CEF struct only */
  base->add_ref = rc_add_ref;
  base->release = rc_release;
  base->has_one_ref = rc_has_one;
  base->has_at_least_one_ref = rc_has_any;
  return base;
}

void fr_add_ref(void* obj) { if (obj) rc_add_ref((cef_base_ref_counted_t*)obj); }
void fr_release(void* obj) { if (obj) rc_release((cef_base_ref_counted_t*)obj); }

/* ----------------------------------------------------------------- strings */

char* cs_to_utf8(const cef_string_t* s) {
  if (!s || !s->str) return str_dup("");
  cef_string_utf8_t out = {0};
  cef_string_utf16_to_utf8(s->str, s->length, &out);
  char* r = (char*)malloc(out.length + 1);
  memcpy(r, out.str ? out.str : "", out.length);
  r[out.length] = 0;
  cef_string_utf8_clear(&out);
  return r;
}

char* csu_to_utf8_free(cef_string_userfree_t s) {
  if (!s) return str_dup("");
  char* r = cs_to_utf8(s);
  cef_string_userfree_free(s);
  return r;
}

void cs_set(cef_string_t* out, const char* utf8) {
  cef_string_from_utf8(utf8 ? utf8 : "", utf8 ? strlen(utf8) : 0, out);
}

void cs_clear(cef_string_t* s) { cef_string_clear(s); }

wchar_t* utf8_to_wide(const char* s) {
  if (!s) s = "";
  int n = MultiByteToWideChar(CP_UTF8, 0, s, -1, NULL, 0);
  wchar_t* w = (wchar_t*)malloc(sizeof(wchar_t) * (size_t)(n > 0 ? n : 1));
  if (n > 0) MultiByteToWideChar(CP_UTF8, 0, s, -1, w, n); else w[0] = 0;
  return w;
}

char* wide_to_utf8(const wchar_t* w) {
  if (!w) w = L"";
  int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
  char* s = (char*)malloc((size_t)(n > 0 ? n : 1));
  if (n > 0) WideCharToMultiByte(CP_UTF8, 0, w, -1, s, n, NULL, NULL); else s[0] = 0;
  return s;
}

char* str_dup(const char* s) {
  if (!s) s = "";
  size_t n = strlen(s);
  char* r = (char*)malloc(n + 1);
  memcpy(r, s, n + 1);
  return r;
}

int str_starts_with(const char* s, const char* prefix) { return s && prefix && strncmp(s, prefix, strlen(prefix)) == 0; }

/* -------------------------------------------------------------------- sbuf */

void sb_init(sbuf* b) { b->cap = 256; b->len = 0; b->data = (char*)malloc(b->cap); b->data[0] = 0; }
void sb_free(sbuf* b) { free(b->data); b->data = NULL; b->len = b->cap = 0; }

void sb_appendn(sbuf* b, const char* s, size_t n) {
  if (b->len + n + 1 > b->cap) {
    while (b->len + n + 1 > b->cap) b->cap *= 2;
    b->data = (char*)realloc(b->data, b->cap);
  }
  memcpy(b->data + b->len, s, n);
  b->len += n;
  b->data[b->len] = 0;
}

void sb_append(sbuf* b, const char* s) { sb_appendn(b, s, strlen(s)); }

void sb_printf(sbuf* b, const char* fmt, ...) {
  char tmp[512];
  va_list ap;
  va_start(ap, fmt);
  int n = vsnprintf(tmp, sizeof tmp, fmt, ap);
  va_end(ap);
  if (n < 0) return;
  if ((size_t)n < sizeof tmp) { sb_appendn(b, tmp, (size_t)n); return; }
  char* big = (char*)malloc((size_t)n + 1);
  va_start(ap, fmt);
  vsnprintf(big, (size_t)n + 1, fmt, ap);
  va_end(ap);
  sb_appendn(b, big, (size_t)n);
  free(big);
}

void sb_json_str(sbuf* b, const char* s) {
  sb_append(b, "\"");
  if (s) {
    for (const unsigned char* p = (const unsigned char*)s; *p; p++) {
      switch (*p) {
        case '"': sb_append(b, "\\\""); break;
        case '\\': sb_append(b, "\\\\"); break;
        case '\n': sb_append(b, "\\n"); break;
        case '\r': sb_append(b, "\\r"); break;
        case '\t': sb_append(b, "\\t"); break;
        case '<': sb_append(b, "\\u003c"); break; /* safe inside inline scripts */
        default:
          if (*p < 0x20) sb_printf(b, "\\u%04x", *p);
          else sb_appendn(b, (const char*)p, 1);
      }
    }
  }
  sb_append(b, "\"");
}

/* -------------------------------------------------------------------- files */

char* file_read_all(const wchar_t* path, size_t* len_out) {
  HANDLE h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
  if (h == INVALID_HANDLE_VALUE) return NULL;
  LARGE_INTEGER size;
  if (!GetFileSizeEx(h, &size) || size.QuadPart > 64LL * 1024 * 1024) { CloseHandle(h); return NULL; }
  char* buf = (char*)malloc((size_t)size.QuadPart + 1);
  DWORD read = 0, total = 0;
  while (total < (DWORD)size.QuadPart && ReadFile(h, buf + total, (DWORD)size.QuadPart - total, &read, NULL) && read > 0) total += read;
  CloseHandle(h);
  buf[total] = 0;
  if (len_out) *len_out = total;
  return buf;
}

int file_write_atomic(const wchar_t* path, const char* data, size_t len) {
  size_t n = wcslen(path);
  wchar_t* tmp = (wchar_t*)malloc(sizeof(wchar_t) * (n + 5));
  memcpy(tmp, path, n * sizeof(wchar_t));
  wcscpy(tmp + n, L".tmp");
  HANDLE h = CreateFileW(tmp, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
  if (h == INVALID_HANDLE_VALUE) { free(tmp); return 0; }
  DWORD written = 0;
  BOOL ok = WriteFile(h, data, (DWORD)len, &written, NULL) && written == len;
  FlushFileBuffers(h);
  CloseHandle(h);
  if (ok) ok = MoveFileExW(tmp, path, MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH);
  if (!ok) DeleteFileW(tmp);
  free(tmp);
  return ok ? 1 : 0;
}

int file_exists_w(const wchar_t* path) { return GetFileAttributesW(path) != INVALID_FILE_ATTRIBUTES; }

wchar_t* path_join(const wchar_t* a, const wchar_t* b) {
  size_t la = wcslen(a), lb = wcslen(b);
  wchar_t* r = (wchar_t*)malloc(sizeof(wchar_t) * (la + lb + 2));
  memcpy(r, a, la * sizeof(wchar_t));
  size_t pos = la;
  if (la && a[la - 1] != L'\\') r[pos++] = L'\\';
  memcpy(r + pos, b, lb * sizeof(wchar_t));
  r[pos + lb] = 0;
  return r;
}

/* --------------------------------------------------------------------- json */

cef_dictionary_value_t* json_parse_object(const char* utf8) {
  cef_string_t s = {0};
  cs_set(&s, utf8);
  cef_value_t* v = cef_parse_json(&s, JSON_PARSER_RFC);
  cs_clear(&s);
  if (!v) return NULL;
  cef_dictionary_value_t* d = NULL;
  if (v->get_type(v) == VTYPE_DICTIONARY) d = v->get_dictionary(v);
  CEF_RELEASE(v);
  return d;
}

static int dict_has(cef_dictionary_value_t* d, const char* key, cef_value_type_t type, cef_string_t* k) {
  cs_set(k, key);
  int ok = d && d->has_key(d, k) && d->get_type(d, k) == type;
  if (!ok) cs_clear(k);
  return ok;
}

char* dict_str(cef_dictionary_value_t* d, const char* key) {
  cef_string_t k = {0};
  if (!dict_has(d, key, VTYPE_STRING, &k)) return NULL;
  char* r = csu_to_utf8_free(d->get_string(d, &k));
  cs_clear(&k);
  return r;
}

int dict_int(cef_dictionary_value_t* d, const char* key, int def) {
  cef_string_t k = {0};
  if (dict_has(d, key, VTYPE_INT, &k)) { int r = d->get_int(d, &k); cs_clear(&k); return r; }
  if (dict_has(d, key, VTYPE_DOUBLE, &k)) { int r = (int)d->get_double(d, &k); cs_clear(&k); return r; }
  return def;
}

double dict_double(cef_dictionary_value_t* d, const char* key, double def) {
  cef_string_t k = {0};
  if (dict_has(d, key, VTYPE_DOUBLE, &k)) { double r = d->get_double(d, &k); cs_clear(&k); return r; }
  if (dict_has(d, key, VTYPE_INT, &k)) { double r = d->get_int(d, &k); cs_clear(&k); return r; }
  return def;
}

int dict_bool(cef_dictionary_value_t* d, const char* key, int def) {
  cef_string_t k = {0};
  if (!dict_has(d, key, VTYPE_BOOL, &k)) return def;
  int r = d->get_bool(d, &k);
  cs_clear(&k);
  return r;
}

cef_list_value_t* dict_list(cef_dictionary_value_t* d, const char* key) {
  cef_string_t k = {0};
  if (!dict_has(d, key, VTYPE_LIST, &k)) return NULL;
  cef_list_value_t* r = d->get_list(d, &k);
  cs_clear(&k);
  return r;
}

cef_dictionary_value_t* dict_dict(cef_dictionary_value_t* d, const char* key) {
  cef_string_t k = {0};
  if (!dict_has(d, key, VTYPE_DICTIONARY, &k)) return NULL;
  cef_dictionary_value_t* r = d->get_dictionary(d, &k);
  cs_clear(&k);
  return r;
}

/* -------------------------------------------------------------------- log */

void flog(const char* fmt, ...) {
  if (!g.profile_dir) return;
  wchar_t* path = path_join(g.profile_dir, L"freedom.log");
  FILE* f = _wfopen(path, L"ab");
  free(path);
  if (!f) return;
  SYSTEMTIME t;
  GetLocalTime(&t);
  fprintf(f, "%04d-%02d-%02d %02d:%02d:%02d ", t.wYear, t.wMonth, t.wDay, t.wHour, t.wMinute, t.wSecond);
  va_list ap;
  va_start(ap, fmt);
  vfprintf(f, fmt, ap);
  va_end(ap);
  fputc('\n', f);
  fclose(f);
}

/* --------------------------------------------------------------- UI tasks */

typedef struct {
  cef_task_t task;
  void (*fn)(void*);
  void* arg;
} fn_task;

static void CEF_CALLBACK fn_task_execute(cef_task_t* self) {
  fn_task* t = (fn_task*)self;
  t->fn(t->arg);
}

void post_ui_task(void (*fn)(void* arg), void* arg) {
  fn_task* t = FR_NEW(fn_task, cef_task_t, NULL);
  t->task.execute = fn_task_execute;
  t->fn = fn;
  t->arg = arg;
  cef_post_task(TID_UI, &t->task); /* ownership of our reference passes to CEF */
}
