package com.blackchapel.recipai.platform.photos

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.blackchapel.recipai.core.Durable
import com.blackchapel.recipai.core.PhotoImport
import com.blackchapel.recipai.core.PhotoStore
import com.blackchapel.recipai.core.Photos
import java.io.FileOutputStream
import java.io.IOException

/**
 * Copies picked images into the photo folder: decoded with a power-of-two
 * sample size, turned upright from the EXIF orientation, scaled down to
 * [Photos.MAX_EDGE] on the longest edge and saved as JPEG. Blocking; call
 * it off the main thread. One photo at a time keeps memory bounded.
 */
internal object PhotoImporter {
    private const val JPEG_QUALITY = 88

    fun importAll(context: Context, store: PhotoStore, uris: List<Uri>): PhotoImport {
        val names = ArrayList<String>(uris.size)
        var failed = 0
        for (uri in uris) {
            val name = importOne(context.contentResolver, store, uri)
            if (name != null) names += name else failed++
        }
        return PhotoImport(names, failed)
    }

    private fun importOne(resolver: ContentResolver, store: PhotoStore, uri: Uri): String? {
        val (name, temp) = try {
            store.newPhoto()
        } catch (e: Exception) {
            return null
        }
        try {
            val bitmap = decodeUpright(resolver, uri) ?: return null
            try {
                FileOutputStream(temp).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) throw IOException("JPEG encoding failed")
                    // Synced before commit renames it into place: no zero-byte photo after a power loss.
                    Durable.sync(out)
                }
            } finally {
                bitmap.recycle()
            }
            return if (store.commit(name, temp)) name else null
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            // A huge or corrupt image: skip it rather than crash.
            return null
        } finally {
            // Gone after a successful commit; a leftover after any failure.
            if (temp.exists()) temp.delete()
        }
    }

    private fun decodeUpright(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val first = resolver.openInputStream(uri) ?: return null
        first.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = Photos.sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val second = resolver.openInputStream(uri) ?: return null
        val decoded = second.use { BitmapFactory.decodeStream(it, null, options) } ?: return null

        val upright = Photos.upright(readOrientation(resolver, uri))
        val (w, h) = Photos.fitWithin(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            setScale(w.toFloat() / decoded.width, h.toFloat() / decoded.height)
            postRotate(upright.rotateDegrees.toFloat())
            if (upright.mirror) postScale(-1f, 1f)
        }
        val transformed = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
        return onWhite(transformed)
    }

    /** JPEG has no transparency: put a transparent PNG on white instead of black. */
    private fun onWhite(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        bitmap.recycle()
        return out
    }

    private fun readOrientation(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
}
