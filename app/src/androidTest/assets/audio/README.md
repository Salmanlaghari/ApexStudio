# AAC decoding test fixture

`pcm_extractor_fixture.m4a` is a generated 440 Hz sine tone, approximately 0.5 seconds, encoded as 44.1 kHz stereo AAC-LC in an M4A container. It contains no recorded speech or third-party music.

The integration test requires a real Android codec and checks normalized nonzero mono 16 kHz output with a duration tolerance for encoder padding. It belongs in Android instrumentation, not a mocked JVM codec test.

Equivalent generation command (exact bytes can vary with the FFmpeg version):

```sh
ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=0.5' \
  -ac 2 -c:a aac -b:a 48k pcm_extractor_fixture.m4a
```

Committed fixture SHA-256: `bdeef8abe894cfeb2c5ff514bc9a110a7bd7ed95c1cdd3865b4111b499fd24f1`.
