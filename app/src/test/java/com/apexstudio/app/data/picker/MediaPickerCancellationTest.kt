package com.apexstudio.app.data.picker

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.presentation.viewmodel.EditorViewModel
import com.apexstudio.app.presentation.viewmodel.clearPendingReplace
import com.apexstudio.app.presentation.viewmodel.setPendingReplaceClip
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaPickerCancellationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cancelledResultsFromAllPickersClearReplaceTargetAndKeepProject() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = Project(id = "project-1", name = "Keep me", durationMs = 1_000L)
        val viewModel = EditorViewModel().apply {
            _state.value = _state.value.copy(project = project)
        }
        val picker = MediaPickerHelper(context)
        val owner = TestActivityResultRegistryOwner(CancellingActivityResultRegistry())

        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                picker.registerLaunchers(onCancelled = { viewModel.clearPendingReplace() })
            }
        }
        compose.waitForIdle()

        viewModel.setPendingReplaceClip("clip-to-replace")
        compose.runOnUiThread { picker.pickSingleMedia.launch("video/*") }
        compose.waitForIdle()
        assertNull(viewModel.state.value.pendingReplaceClipId)
        assertSame(project, viewModel.state.value.project)

        viewModel.setPendingReplaceClip("clip-to-replace")
        compose.runOnUiThread {
            picker.pickMultipleMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        compose.waitForIdle()
        assertNull(viewModel.state.value.pendingReplaceClipId)
        assertSame(project, viewModel.state.value.project)

        viewModel.setPendingReplaceClip("clip-to-replace")
        compose.runOnUiThread { picker.pickAudioMedia.launch("audio/*") }
        compose.waitForIdle()
        assertNull(viewModel.state.value.pendingReplaceClipId)
        assertSame(project, viewModel.state.value.project)
    }

    private class TestActivityResultRegistryOwner(
        override val activityResultRegistry: ActivityResultRegistry
    ) : ActivityResultRegistryOwner

    private class CancellingActivityResultRegistry : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
        }
    }
}
