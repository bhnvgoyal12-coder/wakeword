package com.findmyphone.wakeword.core

/** Suppress repeat fires of the same keyword within [cooldownS]. Mirrors python DetectionGate. */
class DetectionGate(private val cooldownS: Double = 2.0) {
    private val last = HashMap<String, Double>()

    fun allow(keyword: String, nowS: Double): Boolean {
        val prev = last[keyword]
        if (prev != null && nowS - prev < cooldownS) return false
        last[keyword] = nowS
        return true
    }
}
