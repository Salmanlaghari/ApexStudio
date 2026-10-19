#!/usr/bin/env bash
# Offline asset verification and focused AI/editor regressions; not the unrestricted full suite.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
python3 scripts/verify-ai-models.py
python3 -m unittest discover -s scripts/tests -p 'test_ai_model_integrity.py'
./gradlew :app:testDebugUnitTest --no-daemon --max-workers="${GRADLE_TEST_WORKERS:-2}" \
  --tests 'com.apexstudio.app.ai.*' \
  --tests '*AiToolsCatalogTest' \
  --tests '*LocalAnalysisReviewVisibilityTest' \
  --tests '*EditorLocalAnalysisTest' \
  --tests '*EditorFaceTrackReviewTest' \
  --tests '*EditorFaceCropApplyTest' \
  --tests '*EditorGestureControllerTest' \
  --tests '*FaceCropTrackTest' \
  --tests '*FaceCropGlEffectTest' \
  --tests '*EditorWordCaptionTest' \
  --tests '*TextSpriteRendererKaraokeTest' \
  --tests '*WhisperCaptionBoundsTest' \
  --tests '*EditorCaptionReliabilityTest' \
  --tests '*EditorBackgroundControllerTest' \
  --tests '*EditorBackgroundHistoryTest' \
  --tests '*BackgroundOutputPublicationTest' \
  --tests '*WhisperInferenceReliabilityTest' \
  --tests '*WhisperBundledModelCancellationTest' \
  --tests '*WhisperModelIntegrityTest' \
  --tests '*WhisperTokenDecodingTest' \
  --tests '*PcmAudioProcessorTest' \
  --tests '*EditorTrimPositionTest' \
  --tests '*EditorTimelineSnappingTest' \
  --tests '*TimelineTrimScaleTest' \
  --tests '*MainTimelineTrimGestureTest' \
  --tests '*TimelineClipGestureTest' \
  --tests '*ExportAudioSourceConfigTest' \
  --tests '*EditorAudioExportSourcesTest' \
  --tests '*AudioTrackPlacementTest' \
  --tests '*AudioPlaybackPositionTest' \
  --tests '*LocalAudioImportPolicyTest' \
  --tests '*LocalAudioImportDeliveryTest' \
  --tests '*AudioMixerPanelTest' \
  --tests '*MusicExportMixerTest' \
  --tests '*AudioMixExportNoticeTest' \
  --tests '*ExportCodecGuardTest' \
  --tests '*AudioExportWarningTest' \
  --tests '*ExportPcmBufferTest' \
  --tests '*VoiceRecordingTimingTest' \
  --tests 'com.apexstudio.app.domain.model.ModelsTest' \
  --tests '*OverlayClipTest' \
  --tests '*OverlayEditorRefreshTest' \
  --tests '*ClipTimeMappingTest' \
  --tests '*OverlayFrameSelectionPolicyTest' \
  --tests '*TextPanelModerationTest' \
  --tests '*OverlaySplitTrimTest' \
  --tests '*OverlayPropertiesSafetyTest' \
  --tests '*PipOverlayCanvasTest' \
  --tests '*OverlayCropGeometryTest' \
  --tests '*OverlayImageDecoderTest' \
  --tests '*ManualCropEditorTest' \
  --tests '*ManualCropPanelTest' \
  --tests '*CropSourceDimensionsTest' \
  --tests '*EditorManualCropTest' \
  --tests '*CameraKitConfigTest' \
  --tests '*VideoCropPresetTest' \
  --tests '*VideoCropGlEffectTest' \
  --tests '*ModernFxShadersTest' \
  --tests '*LutAssetIntegrityTest'
