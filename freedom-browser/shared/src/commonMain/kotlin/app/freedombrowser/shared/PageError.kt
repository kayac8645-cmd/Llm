package app.freedombrowser.shared

/** User-facing categories of page-load failures. UI layers map these to localised text. */
enum class PageErrorKind(val id: String) {
    OFFLINE("offline"),
    DNS("dns"),
    TIMEOUT("timeout"),
    CONNECTION("connection"),
    TLS("tls"),
    CERTIFICATE("certificate"),
    INVALID_URL("invalid_url"),
    UNSAFE("unsafe"),
    BLOCKED("blocked"),
    TOO_MANY_REDIRECTS("redirects"),
    RENDERER_CRASH("crash"),
    HTTP("http"),
    UNKNOWN("unknown");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}

object PageErrors {
    /** Maps a Chromium `net::Error` code (as reported by CEF) to a category. */
    fun fromChromiumNetError(code: Int): PageErrorKind = when (code) {
        -106 -> PageErrorKind.OFFLINE
        -105, -137, -800 -> PageErrorKind.DNS
        -7, -118 -> PageErrorKind.TIMEOUT
        -100, -101, -102, -104, -109, -21, -324, -15, -111, -130 -> PageErrorKind.CONNECTION
        -107, -113, -117, -123 -> PageErrorKind.TLS
        in -299..-200 -> PageErrorKind.CERTIFICATE
        -300, -301 -> PageErrorKind.INVALID_URL
        -20, -27 -> PageErrorKind.BLOCKED
        -310 -> PageErrorKind.TOO_MANY_REDIRECTS
        else -> PageErrorKind.UNKNOWN
    }

    /** Maps `android.webkit.WebViewClient.ERROR_*` codes to a category. */
    fun fromAndroidWebViewError(code: Int, isOnline: Boolean): PageErrorKind {
        if (!isOnline) return PageErrorKind.OFFLINE
        return when (code) {
            -2 -> PageErrorKind.DNS                // ERROR_HOST_LOOKUP
            -8 -> PageErrorKind.TIMEOUT            // ERROR_TIMEOUT
            -6, -7 -> PageErrorKind.CONNECTION     // ERROR_CONNECT, ERROR_IO
            -11 -> PageErrorKind.TLS               // ERROR_FAILED_SSL_HANDSHAKE
            -12, -10 -> PageErrorKind.INVALID_URL  // ERROR_BAD_URL, ERROR_UNSUPPORTED_SCHEME
            -9 -> PageErrorKind.TOO_MANY_REDIRECTS // ERROR_REDIRECT_LOOP
            -16 -> PageErrorKind.UNSAFE            // ERROR_UNSAFE_RESOURCE (Safe Browsing)
            else -> PageErrorKind.UNKNOWN
        }
    }

    /**
     * Text placed on the clipboard by "Copy error details". Contains no query string, cookies
     * or page content.
     */
    fun details(kind: PageErrorKind, code: Int, description: String, url: String, platform: String, appVersion: String): String =
        buildString {
            appendLine("Freedom Browser $appVersion ($platform)")
            appendLine("Error: ${kind.id} (code $code)")
            if (description.isNotBlank()) appendLine("Engine message: ${description.take(200)}")
            append("URL: ").append(DisplayUrl.redact(url))
        }
}
