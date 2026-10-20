package com.blackchapel.recipai.core

import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupEngineTest {
    private val root: File = Files.createTempDirectory("engine").toFile()
    private val photoDir = File(root, "photos")
    private val workDir = File(root, "cache")
    private val drive = FakeDrive()
    private val engine = BackupEngine(DriveClient(drive, "token", sleeper = {}), workDir, photoDir)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private val photoA = "0b6c7a4e-1f2d-4c3b-9a8e-112233445566.jpg"
    private val photoB = "9f8e7d6c-5b4a-4321-8765-abcdefabcdef.jpg"

    private fun recipes() = listOf(
        Recipe(id = "r1", title = "Soup", servings = 2, photos = listOf(photoA, photoB), coverIndex = 1),
        Recipe(id = "r2", title = "Toast", servings = 1),
    )

    private fun snapshot(at: Long, includePhotos: Boolean = true, kind: String = BackupKind.MANUAL, recipes: List<Recipe> = recipes()) =
        BackupEngine.snapshot(recipes, BackupSettings(theme = "DARK"), kind, includePhotos, "0.2", at, photoDir)

    private fun writePhotos() {
        photoDir.mkdirs()
        File(photoDir, photoA).writeBytes(Random(1).nextBytes(5000))
        File(photoDir, photoB).writeBytes(Random(2).nextBytes(7000))
    }

    @Test
    fun aBackupLandsInTheFolderWithItsDetails() {
        writePhotos()
        val folder = engine.folder(null)
        val labels = ArrayList<String>()
        val entry = engine.backUp(folder, snapshot(1_000L)) { labels += it.label }
        assertEquals("recipai-backup-19700101-000001.zip", entry.name)
        assertEquals(2, entry.recipes)
        assertEquals(2, entry.photos)
        assertEquals(1_000L, entry.createdAt)
        assertTrue(entry.bytes > 12_000)
        assertEquals(listOf(entry), engine.list(folder))
        assertTrue("Packing photos 2 of 2" in labels)
        assertEquals("Checking the backup", labels.last())
        assertTrue(workDir.listFiles()!!.isEmpty(), "the temp archive is deleted")
    }

    @Test
    fun theFolderIsReusedAndRecreatedWhenGone() {
        val first = engine.folder(null)
        assertEquals(first, engine.folder(first))
        assertEquals(first, engine.folder("stale-id"))
        drive.files.getValue(first).trashed = true
        val second = engine.folder(first)
        assertTrue(second != first)
    }

    @Test
    fun keepsOnlyTheNewestSix() {
        val folder = engine.folder(null)
        repeat(8) { i -> engine.backUp(folder, snapshot(1_000L * (i + 1))) }
        val left = engine.list(folder)
        assertEquals(6, left.size)
        assertEquals((8 downTo 3).map { 1_000L * it }, left.map { it.createdAt })
    }

    @Test
    fun aCorruptedUploadIsRemovedAndReported() {
        val folder = engine.folder(null)
        drive.corruptChecksums = true
        val e = assertFailsWith<DriveException> { engine.backUp(folder, snapshot(1_000L)) }
        assertTrue(e.message!!.contains("intact"))
        assertTrue(drive.backups().isEmpty())
    }

    @Test
    fun restoreBringsBackRecipesAndPhotos() {
        writePhotos()
        val folder = engine.folder(null)
        val entry = engine.backUp(folder, snapshot(5_000L))
        val originalA = File(photoDir, photoA).readBytes()
        photoDir.deleteRecursively()

        val (archive, snap) = engine.download(entry)
        assertEquals(2, snap.recipes.size)
        val restored = engine.restore(archive)
        archive.delete()
        assertEquals(recipes(), restored.recipes)
        assertEquals(0, restored.missingPhotos)
        assertContentEquals(originalA, File(photoDir, photoA).readBytes())
        assertEquals("DARK", restored.snapshot.settings.theme)
        assertTrue(photoDir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun aBackupWithoutPhotosKeepsPhotosThatAreStillOnThePhone() {
        writePhotos()
        val folder = engine.folder(null)
        val entry = engine.backUp(folder, snapshot(5_000L, includePhotos = false))
        assertEquals(false, entry.includesPhotos)
        assertEquals(0, entry.photos)
        File(photoDir, photoB).delete()

        val (archive, _) = engine.download(entry)
        val restored = engine.restore(archive)
        val soup = restored.recipes.first()
        assertEquals(listOf(photoA), soup.photos)
        assertEquals(0, soup.coverIndex)
        assertEquals(1, restored.missingPhotos)
    }

    @Test
    fun coverFollowsItsPhotoWhenOthersAreMissing() {
        val r = Recipe(id = "r", title = "R", servings = 1, photos = listOf("a.jpg", "b.jpg", "c.jpg"), coverIndex = 2)
        val (out, missing) = BackupEngine.withAvailablePhotos(listOf(r)) { it != "a.jpg" }
        assertEquals(listOf("b.jpg", "c.jpg"), out.single().photos)
        assertEquals(1, out.single().coverIndex)
        assertEquals(1, missing)
    }

    @Test
    fun listsOnlyRecipaiBackups() {
        assertEquals(null, BackupEngine.entryOf(DriveFile(id = "x", name = "notes.txt")))
        val bare = BackupEngine.entryOf(DriveFile(id = "y", name = "recipai-backup-1.zip", size = "10", createdTime = "2026-10-20T02:00:00.000Z"))!!
        assertEquals(java.time.Instant.parse("2026-10-20T02:00:00Z").toEpochMilli(), bare.createdAt)
        assertEquals(null, bare.recipes)
        assertEquals(10L, bare.bytes)
    }

    @Test
    fun theFingerprintChangesWithTheRecipes() {
        val a = BackupFingerprint.of(recipes())
        assertEquals(a, BackupFingerprint.of(recipes()))
        assertTrue(a != BackupFingerprint.of(recipes().drop(1)))
        assertEquals(64, a.length)
    }
}
