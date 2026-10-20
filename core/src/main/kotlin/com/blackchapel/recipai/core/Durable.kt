package com.blackchapel.recipai.core

import java.io.File
import java.io.FileOutputStream

/**
 * Writes that survive a power loss. Renaming a temp file over the real one
 * is atomic for the file's name, but on f2fs or ext4 the data blocks of the
 * temp file may not be on disk yet when the rename is: a battery pull right
 * after a save can leave the renamed file empty or truncated. Forcing the
 * data to disk first ([sync]) makes the rename publish a complete file.
 */
object Durable {
    /** Flushes [out] and forces its file's data to disk. Call before closing and renaming. */
    fun sync(out: FileOutputStream) {
        out.flush()
        out.fd.sync()
    }

    /** Writes [bytes] to [file] (replacing it) and forces them to disk before returning. */
    fun write(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { out ->
            out.write(bytes)
            sync(out)
        }
    }
}
