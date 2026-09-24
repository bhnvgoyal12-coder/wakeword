package com.findmyphone.wakeword.core

/**
 * One wake phrase and its tuning.
 * @param boost     sherpa keywords_score; higher = easier to trigger.
 * @param threshold sherpa keywords_threshold (0..1); lower = easier to trigger.
 */
data class KeywordSpec(val text: String, val boost: Float = 1.0f, val threshold: Float = 0.25f) {
    val label: String get() = UnigramTokenizer.normalize(text).replace(' ', '_')

    /** e.g. "▁HE Y ▁BU D D Y :1.0 #0.25 @HEY_BUDDY" */
    fun toSherpaLine(tok: UnigramTokenizer): String =
        "${tok.encode(text).joinToString(" ")} :$boost #$threshold @$label"

    /** Non-null if the phrase is likely to false-trigger (too short). Show it to the user. */
    fun shortWarning(tok: UnigramTokenizer): String? {
        val n = tok.encode(text).size
        val words = UnigramTokenizer.normalize(text).split(' ').size
        return if (n < 4 || words < 2) {
            "'$text' is short ($words word(s), $n tokens). Short wake words trigger falsely far more " +
                "often; 2-3 word phrases with distinct sounds work best (e.g. 'hey buddy')."
        } else null
    }

    companion object {
        /** Joined form accepted by KeywordSpotter.createStream(). */
        fun sherpaKeywords(specs: List<KeywordSpec>, tok: UnigramTokenizer): String =
            specs.joinToString("/") { it.toSherpaLine(tok) }
    }
}
