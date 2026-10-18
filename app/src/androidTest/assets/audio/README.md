# AAC decoding test fixture

`pcm_extractor_fixture.m4a` is a generated 440 Hz sine tone, approximately 0.5 seconds, encoded as 44.1 kHz stereo AAC-LC in an M4A container. It contains no recorded speech or third-party music.

The integration test requires a real Android codec and checks normalized nonzero mono 16 kHz output with a duration tolerance for encoder padding. It belongs in Android instrumentation, not a mocked JVM codec test.

Equivalent generation command (exact bytes can vary with the FFmpeg version):

```sh
ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=44100:duration=0.5' \
  -ac 2 -c:a aac -b:a 48k pcm_extractor_fixture.m4a
```

Committed fixture SHA-256: `bdeef8abe894cfeb2c5ff514bc9a110a7bd7ed95c1cdd3865b4111b499fd24f1`.

## English acoustic-alignment fixture

`english_alignment_fixture.wav` is LibriSpeech dev-clean utterance `1272-128104-0000`, converted from FLAC to 16 kHz mono PCM-16 WAV with FFmpeg. Audio content and transcript are unchanged; the container/encoding changed. It is test-only, not a production voice sample or a promised recognition result.

LibriSpeech was prepared by Vassil Panayotov with assistance from Daniel Povey, based on LibriVox read audiobooks. The corpus is distributed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Attribution and original resource: [OpenSLR SLR12](https://www.openslr.org/12). This redistribution is not endorsed by its creators.

The exact source is the first row of [`hf-internal-testing/librispeech_asr_dummy`, pinned revision `5be91486e11a2d616f4ec5db8d3fd248585ac07a`](https://huggingface.co/datasets/hf-internal-testing/librispeech_asr_dummy/resolve/5be91486e11a2d616f4ec5db8d3fd248585ac07a/clean/validation-00000-of-00001.parquet). Transcript: “MISTER QUILTER IS THE APOSTLE OF THE MIDDLE CLASSES AND WE ARE GLAD TO WELCOME HIS GOSPEL”. Duration: 5.855 seconds.

`scripts/verify-english-aligner.py` runs the actual bundled acoustic model on this fixture on a host CPU, without network access. Its greedy output has errors; the script prints them rather than asserting perfect transcription. With `--record`, it records actual log probabilities plus SHA-256/provenance into `app/src/test/resources/ai/english-alignment-logprobs.*` for deterministic JVM forced-alignment regressions. These recorded emissions test the Kotlin alignment algorithm, not Android-native inference. Android execution remains a separate instrumentation test; compilation is not device acceptance.
