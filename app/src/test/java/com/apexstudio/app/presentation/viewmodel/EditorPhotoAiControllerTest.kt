package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.PhotoEditSettings
import com.apexstudio.app.domain.model.Project
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorPhotoAiControllerTest {
    @Test fun photoAiResultCanCommitOnlyToItsOriginalProjectImageSource() {
        val photo = MediaClip("photo", "Photo", "content://photos/1", 3_000L,
            trimEndMs = 3_000L, type = ClipType.IMAGE)
        assertTrue(photoAiResultCanCommit("project", "photo", photo.uri, "project", photo))
        assertFalse(photoAiResultCanCommit("other", "photo", photo.uri, "project", photo))
        assertFalse(photoAiResultCanCommit("project", "other", photo.uri, "project", photo))
        assertFalse(photoAiResultCanCommit("project", "photo", photo.uri, "project", photo.copy(uri = "content://photos/2")))
        assertFalse(photoAiResultCanCommit("project", "photo", photo.uri, "project", photo.copy(type = ClipType.VIDEO)))
        assertFalse(photoAiResultCanCommit("project", "photo", photo.uri, "project", null))
        val settings = PhotoEditSettings()
        assertTrue(photoAiResultCanCommit("project", "photo", photo.uri, "project", photo,
            expectedPhotoEditSettings = settings))
        assertFalse(photoAiResultCanCommit("project", "photo", photo.uri, "project",
            photo.copy(photoEdit = settings.copy(stabilizePhoto = true)), expectedPhotoEditSettings = settings))
    }

    @Test fun autoLevelSettingIsPersistedOnOnlyTheSelectedPhotoAndCanBeTurnedOff() {
        val photo = MediaClip("photo", "Photo", "file:///photo.png", 3_000L,
            trimEndMs = 3_000L, type = ClipType.IMAGE)
        val project = Project("project", "Project", 3_000L, clips = listOf(photo))
        val vm = EditorViewModel().also {
            it._state.value = it._state.value.copy(project = project, selectedClipId = photo.id)
        }
        vm.setPhotoStabilize(photo.id, true)
        assertTrue(vm.state.value.project!!.clips.single().photoEdit.stabilizePhoto)
        vm.setPhotoStabilize(photo.id, false)
        assertFalse(vm.state.value.project!!.clips.single().photoEdit.stabilizePhoto)
    }
}
