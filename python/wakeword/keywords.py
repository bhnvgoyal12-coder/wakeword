"""Turn a user-typed phrase ("hey buddy") into the token line sherpa-onnx KWS wants.

sherpa's open-vocabulary spotter takes each keyword as a sequence of the
model's BPE pieces, e.g. ``▁HE Y ▁BU D D Y :1.5 #0.25 @HEY_BUDDY``.  The model
uses a sentencepiece *unigram* vocabulary, so encoding is a Viterbi search for
the highest-scoring segmentation.  This file implements that search directly
(no sentencepiece dependency) and is mirrored 1:1 by the Kotlin
``UnigramTokenizer`` so the phone and the harness produce identical tokens.
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

WORD_BOUNDARY = "▁"  # ▁
DEFAULT_VOCAB = Path(__file__).resolve().parents[2] / "shared" / "kws_vocab.tsv"

_ALLOWED = re.compile(r"^[A-Z' ]+$")


class KeywordError(ValueError):
    pass


def normalize(text: str) -> str:
    """Uppercase, collapse whitespace. The model was trained on uppercase GigaSpeech text."""
    return " ".join(text.upper().split())


class UnigramTokenizer:
    def __init__(self, vocab_path: str | Path = DEFAULT_VOCAB):
        self.scores: dict[str, float] = {}
        with open(vocab_path, encoding="utf-8") as f:
            for line in f:
                piece, score = line.rstrip("\n").split("\t")
                self.scores[piece] = float(score)
        self.max_len = max(len(p) for p in self.scores)

    def encode(self, text: str) -> list[str]:
        text = normalize(text)
        if not text:
            raise KeywordError("keyword is empty")
        if not _ALLOWED.match(text):
            bad = sorted(set(c for c in text if not _ALLOWED.match(c)))
            raise KeywordError(f"unsupported characters {bad}; use letters only (spell out numbers)")
        s = WORD_BOUNDARY + text.replace(" ", WORD_BOUNDARY)
        n = len(s)
        best = [float("-inf")] * (n + 1)
        back = [0] * (n + 1)
        best[0] = 0.0
        for end in range(1, n + 1):
            for start in range(max(0, end - self.max_len), end):
                if best[start] == float("-inf"):
                    continue
                score = self.scores.get(s[start:end])
                if score is None:
                    continue
                cand = best[start] + score
                if cand > best[end]:
                    best[end] = cand
                    back[end] = start
        if best[n] == float("-inf"):
            raise KeywordError(f"cannot tokenize {text!r} with this model's vocabulary")
        pieces = []
        end = n
        while end > 0:
            start = back[end]
            pieces.append(s[start:end])
            end = start
        return pieces[::-1]


@lru_cache(maxsize=4)
def default_tokenizer() -> UnigramTokenizer:
    return UnigramTokenizer()


@dataclass(frozen=True)
class Keyword:
    """One wake phrase plus its per-keyword tuning.

    boost      -- sherpa "keywords_score" (``:``). Higher = easier to trigger.
    threshold  -- sherpa "keywords_threshold" (``#``), 0..1. Lower = easier to trigger.
    """

    text: str
    boost: float = 1.0
    threshold: float = 0.25

    @property
    def label(self) -> str:
        return normalize(self.text).replace(" ", "_")

    def to_sherpa_line(self, tok: UnigramTokenizer | None = None) -> str:
        tok = tok or default_tokenizer()
        pieces = tok.encode(self.text)
        # Very short keywords (1-2 pieces) are a false-alarm magnet; warn loudly upstream.
        return f"{' '.join(pieces)} :{self.boost} #{self.threshold} @{self.label}"


def short_keyword_warning(kw: Keyword, tok: UnigramTokenizer | None = None) -> str | None:
    tok = tok or default_tokenizer()
    n = len(tok.encode(kw.text))
    words = len(normalize(kw.text).split())
    if n < 4 or words < 2:
        return (
            f"{kw.text!r} is short ({words} word(s), {n} tokens). Short wake words trigger falsely "
            "far more often; 2-3 word phrases with distinct sounds work best (e.g. 'hey buddy')."
        )
    return None
