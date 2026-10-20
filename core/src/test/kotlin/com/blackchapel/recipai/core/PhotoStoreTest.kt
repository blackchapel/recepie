package com.blackchapel.recipai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PhotoStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = PhotoStore.inFilesDir(tmp.root)

    private fun PhotoStore.add(bytes: String = "jpeg"): String {
        val (name, temp) = newPhoto()
        temp.writeText(bytes)
        assertTrue(commit(name, temp))
        return name
    }

    @Test
    fun newPhotosLandInThePhotoFolder() {
        val s = store()
        val name = s.add()
        assertTrue(Photos.isPhotoName(name))
        assertEquals(File(tmp.root, "photos/$name"), s.file(name))
        assertTrue(s.exists(name))
        assertFalse(File(s.dir, "$name.tmp").exists())
    }

    @Test
    fun foreignNamesAreNeverResolved() {
        val s = store()
        assertNull(s.file("../recipes.json"))
        assertNull(s.file("a/b.jpg"))
        assertFalse(s.exists("../recipes.json"))
        assertNotNull(s.file(Photos.nameFor("0f8fad5b-d9cb-469f-a165-70867728950e")))
    }

    @Test
    fun cleanUpDeletesOnlyOldUnreferencedPhotos() {
        val s = store()
        val kept = s.add()
        val orphan = s.add()
        val fresh = s.add()
        val foreign = File(s.dir, "keep-me.txt").apply { writeText("x") }
        val (halfName, half) = s.newPhoto()
        half.writeText("partial")
        val now = System.currentTimeMillis() + 2 * Photos.CLEANUP_GRACE_MS
        File(s.dir, fresh).setLastModified(now - 1_000)

        val deleted = s.cleanUp(referenced = setOf(kept), now = now)

        assertEquals(2, deleted)
        assertTrue(s.exists(kept))
        assertFalse(s.exists(orphan))
        assertTrue(s.exists(fresh))
        assertTrue(foreign.exists())
        assertFalse(half.exists())
        assertFalse(s.exists(halfName))
    }

    @Test
    fun cleanUpOnAMissingFolderDoesNothing() {
        assertEquals(0, PhotoStore(File(tmp.root, "nope")).cleanUp(emptySet()))
    }
}
