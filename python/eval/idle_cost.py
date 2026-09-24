"""CPU cost in a realistic 'phone on a table' day: mostly ambient noise, occasional talk.

    python -m eval.idle_cost --minutes 10 --speech-fraction 0.1

Reports, per gate, how often the neural spotter runs and total CPU per audio-second.
"""
from __future__ import annotations

import argparse
import random
from pathlib import Path

import numpy as np

from eval.make_dataset import colored_noise, rms
from eval.tts import say
from wakeword.engine import EngineConfig, WakeWordEngine
from wakeword.keywords import Keyword
from wakeword.sources import chunks
from wakeword.vad import SAMPLE_RATE

SENTENCES = [s for s in (Path(__file__).parent / "sentences.txt").read_text().splitlines() if s.strip()]


def build(minutes: float, speech_fraction: float, noise_dbfs: float, seed: int) -> np.ndarray:
    rng = random.Random(seed)
    nrng = np.random.default_rng(seed)
    n = int(minutes * 60 * SAMPLE_RATE)
    x = colored_noise(n, nrng, "pink") * (10 ** (noise_dbfs / 20))
    speech_n = 0
    while speech_n < speech_fraction * n:
        a = say(rng.choice(SENTENCES), rng.randrange(900)) * 0.3
        pos = rng.randrange(0, n - len(a))
        x[pos : pos + len(a)] += a
        speech_n += len(a)
    return x.astype(np.float32)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--minutes", type=float, default=10)
    ap.add_argument("--speech-fraction", type=float, default=0.1)
    ap.add_argument("--noise-dbfs", type=float, default=-50, help="ambient noise level (-50 quiet room, -35 busy)")
    ap.add_argument("--seed", type=int, default=0)
    a = ap.parse_args()
    x = build(a.minutes, a.speech_fraction, a.noise_dbfs, a.seed)
    print(f"{a.minutes} min, ~{a.speech_fraction:.0%} speech, ambient {a.noise_dbfs} dBFS (rms {rms(x):.4f})")
    print("| gate | spotter duty | CPU/core | false alarms |")
    print("|---|---|---|---|")
    for gate in ["silero", "energy", "none"]:
        eng = WakeWordEngine(EngineConfig([Keyword("hey buddy")], gate=gate))
        dets = eng.run(chunks(x))
        s = eng.stats
        print(f"| {gate} | {s.duty_cycle:.0%} | {s.rtf:.2%} | {len(dets)} |")


if __name__ == "__main__":
    main()
