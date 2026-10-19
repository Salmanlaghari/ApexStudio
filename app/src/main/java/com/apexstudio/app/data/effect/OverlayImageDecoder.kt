package com.apexstudio.app.data.effect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream

/** Two independent, closed streams: never buffer an entire compressed photo in the GL process. */
internal object OverlayImageDecoder {
    fun decode(context: Context, source: String, maxDimension: Int = 512): Bitmap {
        val uri = Uri.parse(source)
        return decode(maxDimension) {
            when (uri.scheme) {
                null, "" -> File(source).inputStream()
                "file" -> File(uri.path ?: error("Missing overlay file path")).inputStream()
                else -> context.contentResolver.openInputStream(uri)
            }
        }
    }

    internal fun sampleSize(width: Int, height: Int, maxDimension: Int): Int {
        require(width > 0 && height > 0 && maxDimension > 0) { "Invalid overlay dimensions" }
        var sample = 1
        // Ceiling division accounts for decoder rounding on odd source sizes; Long avoids overflow.
        while ((width.toLong() + sample - 1) / sample > maxDimension ||
            (height.toLong() + sample - 1) / sample > maxDimension) {
            require(sample <= Int.MAX_VALUE / 2) { "Overlay dimensions exceed sample limits" }
            sample *= 2
        }
        return sample
    }

    internal fun decode(maxDimension: Int = 512, open: () -> InputStream?): Bitmap {
        require(maxDimension > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: error("Cannot open overlay image")).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = (open() ?: error("Cannot reopen overlay image")).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Cannot decode overlay image")
        // Enforce the limit even for codecs which do not honor inSampleSize exactly.
        if (decoded.width <= maxDimension && decoded.height <= maxDimension) return decoded
        return try {
            val ratio = maxDimension.toDouble() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1),
                (decoded.height * ratio).toInt().coerceAtLeast(1), true)
        } finally {
            decoded.recycle()
        }
    }
}
