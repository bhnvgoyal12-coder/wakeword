"""Golden keyword->pieces pairs from the reference sentencepiece library.
Both the Python and Kotlin tokenizers are tested against this file."""
import random
import string
import sys

import sentencepiece as spm

sp = spm.SentencePieceProcessor(model_file=sys.argv[1])
rnd = random.Random(1)
phrases = ["HEY BUDDY", "FIND MY PHONE", "OKAY JARVIS", "HELLO WORLD", "XYZZY", "WHERE ARE YOU",
           "MARCO POLO", "HEY SIRI", "LOVE AND PEACE", "DON'T PANIC", "A", "PHONE PHONE PHONE"]
for _ in range(300):
    phrases.append(" ".join("".join(rnd.choice(string.ascii_uppercase + "'") for _ in range(rnd.randint(1, 10)))
                            for _ in range(rnd.randint(1, 4))))
with open(sys.argv[2], "w", encoding="utf-8") as f:
    for p in phrases:
        f.write(p + "\t" + " ".join(sp.encode(p, out_type=str)) + "\n")
