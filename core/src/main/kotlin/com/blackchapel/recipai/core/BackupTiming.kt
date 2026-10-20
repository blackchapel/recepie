package com.blackchapel.recipai.core

import java.time.ZonedDateTime

/** When automatic backups run (see [BackupTiming.nextRun]). */
enum class BackupFrequency { DAILY, WEEKLY, MANUAL }

/** Scheduling math for automatic backups. Days are ISO (1 = Monday ... 7 = Sunday). */
object BackupTiming {
    /** The times of day the Backup page offers, as in the prototype: hour to label. */
    val TIMES: List<Pair<Int, String>> = listOf(2 to "Overnight", 8 to "Morning", 20 to "Evening")

    /** The weekday picker's order (Sunday first, as in the prototype) as ISO days. */
    val WEEK: List<Int> = listOf(7, 1, 2, 3, 4, 5, 6)

    /**
     * The next time a backup is due, strictly after [now]: today or
     * tomorrow at [hour]:00 for daily, the next [isoWeekday] at [hour]:00
     * for weekly, null for manual. A time that doesn't exist (a DST gap)
     * moves forward, as [ZonedDateTime] does.
     */
    fun nextRun(now: ZonedDateTime, frequency: BackupFrequency, isoWeekday: Int, hour: Int): ZonedDateTime? {
        if (frequency == BackupFrequency.MANUAL) return null
        val h = hour.coerceIn(0, 23)
        val today = now.toLocalDate()
        return when (frequency) {
            BackupFrequency.DAILY -> {
                val t = today.atTime(h, 0).atZone(now.zone)
                if (t.isAfter(now)) t else today.plusDays(1).atTime(h, 0).atZone(now.zone)
            }
            else -> {
                val day = isoWeekday.coerceIn(1, 7)
                val ahead = ((day - today.dayOfWeek.value) + 7) % 7
                val t = today.plusDays(ahead.toLong()).atTime(h, 0).atZone(now.zone)
                if (t.isAfter(now)) t else today.plusDays(ahead + 7L).atTime(h, 0).atZone(now.zone)
            }
        }
    }

    /** How often background work repeats for [frequency], in hours (null for manual). */
    fun periodHours(frequency: BackupFrequency): Long? = when (frequency) {
        BackupFrequency.DAILY -> 24
        BackupFrequency.WEEKLY -> 24 * 7
        BackupFrequency.MANUAL -> null
    }

    /**
     * A string that changes whenever the schedule does, so the app only
     * re-registers background work when something actually changed.
     */
    fun signature(frequency: BackupFrequency, isoWeekday: Int, hour: Int, wifiOnly: Boolean): String =
        if (frequency == BackupFrequency.MANUAL) "manual" else "${frequency.name.lowercase()}|$isoWeekday|$hour|${if (wifiOnly) "wifi" else "any"}"
}
