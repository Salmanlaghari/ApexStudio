package com.apexstudio.app.data.filter

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomLutImportTest {
    @Test
    fun `imported cube uses shared loader and is rediscovered after editor reopen`() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "custom-lut-${System.nanoTime()}")
        val files = File(root, "files").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = files
        }
        val cube = """
            LUT_3D_SIZE 2
            0 0 0
            1 0 0
            0 1 0
            1 1 0
            0 0 1
            1 0 1
            0 1 1
            0.5 0.5 0.5
        """.trimIndent()

        try {
            val imported = GpuImageLutEngine(context).importCustomCube(
                "Editorial look",
                ByteArrayInputStream(cube.toByteArray(Charsets.UTF_8))
            )
            assertNotNull(imported)
            imported!!

            val renderEngine = LutFilterEngine(context)
            val values = renderEngine.loadLut(imported)
            assertNotNull(values)
            assertEquals(2 * 2 * 2 * 3, values!!.size)
            assertTrue(renderEngine.isCustomLutAvailable(imported))

            val restored = GpuImageLutEngine(context).loadCustomPresets()
            assertEquals(1, restored.size)
            assertEquals(imported.id, restored.single().id)
            assertEquals(imported.asset, restored.single().asset)
            assertEquals("Editorial Look", restored.single().name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `custom asset traversal is rejected without matrix fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val invalid = FilterPreset(
            id = "custom_traversal",
            name = "Invalid",
            category = "Custom",
            asset = "custom/../outside.cube"
        )
        val engine = LutFilterEngine(context)

        assertNull(engine.loadLut(invalid))
        assertFalse(engine.isCustomLutAvailable(invalid))
    }
}
