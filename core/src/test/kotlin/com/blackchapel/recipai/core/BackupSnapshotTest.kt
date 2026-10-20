package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupSnapshotTest {
    private fun snapshot() = BackupSnapshot(
        app = "recipai",
        version = 1,
        createdAt = 1_790_000_000_000,
        appVersion = "0.2.0",
        kind = BackupKind.AUTOMATIC,
        recipes = SampleRecipes.all(0),
        settings = BackupSettings(theme = "DARK", haptics = true, backupSchedule = "WEEKLY", backupWeekday = 3),
        photos = listOf(BackupPhoto("a.jpg", 1234)),
        includesPhotos = true,
    )

    @Test
    fun roundTrips() {
        assertEquals(snapshot(), BackupFormat.decode(BackupFormat.encode(snapshot())))
    }

    @Test
    fun theVersionIsWrittenFromDayOne() {
        val text = BackupFormat.encode(snapshot())
        assertTrue(text.startsWith("{\"app\":\"recipai\",\"version\":1,"), text.take(60))
    }

    @Test
    fun readsVersionOneAsWrittenByThisApp() {
        // A fixed version-1 file: if this stops parsing, older backups stop restoring.
        val v1 = """{"app":"recipai","version":1,"createdAt":5,"appVersion":"0.2","kind":"manual",
            "recipes":[{"id":"r","title":"Tea","servings":1,"photos":["p.jpg"]}],
            "settings":{"theme":"LIGHT"},"photos":[{"name":"p.jpg","bytes":3}],"includesPhotos":true}"""
        val s = BackupFormat.decode(v1)
        assertEquals("Tea", s.recipes.single().title)
        assertEquals(listOf("p.jpg"), s.recipes.single().photos)
        assertEquals("LIGHT", s.settings.theme)
        assertNull(s.settings.haptics)
        assertEquals(1, s.photoCount)
    }

    @Test
    fun ignoresFieldsFromNewerMinorVersions() {
        val text = BackupFormat.encode(snapshot()).replaceFirst("{", "{\"futureField\":[1,2],")
        assertEquals(snapshot(), BackupFormat.decode(text))
    }

    @Test
    fun refusesANewerFormat() {
        val text = BackupFormat.encode(snapshot().copy(version = BackupFormat.VERSION + 1))
        val e = assertFailsWith<BackupFormatException> { BackupFormat.decode(text) }
        assertTrue(e.message!!.contains("newer version"))
    }

    @Test
    fun refusesOtherFiles() {
        assertFailsWith<BackupFormatException> { BackupFormat.decode("""{"hello":1}""") }
        assertFailsWith<BackupFormatException> { BackupFormat.decode("not json") }
        assertFailsWith<BackupFormatException> { BackupFormat.decode(BackupFormat.encode(snapshot().copy(app = "other"))) }
    }

    @Test
    fun kindLabels() {
        assertEquals("Manual", BackupKind.label(BackupKind.MANUAL))
        assertEquals("Automatic", BackupKind.label(BackupKind.AUTOMATIC))
        assertEquals("Before restore", BackupKind.label(BackupKind.BEFORE_RESTORE))
        assertEquals("Manual", BackupKind.label("something-new"))
    }
}
