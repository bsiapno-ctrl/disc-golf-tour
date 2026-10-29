package com.discgolf.game.core

/**
 * The classic sports-game swing meter, adapted to a single button.
 *
 *  1. HOLD the button: the power bar fills. RELEASE to lock the power. Let it run past 100% and it overcooks.
 *  2. A marker then sweeps across the accuracy bar. TAP the button to stop it as close to the middle as you can.
 *
 * Touch gives the golfer two ways to be better than a rookie: the Touch rating slows the power bar
 * (easier to hit a partial power), and the Accuracy rating widens the centre "sweet spot" and slows the sweep.
 *
 * Pure logic with no Android types. Time is fed in through [update].
 */
class SwingMeter(
    private val accuracy01: Float,
    private val touch01: Float,
    private val isPutt: Boolean,
    private val autoAccuracy: Boolean = false,
) {
    enum class Phase { IDLE, CHARGING, ACCURACY, DONE }

    var phase = Phase.IDLE
        private set

    /** 0..1. Live while charging, frozen after release. */
    var power = 0f
        private set

    /** 0..1 position of the accuracy marker. The perfect line is at 0.5. */
    var marker = 0f
        private set

    /** Half-width of the perfect zone, as a fraction of the bar. Wider with Accuracy. */
    val sweetHalfWidth: Float = lerp(0.035f, 0.13f, accuracy01)

    var overcooked = false
        private set
    var perfectPower = false
        private set

    /** -1..1 error. 0 inside the sweet zone. Negative = early (left), positive = late (right). */
    var accuracyError = 0f
        private set
    var hitSweetSpot = false
        private set

    private var holdAtPeak = 0f

    /** Seconds for the bar to go from empty to full. Slower with Touch, and slower again for putts. */
    val fillSeconds: Float = 1.25f * (1f + 0.5f * touch01) * (if (isPutt) 1.6f else 1f)

    /** Seconds for the marker to cross the whole bar. */
    val sweepSeconds: Float = lerp(0.85f, 1.3f, accuracy01) * (if (isPutt) 1.15f else 1f)

    val isFinished get() = phase == Phase.DONE

    fun reset() {
        phase = Phase.IDLE; power = 0f; marker = 0f; overcooked = false
        perfectPower = false; accuracyError = 0f; hitSweetSpot = false; holdAtPeak = 0f
    }

    /** Button went down. */
    fun press() {
        when (phase) {
            Phase.IDLE -> { phase = Phase.CHARGING; power = 0f }
            Phase.ACCURACY -> stopMarker()
            else -> Unit
        }
    }

    /** Button came up. */
    fun release() {
        if (phase == Phase.CHARGING) lockPower()
    }

    fun update(dt: Float) {
        when (phase) {
            Phase.CHARGING -> {
                if (power < 1f) {
                    power = minOf(1f, power + dt / fillSeconds)
                } else {
                    // Sitting at the top: a short grace window, then the throw overcooks.
                    holdAtPeak += dt
                    if (holdAtPeak > PEAK_GRACE) {
                        overcooked = true
                        lockPower()
                    }
                }
            }
            Phase.ACCURACY -> {
                marker += dt / sweepSeconds
                if (marker >= 1f) {
                    marker = 1f
                    stopMarker()
                }
            }
            else -> Unit
        }
    }

    private fun lockPower() {
        // Releasing in the last sliver of the bar counts as a perfect-power release.
        perfectPower = power >= 0.97f && !overcooked
        if (power >= 0.995f && !overcooked) power = 1f
        if (autoAccuracy) {
            accuracyError = 0f; hitSweetSpot = true; phase = Phase.DONE
        } else {
            phase = Phase.ACCURACY
            marker = 0f
        }
    }

    private fun stopMarker() {
        val off = marker - 0.5f
        val mag = kotlin.math.abs(off)
        if (mag <= sweetHalfWidth) {
            accuracyError = 0f
            hitSweetSpot = true
        } else {
            val e = (mag - sweetHalfWidth) / (0.5f - sweetHalfWidth)
            accuracyError = if (off < 0) -e else e
            hitSweetSpot = false
        }
        phase = Phase.DONE
    }

    companion object {
        /** How long the bar can sit full before the throw overcooks. */
        const val PEAK_GRACE = 0.30f
    }
}
