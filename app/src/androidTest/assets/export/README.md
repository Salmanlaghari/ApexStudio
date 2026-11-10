# Export timestamp fixture

`vfr_gap_audio_fixture.mp4` is a deterministic, 320×180 H.264/AAC test source used by `ExportTimestampParityInstrumentedTest`. It is about 2 seconds long and has continuous 48 kHz audio plus deliberately non-uniform video PTS. The selected source timestamps are approximately 0, 33, 67, 100, 400, 433, 800, 833, 1500, 1533, 1933, and 1967 ms; the longest gap is about 667 ms. The sparse intervals make the test sensitive to timing shifts while exercising the long video-gap/audio-mux case.

Fixture SHA-256: `d9c4029d7b45bb6284376192f3b2292a229baa50205eb8963e746ccdeda84893`.

The fixture was generated with FFmpeg using a 30 fps source, retaining only the following source frames so the original PTS are preserved:

```sh
ffmpeg -hide_banner -loglevel error \
  -f lavfi -i 'testsrc2=size=320x180:rate=30:duration=2' \
  -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=2' \
  -vf "select='eq(n,0)+eq(n,1)+eq(n,2)+eq(n,3)+eq(n,12)+eq(n,13)+eq(n,24)+eq(n,25)+eq(n,45)+eq(n,46)+eq(n,58)+eq(n,59)'" \
  -fps_mode:v vfr -c:v libx264 -preset ultrafast -tune zerolatency -bf 0 -g 60 \
  -pix_fmt yuv420p -c:a aac -b:a 48k -shortest \
  -video_track_timescale 90000 -movflags +faststart vfr_gap_audio_fixture.mp4
```

Do not re-encode this asset with a constant-frame-rate output mode: that would erase the sparse source PTS the test is meant to exercise. The instrumentation test exports a non-zero trim through both selected-clip and two-clip whole-project paths, then checks actual muxed sample timestamps against source anchors, the requested FPS cap, and planned duration. Android CI compiles this instrumentation test; it is not a substitute for running it on a supported device/emulator.
