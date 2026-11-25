/* Domain-based ad/tracker rules. Portable C (also compiled natively for tests).
 * Must stay behaviourally identical to shared/src/commonMain/.../BlockList.kt;
 * both are checked against shared/testdata/blocklist-vectors.txt. */
#ifndef FREEDOM_BLOCKLIST_H
#define FREEDOM_BLOCKLIST_H
#include <stddef.h>

typedef struct blocklist blocklist;
blocklist* blocklist_create(void);
void blocklist_free(blocklist* bl);
/* Parses hosts-file / domain-list / "||domain^" lines; returns rules added. */
int blocklist_add_text(blocklist* bl, const char* text, size_t len);
int blocklist_contains_host(const blocklist* bl, const char* host);
size_t blocklist_size(const blocklist* bl);
/* Never blocks main-frame requests; honours per-site exceptions (with parent domains). */
int blocklist_should_block(const blocklist* bl, const char* request_url, const char* page_host,
                           int is_main_frame, int enabled, const char* const* exceptions, int exception_count);
/* 1 if host (minus "www.") or one of its parent domains is in list. */
int host_in_list(const char* host, const char* const* list, int count);
/* Lower-case host of an absolute URL, or NULL. Caller frees. */
char* url_host(const char* url);
#endif
