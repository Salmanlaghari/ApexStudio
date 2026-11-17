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
import com.apexstudio.app.data.media.MediaUriResolver
import com.apexstudio.app.domain.model.ClipType
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        StrictImageProvider.imageMimeType = "image/png"
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
        assertEquals(0, result.rotationDegrees)
        assertEquals(41f / 23f, result.sourceDisplayAspectRatio!!, 0.001f)
        assertNull(result.sourceVideoAspectRatio)
        assertEquals("image extraction must not ask for MediaStore video columns", 0,
            StrictImageProvider.unsupportedProjectionQueries)
        assertEquals("image dimensions and EXIF orientation use provider streams", 2,
            StrictImageProvider.streamOpens)

        imageFile.delete()
    }

    @Test
    fun `provider JPEG EXIF orientation determines portrait display aspect`() {
        val context = RuntimeEnvironment.getApplication()
        val imageFile = File(context.cacheDir, "provider-image-exif.jpg")
        val bitmap = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888)
        try {
            imageFile.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        android.media.ExifInterface(imageFile.absolutePath).apply {
            setAttribute(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_ROTATE_90.toString(),
            )
            saveAttributes()
        }
        StrictImageProvider.imageFile = imageFile
        StrictImageProvider.imageMimeType = "image/jpeg"
        StrictImageProvider.streamOpens = 0
        val provider = Robolectric.buildContentProvider(StrictImageProvider::class.java)
            .create(ProviderInfo().apply {
                authority = AUTHORITY
                exported = false
                grantUriPermissions = true
            })
            .get()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        val uri = Uri.parse("content://$AUTHORITY/document/rotated-photo")

        val extract = MediaPickerHelper::class.java
            .getDeclaredMethod("extractMetadata", Uri::class.java)
            .apply { isAccessible = true }
        val result = try {
            extract.invoke(MediaPickerHelper(context), uri) as? MediaMetadata
        } finally {
            imageFile.delete()
            StrictImageProvider.imageMimeType = "image/png"
        }

        assertNotNull("valid EXIF JPEG must be read from a generic provider", result)
        assertEquals(192, result!!.width)
        assertEquals(108, result.height)
        assertEquals(90, result.rotationDegrees)
        assertEquals(9f / 16f, result.sourceDisplayAspectRatio!!, 0.001f)
        assertEquals("dimensions and EXIF are read through separate provider streams", 2,
            StrictImageProvider.streamOpens)
    }

    @Test
    fun `picked content media is persisted before editor playback resolution`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val sourceFile = File(context.cacheDir, "picked-uri-source.png")
        val sourceBytes = byteArrayOf(13, 37, 42, 99)
        sourceFile.writeBytes(sourceBytes)
        StrictImageProvider.imageFile = sourceFile
        StrictImageProvider.imageMimeType = "image/png"
        val provider = Robolectric.buildContentProvider(StrictImageProvider::class.java)
            .create(ProviderInfo().apply {
                authority = AUTHORITY
                exported = false
                grantUriPermissions = true
            })
            .get()
        ShadowContentResolver.registerProviderInternal(AUTHORITY, provider)
        val sourceUri = Uri.parse("content://$AUTHORITY/document/durable-photo")
        val sourceMetadata = MediaMetadata(
            uri = sourceUri.toString(),
            name = "durable-photo.png",
            durationMs = MediaPickerHelper.DEFAULT_IMAGE_DURATION_MS,
            width = 41,
            height = 23,
            fps = 30,
            type = ClipType.IMAGE
        )
        var createdCopy: File? = null
        try {
            val imported = persistPickedMediaMetadata(context, sourceMetadata)
            val importedUri = Uri.parse(imported.uri)
            assertEquals("picked content media must be promoted to a file URI", "file", importedUri.scheme)
            val importedPath = checkNotNull(importedUri.path)
            val projectMediaDir = File(context.filesDir, "project_media").canonicalPath
            val persistedFile = File(importedPath)
            if (persistedFile.canonicalPath.startsWith(projectMediaDir + File.separator)) {
                createdCopy = persistedFile
            }

            assertTrue(
                "picked media must live in the cleanup-managed project_media directory",
                persistedFile.canonicalPath.startsWith(projectMediaDir + File.separator)
            )
            assertTrue(persistedFile.isFile)
            assertTrue("source fixture should be removable after the import copy is made", sourceFile.delete())
            assertArrayEquals(sourceBytes, persistedFile.readBytes())
            assertEquals(sourceMetadata.copy(uri = imported.uri), imported)
            assertEquals(importedUri, MediaUriResolver.resolvePlayableUri(context, imported.uri))
        } finally {
            createdCopy?.delete()
            sourceFile.delete()
            StrictImageProvider.imageMimeType = "image/png"
        }
    }

    @Test
    fun `still image display aspect honors EXIF rotation and falls back only when no video exists`() {
        val portraitPhoto = MediaMetadata(
            uri = "content://$AUTHORITY/rotated-photo",
            name = "rotated.jpg",
            durationMs = 3_000L,
            width = 1920,
            height = 1080,
            fps = 30,
            type = ClipType.IMAGE,
            rotationDegrees = 90
        )

        assertEquals(9f / 16f, portraitPhoto.sourceDisplayAspectRatio!!, 0.001f)
        assertNull(portraitPhoto.sourceVideoAspectRatio)
        assertEquals(9f / 16f, projectAspectRatioFromMedia(listOf(portraitPhoto))!!, 0.001f)
        assertEquals(
            16f / 9f,
            projectAspectRatioFromMedia(listOf(portraitPhoto, portraitPhoto.copy(type = ClipType.VIDEO, rotationDegrees = 0)))!!,
            0.001f
        )
        assertEquals(90, photoRotationDegreesFromExif(android.media.ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, photoRotationDegreesFromExif(android.media.ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, photoRotationDegreesFromExif(android.media.ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(90, photoRotationDegreesFromExif(android.media.ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(270, photoRotationDegreesFromExif(android.media.ExifInterface.ORIENTATION_TRANSVERSE))
    }

    private companion object {
        const val AUTHORITY = "test.image-provider"
    }

    /** Supports only the Documents/Openable contract, not MediaStore metadata. */
    class StrictImageProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun getType(uri: Uri): String = imageMimeType

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
            var imageMimeType = "image/png"
            var unsupportedProjectionQueries = 0
            var streamOpens = 0
        }
    }
}
