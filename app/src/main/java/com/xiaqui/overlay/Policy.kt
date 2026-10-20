package com.xiaqui.overlay

/** Android-independent decisions, also exercised on the JVM. */
object Policy {
    fun batteryPercent(level: Int, scale: Int): Int =
        if (level < 0 || scale <= 0) -1 else (100L * level / scale).coerceIn(0L, 100L).toInt()
    fun corners(defaultMask: Int, overrideMask: Int?): Int = (overrideMask ?: defaultMask) and 3
    fun windowTypes(sdk: Int, modern: Boolean): List<Int> = when {
        sdk < 26 -> listOf(2006)
        modern -> listOf(2038)
        else -> listOf(2006, 2038)
    }
    fun nextMinute(now: Long): Long = 60_000L - ((now % 60_000L + 60_000L) % 60_000L)
}
