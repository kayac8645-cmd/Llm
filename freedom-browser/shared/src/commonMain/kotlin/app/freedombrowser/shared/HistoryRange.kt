package app.freedombrowser.shared

/** Time ranges offered by "Clear browsing data". */
enum class HistoryRange(val id: String, private val durationMillis: Long?) {
    LAST_HOUR("hour", 3_600_000L),
    LAST_24_HOURS("day", 86_400_000L),
    LAST_7_DAYS("week", 7 * 86_400_000L),
    LAST_4_WEEKS("month", 28 * 86_400_000L),
    ALL_TIME("all", null);

    /** Entries with a timestamp >= the returned value are removed. */
    fun cutoff(nowMillis: Long): Long = durationMillis?.let { nowMillis - it } ?: 0L

    companion object {
        fun fromId(id: String?): HistoryRange = entries.firstOrNull { it.id == id } ?: LAST_HOUR
    }
}

/** Case-insensitive history/bookmark search over title and URL; all terms must match. */
object TextSearch {
    fun matches(query: String, vararg fields: String?): Boolean {
        val terms = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return true
        val haystack = fields.filterNotNull().joinToString(" ").lowercase()
        return terms.all { haystack.contains(it) }
    }
}
