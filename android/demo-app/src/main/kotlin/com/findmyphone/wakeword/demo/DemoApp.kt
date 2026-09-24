package com.findmyphone.wakeword.demo

import android.app.Application
import android.content.Context
import com.findmyphone.wakeword.RingAction
import com.findmyphone.wakeword.WakeWord

/**
 * Detection → ring wiring lives here, not in the Activity, so it keeps working with the
 * screen off / app closed — the same place your launcher would put it.
 */
class DemoApp : Application() {
    lateinit var ringer: RingAction
        private set

    override fun onCreate() {
        super.onCreate()
        ringer = RingAction(this)
        WakeWord.addListener {
            if (prefs(this).getBoolean(PREF_RING, false)) ringer.ring()
        }
    }

    companion object {
        const val PREF_RING = "ring"
        const val PREF_KEYWORD = "keyword"
        const val PREF_SENSITIVE = "sensitive"
        fun prefs(c: Context) = c.getSharedPreferences("demo", Context.MODE_PRIVATE)
    }
}
