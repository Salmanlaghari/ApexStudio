# Color timeline device-test fixture

`sdr_fixture.mp4` is the repository's existing synthetic `video_upscale/source_with_audio.mp4` video stream, without audio, with explicit BT.709 SDR VUI/container metadata. No external media, code or assets were downloaded. It is only an instrumentation fixture; it is never substituted for user media.

Reproduce from the repository root with the existing host FFmpeg tool:

```sh
ffmpeg -i app/src/androidTest/assets/video_upscale/source_with_audio.mp4 \
  -map 0:v:0 -an -c:v copy \
  -bsf:v h264_metadata=colour_primaries=1:transfer_characteristics=1:matrix_coefficients=1 \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
  app/src/androidTest/assets/color_timeline/sdr_fixture.mp4
```

The short H.264/yuv420p fixture is 160×90, 10 fps and 1 second. Its deliberately low frame rate exercises the rule that requested export FPS is not proof of the encoded frame cadence. The comparison renderer must match actual output presentation timestamps before pairing frames.
