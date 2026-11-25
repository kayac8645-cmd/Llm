/* Runs shared/testdata/blocklist-vectors.txt against blocklist.c (native gcc, no Windows needed). */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "../src/blocklist.h"

int main(int argc, char** argv) {
  const char* path = argc > 1 ? argv[1] : "../../shared/testdata/blocklist-vectors.txt";
  FILE* f = fopen(path, "rb");
  if (!f) { perror(path); return 2; }
  fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
  char* text = malloc((size_t)n + 1); if (fread(text, 1, (size_t)n, f) != (size_t)n) { fclose(f); return 2; } text[n] = 0; fclose(f);
  char* list = strstr(text, "[list]"); char* queries = strstr(text, "[queries]");
  if (!list || !queries) { fprintf(stderr, "bad vectors file\n"); return 2; }
  blocklist* bl = blocklist_create();
  blocklist_add_text(bl, list + 6, (size_t)(queries - list - 6));
  int failures = 0, count = 0;
  if (blocklist_size(bl) != 4) { fprintf(stderr, "expected 4 rules, got %zu\n", blocklist_size(bl)); failures++; }
  char* line = queries + 9;
  while (line && *line) {
    char* nl = strchr(line, '\n');
    if (nl) *nl = 0;
    if (strncmp(line, "Q ", 2) == 0) {
      char url[512], page[256], frame[16], exc[512] = "";
      int enabled, expected;
      int fields = sscanf(line, "Q %511s %255s %15s %d %d %511s", url, page, frame, &enabled, &expected, exc);
      if (fields < 5) { fprintf(stderr, "bad line: %s\n", line); failures++; }
      const char* ex[16]; int exn = 0;
      if (fields == 6) {
        char* t = exc;
        while (t && *t && exn < 16) { char* c = strchr(t, ','); if (c) *c = 0; ex[exn++] = t; t = c ? c + 1 : NULL; }
      }
      int got = blocklist_should_block(bl, url, strcmp(page, "-") ? page : NULL, strcmp(frame, "main") == 0, enabled, ex, exn);
      if (got != expected) { fprintf(stderr, "FAIL: %s (got %d)\n", line, got); failures++; }
      count++;
    }
    line = nl ? nl + 1 : NULL;
  }
  /* Large list performance sanity: 100k domains. */
  blocklist* big = blocklist_create();
  char buf[64];
  for (int i = 0; i < 100000; i++) { int k = snprintf(buf, sizeof buf, "0.0.0.0 host%d.example.org\n", i); blocklist_add_text(big, buf, (size_t)k); }
  if (blocklist_size(big) != 100000 || !blocklist_contains_host(big, "a.host99999.example.org")) { fprintf(stderr, "FAIL: big list\n"); failures++; }
  blocklist_free(big);
  blocklist_free(bl);
  printf("blocklist C vectors: %d queries, %d failures\n", count, failures);
  return failures ? 1 : 0;
}
