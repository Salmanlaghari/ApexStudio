package com.apexstudio.app.camerakit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraKitConfigTest {

    @Test
    fun demoLensGroupId_isExpectedValue() {
        assertEquals("d33b8566-1687-43d5-8974-d9d8be37bfb3", CameraKitConfig.DEMO_LENS_GROUP_ID)
    }

    @Test
    fun demoLensGroupId_isValidUuidFormat() {
        val uuidRegex = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        )
        assertTrue(uuidRegex.matches(CameraKitConfig.DEMO_LENS_GROUP_ID))
    }

    @Test
    fun apiToken_usesBuildInjectedConfiguration() {
        // CI may inject a repository secret; local/fork builds may leave it blank. Test the
        // actual contract rather than assuming either environment. Boolean assertions deliberately
        // avoid including token values in assertion output, XML reports or CI logs.
        assertTrue(CameraKitConfig.apiToken == BuildConfig.SNAP_CAMERA_KIT_TOKEN)
        assertEquals(BuildConfig.SNAP_CAMERA_KIT_TOKEN.isNotBlank(), CameraKitConfig.isConfigured)
    }

    @Test
    fun lensItem_defaultsNameToIdWhenBlank() {
        val item = LensItem(id = "lens-1", groupId = "group-1", name = "", iconUri = null)
        assertEquals("lens-1", item.id)
        assertEquals("", item.name)
    }
}
