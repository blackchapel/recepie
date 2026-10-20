package com.blackchapel.recipai.core

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupArchiveTest {
    private val root: File = Files.createTempDirectory("archive").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun snapshot(photos: List<BackupPhoto> = emptyList(), recipes: List<Recipe> = SampleRecipes.all(0)) = BackupSnapshot(
        app = "recipai",
        version = BackupFormat.VERSION,
        createdAt = 42L,
        appVersion = "0.2",
        kind = BackupKind.MANUAL,
        recipes = recipes,
        settings = BackupSettings(theme = "LIGHT", haptics = false),
        photos = photos,
        includesPhotos = true,
    )

    private fun zip(name: String, entries: Map<String, ByteArray>): File {
        val f = File(root, name)
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (k, v) ->
                z.putNextEntry(ZipEntry(k))
                z.write(v)
                z.closeEntry()
            }
        }
        return f
    }

    @Test
    fun writesTheManifestFirstThenPhotos() {
        val src = File(root, "src").apply { mkdirs() }
        File(src, "a.jpg").writeBytes(ByteArray(100) { it.toByte() })
        val out = File(root, "b.zip")
        val progress = ArrayList<Pair<Int, Int>>()
        out.outputStream().use { BackupArchive.write(it, snapshot(listOf(BackupPhoto("a.jpg", 100))), { File(src, it) }) { d, t -> progress += d to t } }
        ZipFile(out).use { z ->
            assertEquals(listOf("backup.json", "photos/a.jpg"), z.entries().toList().map { it.name })
        }
        assertEquals(listOf(1 to 1), progress)
        assertEquals(snapshot(listOf(BackupPhoto("a.jpg", 100))), BackupArchive.readSnapshot(out))
    }

    @Test
    fun extractsPhotosAndSkipsOnesAlreadyThere() {
        val manifest = BackupFormat.encode(snapshot(listOf(BackupPhoto("a.jpg", 3), BackupPhoto("b.jpg", 3))))
        val archive = zip("x.zip", mapOf("backup.json" to manifest.toByteArray(), "photos/a.jpg" to byteArrayOf(1, 2, 3), "photos/b.jpg" to byteArrayOf(4, 5, 6)))
        val dest = File(root, "photos").apply { mkdirs() }
        File(dest, "b.jpg").writeBytes(byteArrayOf(9))
        val out = BackupArchive.extract(archive, dest)
        assertEquals(setOf("a.jpg", "b.jpg"), out.photos)
        assertEquals(listOf<Byte>(1, 2, 3), File(dest, "a.jpg").readBytes().toList())
        assertEquals(listOf<Byte>(9), File(dest, "b.jpg").readBytes().toList(), "an existing photo is not overwritten")
    }

    @Test
    fun neverWritesOutsideThePhotoFolder() {
        val evil = listOf(BackupPhoto("../../evil.jpg", 1), BackupPhoto("/etc/passwd", 1), BackupPhoto(".hidden", 1), BackupPhoto("a/b.jpg", 1))
        val manifest = BackupFormat.encode(snapshot(evil))
        val archive = zip("x.zip", mapOf("backup.json" to manifest.toByteArray(), "photos/../../evil.jpg" to byteArrayOf(1)))
        val dest = File(root, "deep/photos").apply { mkdirs() }
        val out = BackupArchive.extract(archive, dest)
        assertTrue(out.photos.isEmpty())
        assertFalse(File(root, "evil.jpg").exists())
        assertTrue(dest.listFiles()!!.isEmpty())
        evil.forEach { assertFalse(BackupArchive.isSafePhotoName(it.name), it.name) }
        assertTrue(BackupArchive.isSafePhotoName("0b6c7a4e-1f2d-4c3b-9a8e-112233445566.jpg"))
    }

    @Test
    fun aListedPhotoMissingFromTheZipIsSkipped() {
        val manifest = BackupFormat.encode(snapshot(listOf(BackupPhoto("gone.jpg", 3))))
        val archive = zip("x.zip", mapOf("backup.json" to manifest.toByteArray()))
        val out = BackupArchive.extract(archive, File(root, "p"))
        assertTrue(out.photos.isEmpty())
    }

    @Test
    fun rejectsFilesThatAreNotBackups() {
        val notZip = File(root, "n.zip").apply { writeText("hello") }
        assertFailsWith<BackupFormatException> { BackupArchive.readSnapshot(notZip) }
        val noManifest = zip("m.zip", mapOf("other.txt" to byteArrayOf(1)))
        assertFailsWith<BackupFormatException> { BackupArchive.readSnapshot(noManifest) }
        val badJson = zip("j.zip", mapOf("backup.json" to "{nope".toByteArray()))
        assertFailsWith<BackupFormatException> { BackupArchive.readSnapshot(badJson) }
    }
}
