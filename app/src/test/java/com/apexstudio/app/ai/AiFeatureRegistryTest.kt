package com.apexstudio.app.ai

import com.apexstudio.app.ai.mediapipe.BundledMediaPipeModels
import org.junit.Assert.*
import org.junit.Test

class AiFeatureRegistryTest {
    @Test fun everyFeatureHasAnHonestDescription() {
        assertEquals(AiFeatureId.entries.toSet(), AiFeatureRegistry.descriptions.keys)
        assertTrue(AiFeatureRegistry.descriptions.values.all { it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test fun facePoseHandAndBackgroundMapToTheirActualBundledModel() {
        assertEquals(BundledMediaPipeModels.Model.FACE, AiFeatureRegistry.mediaPipeModelFor(AiFeatureId.FACE_BLUR))
        assertEquals(BundledMediaPipeModels.Model.FACE, AiFeatureRegistry.mediaPipeModelFor(AiFeatureId.FACE_AWARE_REFRAME))
        assertEquals(BundledMediaPipeModels.Model.POSE, AiFeatureRegistry.mediaPipeModelFor(AiFeatureId.SKELETON_OVERLAY))
        assertEquals(BundledMediaPipeModels.Model.HAND, AiFeatureRegistry.mediaPipeModelFor(AiFeatureId.GESTURE_CONTROL))
        assertEquals(BundledMediaPipeModels.Model.SELFIE_SEGMENTER, AiFeatureRegistry.mediaPipeModelFor(AiFeatureId.BACKGROUND_BLUR))
        assertNull(AiFeatureRegistry.mediaPipeModelFor(AiFeatureId.CONTENT_MODERATION))
    }

    @Test fun bundledRequirementsHaveOfficialPinnedVersionSourcesAndNoPlaceholderLicense() {
        val requirements = AiFeatureId.entries.filter { AiFeatureRegistry.mediaPipeModelFor(it) != null }
            .flatMap { AiFeatureRegistry.knownRequirements.getValue(it) }
        assertTrue(requirements.isNotEmpty())
        assertTrue(requirements.all { it.id.startsWith("bundled:") && it.source.startsWith("https://storage.googleapis.com/mediapipe-models/") })
        assertTrue(requirements.all { it.license.contains("Apache-2.0") && it.fileName.isNotBlank() })
        assertTrue(requirements.all { "/1/" in it.source })
    }

    @Test fun unknownAdvancedModelsAndModerationCannotInheritMediaPipeReadiness() {
        listOf(AiFeatureId.ONNX_UPSCALING, AiFeatureId.ONNX_STYLE_TRANSFER,
            AiFeatureId.ONNX_BACKGROUND_REMOVAL, AiFeatureId.CONTENT_MODERATION,
            AiFeatureId.WORD_TIMED_SUBTITLES).forEach { feature ->
            assertNull(AiFeatureRegistry.mediaPipeModelFor(feature))
            assertTrue(AiFeatureRegistry.knownRequirements.getValue(feature).isNotEmpty())
        }
    }
}
