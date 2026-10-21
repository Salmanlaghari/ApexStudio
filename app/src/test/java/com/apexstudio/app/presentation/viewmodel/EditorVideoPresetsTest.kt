package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.StylisticEffectType
import com.apexstudio.app.data.template.ProjectVideoPresets
import com.apexstudio.app.domain.model.ClipType
import com.apexstudio.app.domain.model.MediaClip
import com.apexstudio.app.domain.model.Project
import com.apexstudio.app.domain.model.TextOverlay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorVideoPresetsTest {
    private fun editor(clip: MediaClip = videoClip()): EditorViewModel {
        val other = videoClip(id = "other", uri = "file:///other.mp4")
        return EditorViewModel().also { vm ->
            vm._state.value = vm.state.value.copy(
                project = Project("project", "Project", 20_000L, clips = listOf(clip, other)),
                selectedClipId = clip.id,
            )
        }
    }

    @Test
    fun `catalogs are fixed and title factories create fresh literal overlays`() {
        assertEquals(3, ProjectVideoPresets.textTemplates.size)
        assertEquals(6, ProjectVideoPresets.effects.size)
        assertEquals(3, ProjectVideoPresets.textMotions.size)
        assertEquals(2, ProjectVideoPresets.videoTemplates.size)
        assertEquals(4, ProjectVideoPresets.videoEnhancements.size)
        assertEquals(ProjectVideoPresets.textTemplates.size,
            ProjectVideoPresets.textTemplates.map { it.id }.toSet().size)
        assertEquals(ProjectVideoPresets.effects.size,
            ProjectVideoPresets.effects.map { it.id }.toSet().size)
        assertEquals(ProjectVideoPresets.videoEnhancements.size,
            ProjectVideoPresets.videoEnhancements.map { it.id }.toSet().size)

        val template = ProjectVideoPresets.textTemplates.first()
        val first = template.createTextOverlay()
        val second = template.createTextOverlay()
        assertEquals("YOUR STORY", first.text)
        assertTrue(template.label.contains(first.text))
        assertEquals(template.id, first.presetId)
        assertNotEquals(first.id, second.id)
        assertEquals(first.text, second.text)
    }

    @Test
    fun `effect matrices are fixed finite safe and resolved for shared rendering`() {
        (ProjectVideoPresets.effects + ProjectVideoPresets.videoEnhancements).forEach { effect ->
            val matrix = effect.colorMatrix().array
            assertEquals(20, matrix.size)
            assertTrue("${effect.id} has non-finite coefficient", matrix.all { it.isFinite() })
            assertEquals(0f, matrix[15], 0f)
            assertEquals(0f, matrix[16], 0f)
            assertEquals(0f, matrix[17], 0f)
            assertEquals(1f, matrix[18], 0f)
            assertEquals(0f, matrix[19], 0f)
            assertNotNull(ProjectVideoPresets.colorEffect(effect.id))
        }
        val mono = ProjectVideoPresets.effect("mono")!!.colorMatrix().array
        assertEquals(0.299f, mono[0], 0f)
        assertEquals(0.587f, mono[1], 0f)
        assertEquals(0.114f, mono[2], 0f)
        assertEquals(mono[0], mono[5], 0f)
        assertEquals(mono[0], mono[10], 0f)
        val enhance = ProjectVideoPresets.effect("video-enhance-color-lift")!!.colorMatrix().array
        assertEquals(
            listOf(
                1.121848f, -0.07436f, -0.007488f, 0f, -1.12f,
                -0.022152f, 1.06964f, -0.007488f, 0f, -1.12f,
                -0.022152f, -0.07436f, 1.136512f, 0f, -1.12f,
                0f, 0f, 0f, 1f, 0f,
            ), enhance.toList()
        )
        assertNotNull(ProjectVideoPresets.colorEffect("video-enhance-color-lift"))
        assertEquals(null, ProjectVideoPresets.colorEffect(null))
        assertEquals(null, ProjectVideoPresets.colorEffect("unknown-effect"))
    }

    @Test
    fun `fixed video template applies title and effect with one undo snapshot`() {
        val vm = editor()
        val before = vm.state.value.project!!
        val captured = before.clips.first()
        val recipe = ProjectVideoPresets.videoTemplates.first()
        val effect = ProjectVideoPresets.effect(recipe.effectId)!!
        val title = ProjectVideoPresets.textTemplate(recipe.textTemplateId)!!.createTextOverlay()

        assertTrue(vm.applyProjectVideoTemplate(before.id, captured, recipe.id))
        val changed = vm.state.value.project!!
        val edited = changed.clips.first()
        assertEquals(effect.id, edited.projectVideoEffectId)
        assertEquals(listOf(title.text), edited.textOverlays.map { it.text })
        assertEquals(title.presetId, edited.textOverlays.single().presetId)
        assertEquals(before, vm.undoStack.single().project)
        assertNotNull(ProjectVideoPresets.colorEffect(edited.projectVideoEffectId))
    }

    @Test
    fun `fixed text motion adds a literal title with selected shared animation`() {
        val vm = editor()
        val before = vm.state.value.project!!
        val clip = before.clips.first()
        val motion = ProjectVideoPresets.textMotions.first { it.id == "slide-up" }

        assertTrue(vm.applyProjectVideoTextMotion(before.id, clip, motion.id))
        val overlay = vm.state.value.project!!.clips.first().textOverlays.single()
        assertEquals("YOUR STORY", overlay.text)
        assertEquals(motion.animationType, overlay.animationType)
        assertEquals(motion.durationMs, overlay.animationDurationMs)
        assertEquals(before, vm.undoStack.single().project)
    }

    @Test
    fun `text template appends a fresh fixed overlay and captures one undo snapshot`() {
        val vm = editor()
        val initial = vm.state.value.project!!
        val existingOverlay = TextOverlay.of(text = "EXISTING")
        vm._state.value = vm.state.value.copy(project = initial.copy(clips = initial.clips.map {
            if (it.id == "selected") it.copy(textOverlays = listOf(existingOverlay)) else it
        }))
        val before = vm.state.value.project!!
        val captured = before.clips.first()
        val template = ProjectVideoPresets.textTemplates.first()

        assertTrue(vm.applyProjectVideoTextTemplate(before.id, captured, template.id))
        val changed = vm.state.value.project!!
        val edited = changed.clips.first()
        assertEquals(captured.uri, edited.uri)
        assertEquals(2, edited.textOverlays.size)
        assertEquals(existingOverlay, edited.textOverlays.first())
        assertEquals("YOUR STORY", edited.textOverlays.last().text)
        assertEquals(template.id, edited.textOverlays.last().presetId)
        assertEquals(edited.textOverlays.last().id, vm.state.value.selectedTextOverlayId)
        assertEquals(before.clips.last(), changed.clips.last())
        assertEquals(before, vm.undoStack.single().project)

        val afterFirst = edited
        assertTrue(vm.applyProjectVideoTextTemplate(changed.id, afterFirst, template.id))
        val overlays = vm.state.value.project!!.clips.first().textOverlays
        assertEquals(3, overlays.size)
        assertNotEquals(overlays[1].id, overlays[2].id)
        assertEquals(2, vm.undoStack.size)
    }

    @Test
    fun `stale project clip and selection are rejected without mutation or undo`() {
        for (staleCase in listOf("project", "clip", "selection")) {
            val vm = editor()
            val original = vm.state.value.project!!
            val captured = original.clips.first()
            when (staleCase) {
                "project" -> vm._state.value = vm.state.value.copy(project = original.copy(id = "replacement"))
                "clip" -> vm._state.value = vm.state.value.copy(project = original.copy(
                    clips = original.clips.map { if (it.id == captured.id) it.copy(trimStartMs = 500L) else it }
                ))
                "selection" -> vm.selectClip("other")
            }
            val current = vm.state.value.project
            val history = vm.undoStack.size
            assertFalse(staleCase, vm.applyProjectVideoEffect(original.id, captured, "warm-film"))
            assertFalse(staleCase, vm.applyProjectVideoTextMotion(original.id, captured, "fade-in"))
            assertFalse(staleCase, vm.applyProjectVideoTemplate(original.id, captured, "warm-story"))
            assertEquals(current, vm.state.value.project)
            assertEquals(history, vm.undoStack.size)
        }
    }

    @Test
    fun `image audio overlay malformed sources and invalid trim are rejected`() {
        val invalidClips = listOf(
            videoClip(type = ClipType.IMAGE),
            videoClip(type = ClipType.AUDIO),
            videoClip(type = ClipType.OVERLAY),
            videoClip(uri = "  "),
            videoClip(durationMs = 0L, trimEndMs = 0L),
            videoClip(trimStartMs = 100L, trimEndMs = 100L),
            videoClip(trimEndMs = 10_001L),
            videoClip().copy(trackIndex = 1),
        )
        invalidClips.forEachIndexed { index, clip ->
            val vm = editor(clip)
            val before = vm.state.value.project
            assertFalse("invalid target $index", vm.applyProjectVideoTextTemplate("project", clip, "title-bold"))
            assertFalse("invalid target $index", vm.applyProjectVideoEffect("project", clip, "warm-film"))
            assertFalse("invalid target $index", vm.applyProjectVideoTextMotion("project", clip, "fade-in"))
            assertFalse("invalid target $index", vm.applyProjectVideoTemplate("project", clip, "warm-story"))
            assertEquals(before, vm.state.value.project)
            assertTrue(vm.undoStack.isEmpty())
        }
    }

    @Test
    fun `effect stores only its id preserves unrelated clip state and is undoable once`() {
        val initialConfig = GpuFilterConfig(
            filterPresetId = "existing-lut",
            filterIntensity = 0.42f,
            stylisticEffect = StylisticEffectType.VIGNETTE,
            stylisticIntensity = 0.37f,
            effectParam = 0.7f,
            exposure = -0.2f,
            gamma = 1.2f,
            grain = 0.15f,
            activeProfileId = "profile-x",
            profileIntensity = 0.8f,
        )
        val target = videoClip().copy(gpuFilterConfig = initialConfig, volume = 0.6f)
        val vm = editor(target)
        val before = vm.state.value.project!!
        val captured = before.clips.first()
        val effect = ProjectVideoPresets.effects.first()

        assertTrue(vm.applyProjectVideoEffect(before.id, captured, effect.id))
        val after = vm.state.value.project!!
        val edited = after.clips.first()
        assertEquals(captured.uri, edited.uri)
        assertEquals(captured.volume, edited.volume)
        assertEquals(effect.id, edited.projectVideoEffectId)
        assertEquals(initialConfig, edited.gpuFilterConfig)
        assertEquals(before.clips.last(), after.clips.last())
        assertEquals(before, vm.undoStack.single().project)

        val colorEffect = ProjectVideoPresets.colorEffect(edited.projectVideoEffectId)
        assertNotNull(colorEffect)
        assertEquals(20, effect.colorMatrix().array.size)

        // Reapplying an identical preset succeeds but does not create a no-op history entry.
        assertTrue(vm.applyProjectVideoEffect(after.id, edited, effect.id))
        assertEquals(1, vm.undoStack.size)
    }

    @Test
    fun `enhance choices are distinct shared local effects and persist with undo`() {
        val choices = ProjectVideoPresets.videoEnhancements
        assertEquals(4, choices.size)
        assertEquals(choices.size, choices.map { it.id }.toSet().size)
        assertEquals(choices.size, choices.map { it.colorMatrix().array.toList() }.toSet().size)
        choices.forEach { choice ->
            assertNotNull(ProjectVideoPresets.effect(choice.id))
            assertNotNull(ProjectVideoPresets.colorEffect(choice.id))
            assertEquals(20, choice.colorMatrix().array.size)
            assertTrue(choice.colorMatrix().array.all { it.isFinite() })
        }

        val vm = editor()
        val before = vm.state.value.project!!
        val captured = before.clips.first()
        val selectedEnhancement = choices.last()
        assertTrue(vm.applyProjectVideoEffect(before.id, captured, selectedEnhancement.id))
        val changed = vm.state.value.project!!.clips.first()
        assertEquals(selectedEnhancement.id, changed.projectVideoEffectId)
        assertEquals(before, vm.undoStack.single().project)

        val restored = Json.decodeFromString<MediaClip>(Json.encodeToString(changed))
        assertEquals(selectedEnhancement.id, restored.projectVideoEffectId)
        assertNotNull(ProjectVideoPresets.colorEffect(restored.projectVideoEffectId))
        vm.undo()
        assertEquals(before, vm.state.value.project)
    }

    @Test
    fun `clip serialization retains text overlay and deterministic pipeline config`() {
        val vm = editor()
        val project = vm.state.value.project!!
        val captured = project.clips.first()
        assertTrue(vm.applyProjectVideoTextTemplate(project.id, captured, "title-elegant"))
        val titled = vm.state.value.project!!.clips.first()
        assertTrue(vm.applyProjectVideoEffect(project.id, titled, "cool-fade"))

        val encoded = Json.encodeToString(vm.state.value.project!!.clips.first())
        val restored = Json.decodeFromString<MediaClip>(encoded)
        assertEquals(vm.state.value.project!!.clips.first(), restored)
        assertNotNull(restored.textOverlays.singleOrNull())
        assertEquals("cool-fade", restored.projectVideoEffectId)
        assertEquals(GpuFilterConfig(), restored.gpuFilterConfig)
        assertNotNull(ProjectVideoPresets.colorEffect(restored.projectVideoEffectId))

        val legacyJson = kotlinx.serialization.json.JsonObject(
            Json.parseToJsonElement(encoded).jsonObject.minus("projectVideoEffectId")
        ).toString()
        assertEquals(null, Json.decodeFromString<MediaClip>(legacyJson).projectVideoEffectId)
    }

    private fun videoClip(
        id: String = "selected",
        uri: String = "file:///selected.mp4",
        durationMs: Long = 10_000L,
        trimStartMs: Long = 0L,
        trimEndMs: Long = 10_000L,
        type: ClipType = ClipType.VIDEO,
        trackIndex: Int = 0,
    ) = MediaClip(
        id = id,
        name = id,
        uri = uri,
        durationMs = durationMs,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        type = type,
        trackIndex = trackIndex,
    )
}
