package app.freedombrowser.shared

// Chromium's WHATWG URL parser performs UTS #46 processing (punycode) for us.
actual fun idnToAscii(host: String): String? = try {
    val h = host
    js("new URL('http://' + h + '/').hostname") as String
} catch (_: Throwable) {
    null
}

// Chromium does not expose ToUnicode to scripts; the Windows UI shows the ASCII form, which is
// also the safer choice against homograph spoofing.
actual fun idnToUnicode(host: String): String = host
