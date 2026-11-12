# Export timestamp fixtures

`vfr_gap_audio_fixture.mp4` is a deterministic, 320×180 H.264/AAC test source used by `ExportTimestampParityInstrumentedTest`. It is about 2 seconds long and has continuous 48 kHz audio plus deliberately non-uniform video PTS. The selected source timestamps are approximately 0, 33, 67, 100, 400, 433, 800, 833, 1500, 1533, 1933, and 1967 ms; the longest gap is about 667 ms. The sparse intervals make the test sensitive to timing shifts while exercising the long video-gap/audio-mux case.

VFR fixture SHA-256: `d9c4029d7b45bb6284376192f3b2292a229baa50205eb8963e746ccdeda84893`.

`cfr_30fps_audio_fixture.mp4` is a deterministic two-second, 320×180 H.264/AAC constant-frame-rate source with 60 video samples and continuous 48 kHz audio. The 60 fps regression uses it to verify actual encoded cadence from a 30 fps input.

CFR fixture SHA-256: `3934cb1446694f98fb084f91a6c151826f7c05a20cd775d2aa1482be9511c7ce`.

The VFR fixture was generated with FFmpeg using a 30 fps source, retaining only the following source frames so the original PTS are preserved:

```sh
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i 'testsrc2=size=320x180:rate=30:duration=2' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=2' \
  -vf "select='eq(n,0)+eq(n,1)+eq(n,2)+eq(n,3)+eq(n,12)+eq(n,13)+eq(n,24)+eq(n,25)+eq(n,45)+eq(n,46)+eq(n,58)+eq(n,59)'" \
  -fps_mode:v vfr -c:v libx264 -preset ultrafast -tune zerolatency -bf 0 -g 60 \
  -pix_fmt yuv420p -c:a aac -b:a 48k -shortest \
  -video_track_timescale 90000 -movflags +faststart vfr_gap_audio_fixture.mp4
```

The constant-rate fixture was generated with FFmpeg as follows:

```sh
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i 'testsrc2=size=320x180:rate=30:duration=2' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=2' \
  -c:v libx264 -preset ultrafast -tune zerolatency -bf 0 -g 60 \
  -pix_fmt yuv420p -c:a aac -b:a 48k -shortest \
  -video_track_timescale 90000 -movflags +faststart cfr_30fps_audio_fixture.mp4
```

Do not re-encode `vfr_gap_audio_fixture.mp4` with a constant-frame-rate mode: that would erase the sparse source PTS. The VFR regression exports a non-zero trim through selected-clip and two-clip whole-project paths, then checks source anchors, average-FPS budget, and planned duration. It allows an intentional source-frame drop and does not require every VFR PTS gap to be uniform.

The 60 fps regression exports the constant-rate fixture through selected-video and two-clip whole-project paths at 60 fps. It checks actual muxed sample count, average rate, and per-sample PTS gaps against the 60 fps cadence contract.

Android CI runs both instrumented regressions on an API 35 emulator. This does not replace playback testing on a physical device.
