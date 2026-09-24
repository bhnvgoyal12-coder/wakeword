"""Neural TTS (Piper LibriTTS-R, ~900 speakers) for generating test utterances."""
from __future__ import annotations

from functools import lru_cache

import numpy as np

from wakeword import models
from wakeword.vad import SAMPLE_RATE


@lru_cache(maxsize=1)
def _tts():
    import sherpa_onnx

    d = models.tts_dir()
    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(d / "en_US-libritts_r-medium.onnx"),
                tokens=str(d / "tokens.txt"),
                data_dir=str(d / "espeak-ng-data"),
            ),
            num_threads=2,
        ),
    )
    return sherpa_onnx.OfflineTts(cfg)


def num_speakers() -> int:
    return _tts().num_speakers


def say(text: str, speaker: int = 0, speed: float = 1.0) -> np.ndarray:
    """Return float32 16 kHz mono audio."""
    audio = _tts().generate(text, sid=speaker, speed=speed)
    x = np.asarray(audio.samples, dtype=np.float32)
    if audio.sample_rate != SAMPLE_RATE:
        # simple polyphase-free resample (linear); fine for test material
        n = int(len(x) * SAMPLE_RATE / audio.sample_rate)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return x
