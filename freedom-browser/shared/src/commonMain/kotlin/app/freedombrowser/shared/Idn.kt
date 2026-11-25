package app.freedombrowser.shared

/**
 * Converts an internationalized host name (e.g. `bücher.de`) to its ASCII/punycode form
 * (`xn--bcher-kva.de`). Returns null when the host cannot be converted.
 */
expect fun idnToAscii(host: String): String?

/** Converts a punycode host back to Unicode for display. Returns the input when conversion fails. */
expect fun idnToUnicode(host: String): String
