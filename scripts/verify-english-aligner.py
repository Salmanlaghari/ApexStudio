#!/usr/bin/env python3
"""Offline CPU smoke test of the bundled English acoustic model (not Android acceptance).

Requires numpy and onnxruntime; performs no downloads/network calls. Optionally records the
real model log probabilities used by JVM forced-alignment regression tests with --record.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import wave
import numpy as np
import onnxruntime as ort


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--record", action="store_true", help="replace the committed real-emission JVM fixture")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    assets = root / "app/src/main/assets/models/alignment"
    model = assets / "wav2vec2-base-960h-int8.onnx"
    assert hashlib.sha256(model.read_bytes()).hexdigest() == "1d9a366c27b2966625cd5035ac3db8847c53bba617169912bb251b42975a3a22"
    audio = root / "app/src/androidTest/assets/audio/english_alignment_fixture.wav"
    with wave.open(str(audio)) as source:
        assert source.getframerate() == 16000 and source.getnchannels() == 1 and source.getsampwidth() == 2
        samples = np.frombuffer(source.readframes(source.getnframes()), dtype="<i2").astype(np.float32) / 32768.0
    waveform = (samples - samples.mean()) / np.sqrt(samples.var() + 1e-7)
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    session = ort.InferenceSession(str(model), sess_options=options, providers=["CPUExecutionProvider"])
    assert [(i.name, i.type) for i in session.get_inputs()] == [("input_values", "tensor(float)")]
    output = session.run(["logits"], {"input_values": waveform[None, :]})[0]
    assert output.shape[0] == 1 and output.shape[2] == 32 and np.isfinite(output).all()
    logits = output[0]
    shifted = logits - logits.max(axis=1, keepdims=True)
    log_probs = shifted - np.log(np.exp(shifted).sum(axis=1, keepdims=True))
    vocab = {value: token for token, value in json.loads((assets / "vocab.json").read_text()).items()}
    previous = -1
    tokens = []
    for token in logits.argmax(axis=1):
        if token != previous and token != 0:
            tokens.append(vocab[int(token)])
        previous = token
    greedy = "".join(tokens).replace("|", " ").strip()
    # This is a smoke/contract check, not an accuracy benchmark or a golden exact ASR transcript.
    assert "MIDDLE CLASSES" in greedy and "WELCOME HIS GOSPEL" in greedy, greedy
    print(f"Local CPU inference passed: {len(samples) / 16000:.3f}s audio; {len(logits)} real frames.")
    print(f"Greedy transcript (errors are not hidden): {greedy}")
    if args.record:
        resources = root / "app/src/test/resources/ai"
        resources.mkdir(parents=True, exist_ok=True)
        binary = resources / "english-alignment-logprobs.f32le"
        binary.write_bytes(log_probs.astype("<f4").tobytes())
        metadata = {
            "modelSha256": hashlib.sha256(model.read_bytes()).hexdigest(),
            "audioSha256": hashlib.sha256(audio.read_bytes()).hexdigest(),
            "logProbSha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
            "frames": int(log_probs.shape[0]), "vocabularySize": 32,
            "frameStrideMs": 20, "firstFrameCenterMs": 12,
            "transcript": "MISTER QUILTER IS THE APOSTLE OF THE MIDDLE CLASSES AND WE ARE GLAD TO WELCOME HIS GOSPEL",
            "durationMs": len(samples) * 1000 // 16000,
            "provenance": "LibriSpeech dev-clean 1272-128104-0000, CC-BY-4.0; see androidTest/assets/audio/README.md",
            "recordingRuntime": f"onnxruntime CPU {ort.__version__}; numpy {np.__version__}",
            "greedyTranscript": greedy,
        }
        (resources / "english-alignment-logprobs.json").write_text(json.dumps(metadata, indent=2) + "\n")
        print(f"Recorded {binary.stat().st_size} bytes of real emissions.")


if __name__ == "__main__":
    main()
