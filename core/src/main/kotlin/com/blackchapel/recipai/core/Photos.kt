package com.blackchapel.recipai.core

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A recipe's photos and which one is the cover. [photos] are file names in
 * the app's private photo folder (see [Photos.isPhotoName]).
 */
data class PhotoSet(val photos: List<String>, val cover: Int) {
    /** The cover's name, or null when there are no photos. */
    val coverName: String? get() = photos.getOrNull(Photos.clampCover(photos.size, cover))
}

/** What came back from the photo picker after copying into app storage. */
data class PhotoImport(val names: List<String>, val failed: Int) {
    val added: Int get() = names.size
}

/** How to turn a decoded image upright: rotate clockwise, then mirror left-right. */
data class Upright(val rotateDegrees: Int, val mirror: Boolean) {
    /** Width and height after rotating a [w] x [h] image. */
    fun size(w: Int, h: Int): Pair<Int, Int> = if (rotateDegrees % 180 == 0) w to h else h to w
}

/** A file in the photo folder, for [Photos.unreferenced]. */
data class StoredFile(val name: String, val modifiedAt: Long)

/**
 * Pure photo rules (the photos module): file names, cover bookkeeping when
 * photos are added or removed, downscaling sizes and EXIF orientation, and
 * which stored files nothing references any more.
 */
object Photos {
    /** Longest edge a stored photo keeps. */
    const val MAX_EDGE = 2048

    /** The most photos added in one pick. */
    const val MAX_PICK = 10

    /** Photo files younger than this are never cleaned up (a pick may be in flight). */
    const val CLEANUP_GRACE_MS = 60L * 60 * 1000

    private val NAME = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg""")
    private val TEMP = Regex("""[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg(\.restore)?\.tmp""")

    /** The stored name for a new photo with this random UUID. */
    fun nameFor(uuid: String): String = "${uuid.lowercase()}.jpg"

    /**
     * True for names this app writes (`<uuid>.jpg`). Anything else (a path,
     * `..`, a name from a damaged or hand-edited file) is never opened or deleted.
     */
    fun isPhotoName(name: String): Boolean = NAME.matches(name)

    /** A cover index that is valid for [count] photos (0 when there are none). */
    fun clampCover(count: Int, cover: Int): Int = if (count <= 0) 0 else cover.coerceIn(0, count - 1)

    /** Appends [names]; the cover stays the same photo (the first one when there was none). */
    fun add(set: PhotoSet, names: List<String>): PhotoSet {
        val fresh = names.filter { it !in set.photos }.distinct()
        return PhotoSet(set.photos + fresh, clampCover(set.photos.size, set.cover))
    }

    /**
     * Removes the photo at [index]. The cover stays the same photo; when the
     * cover itself goes, the first remaining photo becomes the cover.
     */
    fun remove(set: PhotoSet, index: Int): PhotoSet {
        if (index !in set.photos.indices) return set
        val photos = set.photos.filterIndexed { i, _ -> i != index }
        val cover = clampCover(set.photos.size, set.cover)
        val newCover = when {
            index == cover -> 0
            index < cover -> cover - 1
            else -> cover
        }
        return PhotoSet(photos, clampCover(photos.size, newCover))
    }

    /** Makes the photo at [index] the cover (ignored when out of range). */
    fun setCover(set: PhotoSet, index: Int): PhotoSet =
        if (index in set.photos.indices) set.copy(cover = index) else set

    /**
     * The largest power-of-two decode sample size that still leaves the
     * longest edge at least [maxEdge] (so the final resize only ever shrinks).
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Int {
        val longest = max(width, height)
        var s = 1
        while (longest / (s * 2) >= maxEdge) s *= 2
        return s
    }

    /** [width] x [height] scaled down to fit [maxEdge] on the longest edge, never up. */
    fun fitWithin(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longest = max(width, height)
        if (longest <= maxEdge || longest <= 0) return width to height
        val k = maxEdge.toDouble() / longest
        return max(1, (width * k).roundToInt()) to max(1, (height * k).roundToInt())
    }

    /**
     * The EXIF orientation tag (1-8) as a rotation followed by a mirror,
     * the same order Android's Matrix uses with setRotate then postScale(-1, 1).
     * Unknown values mean "already upright".
     */
    fun upright(exifOrientation: Int): Upright = when (exifOrientation) {
        2 -> Upright(0, true) // flip horizontal
        3 -> Upright(180, false)
        4 -> Upright(180, true) // flip vertical
        5 -> Upright(90, true) // transpose
        6 -> Upright(90, false)
        7 -> Upright(270, true) // transverse
        8 -> Upright(270, false)
        else -> Upright(0, false)
    }

    /**
     * Stored files no recipe references and old enough to be safe to delete:
     * photos removed in the editor, recipes deleted earlier, edits that were
     * cancelled, and half-written temp files. Only names this app writes
     * are ever returned.
     */
    fun unreferenced(
        stored: Collection<StoredFile>,
        referenced: Set<String>,
        now: Long,
        graceMs: Long = CLEANUP_GRACE_MS,
    ): List<String> = stored
        .filter { (isPhotoName(it.name) && it.name !in referenced) || TEMP.matches(it.name) }
        .filter { now - it.modifiedAt >= graceMs }
        .map { it.name }
}

/** This recipe's photos as a [PhotoSet]. */
val Recipe.photoSet: PhotoSet get() = PhotoSet(photos, coverIndex)

/** The cover photo's file name, or null when the recipe has no photos. */
val Recipe.coverPhoto: String? get() = photoSet.coverName

/** This recipe with [set] as its photos. */
fun Recipe.withPhotos(set: PhotoSet): Recipe =
    copy(photos = set.photos, coverIndex = Photos.clampCover(set.photos.size, set.cover))
