package com.findmyphone.wakeword.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTest {
    @Test fun `pcm16 conversion`() {
        val f = shortArrayOf(0, 16384, -32768).toFloatPcm()
        assertEquals(listOf(0f, 0.5f, -1f), f.toList())
    }

    @Test fun `resampler streaming equals one-shot and preserves a tone`() {
        val inRate = 48000
        val x = FloatArray(inRate) { sin(2 * PI * 440 * it / inRate).toFloat() }
        val oneShot = LinearResampler(inRate).process(x)
        val r = LinearResampler(inRate)
        val chunked = (0 until x.size step 1234).flatMap { r.process(x.copyOfRange(it, minOf(x.size, it + 1234))).toList() }
        assertEquals(oneShot.size, chunked.size)
        for (i in oneShot.indices) assertEquals(oneShot[i], chunked[i], 1e-5f)
        assertTrue(abs(oneShot.size - 16000) <= 1)
        for (i in oneShot.indices step 97) assertEquals(sin(2 * PI * 440 * i / 16000).toFloat(), oneShot[i], 1e-3f)
    }

    @Test fun `resampler handles 44100`() {
        val r = LinearResampler(44100)
        val n = (0 until 100).sumOf { r.process(FloatArray(441)).size }
        assertTrue(abs(n - 16000) <= 1, "got $n")
    }
}
