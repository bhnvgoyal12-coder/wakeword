"""What happens on a detection. In the phone app this is 'ring at full volume'."""
from __future__ import annotations

import subprocess
import sys

from .engine import Detection


def log_action(d: Detection) -> None:
    print(f"[{d.time_s:9.2f}s] WAKE: {d.keyword}", flush=True)


def ring_action(d: Detection) -> None:
    """Best-effort audible ring on a desktop (terminal bell + beep if available)."""
    sys.stdout.write("\a")
    sys.stdout.flush()
    try:
        import numpy as np
        import sounddevice as sd

        t = np.arange(int(0.6 * 16000)) / 16000
        sd.play(0.3 * np.sin(2 * np.pi * 880 * t).astype("float32"), 16000)
    except Exception:
        pass


def command_action(cmd: str):
    def _run(d: Detection) -> None:
        subprocess.Popen(cmd, shell=True, env={"WAKE_KEYWORD": d.keyword, "WAKE_TIME": str(d.time_s)})

    return _run
