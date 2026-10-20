package com.blackchapel.recipai.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotosTest {
    private val a = "0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
    private val b = "7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg"
    private val c = "16fd2706-8baf-433b-82eb-8c7fada847da.jpg"

    @Test fun namesAreUuidJpegsOnly() {
        assertEquals(a, Photos.nameFor("0F8FAD5B-D9CB-469F-A165-70867728950E"))
        assertTrue(Photos.isPhotoName(a))
        assertFalse(Photos.isPhotoName("../recipes.json"))
        assertFalse(Photos.isPhotoName("photos/$a"))
        assertFalse(Photos.isPhotoName("$a.tmp"))
        assertFalse(Photos.isPhotoName(""))
    }

    @Test fun coverIsClamped() {
        assertEquals(0, Photos.clampCover(0, 3))
        assertEquals(1, Photos.clampCover(2, 5))
        assertEquals(0, Photos.clampCover(2, -1))
        assertNull(PhotoSet(emptyList(), 0).coverName)
        assertEquals(b, PhotoSet(listOf(a, b), 7).coverName)
    }

    @Test fun addingKeepsTheCover() {
        assertEquals(PhotoSet(listOf(a, b), 0), Photos.add(PhotoSet(emptyList(), 0), listOf(a, b)))
        assertEquals(PhotoSet(listOf(a, b, c), 1), Photos.add(PhotoSet(listOf(a, b), 1), listOf(c)))
        // Duplicates are ignored.
        assertEquals(PhotoSet(listOf(a, b), 0), Photos.add(PhotoSet(listOf(a), 0), listOf(a, b, b)))
    }

    @Test fun removingKeepsTheSameCoverPhoto() {
        val set = PhotoSet(listOf(a, b, c), 2)
        // Before the cover: the cover moves down one place.
        assertEquals(PhotoSet(listOf(b, c), 1), Photos.remove(set, 0))
        // After the cover: unchanged.
        assertEquals(PhotoSet(listOf(a, b), 0), Photos.remove(PhotoSet(listOf(a, b, c), 0), 2))
        // The cover itself: the first remaining photo takes over.
        assertEquals(PhotoSet(listOf(a, b), 0), Photos.remove(set, 2))
        // The last photo.
        assertEquals(PhotoSet(emptyList(), 0), Photos.remove(PhotoSet(listOf(a), 0), 0))
        // Out of range: nothing happens.
        assertEquals(set, Photos.remove(set, 3))
    }

    @Test fun settingTheCover() {
        assertEquals(PhotoSet(listOf(a, b), 1), Photos.setCover(PhotoSet(listOf(a, b), 0), 1))
        assertEquals(PhotoSet(listOf(a, b), 0), Photos.setCover(PhotoSet(listOf(a, b), 0), 2))
    }

    @Test fun recipeHelpers() {
        val r = Recipe(id = "r", title = "T", servings = 2, photos = listOf(a, b), coverIndex = 1)
        assertEquals(b, r.coverPhoto)
        assertNull(r.copy(photos = emptyList()).coverPhoto)
        val updated = r.withPhotos(PhotoSet(listOf(c), 4))
        assertEquals(listOf(c), updated.photos)
        assertEquals(0, updated.coverIndex)
    }

    @Test fun photosSurviveTheRecipeFile() {
        val r = Recipe(id = "r", title = "T", servings = 2, photos = listOf(a, b), coverIndex = 1)
        assertEquals(listOf(r), RecipeJson.decode(RecipeJson.encode(listOf(r))))
        // Files written before photos existed read as "no photos".
        val old = RecipeJson.decode(RecipeJson.encode(listOf(r.copy(photos = emptyList(), coverIndex = 0))))
        assertEquals(emptyList(), old.single().photos)
    }

    @Test fun sampleSizeNeverDropsBelowTheTarget() {
        assertEquals(1, Photos.sampleSize(1200, 800))
        assertEquals(1, Photos.sampleSize(4000, 3000)) // halving would give 2000 < 2048
        assertEquals(2, Photos.sampleSize(4096, 3072))
        assertEquals(4, Photos.sampleSize(12000, 9000))
        assertEquals(4, Photos.sampleSize(9000, 12000))
        assertEquals(1, Photos.sampleSize(0, 0))
    }

    @Test fun fitWithinOnlyShrinks() {
        assertEquals(800 to 600, Photos.fitWithin(800, 600))
        assertEquals(2048 to 1536, Photos.fitWithin(4000, 3000))
        assertEquals(1536 to 2048, Photos.fitWithin(3000, 4000))
        assertEquals(2048 to 1, Photos.fitWithin(10000, 2))
        assertEquals(100 to 100, Photos.fitWithin(100, 100, maxEdge = 100))
    }

    /** Applies [u] to a point the way Matrix.setRotate(deg) + postScale(-1, 1) does. */
    private fun apply(u: Upright, x: Int, y: Int): Pair<Int, Int> {
        val (rx, ry) = when (u.rotateDegrees) {
            90 -> -y to x
            180 -> -x to -y
            270 -> y to -x
            else -> x to y
        }
        return if (u.mirror) -rx to ry else rx to ry
    }

    @Test fun exifOrientationsMatchTheirDefinitions() {
        // A point right of center (1, 0) and one below it (0, 1).
        assertEquals((1 to 0) to (0 to 1), apply(Photos.upright(1), 1, 0) to apply(Photos.upright(1), 0, 1))
        assertEquals(-1 to 0, apply(Photos.upright(2), 1, 0)) // mirror left-right
        assertEquals(0 to 1, apply(Photos.upright(2), 0, 1))
        assertEquals(-1 to 0, apply(Photos.upright(3), 1, 0)) // 180
        assertEquals(0 to -1, apply(Photos.upright(3), 0, 1))
        assertEquals(1 to 0, apply(Photos.upright(4), 1, 0)) // mirror top-bottom
        assertEquals(0 to -1, apply(Photos.upright(4), 0, 1))
        assertEquals(0 to 1, apply(Photos.upright(5), 1, 0)) // transpose: (x, y) -> (y, x)
        assertEquals(1 to 0, apply(Photos.upright(5), 0, 1))
        assertEquals(0 to 1, apply(Photos.upright(6), 1, 0)) // 90 clockwise
        assertEquals(-1 to 0, apply(Photos.upright(6), 0, 1))
        assertEquals(0 to -1, apply(Photos.upright(7), 1, 0)) // transverse: (x, y) -> (-y, -x)
        assertEquals(-1 to 0, apply(Photos.upright(7), 0, 1))
        assertEquals(0 to -1, apply(Photos.upright(8), 1, 0)) // 270 clockwise
        assertEquals(1 to 0, apply(Photos.upright(8), 0, 1))
        assertEquals(Upright(0, false), Photos.upright(0))
        assertEquals(Upright(0, false), Photos.upright(42))
    }

    @Test fun rotatedSizesSwap() {
        assertEquals(3000 to 4000, Photos.upright(6).size(4000, 3000))
        assertEquals(4000 to 3000, Photos.upright(3).size(4000, 3000))
    }

    @Test fun cleanupKeepsReferencedRecentAndForeignFiles() {
        val now = 10 * Photos.CLEANUP_GRACE_MS
        val old = now - Photos.CLEANUP_GRACE_MS
        val stored = listOf(
            StoredFile(a, old), // referenced
            StoredFile(b, old), // orphan
            StoredFile(c, now - 1000), // orphan, but too new
            StoredFile("$a.tmp", old), // leftover temp file
            StoredFile("notes.txt", old), // not ours
        )
        assertEquals(listOf(b, "$a.tmp"), Photos.unreferenced(stored, setOf(a), now))
    }
}
