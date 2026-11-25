Freedom Browser 1.0.0 - Windows x86 (32-bit)
=============================================

Requirements
- Windows 10 (32-bit or 64-bit), version 1809 or later recommended.
  Windows 11 exists only as 64-bit; this 32-bit build also runs there (WOW64),
  but a 64-bit browser is the better choice on 64-bit Windows.
- Windows 7/8/8.1 are NOT supported (Chromium dropped them in version 110).
- About 450 MB free disk space; 2 GB RAM or more recommended.

Start
- FreedomBrowser.exe  : normal start, Chromium sandbox enabled (recommended).
- freedom-compat.exe  : same browser WITHOUT the Chromium sandbox. Only for
                        troubleshooting if FreedomBrowser.exe does not start
                        (for example on locked-down systems). Less secure.

Portable edition
- The file "portable.flag" next to the program makes Freedom keep all data in
  the "profile" folder beside it. Delete that folder to remove your data.
Installed edition
- Data lives in %LOCALAPPDATA%\FreedomBrowser\User Data.

Network
- Freedom uses the computer's ordinary internet connection. It has no VPN,
  proxy or tunnelling and cannot bypass school, company or network
  administrator restrictions, or blocks applied by websites.

Troubleshooting
- "Cannot start" with an error about the profile: another Freedom instance is
  running with the same profile; close it.
- Blank window on very old graphics drivers: start with
  FreedomBrowser.exe --disable-gpu
- Diagnostic log without page content: profile\freedom.log
  Chromium's own log: start with --freedom-debug-log (may contain URLs).

Licenses: LICENSE.txt, licenses\CEF-LICENSE.txt, CREDITS.html
