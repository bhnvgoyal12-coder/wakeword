package com.findmyphone.wakeword

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.findmyphone.wakeword.core.Detection
import com.findmyphone.wakeword.core.KeywordSpec
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Public entry point for the host app (launcher).
 *
 *   // settings screen
 *   val v = WakeWord.validate(ctx, KeywordSpec(text))  // v.error blocks, v.warning advises
 *   WakeWord.start(ctx, listOf(KeywordSpec(text)))
 *
 *   // home activity
 *   override fun onResume() { super.onResume(); WakeWord.ensureRunning(this) }
 *   WakeWord.addListener { d -> ringer.ring() }
 */
object WakeWord {
    /** Also sent as a package-local broadcast for components that aren't in-process listeners. */
    const val ACTION_DETECTED = "com.findmyphone.wakeword.DETECTED"
    const val EXTRA_KEYWORD = "keyword"

    sealed interface State {
        data object Stopped : State
        data object Listening : State
        data class Error(val message: String) : State
    }

    /** Mic level (dBFS, ~-90..0) and whether the VAD gate is open, ~10x per second. */
    data class Meter(val levelDb: Float, val speech: Boolean)

    /** A detection with wall-clock time, for display. */
    data class Event(val keyword: String, val wallTimeMs: Long)

    fun interface Listener { fun onWakeWord(detection: Detection) }
    fun interface StateListener { fun onState(state: State) }
    fun interface MeterListener { fun onMeter(meter: Meter) }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val stateListeners = CopyOnWriteArraySet<StateListener>()
    internal val meterListeners = CopyOnWriteArraySet<MeterListener>()
    private val recent = ArrayDeque<Event>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Last [MAX_RECENT] detections in this process, newest last (survives the UI closing, not process death). */
    val recentDetections: List<Event> get() = synchronized(recent) { recent.toList() }
    @Volatile var state: State = State.Stopped
        private set

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)
    fun addStateListener(l: StateListener) = stateListeners.add(l)
    fun removeStateListener(l: StateListener) = stateListeners.remove(l)
    /** Listeners are called on the main thread. Metering only runs while at least one is registered. */
    fun addMeterListener(l: MeterListener) = meterListeners.add(l)
    fun removeMeterListener(l: MeterListener) = meterListeners.remove(l)

    fun validate(context: Context, keyword: KeywordSpec): KeywordValidation =
        WakeWordEngine.validate(context, keyword)

    /** Save keywords, enable, and (re)start listening. Call while the app is in the foreground. */
    fun start(context: Context, keywords: List<KeywordSpec>) {
        saveKeywords(context, keywords)
        prefs(context).edit().putBoolean(KEY_ENABLED, true).apply()
        context.startForegroundService(WakeWordService.intent(context))
    }

    /** Idempotent; call from the home activity's onResume. */
    fun ensureRunning(context: Context) {
        if (isEnabled(context) && state != State.Listening) {
            context.startForegroundService(WakeWordService.intent(context))
        }
    }

    fun stop(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
        context.stopService(WakeWordService.intent(context))
    }

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Callable from any thread; listeners always run on the main thread. */
    internal fun publishState(s: State) {
        state = s
        main.post { for (l in stateListeners) l.onState(s) }
    }

    internal fun publishMeter(m: Meter) {
        for (l in meterListeners) l.onMeter(m)
    }

    internal fun dispatchDetection(context: Context, d: Detection) {
        synchronized(recent) {
            recent.addLast(Event(d.keyword, System.currentTimeMillis()))
            while (recent.size > MAX_RECENT) recent.removeFirst()
        }
        for (l in listeners) l.onWakeWord(d)
        context.sendBroadcast(Intent(ACTION_DETECTED).setPackage(context.packageName).putExtra(EXTRA_KEYWORD, d.keyword))
    }

    // Stored as lines of "boost<TAB>threshold<TAB>text".
    internal fun loadKeywords(context: Context): List<KeywordSpec> =
        prefs(context).getString(KEY_KEYWORDS, null).orEmpty().lines().filter { it.isNotBlank() }.mapNotNull {
            val p = it.split('\t', limit = 3)
            if (p.size == 3) KeywordSpec(p[2], p[0].toFloat(), p[1].toFloat()) else null
        }

    private fun saveKeywords(context: Context, keywords: List<KeywordSpec>) {
        prefs(context).edit()
            .putString(KEY_KEYWORDS, keywords.joinToString("\n") { "${it.boost}\t${it.threshold}\t${it.text}" })
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("wakeword", Context.MODE_PRIVATE)
    private const val MAX_RECENT = 50
    private const val KEY_ENABLED = "enabled"
    private const val KEY_KEYWORDS = "keywords"
}
