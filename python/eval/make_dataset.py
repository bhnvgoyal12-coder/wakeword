"""Build a synthetic evaluation set for one keyword.

Produces, per SNR condition, two long "stream" WAVs plus ground truth:
  pos_<cond>.wav  -- keyword utterances (many TTS speakers, speeds, carrier phrases)
                     separated by negative speech and silence. truth: keyword spans.
  neg_<cond>.wav  -- ~N minutes of speech that does NOT contain the keyword, including
                     near-miss phrases, with noise-only gaps (like a phone on a table).

    python -m eval.make_dataset --keyword "hey buddy" --out ../data/hey_buddy \
        --confusable "hey body" --confusable "hey bobby"

Caveat: this is synthetic. TTS voices are cleaner and more uniform than people, and the
noise is generated, not recorded. Use it to compare settings, not as a promise.
"""
from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

import numpy as np
import soundfile as sf

from eval.tts import num_speakers, say
from wakeword.keywords import normalize
from wakeword.vad import SAMPLE_RATE

HERE = Path(__file__).parent
CARRIERS_BEFORE = ["", "", "", "um, ", "okay ", "so "]
CARRIERS_AFTER = ["", "", "", " where are you", " I need you", " are you there"]
CONDITIONS = {"clean": None, "snr10": 10.0, "snr0": 0.0}


def rms(x: np.ndarray) -> float:
    return float(np.sqrt(np.mean(x.astype(np.float64) ** 2)) + 1e-12)


def colored_noise(n: int, rng: np.random.Generator, kind: str) -> np.ndarray:
    white = rng.standard_normal(n)
    if kind == "white":
        x = white
    else:
        spec = np.fft.rfft(white)
        f = np.arange(len(spec)) + 1.0
        spec /= np.sqrt(f) if kind == "pink" else f  # pink 1/f power, brown 1/f^2
        x = np.fft.irfft(spec, n)
    return (x / rms(x)).astype(np.float32)


def auto_confusables(keyword: str) -> list[str]:
    words = normalize(keyword).lower().split()
    out = list(words) if len(words) > 1 else []
    if len(words) > 1:
        out.append(" ".join(reversed(words)))
        out.append(" ".join(words[:-1] + ["the"]))
    return out


def silence(sec: float) -> np.ndarray:
    return np.zeros(int(sec * SAMPLE_RATE), np.float32)


def build(args) -> None:
    rng = random.Random(args.seed)
    nrng = np.random.default_rng(args.seed)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    kw_norm = normalize(args.keyword)

    sentences = [s for s in (HERE / "sentences.txt").read_text().splitlines() if s.strip()]
    sentences = [s for s in sentences if kw_norm not in normalize(s.replace(",", ""))]
    confusables = auto_confusables(args.keyword) + args.confusable
    speakers = list(range(num_speakers()))

    def tts(text):
        return say(text, rng.choice(speakers), speed=rng.uniform(0.85, 1.2))

    # ---- positive stream --------------------------------------------------------
    parts, truth, t = [silence(1.0)], [], 1.0
    for i in range(args.positives):
        pre, post = rng.choice(CARRIERS_BEFORE), rng.choice(CARRIERS_AFTER)
        spk, spd = rng.choice(speakers), rng.uniform(0.85, 1.2)
        # render keyword separately so we know exactly where it is
        seg = []
        if pre:
            seg.append(say(pre.strip(), spk, spd))
        start = t + sum(len(s) for s in seg) / SAMPLE_RATE
        kw_audio = say(args.keyword, spk, spd)
        seg.append(kw_audio)
        end = start + len(kw_audio) / SAMPLE_RATE
        if post:
            seg.append(say(post.strip(), spk, spd))
        seg.append(silence(rng.uniform(1.0, 2.5)))
        if rng.random() < 0.5:  # some unrelated talk between calls
            seg.append(tts(rng.choice(sentences)))
            seg.append(silence(rng.uniform(1.0, 2.5)))
        truth.append({"start": round(start, 3), "end": round(end, 3), "speaker": spk})
        for s in seg:
            parts.append(s)
            t += len(s) / SAMPLE_RATE
        if i % 20 == 0:
            print(f"positives {i}/{args.positives}")
    pos = np.concatenate(parts)

    # ---- negative stream --------------------------------------------------------
    parts, t, n_conf = [silence(1.0)], 1.0, 0
    target = args.negative_minutes * 60
    seg_log = []
    while t < target:
        near = bool(confusables) and rng.random() < 0.25
        text = rng.choice(confusables) if near else rng.choice(sentences)
        n_conf += near
        a = tts(text)
        seg_log.append({"t": round(t, 2), "end": round(t + len(a) / SAMPLE_RATE, 2), "text": text, "near_miss": near})
        gap = silence(rng.uniform(0.5, 1.5) if rng.random() < 0.7 else rng.uniform(4, 12))
        parts += [a, gap]
        t += (len(a) + len(gap)) / SAMPLE_RATE
        if len(parts) % 100 == 0:
            print(f"negatives {t/60:.1f}/{args.negative_minutes} min")
    neg = np.concatenate(parts)

    # ---- noise conditions -------------------------------------------------------
    # One babble track reused as noise: overlapping far-away talkers (TV/cafe-like).
    babble = np.zeros(len(neg), np.float32)
    for _ in range(4):
        babble += np.roll(neg, nrng.integers(len(neg)))
    babble /= rms(babble)

    def mix(clean: np.ndarray, snr_db: float | None) -> np.ndarray:
        if snr_db is None:
            return clean
        speech_rms = rms(clean[np.abs(clean) > 1e-4])
        n = len(clean)
        kind = ["pink", "brown", "white", "babble"]
        noise = np.zeros(n, np.float32)
        # change noise type every ~20 s
        for s in range(0, n, 20 * SAMPLE_RATE):
            e = min(n, s + 20 * SAMPLE_RATE)
            k = kind[(s // (20 * SAMPLE_RATE)) % len(kind)]
            if k == "babble":
                off = int(nrng.integers(len(babble) - (e - s)))
                noise[s:e] = babble[off : off + e - s]
            else:
                noise[s:e] = colored_noise(e - s, nrng, k)
        noise *= speech_rms / (10 ** (snr_db / 20))
        y = clean + noise
        return (y / max(1.0, float(np.max(np.abs(y))))).astype(np.float32)

    for cond, snr in CONDITIONS.items():
        sf.write(out / f"pos_{cond}.wav", mix(pos, snr), SAMPLE_RATE, subtype="PCM_16")
        sf.write(out / f"neg_{cond}.wav", mix(neg, snr), SAMPLE_RATE, subtype="PCM_16")
    meta = {
        "keyword": args.keyword,
        "positives": truth,
        "confusables": confusables,
        "confusable_count": n_conf,
        "negative_log": seg_log,
        "pos_seconds": len(pos) / SAMPLE_RATE,
        "neg_seconds": len(neg) / SAMPLE_RATE,
        "conditions": CONDITIONS,
    }
    (out / "truth.json").write_text(json.dumps(meta, indent=1))
    print(f"wrote {out}: {len(truth)} positives ({len(pos)/SAMPLE_RATE/60:.1f} min), "
          f"{len(neg)/SAMPLE_RATE/60:.1f} min negatives incl. {n_conf} near-misses")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--keyword", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--positives", type=int, default=100)
    ap.add_argument("--negative-minutes", type=float, default=30)
    ap.add_argument("--confusable", action="append", default=[], help="near-miss phrase (repeatable)")
    ap.add_argument("--seed", type=int, default=0)
    build(ap.parse_args())


if __name__ == "__main__":
    main()
