package com.findmyphone.wakeword

import android.content.res.AssetManager
import com.findmyphone.wakeword.core.KeywordDecoder
import com.findmyphone.wakeword.core.SpeechGate
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/** Asset layout produced by `./gradlew :wakeword-android:fetchModels` (see build.gradle.kts). */
object ModelAssets {
    const val KWS_DIR = "wakeword/kws"
    const val VAD = "wakeword/silero_vad.onnx"
    const val VOCAB = "wakeword/kws_vocab.tsv"
    private const val STEM = "epoch-12-avg-2-chunk-16-left-64.int8.onnx"
    const val ENCODER = "$KWS_DIR/encoder-$STEM"
    const val DECODER = "$KWS_DIR/decoder-$STEM"
    const val JOINER = "$KWS_DIR/joiner-$STEM"
    const val TOKENS = "$KWS_DIR/tokens.txt"
    /** sherpa insists on a keywords file at construction; real keywords are passed per stream. */
    const val DEFAULT_KEYWORDS = "$KWS_DIR/keywords.txt"
}

/**
 * sherpa-onnx open-vocabulary keyword spotter. [keywords] is the "/"-joined token form from
 * KeywordSpec.sherpaKeywords(). Settings mirror python EngineConfig defaults.
 */
class SherpaKeywordDecoder(
    assets: AssetManager,
    private val keywords: String,
    maxActivePaths: Int = 4,
    numThreads: Int = 1,
) : KeywordDecoder {
    private val spotter = KeywordSpotter(
        assets,
        KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = ModelAssets.ENCODER,
                    decoder = ModelAssets.DECODER,
                    joiner = ModelAssets.JOINER,
                ),
                tokens = ModelAssets.TOKENS,
                numThreads = numThreads,
                modelType = "zipformer2",
            ),
            maxActivePaths = maxActivePaths,
            keywordsFile = ModelAssets.DEFAULT_KEYWORDS,
            numTrailingBlanks = 1,
        ),
    )
    private var stream: OnlineStream? = null

    override fun openStream() {
        stream = spotter.createStream(keywords)
    }

    override fun accept(samples: FloatArray): List<String> {
        val s = stream ?: return emptyList()
        s.acceptWaveform(samples, 16000)
        var out: MutableList<String>? = null
        while (spotter.isReady(s)) {
            spotter.decode(s)
            val kw = spotter.getResult(s).keyword
            if (kw.isNotEmpty()) {
                spotter.reset(s)
                (out ?: ArrayList<String>().also { out = it }).add(kw)
            }
        }
        return out ?: emptyList()
    }

    override fun closeStream() {
        stream?.release()
        stream = null
    }

    fun release() {
        closeStream()
        spotter.release()
    }
}

/** Silero VAD: speech if any 512-sample window's probability exceeds [threshold]. */
class SileroSpeechGate(assets: AssetManager, private val threshold: Float = 0.4f) : SpeechGate {
    private val vad = Vad(
        assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = ModelAssets.VAD,
                threshold = threshold,
                minSilenceDuration = 0.1f,
                minSpeechDuration = 0.1f,
                windowSize = WINDOW,
            ),
            sampleRate = 16000,
            numThreads = 1,
        ),
    )
    private var pending = FloatArray(0)

    override fun isSpeech(samples: FloatArray): Boolean {
        val buf = pending + samples
        var speech = false
        var i = 0
        while (i + WINDOW <= buf.size) {
            if (vad.compute(buf.copyOfRange(i, i + WINDOW)) > threshold) speech = true
            i += WINDOW
        }
        pending = buf.copyOfRange(i, buf.size)
        return speech
    }

    override fun reset() {
        vad.reset()
        pending = FloatArray(0)
    }

    fun release() = vad.release()

    private companion object { const val WINDOW = 512 }
}
