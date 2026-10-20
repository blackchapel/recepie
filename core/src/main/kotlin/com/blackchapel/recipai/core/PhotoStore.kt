package com.blackchapel.recipai.core

import java.io.File
import java.util.UUID

/**
 * Recipe photos on disk: one JPEG per photo in app-private storage
 * (`filesDir/photos/<uuid>.jpg`). `Recipe.photos` holds the file names.
 * Plain java.io, so it lives in :core and is unit-tested on the JVM; the
 * Android side (decoding and downscaling a picked photo, showing one) is in
 * `platform/photos`.
 */
class PhotoStore(val dir: File) {
    /** The file for a stored photo, or null for a name this app never writes. */
    fun file(name: String): File? = if (Photos.isPhotoName(name)) File(dir, name) else null

    fun exists(name: String): Boolean = file(name)?.isFile == true

    /**
     * A fresh name and the temp file to write it to. Call [commit] once the
     * temp file is complete; a half-written temp file is cleaned up later.
     */
    fun newPhoto(): Pair<String, File> {
        dir.mkdirs()
        val name = Photos.nameFor(UUID.randomUUID().toString())
        return name to File(dir, "$name.tmp")
    }

    /** Moves a finished temp file into place. False if that failed (the temp file is removed). */
    fun commit(name: String, temp: File): Boolean {
        val target = file(name) ?: return false
        val ok = temp.renameTo(target)
        if (!ok) temp.delete()
        return ok
    }

    /** Deletes one stored photo now (e.g. an import that could not finish). */
    fun delete(name: String) {
        file(name)?.delete()
    }

    /** Every file in the folder, with its last-modified time. */
    fun stored(): List<StoredFile> =
        dir.listFiles()?.filter { it.isFile }?.map { StoredFile(it.name, it.lastModified()) } ?: emptyList()

    /**
     * Deletes photos no recipe in [referenced] uses (and leftover temp
     * files), keeping anything newer than [Photos.CLEANUP_GRACE_MS]. Returns
     * how many files were deleted.
     */
    fun cleanUp(referenced: Set<String>, now: Long = System.currentTimeMillis()): Int =
        Photos.unreferenced(stored(), referenced, now).count { File(dir, it).delete() }

    companion object {
        /** The photo folder's name inside the app's files directory. */
        const val FOLDER = "photos"

        fun inFilesDir(filesDir: File): PhotoStore = PhotoStore(File(filesDir, FOLDER))
    }
}
