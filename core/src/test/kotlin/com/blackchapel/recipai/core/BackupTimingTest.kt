package com.blackchapel.recipai.core

import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTimingTest {
    private val zone = ZoneId.of("Europe/London")

    // 2026-10-20 is a Tuesday.
    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 10) = ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    @Test
    fun dailyIsTodayOrTomorrow() {
        assertEquals(at(20, 2), BackupTiming.nextRun(at(20, 1, 30), BackupFrequency.DAILY, 7, 2))
        assertEquals(at(21, 2), BackupTiming.nextRun(at(20, 2), BackupFrequency.DAILY, 7, 2))
        assertEquals(at(21, 2), BackupTiming.nextRun(at(20, 15), BackupFrequency.DAILY, 7, 2))
    }

    @Test
    fun weeklyIsTheNextChosenDay() {
        // Sunday 25th.
        assertEquals(at(25, 20), BackupTiming.nextRun(at(20, 9), BackupFrequency.WEEKLY, 7, 20))
        // Tuesday, later today.
        assertEquals(at(20, 20), BackupTiming.nextRun(at(20, 9), BackupFrequency.WEEKLY, 2, 20))
        // Tuesday, already past: next week.
        assertEquals(at(27, 8), BackupTiming.nextRun(at(20, 9), BackupFrequency.WEEKLY, 2, 8))
    }

    @Test
    fun manualHasNoNextRun() {
        assertNull(BackupTiming.nextRun(at(20, 9), BackupFrequency.MANUAL, 7, 2))
        assertNull(BackupTiming.periodHours(BackupFrequency.MANUAL))
        assertEquals(168L, BackupTiming.periodHours(BackupFrequency.WEEKLY))
    }

    @Test
    fun aTimeSkippedByDaylightSavingMovesForward() {
        // UK clocks go forward at 1:00 on 29 March 2026; 1:30 doesn't exist.
        val next = BackupTiming.nextRun(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone), BackupFrequency.DAILY, 7, 1)!!
        assertEquals(29, next.dayOfMonth)
        assertEquals(2, next.hour)
    }

    @Test
    fun signatureChangesWithTheSchedule() {
        val a = BackupTiming.signature(BackupFrequency.WEEKLY, 7, 2, true)
        assertTrue(a != BackupTiming.signature(BackupFrequency.WEEKLY, 1, 2, true))
        assertTrue(a != BackupTiming.signature(BackupFrequency.WEEKLY, 7, 2, false))
        assertEquals("manual", BackupTiming.signature(BackupFrequency.MANUAL, 1, 8, false))
    }

    @Test
    fun scheduledSignaturesAreVersionedSoOldPhonesReRegisterOnce() {
        // Earlier builds scheduled a periodic job under "daily|7|20|wifi"; the chained
        // one-time jobs use a new signature, so the app registers them after the update.
        val now = BackupTiming.signature(BackupFrequency.DAILY, 7, 20, true)
        assertTrue(now.startsWith("v2|"))
        assertTrue(now != "daily|7|20|wifi")
    }

    @Test
    fun aRunDelayedPastItsTimeStillAimsTheNextOneAtTheChosenHour() {
        // The 8 PM run actually started at 9:15 PM (waiting for Wi-Fi, say): the next is
        // tomorrow at 8 PM, not 24 hours after 9:15.
        val late = ZonedDateTime.of(2026, 10, 20, 21, 15, 0, 0, zone)
        val next = BackupTiming.nextRun(late, BackupFrequency.DAILY, 7, 20)!!
        assertEquals(ZonedDateTime.of(2026, 10, 21, 20, 0, 0, 0, zone), next)
        // Weekly keeps its weekday too (20 Oct 2026 is a Tuesday).
        val weekly = BackupTiming.nextRun(late, BackupFrequency.WEEKLY, 2, 20)!!
        assertEquals(ZonedDateTime.of(2026, 10, 27, 20, 0, 0, 0, zone), weekly)
    }
}

