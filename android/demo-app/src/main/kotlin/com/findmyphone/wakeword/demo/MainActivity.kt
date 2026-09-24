package com.findmyphone.wakeword.demo

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.findmyphone.wakeword.WakeWord
import com.findmyphone.wakeword.core.KeywordSpec
import java.text.DateFormat
import java.util.Date

/**
 * One-screen test app: type a phrase, start listening, and see — live — whether the mic
 * hears you (level bar), whether it thinks you're speaking (VAD dot), and every detection
 * (green flash + log). Built in code with platform widgets: no XML, no AndroidX.
 */
class MainActivity : Activity() {

    private val bg = Color.rgb(18, 18, 20)
    private val fg = Color.rgb(235, 235, 240)
    private val dim = Color.rgb(140, 140, 150)
    private val green = Color.rgb(34, 197, 94)
    private val amber = Color.rgb(245, 158, 11)
    private val red = Color.rgb(239, 68, 68)

    private lateinit var keyword: EditText
    private lateinit var hint: TextView
    private lateinit var sensitive: RadioButton
    private lateinit var ring: Switch
    private lateinit var startStop: Button
    private lateinit var status: TextView
    private lateinit var level: ProgressBar
    private lateinit var levelText: TextView
    private lateinit var speechDot: TextView
    private lateinit var panel: TextView
    private lateinit var counter: TextView
    private lateinit var log: TextView
    private var flash: ValueAnimator? = null
    private var sessionCount = 0

    private val onDetect = WakeWord.Listener { d -> showDetection(d.keyword) }
    private val onState = WakeWord.StateListener { renderState(it) }
    private val onMeter = WakeWord.MeterListener { m ->
        level.progress = ((m.levelDb + 70f) / 70f * 100f).toInt().coerceIn(0, 100)
        levelText.text = "%.0f dB".format(m.levelDb)
        speechDot.setTextColor(if (m.speech) green else dim)
        speechDot.text = if (m.speech) "●  speech — spotter running" else "○  quiet — spotter idle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        setContentView(buildUi())
        val p = DemoApp.prefs(this)
        keyword.setText(p.getString(DemoApp.PREF_KEYWORD, "hey buddy"))
        sensitive.isChecked = p.getBoolean(DemoApp.PREF_SENSITIVE, false)
        ring.isChecked = p.getBoolean(DemoApp.PREF_RING, false)
        ring.setOnCheckedChangeListener { _, on -> p.edit().putBoolean(DemoApp.PREF_RING, on).apply() }
        validate()
        refreshLog()
    }

    override fun onResume() {
        super.onResume()
        WakeWord.addListener(onDetect)
        WakeWord.addStateListener(onState)
        WakeWord.addMeterListener(onMeter)
        WakeWord.ensureRunning(this) // the launcher pattern: revive the service whenever visible
        renderState(WakeWord.state)
        refreshLog()
    }

    override fun onPause() {
        WakeWord.removeListener(onDetect)
        WakeWord.removeStateListener(onState)
        WakeWord.removeMeterListener(onMeter) // metering stops when nobody is watching
        super.onPause()
    }

    // ---- actions ------------------------------------------------------------

