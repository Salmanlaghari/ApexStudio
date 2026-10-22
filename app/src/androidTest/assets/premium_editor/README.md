# Premium editor UI test footage

`twelve_second_fixture.mp4` is a deterministic twelve-second repetition of the repository's existing one-second `video_upscale/source_with_audio.mp4` test fixture. It is not owner media, stock scenery, or an approval screenshot. It exists only in the instrumentation test APK, never in the phone app's production assets.

Reproduction:

```sh
ffmpeg -stream_loop 11 -i app/src/androidTest/assets/video_upscale/source_with_audio.mp4 -t 12 -c copy app/src/androidTest/assets/premium_editor/twelve_second_fixture.mp4
```

`PremiumEditorInstrumentedTest` copies it into a test-only cache file and composes the production editor with an in-memory project. It does not read, delete, or replace saved project records. The capture verifies actual installed Compose/PlayerView layout, not appearance parity for the owner's footage or supported multitrack export.
