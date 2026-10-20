package com.blackchapel.recipai.core

import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/** User-facing text for the Backup page and the Settings row (English, like the rest of the app). */
object BackupText {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val DATE_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun zoned(epochMillis: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(zone)

    /** "2:00 AM". */
    fun hour(hour: Int): String = ZonedDateTime.of(2000, 1, 1, hour.coerceIn(0, 23), 0, 0, 0, ZoneId.of("UTC")).format(TIME)

    /** "Overnight (2:00 AM)", or just "3:00 PM" for an hour that isn't one of [BackupTiming.TIMES]. */
    fun timeOfDay(hour: Int): String {
        val label = BackupTiming.TIMES.firstOrNull { it.first == hour }?.second
        return if (label != null) "$label (${hour(hour)})" else hour(hour)
    }

    /** "Sun", "Mon" ... for an ISO day. */
    fun weekdayShort(isoDay: Int): String = DayOfWeek.of(isoDay.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, Locale.US)

    /** "Today at 2:00 AM", "Yesterday at ...", "Tomorrow at ...", "Friday at ..." (the coming week), "Oct 3 at ...". */
    fun whenText(at: ZonedDateTime, now: ZonedDateTime): String {
        val local = at.withZoneSameInstant(now.zone)
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), local.toLocalDate())
        val time = local.format(TIME)
        val day = when {
            days == 0L -> "Today"
            days == -1L -> "Yesterday"
            days == 1L -> "Tomorrow"
            days in 2..6 -> local.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
            local.year == now.year -> local.format(DATE)
            else -> local.format(DATE_YEAR)
        }
        return "$day at $time"
    }

    /** "just now", "5 minutes ago", "1 hour ago", "yesterday", "3 days ago". */
    fun ago(thenMillis: Long, nowMillis: Long): String {
        val s = ((nowMillis - thenMillis) / 1000.0).roundToLong().coerceAtLeast(0)
        return when {
            s < 45 -> "just now"
            s < 3600 -> plural((s / 60.0).roundToLong().coerceAtLeast(1), "minute") + " ago"
            s < 86400 -> plural((s / 3600.0).roundToLong().coerceAtLeast(1), "hour") + " ago"
            else -> {
                val d = (s / 86400.0).roundToLong()
                if (d <= 1) "yesterday" else "$d days ago"
            }
        }
    }

    /** "512 B", "34 KB", "1.2 MB", "2.4 GB". */
    fun bytes(b: Long): String {
        val nf = NumberFormat.getNumberInstance(Locale.US)
        return when {
            b < 1024 -> "$b B"
            b < 1024L * 1024 -> nf.apply { maximumFractionDigits = 0 }.format(b / 1024.0) + " KB"
            b < 1024L * 1024 * 1024 -> nf.apply { maximumFractionDigits = 1 }.format(b / (1024.0 * 1024)) + " MB"
            else -> nf.apply { maximumFractionDigits = 1 }.format(b / (1024.0 * 1024 * 1024)) + " GB"
        }
    }

    fun plural(n: Long, word: String): String = "$n ${if (n == 1L) word else word + "s"}"

    fun plural(n: Int, word: String): String = plural(n.toLong(), word)

    /** "12 recipes · 3 photos · 1.2 MB · Manual" for a row in the backup list. */
    fun entryDetail(e: BackupEntry): String = buildList {
        e.recipes?.let { add(plural(it, "recipe")) }
        if (e.includesPhotos) e.photos?.let { add(plural(it, "photo")) } else add("no photos")
        add(bytes(e.bytes))
        add(BackupKind.label(e.kind))
    }.joinToString(" · ")

    /**
     * The Google Drive row's subtitle in Settings: progress while running,
     * else connection and how fresh the last backup is.
     */
    fun settingsSubtitle(
        connected: Boolean,
        needsReconnect: Boolean,
        runningLabel: String?,
        runningFraction: Float,
        lastBackupAt: Long?,
        unsaved: Boolean,
        nowMillis: Long,
    ): String = when {
        runningLabel != null -> "$runningLabel · ${(runningFraction * 100).toInt()}%"
        !connected -> "Not connected"
        needsReconnect -> "Sign in again to keep backing up"
        lastBackupAt == null -> "Connected · no backup yet"
        unsaved -> "Changes since ${ago(lastBackupAt, nowMillis)}"
        else -> "Backed up ${ago(lastBackupAt, nowMillis)}"
    }
}
