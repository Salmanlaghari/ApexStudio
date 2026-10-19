package com.apexstudio.app.data.picker

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.apexstudio.app.domain.model.ClipType
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPickerImageMetadataTest {
    @Test
    fun `generic image provider without MediaStore columns still yields image metadata`() {
        val context = RuntimeEnvironment.getApplication()
        val imageFile = File(context.cacheDir, "provider-image.png")
        val bitmap = Bitmap.createBitmap(41, 23, Bitmap.Config.ARGB_8888)
        try {
            imageFile.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        StrictImageProvider.imageFile = imageFile
        StrictImageProvider.unsupportedProjectionQueries = 0
        StrictImageProvider.streamOpens = 0
        val provider = Robolectric.buildContentProvider(StrictImageProvider::class.java)
            .create(ProviderInfo().apply {
                authority = AUTHORITY
                exported = false
                grantUriPermissions = true
            })
            .get()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        val uri = Uri.parse("content://$AUTHORITY/document/photo-1")

        val extract = MediaPickerHelper::class.java
            .getDeclaredMethod("extractMetadata", Uri::class.java)
            .apply { isAccessible = true }
        val result = extract.invoke(MediaPickerHelper(context), uri) as? MediaMetadata

        assertNotNull("valid provider-backed PNG must not be dropped", result)
        assertEquals(uri.toString(), result!!.uri)
        assertEquals("provider-image.png", result.name)
        assertEquals(ClipType.IMAGE, result.type)
        assertEquals(MediaPickerHelper.DEFAULT_IMAGE_DURATION_MS, result.durationMs)
        assertEquals(41, result.width)
        assertEquals(23, result.height)
        assertEquals("image extraction must not ask for MediaStore video columns", 0,
            StrictImageProvider.unsupportedProjectionQueries)
        assertEquals("image dimensions are read from the provider stream", 1,
            StrictImageProvider.streamOpens)

        imageFile.delete()
    }

    private companion object {
        const val AUTHORITY = "test.image-provider"
    }

    /** Supports only the Documents/Openable contract, not MediaStore metadata. */
    class StrictImageProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String = "image/png"

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            if (projection?.contentEquals(arrayOf(OpenableColumns.DISPLAY_NAME)) != true) {
                unsupportedProjectionQueries++
                throw IllegalArgumentException("Only OpenableColumns.DISPLAY_NAME is supported")
            }
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf("provider-image.png"))
            }
        }

        @Throws(FileNotFoundException::class)
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            streamOpens++
            return ParcelFileDescriptor.open(imageFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? =
            throw UnsupportedOperationException()

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException()

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = throw UnsupportedOperationException()

        companion object {
            lateinit var imageFile: File
            var unsupportedProjectionQueries = 0
            var streamOpens = 0
        }
    }
}