    private fun onStartStop() {
        if (WakeWord.isEnabled(this)) {
            WakeWord.stop(this)
            renderState(WakeWord.State.Stopped)
            return
        }
        if (!validate()) return
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), REQ_PERMS) else start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQ_PERMS) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            start()
        } else {
            status.text = "Microphone permission denied — can't listen"
            status.setTextColor(red)
        }
    }

    private fun start() {
        val text = keyword.text.toString()
        DemoApp.prefs(this).edit()
            .putString(DemoApp.PREF_KEYWORD, text)
            .putBoolean(DemoApp.PREF_SENSITIVE, sensitive.isChecked)
            .apply()
        sessionCount = 0
        counter.text = "0 detections this session"
        WakeWord.start(this, listOf(spec(text)))
        status.text = "Starting…"
        status.setTextColor(amber)
        startStop.text = "Stop"
    }

    /** Normal = defaults; Sensitive = boost 2.0 (more catches in noise, ~3x false alarms; see README). */
    private fun spec(text: String) = KeywordSpec(text, boost = if (sensitive.isChecked) 2.0f else 1.0f)

    private fun validate(): Boolean {
        val v = WakeWord.validate(this, spec(keyword.text.toString()))
        when {
            v.error != null -> { hint.text = v.error; hint.setTextColor(red) }
            v.warning != null -> { hint.text = v.warning; hint.setTextColor(amber) }
            else -> { hint.text = "Good phrase."; hint.setTextColor(green) }
        }
        return v.ok
    }

    // ---- rendering ----------------------------------------------------------

    private fun renderState(s: WakeWord.State) {
        val enabled = WakeWord.isEnabled(this)
        startStop.text = if (enabled) "Stop" else "Start listening"
        keyword.isEnabled = !enabled
        sensitive.isEnabled = !enabled
        when (s) {
            WakeWord.State.Listening -> {
                status.text = "Listening for \"${keyword.text}\" — works with the screen off too"
                status.setTextColor(green)
            }
            WakeWord.State.Stopped -> {
                status.text = if (enabled) "Starting…" else "Stopped"
                status.setTextColor(if (enabled) amber else dim)
                if (!enabled) onMeter.onMeter(WakeWord.Meter(-90f, false))
            }
            is WakeWord.State.Error -> {
                status.text = "Error: ${s.message}"
                status.setTextColor(red)
            }
        }
    }

    private fun showDetection(kw: String) {
        sessionCount++
        val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())
        panel.text = "DETECTED\n\"${kw.replace('_', ' ').lowercase()}\"\n$time"
        panel.setTextColor(Color.BLACK)
        flash?.cancel()
        flash = ValueAnimator.ofObject(ArgbEvaluator(), green, Color.rgb(32, 32, 36)).apply {
            startDelay = 1200
            duration = 1500
            addUpdateListener { (panel.background as GradientDrawable).setColor(it.animatedValue as Int) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    panel.setTextColor(fg)
                }
            })
        }
        (panel.background as GradientDrawable).setColor(green)
        flash?.start()
        counter.text = "$sessionCount detection${if (sessionCount == 1) "" else "s"} this session"
        refreshLog()
    }

    private fun refreshLog() {
        val events = WakeWord.recentDetections
        val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
        log.text = if (events.isEmpty()) {
            "No detections yet."
        } else {
            events.asReversed().joinToString("\n") {
                "${fmt.format(Date(it.wallTimeMs))}   ${it.keyword.replace('_', ' ').lowercase()}"
            }
        }
    }

    // ---- layout -------------------------------------------------------------

    private fun buildUi(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(bg)
        }
        fun label(text: String, size: Float = 14f, color: Int = dim, bold: Boolean = false) = TextView(this).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

        col.addView(label("Wake word test", 24f, fg, bold = true))
        col.addView(gap(16))
        col.addView(label("Phrase"))
        keyword = EditText(this).apply {
            setTextColor(fg)
            setHintTextColor(dim)
            hint = "e.g. hey buddy"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { validate() }
            })
        }
        col.addView(keyword, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        hint = label("", 13f)
        col.addView(hint)
        col.addView(gap(12))

        val radios = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val normal = RadioButton(this).apply { text = "Normal"; setTextColor(fg); id = View.generateViewId() }
        sensitive = RadioButton(this).apply { text = "Sensitive"; setTextColor(fg); id = View.generateViewId() }
        radios.addView(normal); radios.addView(sensitive)
        normal.isChecked = true
        col.addView(radios)
        ring = Switch(this).apply { text = "Ring on detection"; setTextColor(fg) }
        col.addView(ring, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(gap(12))

        startStop = Button(this).apply {
            text = "Start listening"
            setOnClickListener { onStartStop() }
        }
        col.addView(startStop, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        status = label("Stopped", 14f)
        col.addView(status)
        col.addView(gap(16))

        val meterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        level = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        meterRow.addView(level, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        levelText = label("—", 12f).apply { setPadding(dp(8), 0, 0, 0) }
        meterRow.addView(levelText, LinearLayout.LayoutParams(dp(56), WRAP_CONTENT))
        col.addView(label("Microphone level", 12f))
        col.addView(meterRow)
        speechDot = label("○  quiet — spotter idle", 13f)
        col.addView(speechDot)
        col.addView(gap(16))

        panel = label("Say your phrase…", 22f, fg, bold = true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.rgb(32, 32, 36)) }
            setPadding(dp(16), dp(28), dp(16), dp(28))
            setOnClickListener { (application as DemoApp).ringer.stop() } // tap to silence
        }
        col.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(label("Tap the panel to stop ringing", 12f).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(gap(16))
        counter = label("0 detections this session", 14f, fg, bold = true)
        col.addView(counter)
        col.addView(label("Recent (including while the app was closed)", 12f))
        log = label("", 14f, fg).apply { typeface = Typeface.MONOSPACE }
        col.addView(log)

        return ScrollView(this).apply { setBackgroundColor(bg); addView(col) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object { const val REQ_PERMS = 1 }
}
