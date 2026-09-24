package com.findmyphone.wakeword.core

import java.io.Reader

/**
 * Sentencepiece-unigram encoder for the KWS model's vocabulary (Viterbi over piece scores).
 * Port of python/wakeword/keywords.py; both are tested against shared/tokenizer_golden.tsv.
 * Vocab format (shared/kws_vocab.tsv): one "piece<TAB>score" per line.
 */
class UnigramTokenizer(vocab: Reader) {
    private val scores: Map<String, Double>
    private val maxLen: Int

    init {
        val m = HashMap<String, Double>()
        vocab.buffered().useLines { lines ->
            lines.filter { it.isNotEmpty() }.forEach { line ->
                val tab = line.indexOf('\t')
                m[line.substring(0, tab)] = line.substring(tab + 1).toDouble()
            }
        }
        scores = m
        maxLen = m.keys.maxOf { it.length }
    }

    fun encode(text: String): List<String> {
        val norm = normalize(text)
        if (norm.isEmpty()) throw KeywordException("keyword is empty")
        val bad = norm.filterNot { it in 'A'..'Z' || it == '\'' || it == ' ' }.toSet()
        if (bad.isNotEmpty()) {
            throw KeywordException("unsupported characters ${bad.sorted()}; use letters only (spell out numbers)")
        }
        val s = WORD_BOUNDARY + norm.replace(' ', WORD_BOUNDARY)
        val n = s.length
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val back = IntArray(n + 1)
        best[0] = 0.0
        for (end in 1..n) {
            for (start in maxOf(0, end - maxLen) until end) {
                if (best[start] == Double.NEGATIVE_INFINITY) continue
                val score = scores[s.substring(start, end)] ?: continue
                val cand = best[start] + score
                if (cand > best[end]) {
                    best[end] = cand
                    back[end] = start
                }
            }
        }
        if (best[n] == Double.NEGATIVE_INFINITY) throw KeywordException("cannot tokenize '$norm' with this model's vocabulary")
        val pieces = ArrayList<String>()
        var end = n
        while (end > 0) {
            val start = back[end]
            pieces.add(s.substring(start, end))
            end = start
        }
        return pieces.asReversed()
    }

    companion object {
        const val WORD_BOUNDARY = '▁'

        /** Uppercase + collapse whitespace; the model was trained on uppercase text. */
        fun normalize(text: String): String = text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
    }
}

class KeywordException(message: String) : IllegalArgumentException(message)
