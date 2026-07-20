package com.circumspace.contactstr.data.blob

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a picked image into a small, square, JPEG avatar suitable for an encrypted Blossom blob:
 * downscaled to [SIZE]×[SIZE], center-cropped, and re-encoded so the ciphertext stays well under
 * NIP-44's 64 KB budget (a 256² JPEG is ~10–20 KB). EXIF orientation is applied so portrait photos
 * don't upload sideways.
 */
object ImageProcessing {
    const val SIZE = 256
    private const val QUALITY = 85
    const val MIME = "image/jpeg"

    /** Decode → orient → center-crop-square → downscale → JPEG bytes. Null if the image can't be read. */
    fun process(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver

        // Bounds-only decode to pick an inSampleSize, so we never load a huge image fully into memory.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, SIZE)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val oriented = applyExifOrientation(context, uri, decoded)
        val square = centerCropSquare(oriented)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)

        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            out.toByteArray()
        }
    }

    /** Largest power-of-two subsample that keeps the shorter side ≥ [target]. */
    private fun sampleSizeFor(w: Int, h: Int, target: Int): Int {
        var sample = 1
        var shorter = min(w, h)
        while (shorter / 2 >= target) {
            shorter /= 2
            sample *= 2
        }
        return sample
    }

    private fun centerCropSquare(bmp: Bitmap): Bitmap {
        val side = min(bmp.width, bmp.height)
        val x = (bmp.width - side) / 2
        val y = (bmp.height - side) / 2
        return if (side == bmp.width && side == bmp.height) bmp else Bitmap.createBitmap(bmp, x, y, side, side)
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    @Suppress("unused")
    private fun clamp(v: Int, lo: Int, hi: Int) = max(lo, min(hi, v))
}
