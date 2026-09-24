from pathlib import Path

import pytest

from wakeword.keywords import Keyword, KeywordError, UnigramTokenizer, short_keyword_warning

GOLDEN = Path(__file__).resolve().parents[2] / "shared" / "tokenizer_golden.tsv"


def test_matches_sentencepiece_golden():
    tok = UnigramTokenizer()
    for line in GOLDEN.read_text(encoding="utf-8").splitlines():
        text, pieces = line.split("\t")
        assert tok.encode(text) == pieces.split(), text


def test_normalizes_case_and_spaces():
    tok = UnigramTokenizer()
    assert tok.encode("  hey   Buddy ") == tok.encode("HEY BUDDY") == ["▁HE", "Y", "▁BU", "D", "D", "Y"]


@pytest.mark.parametrize("bad", ["", "   ", "find phone 2", "héllo", "hey-buddy"])
def test_rejects_unsupported(bad):
    with pytest.raises(KeywordError):
        UnigramTokenizer().encode(bad)


def test_sherpa_line():
    assert Keyword("hey buddy", boost=1.5, threshold=0.3).to_sherpa_line() == "▁HE Y ▁BU D D Y :1.5 #0.3 @HEY_BUDDY"


def test_short_keyword_warning():
    assert short_keyword_warning(Keyword("buddy"))
    assert short_keyword_warning(Keyword("find my phone")) is None
