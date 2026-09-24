"""Export the KWS model's sentencepiece pieces + scores to a plain TSV.

The TSV is what both tokenizers (Python `wakeword.keywords` and Kotlin
`UnigramTokenizer`) load, so neither the phone nor the harness needs the
sentencepiece library at runtime.

    python tools/export_vocab.py ../models/<kws-dir>/bpe.model ../shared/kws_vocab.tsv
"""
import sys

from sentencepiece import sentencepiece_model_pb2 as pb

NORMAL, UNKNOWN, CONTROL, USER_DEFINED = 1, 2, 3, 4


def main(src: str, dst: str) -> None:
    m = pb.ModelProto()
    with open(src, "rb") as f:
        m.ParseFromString(f.read())
    assert m.trainer_spec.model_type == pb.TrainerSpec.UNIGRAM, "tokenizer assumes a unigram model"
    with open(dst, "w", encoding="utf-8") as out:
        for p in m.pieces:
            if p.type == NORMAL:
                out.write(f"{p.piece}\t{p.score!r}\n")


if __name__ == "__main__":
    main(*sys.argv[1:3])
