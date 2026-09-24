"""Voice-activity gate. Cheap check that runs on every frame so the (costlier)
keyword spotter only runs while someone is talking."""
from __future__ import annotations

import numpy as np

SAMPLE_RATE = 16000


class SileroGate:
    """Silero VAD (~0.6 MB ONNX) via sherpa-onnx. Processes 512-sample windows."""

    window = 512

    def __init__(self, model_path: str, threshold: float = 0.4, num_threads: int = 1):
        import sherpa_onnx

        cfg = sherpa_onnx.VadModelConfig()
        cfg.silero_vad.model = str(model_path)
        cfg.silero_vad.threshold = threshold
        cfg.silero_vad.min_speech_duration = 0.1
        cfg.silero_vad.min_silence_duration = 0.1
        cfg.silero_vad.window_size = self.window
        cfg.sample_rate = SAMPLE_RATE
        cfg.num_threads = num_threads
        self._model = sherpa_onnx.VadModel.create(cfg)
        self._pending = np.zeros(0, dtype=np.float32)

    def is_speech(self, samples: np.ndarray) -> bool:
        """True if any complete window in (carry-over + samples) contains speech."""
        buf = np.concatenate([self._pending, samples])
        speech = False
        i = 0
        while i + self.window <= len(buf):
            if self._model.is_speech(buf[i : i + self.window]):
                speech = True
            i += self.window
        self._pending = buf[i:]
        return speech

    def reset(self) -> None:
        self._model.reset()
        self._pending = np.zeros(0, dtype=np.float32)


class EnergyGate:
    """Fallback gate: RMS above an adaptive noise floor. No model needed, much
    dumber — music and TV open it constantly."""

    def __init__(self, ratio_db: float = 9.0, floor_init: float = 1e-3):
        self.ratio = 10 ** (ratio_db / 20)
        self.floor = floor_init

    def is_speech(self, samples: np.ndarray) -> bool:
        rms = float(np.sqrt(np.mean(samples.astype(np.float64) ** 2)) + 1e-9)
        speech = rms > self.floor * self.ratio
        # track the floor slowly upward, quickly downward
        a = 0.995 if rms > self.floor else 0.9
        self.floor = a * self.floor + (1 - a) * rms
        return speech

    def reset(self) -> None:
        pass


class AlwaysOn:
    """No gating: spotter sees every sample (baseline for measuring the gate's cost)."""

    def is_speech(self, samples: np.ndarray) -> bool:
        return True

    def reset(self) -> None:
        pass
