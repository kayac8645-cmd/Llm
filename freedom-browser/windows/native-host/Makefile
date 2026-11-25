# Builds the Windows x86 (32-bit) native host with MinGW-w64 (cross-compile from Linux or MSYS2).
#   make CEF_DIR=/path/to/cef_binary_..._windows32_minimal
# Outputs:
#   build/LumoBrowser.dll  - loaded by CEF's bootstrap.exe (renamed LumoBrowser.exe): sandbox ON
#   build/freedom-compat.exe  - standalone executable without the Chromium sandbox (troubleshooting)
#   build/blocklist_test      - native unit test (host compiler)

CROSS   ?= i686-w64-mingw32-
CC      := $(CROSS)gcc
WINDRES := $(CROSS)windres
HOSTCC  ?= gcc
CEF_DIR ?= ../cef
OUT     := build

CFLAGS  := -std=gnu11 -O2 -Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers \
           -fstack-protector-strong -I$(CEF_DIR) -DUNICODE -D_UNICODE -DWINVER=0x0A00 -D_WIN32_WINNT=0x0A00 \
           -DCEF_API_VERSION=15800
LDFLAGS := -s -static-libgcc -Wl,--dynamicbase,--nxcompat,--large-address-aware -Wl,-Bstatic -lssp -Wl,-Bdynamic
LIBS    := $(CEF_DIR)/Release/libcef.dll -lshell32 -lole32 -luuid -lcomdlg32 -luser32 -lgdi32 -lkernel32

SRC := src/main.c src/ui.c src/browser.c src/util.c src/blocklist.c
HDR := src/freedom.h src/blocklist.h

all: $(OUT)/LumoBrowser.dll $(OUT)/freedom-compat.exe

$(OUT):
	mkdir -p $(OUT)

$(OUT)/res-dll.o: freedom.rc freedom.ico freedom.manifest | $(OUT)
	$(WINDRES) -DFREEDOM_BOOTSTRAP -O coff -i freedom.rc -o $@

$(OUT)/res-exe.o: freedom.rc freedom.ico freedom.manifest | $(OUT)
	$(WINDRES) -O coff -i freedom.rc -o $@

$(OUT)/LumoBrowser.dll: $(SRC) $(HDR) $(OUT)/res-dll.o
	$(CC) $(CFLAGS) -DFREEDOM_BOOTSTRAP -shared -o $@ $(SRC) $(OUT)/res-dll.o $(LDFLAGS) $(LIBS)

$(OUT)/freedom-compat.exe: $(SRC) $(HDR) $(OUT)/res-exe.o
	$(CC) $(CFLAGS) -mwindows -municode -o $@ $(SRC) $(OUT)/res-exe.o $(LDFLAGS) $(LIBS)

$(OUT)/blocklist_test: tests/blocklist_test.c src/blocklist.c src/blocklist.h | $(OUT)
	$(HOSTCC) -O2 -Wall -Wextra -o $@ tests/blocklist_test.c src/blocklist.c

test: $(OUT)/blocklist_test
	$(OUT)/blocklist_test ../../shared/testdata/blocklist-vectors.txt

clean:
	rm -rf $(OUT)

.PHONY: all test clean
