"""Render the WAV fixtures used by tests/test_engine.py.

TTS output is random on every call (VITS noise sampling), so tests use these
committed files instead of synthesizing on the fly. Regression tests only:
accuracy is measured by eval/, not here.

    python tools/make_test_fixtures.py
"""
from pathlib import Path

import soundfile as sf

from eval.tts import say

OUT = Path(__file__).resolve().parents[1] / "tests" / "fixtures"

FIXTURES = {
    "hey_buddy_a": ("hey buddy", 0),
    "hey_buddy_b": ("hey buddy", 123),
    "hey_buddy_c": ("hey buddy", 456),
    "find_my_phone": ("find my phone", 9),
    "neg_my_buddy": ("my buddy from college is getting married", 7),
    "neg_hey_there": ("hey there, how are you doing today", 8),
}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (text, spk) in FIXTURES.items():
        sf.write(OUT / f"{name}.wav", say(text, spk), 16000, subtype="PCM_16")
        print("wrote", name)
