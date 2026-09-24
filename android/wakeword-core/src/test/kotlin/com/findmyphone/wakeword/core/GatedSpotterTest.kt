package com.findmyphone.wakeword.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val SR = 16000
private const val CHUNK = 1600 // 100 ms

/** "speech" = any sample with |x| > 0.1 */
private class AmplitudeGate : SpeechGate {
    override fun isSpeech(samples: FloatArray) = samples.any { kotlin.math.abs(it) > 0.1f }
    override fun reset() {}
}

/** Fires "KW" whenever it sees the marker value 0.77 (the "keyword"). Records everything fed. */
private class FakeDecoder : KeywordDecoder {
    var open = false
    val fed = ArrayList<Float>()
    var opened = 0
    override fun openStream() { check(!open); open = true; opened++ }
    override fun accept(samples: FloatArray): List<String> {
        check(open) { "fed while closed" }
        samples.forEach { fed.add(it) }
        return if (samples.any { it == 0.77f }) listOf("KW") else emptyList()
    }
    override fun closeStream() { check(open); open = false }
}

private fun silence(s: Double) = FloatArray((s * SR).toInt())
private fun speech(s: Double, v: Float = 0.5f) = FloatArray((s * SR).toInt()) { v }
private fun keyword() = FloatArray(CHUNK) { if (it == 800) 0.77f else 0.5f }

private fun GatedSpotter.run(vararg parts: FloatArray): List<Detection> {
    val all = parts.reduce { a, b -> a + b }
    val out = ArrayList<Detection>()
    var i = 0
    while (i < all.size) { out += accept(all.copyOfRange(i, minOf(all.size, i + CHUNK))); i += CHUNK }
    return out + flush()
}

class GatedSpotterTest {
    @Test fun `decoder idle in silence`() {
        val d = FakeDecoder()
        val s = GatedSpotter(AmplitudeGate(), d)
        s.run(silence(30.0))
        assertEquals(0, d.opened)
        assertEquals(0.0, s.stats.dutyCycle)
    }

    @Test fun `preroll is replayed on activation`() {
        val d = FakeDecoder()
        val pre = FloatArray(CHUNK * 10) { 0.01f * (it / CHUNK) } // quiet ramp: below gate, must still reach decoder
        GatedSpotter(AmplitudeGate(), d, EngineConfig(prerollS = 0.5)).run(pre, speech(0.1))
        // last 0.5 s of pre-roll (5 chunks), then the speech chunk
        assertEquals(0.05f, d.fed[0], 1e-6f)
        assertEquals(0.5f, d.fed[5 * CHUNK])
    }

    @Test fun `detects and reports audio-clock time`() {
        val dets = GatedSpotter(AmplitudeGate(), FakeDecoder()).run(silence(1.0), keyword(), silence(2.0))
        assertEquals(listOf(Detection("KW", 1.1)), dets)
    }

    @Test fun `stream closes after hangover and tail pad is fed`() {
        val d = FakeDecoder()
        val s = GatedSpotter(AmplitudeGate(), d, EngineConfig(prerollS = 0.0, hangoverS = 1.0, tailPadS = 0.8))
        s.run(speech(0.5), silence(3.0))
        assertFalse(d.open)
        assertEquals(1, d.opened)
        // 0.5 speech + 1.0 hangover + 0.8 pad
        assertEquals((2.3 * SR).toInt(), d.fed.size)
    }

    @Test fun `new utterance after long pause opens a fresh stream`() {
        val d = FakeDecoder()
        GatedSpotter(AmplitudeGate(), d).run(speech(0.5), silence(3.0), speech(0.5), silence(3.0))
        assertEquals(2, d.opened)
    }

    @Test fun `short pause keeps the same stream`() {
        val d = FakeDecoder()
        val s = GatedSpotter(AmplitudeGate(), d)
        s.accept(speech(0.1)); s.accept(silence(0.1)); s.accept(silence(0.1)); s.accept(speech(0.1))
        assertTrue(s.isActive)
        assertEquals(1, d.opened)
    }

    @Test fun `cooldown suppresses repeats`() {
        val parts = arrayOf(silence(1.0), keyword(), silence(0.5), keyword(), silence(2.0))
        assertEquals(1, GatedSpotter(AmplitudeGate(), FakeDecoder(), EngineConfig(cooldownS = 5.0)).run(*parts).size)
        assertEquals(2, GatedSpotter(AmplitudeGate(), FakeDecoder(), EngineConfig(cooldownS = 0.1)).run(*parts).size)
    }

    @Test fun `detection gate`() {
        val g = DetectionGate(2.0)
        assertTrue(g.allow("A", 10.0)); assertFalse(g.allow("A", 11.9)); assertTrue(g.allow("B", 11.9)); assertTrue(g.allow("A", 12.0))
    }
}
