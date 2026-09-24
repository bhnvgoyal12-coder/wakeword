"""Score the engine on a dataset from make_dataset.py.

    python -m eval.evaluate ../data/hey_buddy                 # default settings
    python -m eval.evaluate ../data/hey_buddy --sweep         # threshold sweep -> pick operating point
    python -m eval.evaluate ../data/hey_buddy --gates         # silero vs energy vs no gate (CPU/recall)

Metrics
  recall      fraction of spoken keywords detected (miss rate = 1 - recall)
  FA/h        false activations per hour of non-keyword audio (the number users feel)
  latency     detection time minus end of the spoken keyword (median / p90)
  duty        fraction of audio the neural spotter actually ran on (VAD gate saves the rest)
  CPU         CPU-seconds per audio-second on THIS machine, single thread. A mid-range phone
              core is very roughly 2-4x slower; measure on device before trusting battery maths.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from wakeword.engine import EngineConfig, WakeWordEngine
from wakeword.keywords import Keyword
from wakeword.sources import chunks, load_wav

MATCH_AFTER_END_S = 2.0


@dataclass
class Result:
    label: str
    cond: str
    recall: float
    fa_per_hour: float
    fa_count: int
    fa_near_miss: int
    fa_texts: list
    lat_med: float
    lat_p90: float
    duty: float
    cpu: float


def _run(wav: Path, cfg: EngineConfig):
    eng = WakeWordEngine(cfg)
    dets = eng.run(chunks(load_wav(str(wav))))
    return [d.time_s for d in dets], eng.stats


def evaluate_one(data: Path, cond: str, label: str, cfg: EngineConfig) -> Result:
    meta = json.loads((data / "truth.json").read_text())
    truth = meta["positives"]

    pos_times, pos_stats = _run(data / f"pos_{cond}.wav", cfg)
    hit = [False] * len(truth)
    lat, stray = [], 0
    for t in pos_times:
        for i, p in enumerate(truth):
            if not hit[i] and p["start"] <= t <= p["end"] + MATCH_AFTER_END_S:
                hit[i] = True
                lat.append(t - p["end"])
                break
        else:
            stray += 1

    neg_times, neg_stats = _run(data / f"neg_{cond}.wav", cfg)
    fa_texts = []
    for t in neg_times:  # the segment being spoken (or just finished) when it fired
        seg = max((s for s in meta["negative_log"] if s["t"] <= t), key=lambda s: s["t"], default=None)
        fa_texts.append(seg)
    near = sum(1 for s in fa_texts if s and s["near_miss"])
    fa = len(neg_times) + stray
    hours = (meta["neg_seconds"]) / 3600
    audio = pos_stats.audio_s + neg_stats.audio_s
    return Result(
        label=label,
        cond=cond,
        recall=sum(hit) / len(truth),
        fa_per_hour=fa / hours,
        fa_count=fa,
        fa_near_miss=near,
        fa_texts=[s["text"] for s in fa_texts if s],
        lat_med=float(np.median(lat)) if lat else float("nan"),
        lat_p90=float(np.percentile(lat, 90)) if lat else float("nan"),
        duty=neg_stats.duty_cycle,  # negatives stream = the "phone on a table all day" case
        cpu=(pos_stats.vad_cpu_s + pos_stats.kws_cpu_s + neg_stats.vad_cpu_s + neg_stats.kws_cpu_s) / audio,
    )


def _job(args):
    return evaluate_one(*args)


def table(results: list[Result]) -> str:
    rows = [
        "| setting | noise | recall | FA/h (near-miss) | latency med / p90 | spotter duty | CPU/core |",
        "|---|---|---|---|---|---|---|",
    ]
    for r in results:
        rows.append(
            f"| {r.label} | {r.cond} | {r.recall:.0%} | {r.fa_per_hour:.1f} ({r.fa_near_miss}) | "
            f"{r.lat_med:.2f}s / {r.lat_p90:.2f}s | {r.duty:.0%} | {r.cpu:.2%} |"
        )
    return "\n".join(rows)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("data", type=Path)
    ap.add_argument("--sweep", action="store_true")
    ap.add_argument("--gates", action="store_true")
    ap.add_argument("--threshold", type=float, default=0.25)
    ap.add_argument("--boost", type=float, default=1.0)
    ap.add_argument("--conds", default="clean,snr10,snr0")
    ap.add_argument("--jobs", type=int, default=4)
    ap.add_argument("--show-fa", action="store_true", help="list phrases that caused false alarms")
    ap.add_argument("--paths", type=int, default=None, help="override max_active_paths")
    a = ap.parse_args()
    keyword = json.loads((a.data / "truth.json").read_text())["keyword"]
    conds = a.conds.split(",")

    extra = {} if a.paths is None else {"max_active_paths": a.paths}

    def cfg(boost=a.boost, th=a.threshold, **overrides):
        return EngineConfig([Keyword(keyword, boost, th)], **{**extra, **overrides})

    settings: list[tuple[str, EngineConfig]] = []
    if a.sweep:
        for boost in [1.0, 2.0]:
            for th in [0.15, 0.25, 0.35, 0.45]:
                settings.append((f"boost {boost} th {th}", cfg(boost, th)))
    if a.gates:
        for g in ["silero", "energy", "none"]:
            settings.append((f"gate {g}", cfg(gate=g)))
    if not settings:
        settings.append((f"th {a.threshold} boost {a.boost}", cfg()))
    if a.paths is not None:
        settings = [(f"{label}, beam {a.paths}", c) for label, c in settings]

    jobs = [(a.data, cond, label, c) for label, c in settings for cond in conds]
    with ProcessPoolExecutor(a.jobs) as ex:
        results = list(ex.map(_job, jobs))
    print(f"keyword: {keyword!r}  data: {a.data}")
    print(table(results))
    if a.show_fa:
        for r in results:
            if r.fa_texts:
                print(f"\nfalse alarms, {r.label} / {r.cond}:")
                for text, n in Counter(r.fa_texts).most_common():
                    print(f"  {n}x  {text}")


if __name__ == "__main__":
    main()
