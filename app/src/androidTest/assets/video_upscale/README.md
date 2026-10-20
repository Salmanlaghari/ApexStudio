# Video upscale device-smoke fixture

`source_with_audio.mp4` remuxes the repository's existing synthetic fixtures without re-encoding:

```sh
ffmpeg -i ../audio/overlay_silent_base.mp4 -i ../audio/pcm_extractor_fixture.m4a \
  -map 0:v:0 -map 1:a:0 -c copy source_with_audio.mp4
```

It contains a one-second 160×90, 10 fps H.264 video and the existing half-second AAC fixture audio. `source_with_long_audio.mp4` uses that same synthetic video with the existing AAC fixture looped to about 1.51 seconds, exercising trimming a longer audio track without changing video duration. No user media or newly sourced third-party material is included. See `../audio/README.md` for source fixture descriptions. `VideoUpscaleInstrumentedTest` checks x3 dimensions, video frame timestamps, passthrough audio packet timestamps/payload, and that measured muxed AAC duration does not outlast the processed video. The test requires a supported Android device and is not run by host Robolectric tests.
