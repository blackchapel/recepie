package com.blackchapel.recipai.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * One backup is one `.zip`: `backup.json` ([BackupSnapshot]) first, then
 * each photo under `photos/`. Plain java.util.zip so it is unit-tested on
 * the JVM and any unzip tool can open a backup.
 */
object BackupArchive {
    const val MANIFEST = "backup.json"
    const val PHOTO_DIR = "photos/"

    /** Refuse anything bigger when reading (a damaged or hostile archive). */
    const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024
    const val MAX_PHOTO_BYTES = 64L * 1024 * 1024

    private val SAFE_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")

    /**
     * A photo name that is safe to use as a file name in the photo folder:
     * no path separators, no `..`, nothing hidden.
     */
    fun isSafePhotoName(name: String): Boolean = SAFE_NAME.matches(name) && ".." !in name

    /**
     * Writes [snapshot] and its photos to [out]. [photoFile] finds each
     * photo listed in the snapshot; [onPhoto] reports progress after each
     * one (1-based index, total).
     */
    fun write(
        out: OutputStream,
        snapshot: BackupSnapshot,
        photoFile: (String) -> File?,
        onPhoto: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        ZipOutputStream(out).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(BackupFormat.encode(snapshot).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            // JPEGs are already compressed; deflating them again only costs time.
            zip.setLevel(Deflater.NO_COMPRESSION)
            snapshot.photos.forEachIndexed { i, photo ->
                require(isSafePhotoName(photo.name)) { "Unsafe photo name" }
                val file = photoFile(photo.name) ?: throw IOException("A photo is missing: ${photo.name}")
                zip.putNextEntry(ZipEntry(PHOTO_DIR + photo.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                onPhoto(i + 1, snapshot.photos.size)
            }
        }
    }

    /** Reads only `backup.json` from an archive file. */
    fun readSnapshot(archive: File): BackupSnapshot = openZip(archive).use { zip -> readSnapshot(zip) }

    /**
     * Reads [archive] and copies each listed photo into [photoDir] (through a
     * temp file, so a half-copied photo never appears). Photos already there
     * are left alone: names are unique per photo, so the same name means the
     * same picture. Returns the snapshot and the names now available.
     */
    fun extract(
        archive: File,
        photoDir: File,
        onPhoto: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Extracted = openZip(archive).use { zip ->
        val snapshot = readSnapshot(zip)
        val available = LinkedHashSet<String>()
        val listed = snapshot.photos.filter { isSafePhotoName(it.name) }
        if (listed.isNotEmpty()) photoDir.mkdirs()
        listed.forEachIndexed { i, photo ->
            val target = File(photoDir, photo.name)
            val entry = zip.getEntry(PHOTO_DIR + photo.name)
            if (target.isFile && target.length() > 0) {
                available += photo.name
            } else if (entry != null) {
                val tmp = File(photoDir, "${photo.name}.restore.tmp")
                try {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(tmp).use { out ->
                            copyCapped(input, out, MAX_PHOTO_BYTES)
                            // Synced before the rename: no zero-length photo after a power loss.
                            Durable.sync(out)
                        }
                    }
                    if (tmp.renameTo(target)) available += photo.name
                } finally {
                    tmp.delete()
                }
            }
            onPhoto(i + 1, listed.size)
        }
        Extracted(snapshot, available)
    }

    data class Extracted(val snapshot: BackupSnapshot, val photos: Set<String>)

    private fun openZip(archive: File): ZipFile = try {
        ZipFile(archive)
    } catch (e: ZipException) {
        throw BackupFormatException("This backup file is damaged.", e)
    }

    private fun readSnapshot(zip: ZipFile): BackupSnapshot {
        val entry = zip.getEntry(MANIFEST) ?: throw BackupFormatException("This isn't a Recipai backup.")
        val bytes = zip.getInputStream(entry).use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            copyCapped(input, buffer, MAX_MANIFEST_BYTES)
            buffer.toByteArray()
        }
        return BackupFormat.decode(String(bytes, Charsets.UTF_8))
    }

    /** Copies at most [limit] bytes; more than that means the archive is not what it claims. */
    private fun copyCapped(input: InputStream, out: OutputStream, limit: Long) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw BackupFormatException("This backup file is damaged.")
            out.write(buf, 0, n)
        }
    }
}
