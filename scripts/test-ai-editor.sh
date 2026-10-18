#!/usr/bin/env bash
# Offline asset verification and focused AI/editor regressions; not the unrestricted full suite.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
python3 scripts/verify-ai-models.py
python3 -m unittest discover -s scripts/tests -p 'test_ai_model_integrity.py'
./gradlew :app:testDebugUnitTest --no-daemon --max-workers="${GRADLE_TEST_WORKERS:-2}" \
  --tests 'com.apexstudio.app.ai.*' \
  --tests '*AiToolsCatalogTest' \
  --tests '*EditorLocalAnalysisTest' \
  --tests '*WhisperCaptionBoundsTest' \
  --tests '*EditorCaptionReliabilityTest' \
  --tests '*EditorBackgroundControllerTest' \
  --tests '*EditorBackgroundHistoryTest' \
  --tests '*BackgroundOutputPublicationTest' \
  --tests '*WhisperInferenceReliabilityTest' \
  --tests '*WhisperModelIntegrityTest' \
  --tests '*WhisperTokenDecodingTest' \
  --tests '*PcmAudioProcessorTest' \
  --tests '*EditorTrimPositionTest' \
  --tests '*AudioTrackPlacementTest' \
  --tests '*LocalAudioImportPolicyTest' \
  --tests '*MusicExportMixerTest' \
  --tests '*VoiceRecordingTimingTest' \
  --tests '*OverlayClipTest' \
  --tests '*ClipTimeMappingTest' \
  --tests '*OverlaySplitTrimTest' \
  --tests '*PipOverlayCanvasTest' \
  --tests '*VideoCropPresetTest' \
  --tests '*ModernFxShadersTest' \
  --tests '*LutAssetIntegrityTest'
