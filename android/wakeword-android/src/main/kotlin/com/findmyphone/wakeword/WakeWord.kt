package com.findmyphone.wakeword

import android.content.Context
import android.content.Intent
import com.findmyphone.wakeword.core.Detection
import com.findmyphone.wakeword.core.KeywordSpec
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Public entry point for the host app (launcher).
 *
 *   // settings screen
 *   WakeWord.validate(ctx, KeywordSpec(text))?.let { showWarning(it) }
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

    fun interface Listener { fun onWakeWord(detection: Detection) }
    fun interface StateListener { fun onState(state: State) }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val stateListeners = CopyOnWriteArraySet<StateListener>()
    @Volatile var state: State = State.Stopped
        private set

    fun addListener(l: Listener) = listeners.add(l)
    fun removeListener(l: Listener) = listeners.remove(l)
    fun addStateListener(l: StateListener) = stateListeners.add(l)
    fun removeStateListener(l: StateListener) = stateListeners.remove(l)

    fun validate(context: Context, keyword: KeywordSpec): String? = WakeWordEngine.validate(context, keyword)

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

    internal fun publishState(s: State) {
        state = s
        for (l in stateListeners) l.onState(s)
    }

    internal fun dispatchDetection(context: Context, d: Detection) {
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
    private const val KEY_ENABLED = "enabled"
    private const val KEY_KEYWORDS = "keywords"
}
