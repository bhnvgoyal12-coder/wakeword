package com.findmyphone.wakeword.core

/** PCM16 -> float in [-1, 1). */
fun ShortArray.toFloatPcm(n: Int = size): FloatArray = FloatArray(n) { this[it] / 32768f }

/**
 * Streaming linear resampler, for feeding frames from an existing mic loop that runs at
 * 44.1/48 kHz (e.g. the clap/whistle detector) into the 16 kHz engine. Low-pass first if
 * the source has much energy above 8 kHz; for speech + phone mics linear is adequate.
 */
class LinearResampler(private val inRate: Int, private val outRate: Int = 16000) {
    private val step = inRate.toDouble() / outRate
    private var pos = 0.0          // position of next output sample, relative to `prev`
    private var prev = 0f
    private var primed = false

    fun process(input: FloatArray): FloatArray {
        if (inRate == outRate) return input
        if (input.isEmpty()) return input
        // virtual buffer: [prev] + input, indices -1 .. input.size-1
        val out = ArrayList<Float>((input.size / step).toInt() + 2)
        if (!primed) { prev = input[0]; primed = true; pos = 0.0 } // start aligned to input[0]
        var p = pos
        while (p <= input.size - 1) {
            val i = kotlin.math.floor(p).toInt()
            val frac = (p - i).toFloat()
            val a = if (i < 0) prev else input[i]
            val b = input[minOf(i + 1, input.size - 1)]
            out.add(a + (b - a) * frac)
            p += step
        }
        pos = p - input.size  // relative to the new `prev` (= last input sample) at index -1
        prev = input[input.size - 1]
        return out.toFloatArray()
    }
}
