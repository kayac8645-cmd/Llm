package app.freedombrowser.shared

import java.net.IDN

actual fun idnToAscii(host: String): String? = try {
    IDN.toASCII(host, IDN.ALLOW_UNASSIGNED)
} catch (_: IllegalArgumentException) {
    null
}

actual fun idnToUnicode(host: String): String = try {
    IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
} catch (_: IllegalArgumentException) {
    host
}