class BackupTextTest {
    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 10, 20, 15, 0, 0, 0, zone)

    @Test
    fun whenTextReadsLikeThePrototype() {
        assertEquals("Today at 2:00 AM", BackupText.whenText(now.withHour(2), now))
        assertEquals("Yesterday at 9:05 PM", BackupText.whenText(now.minusDays(1).withHour(21).withMinute(5), now))
        assertEquals("Tomorrow at 2:00 AM", BackupText.whenText(now.plusDays(1).withHour(2), now))
        assertEquals("Sunday at 8:00 PM", BackupText.whenText(now.plusDays(5).withHour(20), now))
        assertEquals("Oct 3 at 3:00 PM", BackupText.whenText(now.withDayOfMonth(3), now))
        assertEquals("Dec 30, 2025 at 3:00 PM", BackupText.whenText(now.withYear(2025).withMonth(12).withDayOfMonth(30), now))
    }

    @Test
    fun whenTextInsideASentence() {
        assertEquals("today at 2:00 AM", BackupText.inSentence("Today at 2:00 AM"))
        assertEquals("yesterday at 9:05 PM", BackupText.inSentence("Yesterday at 9:05 PM"))
        assertEquals("Sunday at 8:00 PM", BackupText.inSentence("Sunday at 8:00 PM"))
        assertEquals("Oct 3 at 3:00 PM", BackupText.inSentence("Oct 3 at 3:00 PM"))
    }

    @Test
    fun agoText() {
        val t = 1_000_000_000L
        assertEquals("just now", BackupText.ago(t, t + 10_000))
        assertEquals("1 minute ago", BackupText.ago(t, t + 60_000))
        assertEquals("5 minutes ago", BackupText.ago(t, t + 5 * 60_000))
        assertEquals("1 hour ago", BackupText.ago(t, t + 3_600_000))
        assertEquals("3 hours ago", BackupText.ago(t, t + 3 * 3_600_000))
        assertEquals("yesterday", BackupText.ago(t, t + 26 * 3_600_000L))
        assertEquals("3 days ago", BackupText.ago(t, t + 3 * 86_400_000L))
        assertEquals("just now", BackupText.ago(t + 5000, t), "a clock that went back")
    }

    @Test
    fun bytes() {
        assertEquals("512 B", BackupText.bytes(512))
        assertEquals("34 KB", BackupText.bytes(34 * 1024))
        assertEquals("1,023 KB", BackupText.bytes(1023 * 1024))
        assertEquals("1.2 MB", BackupText.bytes(1_250_000))
        assertEquals("3 MB", BackupText.bytes(3L * 1024 * 1024))
        assertEquals("2.5 GB", BackupText.bytes(5L * 512 * 1024 * 1024))
    }

    @Test
    fun timesAndDays() {
        assertEquals("Overnight (2:00 AM)", BackupText.timeOfDay(2))
        assertEquals("Evening (8:00 PM)", BackupText.timeOfDay(20))
        assertEquals("12:00 PM", BackupText.timeOfDay(12))
        assertEquals("Sun", BackupText.weekdayShort(7))
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), BackupTiming.WEEK.map(BackupText::weekdayShort))
    }

    @Test
    fun entryDetail() {
        val e = BackupEntry("id", "n", 0, 1_250_000, recipes = 12, photos = 1, kind = BackupKind.AUTOMATIC)
        assertEquals("12 recipes · 1 photo · 1.2 MB · Automatic", BackupText.entryDetail(e))
        assertEquals("1 recipe · no photos · 10 B · Before restore", BackupText.entryDetail(BackupEntry("i", "n", 0, 10, 1, 0, BackupKind.BEFORE_RESTORE, includesPhotos = false)))
        assertEquals("10 B · Manual", BackupText.entryDetail(BackupEntry("i", "n", 0, 10)))
    }

    @Test
    fun settingsSubtitle() {
        val t = 1_000_000_000L
        fun sub(connected: Boolean = true, reconnect: Boolean = false, label: String? = null, last: Long? = null, unsaved: Boolean = false) =
            BackupText.settingsSubtitle(connected, reconnect, label, 0.42f, last, unsaved, t + 7_200_000)
        assertEquals("Not connected", sub(connected = false))
        assertEquals("Uploading to Google Drive · 42%", sub(label = "Uploading to Google Drive"))
        assertEquals("Connected · no backup yet", sub())
        assertEquals("Backed up 2 hours ago", sub(last = t))
        assertEquals("Changes since 2 hours ago", sub(last = t, unsaved = true))
        assertEquals("Sign in again to keep backing up", sub(reconnect = true, last = t))
    }
}

class BackupStatusStoreTest {
    @Test
    fun persistsAndNotifies() {
        val dir: File = Files.createTempDirectory("status").toFile()
        try {
            val file = File(dir, "backup.json")
            val store = BackupStatusStore(file)
            assertEquals(BackupStatus(), store.read())
            val heard = ArrayList<BackupStatus>()
            val stop = store.listen { heard += it }
            store.update { it.copy(connected = true, accountEmail = "a@b.c") }
            stop()
            store.update { it.copy(lastBackupAt = 5) }
            assertEquals(1, heard.size)
            assertEquals(BackupStatus(connected = true, accountEmail = "a@b.c", lastBackupAt = 5), BackupStatusStore(file).read())
            assertTrue(!file.readText().contains("token"))

            file.writeText("{broken")
            assertEquals(BackupStatus(), BackupStatusStore(file).read())
        } finally {
            dir.deleteRecursively()
        }
    }
}
