package app.freedombrowser.shared

import kotlin.math.abs
import kotlin.math.roundToLong

object Formatters {
    private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

    /** 1536 -> "1.5 KB" (binary multiples, one decimal above KB). Negative/unknown -> "—". */
    fun bytes(value: Long): String {
        if (value < 0) return "—"
        var v = value.toDouble()
        var unit = 0
        while (v >= 1024 && unit < UNITS.lastIndex) { v /= 1024; unit++ }
        return if (unit == 0) "$value B" else "${oneDecimal(v)} ${UNITS[unit]}"
    }

    fun speed(bytesPerSecond: Long): String = if (bytesPerSecond <= 0) "" else bytes(bytesPerSecond) + "/s"

    /** Remaining time as "1 h 3 min", "4 min", "12 s"; empty when unknown. */
    fun eta(remainingBytes: Long, bytesPerSecond: Long): String {
        if (remainingBytes <= 0 || bytesPerSecond <= 0) return ""
        val secs = remainingBytes / bytesPerSecond
        return when {
            secs >= 3600 -> "${secs / 3600} h ${(secs % 3600) / 60} min"
            secs >= 60 -> "${secs / 60} min"
            else -> "$secs s"
        }
    }

    fun percent(done: Long, total: Long): Int = if (total <= 0) -1 else ((done * 100) / total).toInt().coerceIn(0, 100)

    private fun oneDecimal(v: Double): String {
        val tenths = (v * 10).roundToLong()
        val whole = tenths / 10
        val frac = abs(tenths % 10)
        return if (frac == 0L) "$whole" else "$whole.$frac"
    }
}

/** Exponentially smoothed transfer-speed estimator. */
class SpeedMeter(private val smoothing: Double = 0.3) {
    private var lastBytes = -1L
    private var lastTime = 0L
    var bytesPerSecond: Long = 0
        private set

    fun update(totalBytes: Long, nowMillis: Long): Long {
        if (lastBytes >= 0 && nowMillis > lastTime) {
            val instant = ((totalBytes - lastBytes) * 1000.0 / (nowMillis - lastTime)).coerceAtLeast(0.0)
            bytesPerSecond = if (bytesPerSecond == 0L) instant.toLong() else (smoothing * instant + (1 - smoothing) * bytesPerSecond).toLong()
        }
        lastBytes = totalBytes
        lastTime = nowMillis
        return bytesPerSecond
    }
}
