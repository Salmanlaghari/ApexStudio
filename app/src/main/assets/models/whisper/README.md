# Optional Whisper tiny multilingual ONNX model pack

These are the multilingual Whisper tiny encoder/decoder weights, quantized to int8, plus the Sherpa-ONNX token table. Standard Release APKs omit these large files to keep the app download smaller. Users can install the checksum-pinned pack from **AI tools → Optional AI models**; the three files are verified and stored in app-private storage. Once installed, transcription runs locally without inference-time network access. Regression builds may include bundled weights with `-PincludeBundledAiModels=true`.

## Provenance and integrity

- Canonical Sherpa-ONNX Whisper export: [export-onnx documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/export-onnx.html)
- Model distribution: [`csukuangfj/sherpa-onnx-whisper-tiny`](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny)
- Immutable Hugging Face revision: [`65176e2deb88badc814a94058666cadccc29b61c`](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/65176e2deb88badc814a94058666cadccc29b61c)
- Upstream Whisper project and license: [openai/whisper](https://github.com/openai/whisper); the accompanying `LICENSE` is the full MIT license text (Copyright (c) 2022 OpenAI).

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `tiny-encoder.int8.onnx` | 12,937,772 | `d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434` |
| `tiny-decoder.int8.onnx` | 89,855,401 | `d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925` |
| `tiny-tokens.txt` | 816,730 | `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` |
| **Total installed pack** | **103,609,903** | — |

After download, the model uses about **104 MB of local storage** (before package compression). Its multilingual metadata supports language selection and auto-detection, but this integration has not been accuracy-tested across all languages. Whisper timestamps remain segment-level; token-to-word splitting does not create genuine word timing. See `LocalWhisperSubtitles` timing precision and its separate explicit CTC forced-alignment primitive.
