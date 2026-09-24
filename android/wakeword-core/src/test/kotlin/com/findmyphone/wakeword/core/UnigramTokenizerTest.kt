package com.findmyphone.wakeword.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UnigramTokenizerTest {
    private val shared = File(System.getProperty("wakeword.shared") ?: "../../shared")
    private val tok = UnigramTokenizer(File(shared, "kws_vocab.tsv").reader())

    @Test fun `matches sentencepiece on golden file`() {
        val lines = File(shared, "tokenizer_golden.tsv").readLines().filter { it.isNotEmpty() }
        assert(lines.size > 300)
        for (line in lines) {
            val (text, pieces) = line.split('\t')
            assertEquals(pieces.split(' '), tok.encode(text), text)
        }
    }

    @Test fun `normalizes case and whitespace`() {
        assertEquals(listOf("▁HE", "Y", "▁BU", "D", "D", "Y"), tok.encode("  hey   Buddy "))
    }

    @Test fun `rejects unsupported input`() {
        for (bad in listOf("", "   ", "find phone 2", "héllo", "hey-buddy")) {
            assertFailsWith<KeywordException>(bad) { tok.encode(bad) }
        }
    }

    @Test fun `sherpa line matches python`() {
        assertEquals("▁HE Y ▁BU D D Y :1.5 #0.3 @HEY_BUDDY", KeywordSpec("hey buddy", 1.5f, 0.3f).toSherpaLine(tok))
        assertEquals(
            "▁HE Y ▁BU D D Y :1.0 #0.25 @HEY_BUDDY/▁FIND ▁MY ▁PH ONE :1.0 #0.25 @FIND_MY_PHONE",
            KeywordSpec.sherpaKeywords(listOf(KeywordSpec("hey buddy"), KeywordSpec("find my phone")), tok),
        )
    }

    @Test fun `short keyword warning`() {
        assertNotNull(KeywordSpec("buddy").shortWarning(tok))
        assertNull(KeywordSpec("find my phone").shortWarning(tok))
    }
}
