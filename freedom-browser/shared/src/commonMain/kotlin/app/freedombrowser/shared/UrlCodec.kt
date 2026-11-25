package app.freedombrowser.shared

/** Percent-encoding helpers that behave identically on every platform (UTF-8 based). */
object UrlCodec {
    private const val HEX = "0123456789ABCDEF"
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"

    /** Encodes a query value like `encodeURIComponent`. Spaces become `%20`. */
    fun encodeQueryComponent(value: String): String = encode(value, UNRESERVED)

    /**
     * Encodes characters that are not allowed to appear literally in a URL (spaces, non-ASCII,
     * control characters, quotes, angle brackets) while preserving reserved URL syntax and any
     * existing `%XX` escapes.
     */
    fun encodeUnsafe(url: String): String {
        val sb = StringBuilder(url.length + 16)
        val bytes = url.encodeToByteArray()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            val c = b.toChar()
            val safe = b in 0x21..0x7E && c != '"' && c != '<' && c != '>' && c != '\\' &&
                c != '^' && c != '`' && c != '{' && c != '|' && c != '}'
            if (safe) sb.append(c) else appendEscaped(sb, b)
            i++
        }
        return sb.toString()
    }

    /** Decodes `%XX` escapes (UTF-8). Malformed escapes are kept literally. */
    fun decode(value: String): String {
        if (value.indexOf('%') < 0) return value
        val out = ByteArrayBuilder()
        var i = 0
        var runStart = 0
        while (i < value.length) {
            if (value[i] == '%' && i + 2 < value.length && isHex(value[i + 1]) && isHex(value[i + 2])) {
                out.append(value.substring(runStart, i).encodeToByteArray())
                out.append(((hexVal(value[i + 1]) shl 4) or hexVal(value[i + 2])).toByte())
                i += 3
                runStart = i
            } else {
                i++
            }
        }
        out.append(value.substring(runStart).encodeToByteArray())
        return out.toByteArray().decodeToString()
    }

    private class ByteArrayBuilder {
        private var buf = ByteArray(64)
        private var size = 0
        fun append(b: Byte) { ensure(1); buf[size++] = b }
        fun append(bytes: ByteArray) { ensure(bytes.size); bytes.copyInto(buf, size); size += bytes.size }
        private fun ensure(n: Int) { if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n)) }
        fun toByteArray() = buf.copyOf(size)
    }

    private fun encode(value: String, allowed: String): String {
        val sb = StringBuilder(value.length * 3)
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            if (b < 0x80 && allowed.indexOf(b.toChar()) >= 0) sb.append(b.toChar()) else appendEscaped(sb, b)
        }
        return sb.toString()
    }

    private fun appendEscaped(sb: StringBuilder, b: Int) {
        sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    private fun hexVal(c: Char) = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        else -> c - 'A' + 10
    }
}
