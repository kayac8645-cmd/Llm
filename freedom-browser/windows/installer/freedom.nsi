; Freedom Browser - Windows x86 installer (NSIS 3, 32-bit stub).
; Per-user installation (no administrator rights needed) into %LOCALAPPDATA%\Programs\Freedom Browser.
; Build: makensis -DSTAGE=<staged app dir> -DOUTFILE=<setup.exe> -DVERSION=1.0.0 freedom.nsi

Unicode true
SetCompressor /SOLID lzma
RequestExecutionLevel user
ManifestDPIAware true
Target x86-unicode

!include "MUI2.nsh"
!include "WinVer.nsh"
!include "x64.nsh"

!ifndef VERSION
  !define VERSION "1.0.0"
!endif
!define APPNAME "Freedom Browser"
!define UNINSTKEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\FreedomBrowser"

Name "${APPNAME}"
OutFile "${OUTFILE}"
InstallDir "$LOCALAPPDATA\Programs\Freedom Browser"
InstallDirRegKey HKCU "Software\FreedomBrowser" "InstallDir"
BrandingText "${APPNAME} ${VERSION} (x86)"
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APPNAME}"
VIAddVersionKey "FileDescription" "${APPNAME} installer (Windows x86)"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "MIT License; Chromium/CEF under BSD-style licenses"

!define MUI_ICON "..\native-host\freedom.ico"
!define MUI_UNICON "..\native-host\freedom.ico"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\FreedomBrowser.exe"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${STAGE}\LICENSE.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "Turkish"

LangString MinOs ${LANG_ENGLISH} "Freedom Browser requires Windows 10 or later."
LangString MinOs ${LANG_TURKISH} "Freedom Browser Windows 10 veya daha yenisini gerektirir."
LangString Wow64 ${LANG_ENGLISH} "This is the 32-bit (x86) edition. It also runs on 64-bit Windows, but a 64-bit browser is recommended there. Continue?"
LangString Wow64 ${LANG_TURKISH} "Bu 32 bit (x86) sürümdür. 64 bit Windows'ta da çalışır ancak orada 64 bit bir tarayıcı önerilir. Devam edilsin mi?"
LangString RemoveData ${LANG_ENGLISH} "Also delete your browsing data (history, bookmarks, settings)?"
LangString RemoveData ${LANG_TURKISH} "Tarama verileriniz de (geçmiş, yer imleri, ayarlar) silinsin mi?"

Function .onInit
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_ICONSTOP "$(MinOs)" /SD IDOK
    Abort
  ${EndIf}
  ${If} ${RunningX64}
    MessageBox MB_YESNO|MB_ICONINFORMATION "$(Wow64)" /SD IDYES IDYES +2
    Abort
  ${EndIf}
FunctionEnd

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "${STAGE}\*.*"
  Delete "$INSTDIR\portable.flag" ; installed edition keeps data in %LOCALAPPDATA%
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\Freedom Browser"
  CreateShortCut "$SMPROGRAMS\Freedom Browser\Freedom Browser.lnk" "$INSTDIR\FreedomBrowser.exe" "" "$INSTDIR\FreedomBrowser.dll" 0
  CreateShortCut "$SMPROGRAMS\Freedom Browser\Uninstall Freedom Browser.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortCut "$DESKTOP\Freedom Browser.lnk" "$INSTDIR\FreedomBrowser.exe" "" "$INSTDIR\FreedomBrowser.dll" 0
  WriteRegStr HKCU "Software\FreedomBrowser" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayName" "${APPNAME}"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${UNINSTKEY}" "Publisher" "Freedom Browser project"
  WriteRegStr HKCU "${UNINSTKEY}" "DisplayIcon" "$INSTDIR\FreedomBrowser.dll,0"
  WriteRegStr HKCU "${UNINSTKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTKEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  RMDir /r "$INSTDIR"
  Delete "$SMPROGRAMS\Freedom Browser\*.lnk"
  RMDir "$SMPROGRAMS\Freedom Browser"
  Delete "$DESKTOP\Freedom Browser.lnk"
  DeleteRegKey HKCU "${UNINSTKEY}"
  DeleteRegKey HKCU "Software\FreedomBrowser"
  MessageBox MB_YESNO|MB_ICONQUESTION "$(RemoveData)" /SD IDNO IDNO +2
    RMDir /r "$LOCALAPPDATA\FreedomBrowser"
SectionEnd
