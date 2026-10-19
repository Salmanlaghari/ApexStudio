# Video upscale device-smoke fixture

`source_with_audio.mp4` remuxes the repository's existing synthetic fixtures without re-encoding:

```sh
ffmpeg -i ../audio/overlay_silent_base.mp4 -i ../audio/pcm_extractor_fixture.m4a \
  -map 0:v:0 -map 1:a:0 -c copy source_with_audio.mp4
```

It contains a one-second 160×90, 10 fps H.264 video and the existing half-second AAC fixture audio. No user media or newly sourced third-party material is included. See `../audio/README.md` for the source fixture descriptions. `VideoUpscaleInstrumentedTest` checks x3 dimensions, video frame timestamps, and passthrough audio packet timestamps/payload. The test requires a supported Android device and is not run by host Robolectric tests.
