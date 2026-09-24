package com.findmyphone.wakeword.core

/** Cheap per-frame speech check (Silero VAD on device). */
interface SpeechGate {
    fun isSpeech(samples: FloatArray): Boolean
    fun reset()
}

/** The keyword spotter (sherpa-onnx on device). One "stream" = one span of speech. */
interface KeywordDecoder {
    fun openStream()
    /** Feed audio to the open stream; return labels of keywords that fired. */
    fun accept(samples: FloatArray): List<String>
    fun closeStream()
}

data class Detection(val keyword: String, val timeS: Double)

data class EngineConfig(
    val sampleRate: Int = 16000,
    val prerollS: Double = 0.5,
    val hangoverS: Double = 1.0,
    /** silence fed on deactivation so a keyword at the very end of speech still decodes */
    val tailPadS: Double = 0.8,
    val cooldownS: Double = 2.0,
)

class EngineStats {
    var audioS = 0.0; internal set
    var spotterAudioS = 0.0; internal set
    var activations = 0; internal set
    /** fraction of audio the neural spotter ran on */
    val dutyCycle: Double get() = if (audioS > 0) spotterAudioS / audioS else 0.0
}

/**
 * The always-on pipeline, platform-independent:
 *
 *   frames -> pre-roll ring -> SpeechGate -> KeywordDecoder -> DetectionGate -> detections
 *
 * The decoder only runs while someone is talking (+[EngineConfig.hangoverS]); the pre-roll
 * replays the audio from just before the VAD fired so the first syllable isn't lost.
 * Port of python/wakeword/engine.py. Not thread-safe: call from the audio thread only.
 */
class GatedSpotter(
    private val gate: SpeechGate,
    private val decoder: KeywordDecoder,
    private val config: EngineConfig = EngineConfig(),
) {
    val stats = EngineStats()
    private val detections = DetectionGate(config.cooldownS)
    private val preroll = ArrayDeque<FloatArray>()
    private var prerollN = 0
    private var active = false
    private var silenceN = 0
    private var clockN = 0L

    val isActive: Boolean get() = active

    fun accept(samples: FloatArray): List<Detection> {
        clockN += samples.size
        stats.audioS += samples.size.toDouble() / config.sampleRate
        val speech = gate.isSpeech(samples)
        val out = ArrayList<Detection>()
        if (!active && speech) {
            decoder.openStream()
            active = true
            silenceN = 0
            stats.activations++
            for (chunk in preroll) out += feed(chunk)
        }
        if (active) {
            out += feed(samples)
            silenceN = if (speech) 0 else silenceN + samples.size
            if (silenceN >= config.hangoverS * config.sampleRate) out += deactivate()
        }
        preroll.addLast(samples)
        prerollN += samples.size
        while (preroll.isNotEmpty() && prerollN - preroll.first().size >= config.prerollS * config.sampleRate) {
            prerollN -= preroll.removeFirst().size
        }
        return out
    }

    fun flush(): List<Detection> = if (active) deactivate() else emptyList()

    private fun deactivate(): List<Detection> {
        val out = feed(FloatArray((config.tailPadS * config.sampleRate).toInt()))
        decoder.closeStream()
        active = false
        return out
    }

    private fun feed(samples: FloatArray): List<Detection> {
        stats.spotterAudioS += samples.size.toDouble() / config.sampleRate
        val now = clockN.toDouble() / config.sampleRate
        return decoder.accept(samples)
            .filter { detections.allow(it, now) }
            .map { Detection(it, now) }
    }
}
