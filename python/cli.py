"""Listen for wake words on the mic or a WAV file.

    python cli.py --keyword "hey buddy" --mic
    python cli.py --keyword "hey buddy" --keyword "find my phone" --wav recording.wav
    python cli.py --keyword "hey buddy" --mic --action "notify-send 'found you'"
"""
import argparse
import sys

from wakeword import actions
from wakeword.engine import EngineConfig, WakeWordEngine
from wakeword.keywords import Keyword, KeywordError, short_keyword_warning
from wakeword.sources import mic_source, wav_source


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--keyword", action="append", required=True, help="wake phrase (repeatable)")
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--mic", action="store_true")
    src.add_argument("--wav")
    ap.add_argument("--realtime", action="store_true", help="play WAV at real-time speed")
    ap.add_argument("--threshold", type=float, default=0.25, help="0..1, lower = more sensitive")
    ap.add_argument("--boost", type=float, default=1.0, help="higher = more sensitive")
    ap.add_argument("--gate", choices=["silero", "energy", "none"], default="silero")
    ap.add_argument("--cooldown", type=float, default=2.0)
    ap.add_argument("--ring", action="store_true", help="beep on detection")
    ap.add_argument("--action", help="shell command to run on detection ($WAKE_KEYWORD is set)")
    a = ap.parse_args()

    kws = [Keyword(k, boost=a.boost, threshold=a.threshold) for k in a.keyword]
    try:
        for k in kws:
            print("keyword:", k.to_sherpa_line())
            if w := short_keyword_warning(k):
                print("warning:", w, file=sys.stderr)
    except KeywordError as e:
        sys.exit(f"bad keyword: {e}")

    eng = WakeWordEngine(EngineConfig(kws, gate=a.gate, cooldown_s=a.cooldown))
    eng.on_detect(actions.log_action)
    if a.ring:
        eng.on_detect(actions.ring_action)
    if a.action:
        eng.on_detect(actions.command_action(a.action))

    source = mic_source() if a.mic else wav_source(a.wav, a.realtime)
    print("listening... (Ctrl-C to stop)" if a.mic else f"processing {a.wav}")
    try:
        eng.run(source)
    except KeyboardInterrupt:
        pass
    s = eng.stats
    print(f"audio {s.audio_s:.1f}s | spotter active {s.duty_cycle:.0%} | CPU {s.rtf:.2%} of one core")


if __name__ == "__main__":
    main()
