# Windows x86 (32-bit) compatibility report

## Result

| Item | Value |
|---|---|
| Engine | Chromium Embedded Framework **158.0.6** (Chromium **158.0.8127.89**), official `windows32` binary distribution |
| Runtime architecture | 100 % 32-bit x86: every `.exe`/`.dll` in the package is `PE32`, machine `i386` |
| Minimum OS | **Windows 10** (32-bit or 64-bit). The installer refuses older versions. |
| Not supported | Windows 7, 8, 8.1 (Chromium dropped them in M110). Windows 11 exists only as 64-bit; the x86 build runs there through WOW64 but is not the target. |
| Recommended | 2 GB RAM or more, ~450 MB disk |
| Sandbox | Chromium sandbox **enabled** (CEF `bootstrap.exe`); `freedom-compat.exe` = troubleshooting build without sandbox |

## How the stack was chosen (verified 2026-11-25)

| Option | Finding | Decision |
|---|---|---|
| JCEF (`me.friwi:jcef-natives-windows-i386`) | latest i386 natives are CEF **132.3.1** / Chromium 132 (≈26 major versions old, unpatched) | rejected: security |
| Compose Desktop | Skiko runtime exists only for `windows-x64` and `windows-arm64` | rejected: no x86 runtime |
| Kotlin/Native `mingwX86` | target removed from Kotlin (deprecated 1.8.20, removed 1.9.20) | rejected |
| JVM UI (Swing) + JCEF | needs a 32-bit JRE (Temurin 17 x86 exists; 21 x86 does not) **and** the outdated JCEF above | rejected |
| **CEF C API + Win32 host (C, MinGW-w64 i686) + HTML UI in CEF** | official CEF 158 `windows32` builds exist; the C API is ABI-stable C, so no MSVC is required for our code | **chosen** |

Kotlin is still used on Windows where it is practical: all domain logic (URL/search resolution, file-name rules,
history ranges, bookmark rules, error classification, design tokens) is the shared Kotlin module compiled to
JavaScript and executed by the UI. The native host (~3,200 lines of C) contains only what must be native.

## Verification method

1. **Archive integrity** – CEF archive SHA-1 `d8baa8eb95b4e4c4a913be7cdfc8f9f1b874489c` checked against the CDN's
   published `.sha1`.
2. **Binary architecture** – `scripts/build-windows.sh` runs `file` and `i686-w64-mingw32-objdump -f` on every PE file
   and fails the build on anything that is not `PE32` + `i386`. Report: `dist/windows-x86-verification.txt`
   (8/8 files OK: LumoBrowser.exe, LumoBrowser.dll, freedom-compat.exe, libcef.dll, chrome_elf.dll,
   d3dcompiler_47.dll, vk_swiftshader.dll, vulkan-1.dll). The installer EXE is a 32-bit NSIS stub.
3. **Dependencies** – `LumoBrowser.dll` imports only Windows system DLLs (`KERNEL32, USER32, GDI32, ADVAPI32,
   SHELL32, OLE32, COMDLG32, msvcrt`) and `libcef.dll`; no MinGW runtime DLLs are required (static libgcc/libssp).
   Linker flags enable ASLR (`--dynamicbase`), DEP (`--nxcompat`) and large-address awareness.
4. **Execution** – the staged x86 binaries were run under **Wine 11.0** (WoW64, Xvfb) with an automated
   xdotool scenario (`windows/tests/e2e/run-wine-e2e.sh`): sandboxed start, loading real HTTPS sites, ad blocking,
   bookmarks, error page, private tab, downloads incl. dangerous-file prompt, history, menus, site permissions,
   settings, pop-up blocking, reopen closed tab, and silent install → launch → uninstall of the NSIS installer.
   Results and screenshots: `docs/TEST_RESULTS.md`, `docs/screenshots/windows-*.png`.

## Limits of this verification (stated plainly)

* **No physical or virtual 32-bit Windows 10 installation was available** in the build environment. Wine is a
  compatibility layer, not Windows: GPU acceleration, the real Windows sandbox (job objects / restricted tokens),
  DirectComposition, Windows Defender/SmartScreen behaviour and font rendering differ. Final acceptance therefore
  still requires running the portable ZIP and the installer on a real 32-bit Windows 10 machine (checklist below).
* The binaries are **not code-signed**; SmartScreen will warn on first start. Organisations should sign them.
* Security depends on keeping CEF current. Rebuild with each new CEF `windows32` release (update `CEF_VERSION`
  and `CEF_SHA1` in `scripts/build-windows.sh`).

## Acceptance checklist for a real 32-bit Windows 10 PC

1. `systeminfo | findstr /C:"System Type"` → `X86-based PC`.
2. Unzip the portable package, run `LumoBrowser.exe` (policy permitting) → start page appears.
3. Task Manager → Details → *Platform* column shows **32-bit** for all `LumoBrowser.exe` processes; Chromium's
   `chrome://sandbox` is not exposed, but `freedom.log` contains `sandbox=on`.
4. Open a site, a second tab, a private tab, download a file, bookmark, check history (private site absent).
5. Run the installer without admin rights, launch from the Start menu, uninstall from *Apps & features*.
