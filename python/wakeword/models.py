"""Locate / download the ONNX models (all hosted on sherpa-onnx GitHub releases)."""
from __future__ import annotations

import os
import tarfile
import urllib.request
from pathlib import Path

BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download"
KWS_NAME = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"
TTS_NAME = "vits-piper-en_US-libritts_r-medium"

MODELS_DIR = Path(os.environ.get("WAKEWORD_MODELS", Path(__file__).resolve().parents[2] / "models"))


def _fetch(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    print(f"downloading {url}")
    urllib.request.urlretrieve(url, tmp)
    tmp.rename(dest)


def _fetch_tarball(release: str, name: str) -> Path:
    d = MODELS_DIR / name
    if not d.is_dir():
        tb = MODELS_DIR / f"{name}.tar.bz2"
        if not tb.exists():
            _fetch(f"{BASE}/{release}/{name}.tar.bz2", tb)
        with tarfile.open(tb) as t:
            t.extractall(MODELS_DIR)
    return d


def kws_dir() -> Path:
    return _fetch_tarball("kws-models", KWS_NAME)


def vad_model() -> Path:
    p = MODELS_DIR / "silero_vad.onnx"
    if not p.exists():
        _fetch(f"{BASE}/asr-models/silero_vad.onnx", p)
    return p


def tts_dir() -> Path:
    return _fetch_tarball("tts-models", TTS_NAME)
