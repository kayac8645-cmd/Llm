#include "blocklist.h"

#include <ctype.h>
#include <stdlib.h>
#include <string.h>

/* Open-addressing hash set of lower-case domain strings. */
struct blocklist {
  char** slots;
  size_t cap;
  size_t count;
};

static unsigned long hash_str(const char* s, size_t n) {
  unsigned long h = 5381;
  for (size_t i = 0; i < n; i++) h = ((h << 5) + h) ^ (unsigned char)s[i];
  return h;
}

blocklist* blocklist_create(void) {
  blocklist* bl = (blocklist*)calloc(1, sizeof(blocklist));
  bl->cap = 1024;
  bl->slots = (char**)calloc(bl->cap, sizeof(char*));
  return bl;
}

void blocklist_free(blocklist* bl) {
  if (!bl) return;
  for (size_t i = 0; i < bl->cap; i++) free(bl->slots[i]);
  free(bl->slots);
  free(bl);
}

size_t blocklist_size(const blocklist* bl) { return bl ? bl->count : 0; }

static int find_slot(char** slots, size_t cap, const char* s, size_t n, size_t* out) {
  size_t i = hash_str(s, n) & (cap - 1);
  for (;;) {
    if (!slots[i]) { *out = i; return 0; }
    if (strlen(slots[i]) == n && memcmp(slots[i], s, n) == 0) { *out = i; return 1; }
    i = (i + 1) & (cap - 1);
  }
}

static void grow(blocklist* bl) {
  size_t ncap = bl->cap * 2;
  char** ns = (char**)calloc(ncap, sizeof(char*));
  for (size_t i = 0; i < bl->cap; i++) {
    if (!bl->slots[i]) continue;
    size_t at;
    find_slot(ns, ncap, bl->slots[i], strlen(bl->slots[i]), &at);
    ns[at] = bl->slots[i];
  }
  free(bl->slots);
  bl->slots = ns;
  bl->cap = ncap;
}

static int insert(blocklist* bl, const char* s, size_t n) {
  if ((bl->count + 1) * 10 > bl->cap * 7) grow(bl);
  size_t at;
  if (find_slot(bl->slots, bl->cap, s, n, &at)) return 0;
  char* copy = (char*)malloc(n + 1);
  memcpy(copy, s, n);
  copy[n] = 0;
  bl->slots[at] = copy;
  bl->count++;
  return 1;
}

static int contains_n(const blocklist* bl, const char* s, size_t n) {
  size_t at;
  return bl && bl->count && find_slot(bl->slots, bl->cap, s, n, &at);
}

/* Same rules as UrlResolver.looksLikeDomain: >=2 labels of [a-z0-9-], no leading/trailing '-',
 * TLD alphabetic (2-63) or punycode "xn--". */
static int looks_like_domain(const char* h, size_t n) {
  if (n == 0 || n > 253) return 0;
  size_t labels = 0, start = 0;
  for (size_t i = 0; i <= n; i++) {
    if (i == n || h[i] == '.') {
      size_t len = i - start;
      if (len == 0 || len > 63) return 0;
      if (h[start] == '-' || h[i - 1] == '-') return 0;
      for (size_t k = start; k < i; k++) {
        char c = h[k];
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-')) return 0;
      }
      labels++;
      if (i == n) {
        /* last label = TLD */
        if (len >= 4 && strncmp(h + start, "xn--", 4) == 0) break;
        if (len < 2) return 0;
        for (size_t k = start; k < i; k++) if (!(h[k] >= 'a' && h[k] <= 'z')) return 0;
      }
      start = i + 1;
    }
  }
  return labels >= 2;
}

static int is_skip_host(const char* h, size_t n) {
  static const char* skip[] = {"localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0", "ip6-localhost", "ip6-loopback"};
  for (size_t i = 0; i < sizeof(skip) / sizeof(skip[0]); i++)
    if (strlen(skip[i]) == n && memcmp(skip[i], h, n) == 0) return 1;
  return 0;
}

