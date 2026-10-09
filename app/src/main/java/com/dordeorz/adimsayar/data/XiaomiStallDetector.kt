package com.dordeorz.adimsayar.data

class XiaomiStallDetector(
    private val windowMs: Long = 10 * 60 * 1000L,
    private val threshold: Long = 100L,
) {
    private var lastToday: Long? = null
    private var baseDetector = 0L
    private var baseMs = 0L

    fun onSync(today: Long, detectorSteps: Long, nowMs: Long): Boolean {
        if (today != lastToday || detectorSteps < baseDetector) {
            lastToday = today
            baseDetector = detectorSteps
            baseMs = nowMs
            return false
        }
        return detectorSteps - baseDetector >= threshold && nowMs - baseMs >= windowMs
    }
}
