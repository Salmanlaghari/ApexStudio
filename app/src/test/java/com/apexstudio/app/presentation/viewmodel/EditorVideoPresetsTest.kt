package com.apexstudio.app.presentation.viewmodel

import com.apexstudio.app.data.filter.GpuFilterConfig
import com.apexstudio.app.data.filter.OneTapLooks
import com.apexstudio.app.data.filter.StylisticEffectType
import com.apexstudio.app.data.export.ProjectTransitionTimeline
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
    fun `Viral HDR selection is one undoable edit and survives clip serialization`() {
        val vm = editor()
        val before = vm.state.value.project!!

        vm.applyGpuFilterProfilePreset(OneTapLooks.VIRAL_HDR_ID)

        assertEquals(1, vm.undoStack.size)
        assertEquals(before, vm.undoStack.single().project)
        val updated = vm.state.value.project!!.clips.first()
        assertEquals(OneTapLooks.VIRAL_HDR.applyWithIntensity(1f), updated.gpuFilterConfig)
        assertEquals(null, vm.state.value.activeFilterId)
        val restored = Json.decodeFromString<MediaClip>(Json.encodeToString(updated))
        assertEquals("viral_hdr", restored.gpuFilterConfig.activeProfileId)
        assertEquals(updated.gpuFilterConfig, restored.gpuFilterConfig)
    }

    @Test
    fun `per clip filter strength does not become a duplicate project wide look`() {
        val vm = editor()
        val globalFilterBefore = vm.state.value.activeFilterId to vm.state.value.filterIntensity
        val config = GpuFilterConfig(filterPresetId = "cinematic", filterIntensity = 0.4f)

        vm.setGpuFilterConfig(config)

        assertEquals(config, vm.state.value.project!!.clips.first().gpuFilterConfig)
        assertEquals(globalFilterBefore, vm.state.value.activeFilterId to vm.state.value.filterIntensity)
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
    fun `video template creates timed shots with look and title and one undo restores original`() {
        val source = videoClip(trimStartMs = 2_000L, trimEndMs = 8_000L).copy(speedMultiplier = 2.5f)
        val vm = editor(source)
        val before = vm.state.value.project!!
        val captured = before.clips.first()
        val recipe = ProjectVideoPresets.videoTemplates.first()
        val effect = ProjectVideoPresets.effect(recipe.effectId)!!
        val template = ProjectVideoPresets.textTemplate(recipe.textTemplateId)!!
        val expectedTitle = template.createTextOverlay()

        assertTrue(vm.applyProjectVideoTemplate(before.id, captured, recipe.id))
        val changed = vm.state.value.project!!
        val shots = changed.clips.take(3)
        assertEquals(3, shots.size)
        assertEquals(listOf(2_000L, 4_000L, 6_000L), shots.map { it.trimStartMs })
        assertEquals(listOf(4_000L, 6_000L, 8_000L), shots.map { it.trimEndMs })
        assertEquals(listOf("selected", "selected_template_warm-story_1", "selected_template_warm-story_2"), shots.map { it.id })
        val composition = ProjectTransitionTimeline.build(changed.clips, changed.transitions)
        assertEquals(
            listOf("selected", "selected_template_warm-story_1", "selected_template_warm-story_2", "other"),
            composition.entries.map { it.clip.id },
        )
        assertEquals(listOf(0L, 800L, 1_600L, 2_400L), composition.entries.map { it.timelineStartMs })
        assertEquals(12_400L, composition.durationMs)
        shots.forEach {
            assertEquals(effect.id, it.projectVideoEffectId)
            assertEquals(source.speedMultiplier, it.speedMultiplier, 0f)
        }
        val edited = shots.first()
        val title = edited.textOverlays.single()
        assertEquals(expectedTitle.text, title.text)
        assertEquals(expectedTitle.presetId, title.presetId)
        assertEquals(expectedTitle.sizeScale, title.sizeScale, 0f)
        assertEquals(expectedTitle.colorArgb, title.colorArgb)
        assertEquals(expectedTitle.fontFamily, title.fontFamily)
        assertEquals(expectedTitle.isBold, title.isBold)
        assertEquals(expectedTitle.isItalic, title.isItalic)
        assertEquals(expectedTitle.strokeColorArgb, title.strokeColorArgb)
        assertEquals(expectedTitle.bgArgb, title.bgArgb)
        assertEquals(expectedTitle.x, title.x, 0f)
        assertEquals(expectedTitle.y, title.y, 0f)
        assertEquals(0L, title.startMs)
        assertEquals(Long.MAX_VALUE, title.endMs)
        assertEquals(expectedTitle.animationType, title.animationType)
        assertEquals(expectedTitle.animationDurationMs, title.animationDurationMs)
        assertEquals(expectedTitle.rangeEndExclusive, title.rangeEndExclusive)
        assertTrue(title.isActiveAt(0L))
        assertTrue(title.isActiveAt(source.trimEndMs))
        assertEquals(title.id, vm.state.value.selectedTextOverlayId)
        assertEquals(before, vm.undoStack.single().project)
        assertNotNull(ProjectVideoPresets.colorEffect(edited.projectVideoEffectId))

        vm.undo()
        assertEquals(before, vm.state.value.project)
        assertTrue(vm.undoStack.isEmpty())
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
    fun `cool moment creates four ordered shots and remaps only boundary transitions`() {
        val previous = videoClip(id = "previous", uri = "file:///previous.mp4", durationMs = 2_000L, trimEndMs = 2_000L)
        val target = videoClip(id = "target", uri = "file:///target.mp4", durationMs = 8_000L, trimEndMs = 8_000L)
        val next = videoClip(id = "next", uri = "file:///next.mp4", durationMs = 2_000L, trimEndMs = 2_000L)
        val project = Project("project", "Project", 12_000L, clips = listOf(previous, target, next), transitions = listOf(
            com.apexstudio.app.domain.model.ClipTransition(fromClipId = previous.id, toClipId = target.id),
            com.apexstudio.app.domain.model.ClipTransition(fromClipId = target.id, toClipId = next.id),
        ))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(project = project, selectedClipId = target.id)
        }

        assertTrue(vm.applyProjectVideoTemplate(project.id, target, "cool-moment"))
        val changed = vm.state.value.project!!
        val shots = changed.clips.filter { it.id == target.id || it.id.startsWith("target_template_cool-moment_") }
        assertEquals(4, shots.size)
        assertEquals(listOf(0L, 2_000L, 4_000L, 6_000L), shots.map { it.trimStartMs })
        assertEquals(listOf(2_000L, 4_000L, 6_000L, 8_000L), shots.map { it.trimEndMs })
        assertEquals(4, shots.map { it.id }.toSet().size)
        assertTrue(shots.all { it.projectVideoEffectId == "cool-fade" && it.textOverlays.single().presetId == "title-elegant" })
        assertEquals(previous.id to shots.first().id, changed.transitions.first().fromClipId to changed.transitions.first().toClipId)
        assertEquals(shots.last().id to next.id, changed.transitions.last().fromClipId to changed.transitions.last().toClipId)
        assertEquals(1, vm.undoStack.size)
        vm.undo()
        assertEquals(project, vm.state.value.project)
    }

    @Test
    fun `montage on preview clip preserves unrelated PiP selection and overlay focus`() {
        val target = videoClip(id = "main", uri = "file:///main.mp4", durationMs = 8_000L, trimEndMs = 8_000L)
        val pip = videoClip(id = "pip", uri = "file:///pip.mp4", durationMs = 8_000L, trimEndMs = 8_000L)
            .copy(type = ClipType.OVERLAY, trackIndex = 1)
        val project = Project("project", "Project", 8_000L, clips = listOf(target, pip))
        val vm = EditorViewModel().also {
            it._state.value = it.state.value.copy(
                project = project,
                selectedClipId = pip.id,
                selectedTextOverlayId = "pip-title",
                selectedStickerId = "pip-sticker",
            )
        }

        assertTrue(vm.applyProjectVideoTemplate(project.id, target, "warm-story", allowUnselectedMainTrackTarget = true))
        assertEquals(pip.id, vm.state.value.selectedClipId)
        assertEquals("pip-title", vm.state.value.selectedTextOverlayId)
        assertEquals("pip-sticker", vm.state.value.selectedStickerId)
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