/* Parses one line (without newline) and inserts the domain if valid. */
static int add_line(blocklist* bl, const char* line, size_t n) {
  char buf[300];
  while (n && isspace((unsigned char)*line)) { line++; n--; }
  while (n && isspace((unsigned char)line[n - 1])) n--;
  if (n == 0 || line[0] == '#' || line[0] == '!' || line[0] == '[') return 0;
  /* strip trailing " #comment" */
  for (size_t i = 0; i + 1 < n; i++) if (line[i] == ' ' && line[i + 1] == '#') { n = i; break; }
  while (n && isspace((unsigned char)line[n - 1])) n--;
  const char* host = NULL;
  size_t hn = 0;
  if (n >= 2 && line[0] == '|' && line[1] == '|') {
    if (line[n - 1] != '^') return 0;
    host = line + 2;
    hn = n - 3;
  } else {
    /* split on spaces/tabs */
    const char* parts[3];
    size_t plen[3];
    int count = 0;
    size_t i = 0;
    while (i < n && count < 3) {
      while (i < n && (line[i] == ' ' || line[i] == '\t')) i++;
      if (i >= n) break;
      size_t s = i;
      while (i < n && line[i] != ' ' && line[i] != '\t') i++;
      parts[count] = line + s;
      plen[count] = i - s;
      count++;
    }
    while (i < n && (line[i] == ' ' || line[i] == '\t')) i++;
    int more = i < n;
    if (count >= 2) {
      int ip = (plen[0] == 7 && !memcmp(parts[0], "0.0.0.0", 7)) || (plen[0] == 9 && !memcmp(parts[0], "127.0.0.1", 9)) ||
               (plen[0] == 2 && !memcmp(parts[0], "::", 2)) || (plen[0] == 3 && !memcmp(parts[0], "::1", 3));
      if (!ip) return 0;
      host = parts[1];
      hn = plen[1];
    } else if (count == 1 && !more) {
      host = parts[0];
      hn = plen[0];
    } else {
      return 0;
    }
  }
  if (hn == 0 || hn >= sizeof(buf)) return 0;
  for (size_t i = 0; i < hn; i++) buf[i] = (char)tolower((unsigned char)host[i]);
  while (hn && buf[hn - 1] == '.') hn--;
  buf[hn] = 0;
  if (is_skip_host(buf, hn) || !looks_like_domain(buf, hn)) return 0;
  return insert(bl, buf, hn);
}

int blocklist_add_text(blocklist* bl, const char* text, size_t len) {
  int added = 0;
  size_t start = 0;
  for (size_t i = 0; i <= len; i++) {
    if (i == len || text[i] == '\n' || text[i] == '\r') {
      if (i > start) added += add_line(bl, text + start, i - start);
      start = i + 1;
    }
  }
  return added;
}

int blocklist_contains_host(const blocklist* bl, const char* host) {
  if (!bl || !host) return 0;
  size_t n = strlen(host);
  while (n && host[n - 1] == '.') n--;
  const char* h = host;
  for (;;) {
    if (contains_n(bl, h, n)) return 1;
    const char* dot = memchr(h, '.', n);
    if (!dot) return 0;
    n -= (size_t)(dot + 1 - h);
    h = dot + 1;
  }
}

char* url_host(const char* url) {
  if (!url) return NULL;
  const char* p = strstr(url, "://");
  if (!p) return NULL;
  for (const char* q = url; q < p; q++) {
    char c = *q;
    if (!(isalnum((unsigned char)c) || c == '+' || c == '-' || c == '.')) return NULL;
  }
  p += 3;
  const char* end = p;
  while (*end && *end != '/' && *end != '?' && *end != '#') end++;
  const char* at = NULL;
  for (const char* q = p; q < end; q++) if (*q == '@') at = q;
  if (at) p = at + 1;
  const char* hs = p;
  const char* he;
  if (*hs == '[') {
    const char* close = memchr(hs, ']', (size_t)(end - hs));
    if (!close) return NULL;
    hs++;
    he = close;
  } else {
    he = end;
    for (const char* q = hs; q < end; q++) if (*q == ':') { he = q; break; }
  }
  size_t n = (size_t)(he - hs);
  while (n && hs[n - 1] == '.') n--;
  if (n == 0) return NULL;
  char* out = (char*)malloc(n + 1);
  for (size_t i = 0; i < n; i++) out[i] = (char)tolower((unsigned char)hs[i]);
  out[n] = 0;
  return out;
}

int host_in_list(const char* host, const char* const* list, int count) {
  if (!host || count <= 0) return 0;
  const char* h = host;
  if (strncmp(h, "www.", 4) == 0) h += 4;
  for (;;) {
    for (int i = 0; i < count; i++) if (list[i] && strcmp(list[i], h) == 0) return 1;
    const char* dot = strchr(h, '.');
    if (!dot) return 0;
    h = dot + 1;
  }
}

int blocklist_should_block(const blocklist* bl, const char* request_url, const char* page_host,
                           int is_main_frame, int enabled, const char* const* exceptions, int exception_count) {
  if (!enabled || is_main_frame) return 0;
  if (page_host && host_in_list(page_host, exceptions, exception_count)) return 0;
  char* host = url_host(request_url);
  if (!host) return 0;
  int r = blocklist_contains_host(bl, host);
  free(host);
  return r;
}
