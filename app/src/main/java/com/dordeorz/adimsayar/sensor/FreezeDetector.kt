package com.dordeorz.adimsayar.sensor

class FreezeDetector(
    private val windowMs: Long = 120_000L,
    private val detectorThreshold: Int = 50,
) {
    private val detectorTimes = ArrayDeque<Long>()
    private var lastCounter: Long? = null
    private var lastAdvanceMs: Long = 0L

    fun reset(nowMs: Long) {
        detectorTimes.clear()
        lastCounter = null
        lastAdvanceMs = nowMs
    }

    fun onCounter(value: Long, nowMs: Long) {
        val previous = lastCounter
        if (previous == null || value > previous) lastAdvanceMs = nowMs
        lastCounter = value
    }

    fun onDetectorStep(nowMs: Long) {
        detectorTimes.addLast(nowMs)
        trim(nowMs)
    }

    fun isFrozen(nowMs: Long): Boolean {
        trim(nowMs)
        return nowMs - lastAdvanceMs >= windowMs && detectorTimes.size >= detectorThreshold
    }

    private fun trim(nowMs: Long) {
        while (detectorTimes.isNotEmpty() && nowMs - detectorTimes.first() > windowMs) detectorTimes.removeFirst()
    }
}
