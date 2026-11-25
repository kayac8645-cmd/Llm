# Building Freedom Browser

## Toolchain (pinned, verified 2026-11-25)

| Component | Version |
|---|---|
| Gradle (wrapper) | 9.9.2 |
| Android Gradle Plugin | 9.5.2 (built-in Kotlin) |
| Kotlin / KSP | 2.4.21 / 2.3.14 |
| Compose BOM | 2026.11.01 (requires compileSdk 37.1) |
| Room / DataStore / androidx.webkit | 2.8.5 / 1.3.0 / 1.18.0 |
| OkHttp | 5.5.1 |
| JDK used for the build | 21 (bytecode target 17) |
| Android SDK | platform `android-37.1`, build-tools 36.0.0 |
| CEF (Windows) | 158.0.6+g23f1736+chromium-158.0.8127.89, **windows32** minimal distribution |
| MinGW-w64 | i686-w64-mingw32-gcc 13 (win32 threads) |
| NSIS | 3.09 |

All versions live in `gradle/libs.versions.toml`, `scripts/build-windows.sh` and `windows/native-host/Makefile`.

## Android

```bash
export ANDROID_HOME=/path/to/android-sdk        # needs platforms;android-37.1 and build-tools;36.0.0
./gradlew :shared:jvmTest :androidApp:testDebugUnitTest
./gradlew :androidApp:assembleDebug             # androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :androidApp:assembleRelease           # R8-minified; unsigned unless a keystore is configured
```

### Release signing

Release signing is configured only through environment variables, so no key ever lives in the repository:

```bash
export FREEDOM_KEYSTORE=/secure/path/freedom.jks FREEDOM_KEYSTORE_PASSWORD=... FREEDOM_KEY_ALIAS=freedom FREEDOM_KEY_PASSWORD=...
./gradlew :androidApp:assembleRelease
```

The `FreedomBrowser-1.0.0-release.apk` produced in this delivery was signed with a throw-away **sideload test key**
(`CN=Freedom Browser sideload test key`, SHA-256 `81f5b2e7…5de5`). Android only installs updates signed with the same
key, so for a store or long-term distribution create your own key and rebuild.

### Maven Central rate limits

If `repo.maven.apache.org` answers HTTP 429, route Maven Central through a mirror without editing files:

```bash
./gradlew -PmavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2/ ...
# or: export MAVEN_CENTRAL_MIRROR=...
```

## Windows x86 (from Linux)

```bash
sudo apt install gcc-mingw-w64-i686 binutils-mingw-w64-i686 nsis zip curl python3 file
scripts/build-windows.sh          # add --skip-gradle to reuse an existing shared JS build
```

The script:
1. downloads `cef_binary_158.0.6+g23f1736+chromium-158.0.8127.89_windows32_minimal.tar.bz2` from the official CEF CDN and
   checks its SHA-1 (`d8baa8eb…489c`);
2. compiles the shared Kotlin module to JavaScript and smoke-tests the facade with Node;
3. builds `FreedomBrowser.dll` (bootstrap/sandbox mode) and `freedom-compat.exe` with `i686-w64-mingw32-gcc`, and runs the
   native block-list test against the shared vectors;
4. stages the app (CEF's `bootstrap.exe` renamed to `FreedomBrowser.exe`, our DLL, CEF runtime, UI, licenses);
5. verifies **every** `.exe`/`.dll` is `PE32` + `Intel 80386` (`file` and `objdump`) → `dist/windows-x86-verification.txt`;
6. writes `dist/FreedomBrowser-1.0.0-windows-x86-portable.zip` and `dist/FreedomBrowser-1.0.0-windows-x86-setup.exe`.

### On Windows (MSYS2)

Install `mingw-w64-i686-gcc`, `make`, NSIS and a JDK, then run the same steps manually:
`make -C windows/native-host CEF_DIR=C:/path/to/cef_binary_..._windows32_minimal` and `makensis` with
`-DSTAGE=... -DOUTFILE=... -DVERSION=1.0.0 windows/installer/freedom.nsi`.

### End-to-end test under Wine (optional)

```bash
WINEPREFIX=~/.wine-freedom windows/tests/e2e/run-wine-e2e.sh dist/stage/FreedomBrowser dist/e2e-wine
```

Requires Wine ≥ 9 with 32-bit support, Xvfb, xdotool and ImageMagick. Wine is not Windows; see the compatibility report.

## Continuous integration

`.github/workflows/freedom-browser.yml` (repository root) runs both pipelines on every push touching
`freedom-browser/**` and uploads the APKs, the Windows ZIP/installer and the verification report as workflow artifacts.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `checkDebugAarMetadata` asks for compileSdk 37 | install `platforms;android-37.1` |
| HTTP 429 from Maven Central | use `-PmavenCentralMirror=…` (above) |
| `windows.h: No such file` | `CC` must be the MinGW cross compiler (`make CROSS=i686-w64-mingw32-`) |
| Windows: app does not start | try `freedom-compat.exe` (no sandbox) and check `profile\freedom.log`; start with `--freedom-debug-log` for Chromium's log |
| Windows: blank window on old GPUs | start with `--disable-gpu` |
| Android: private tabs disabled | update *Android System WebView* (needs the multi-profile feature) |
