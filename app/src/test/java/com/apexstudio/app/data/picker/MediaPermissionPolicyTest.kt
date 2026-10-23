package com.apexstudio.app.data.picker

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPermissionPolicyTest {
    @Test
    fun selectedMediaWorkflowDoesNotDeclareBroadLibraryAccess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS
        ).requestedPermissions.orEmpty().toSet()
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        ).forEach { permission ->
            assertFalse("Selected-media editing must not request $permission", permission in permissions)
        }
    }
}
