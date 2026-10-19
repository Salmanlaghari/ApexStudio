# Selected-clip export stall: follow-up diagnosis

## Finding

The reported **97% is ambiguous: it can be a live Media3 video-render estimate or a pipeline boundary value**. `ExportEngine.startProgressTracking` can publish actual Transformer percentages through 99%. Separately, `ExportEngine.buildHardwareTransformer`'s `onCompleted` callback cancels video progress polling and, when post-mix inputs exist, explicitly publishes `progress = 0.97` while it prepares overlay audio and/or calls `MusicExportMixer.mixMusicIntoVideo`. The export screen displays both cases as the same percentage. Therefore the screenshot alone does not establish that the video render completed or identify a root cause.

If the new log shows `video_transformer_completed` followed by `post_mix_started`, then that run reached the post-Transformer audio-mix phase; if the last breadcrumb is a `video_progress` milestone without a completion event, the Transformer had not reported completion when the log was captured. Either outcome still needs the device trace to explain why it stopped advancing. A 403 MB estimate/output and 2:59 duration do not identify a codec, file, or device bottleneck. No export was run against the reported media or a device here.

## Pipeline evidence

- `EditorExportController.startExport` selects the base video clip, preserves its trim and edits, then supplies enabled project `audioTracks` as `musicMixTracks` and overlay audio sources as `overlayAudioSources`.
- `ExportEngine.startProgressTracking` polls Media3 every 100 ms, clamps the displayed video render progress to at most 99%, and updates the state only while the Transformer is active.
- `ExportEngine.buildHardwareTransformer`'s `onCompleted` is the point that switches to fixed 97% when either post-mix input list is non-empty. It cancels the Media3 progress job before starting post-mix work.
- The post-mix coroutine can probe/render overlay audio and then synchronously perform music mixing/transcoding/muxing. Its only terminal state update is after that work, or after an exception fallback. If a captured run is in this phase, 97% may remain visible throughout it.
- With no music or overlay audio sources, this callback does **not** set 97%; it publishes completion immediately. A live Transformer can also independently report 97%. A device log is needed to tell which case occurred in the screenshot's run.

## Changes made

Added `ExportDiagnosticTrace`, a privacy-conscious elapsed-time breadcrumb helper, and wired it to the selected-clip export route. It emits `ExportEngine` info logs for video Transformer start, 10%-bucket progress milestones, Transformer completion/failure, post-mix start, overlay-audio preparation start/completion, music-mix start/completion, and post-mix failure. Events include elapsed milliseconds and aggregate counts/duration/byte-size only; they do not include source URIs, output paths, filenames, or user-authored media content. The existing export pipeline, displayed progress values, output handling, and preview/export effect configuration are unchanged.

`ExportDiagnosticTraceTest` verifies elapsed timing and progress-bucket throttling. The instrumentation deliberately does not yet split `MusicExportMixer` into internal WAV render, AAC encode, and MP4 mux events; if a device trace ends at `music_audio_mix_started`, that narrows the stall to this helper but does not locate its internal operation.

## Reproduction and validation

A real reproduction was **not possible in this environment**: `adb devices -l` listed no connected device, no emulator/system image was installed, and no representative large source clip was present in the checkout/workspace. The supplied 403 MB / 2:59 observation is treated as user-reported screenshot evidence, not independently measured media metadata.

The parent agent is running the repository's baseline Gradle validation; to avoid competing Gradle processes, this task did not start another Gradle invocation. After that run completes, execute:

```bash
cd /workspace/apexstudio-followup
./gradlew :app:testDebugUnitTest --tests com.apexstudio.app.data.export.ExportDiagnosticTraceTest
```

For device diagnosis, install/run the tested build, start export on the same selected clip/project that exhibits the stall, and capture:

```bash
adb logcat -c
# Reproduce the export in the app, then:
adb logcat -d -v time -s ExportEngine:I
```

Interpretation: a `video_transformer_completed` event means Media3 delivered completion; if followed by `post_mix_started`, inspect the last subsequent stage and elapsed time. A last `overlay_audio_prepare_started` or `music_audio_mix_started` event identifies which coarse operation did not report completion before capture. If no `video_transformer_completed` event appears and the last progress breadcrumb is a `video_progress` event, the Transformer had not reported completion by capture time (the logged progress is bucketed, not exact). Save the full log with device/build details and the project's enabled audio-source counts; redact other app/device data before sharing.

## Limits / next decision

This is a **diagnosis and instrumentation follow-up, not a confirmed device root-cause fix**. The source establishes two different paths that can render as 97%; it cannot establish which path the screenshot represents, codec/device failure, memory pressure, a malformed source, a native deadlock, or a specific mixer stage without a reproducible input and device trace. Keep the current preview/export behavior unchanged until the same clip is reproduced and the new trace points to a concrete failure or non-terminating stage. If it consistently stalls within `MusicExportMixer`, instrument that helper's decode/WAV, AAC, and mux boundaries next, then decide on a narrowly scoped fix from the evidence.
