"""Audio sources yielding float32 16 kHz mono chunks."""
from __future__ import annotations

import time
from typing import Iterator

import numpy as np

from .vad import SAMPLE_RATE

CHUNK = 1600  # 100 ms


def load_wav(path: str) -> np.ndarray:
    import soundfile as sf

    x, sr = sf.read(path, dtype="float32", always_2d=True)
    x = x.mean(axis=1)
    if sr != SAMPLE_RATE:
        n = int(len(x) * SAMPLE_RATE / sr)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)
    return x


def chunks(x: np.ndarray, size: int = CHUNK) -> Iterator[np.ndarray]:
    for i in range(0, len(x), size):
        yield x[i : i + size]


def wav_source(path: str, realtime: bool = False) -> Iterator[np.ndarray]:
    for c in chunks(load_wav(path)):
        yield c
        if realtime:
            time.sleep(len(c) / SAMPLE_RATE)


def mic_source(device=None) -> Iterator[np.ndarray]:
    try:
        import sounddevice as sd
    except ImportError as e:  # pragma: no cover
        raise SystemExit("--mic needs `pip install sounddevice` (and PortAudio)") from e
    with sd.InputStream(samplerate=SAMPLE_RATE, channels=1, dtype="float32", blocksize=CHUNK, device=device) as s:
        while True:
            data, _ = s.read(CHUNK)
            yield data[:, 0].copy()
