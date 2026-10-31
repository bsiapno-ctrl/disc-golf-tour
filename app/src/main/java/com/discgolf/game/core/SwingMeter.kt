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
    /** Power (0..1) the caddie says reaches the pin. The bar eases off around it so it is easy to hit. Negative = none. */
    private val markPower: Float = -1f,
    /** A short putt inside circle 1: the accuracy zone is more generous and the marker slower. */
    private val circle1: Boolean = false,
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
    val sweetHalfWidth: Float = lerp(0.055f, 0.12f, accuracy01) * (if (circle1) 1.25f else 1f)

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
    val sweepSeconds: Float = lerp(1.0f, 1.5f, accuracy01) * (if (isPutt) (if (circle1) 1.35f else 1.15f) else 1f)

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
                    power = minOf(1f, power + dt / fillSeconds * markEase())
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

    /**
     * Speed factor for the bar. Near the pin mark it slows (more with Touch) so a phone thumb has
     * a real window: about 0.3 s at rookie Touch, nearly half a second at high Touch.
     */
    private fun markEase(): Float {
        if (markPower <= 0.02f || markPower >= 0.95f) return 1f
        val d = kotlin.math.abs(power - markPower)
        if (d >= MARK_WINDOW) return 1f
        val k = 1f - d / MARK_WINDOW
        return 1f - (0.35f + 0.30f * touch01) * k
    }

    private fun lockPower() {
        // Releasing in the last sliver of the bar counts as a perfect-power release.
        perfectPower = power >= PERFECT_POWER && !overcooked
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
            // Small misses stay small: the error curve is gentle near the sweet spot and steep at the ends.
            val e = Math.pow(((mag - sweetHalfWidth) / (0.5f - sweetHalfWidth)).toDouble(), 1.3).toFloat()
            accuracyError = if (off < 0) -e else e
            hitSweetSpot = false
        }
        phase = Phase.DONE
    }

    companion object {
        /** How long the bar can sit full before the throw overcooks. */
        const val PEAK_GRACE = 0.30f
        /** Releasing at or above this counts as a perfect-power release. */
        const val PERFECT_POWER = 0.97f
        /** Half-width of the slow zone around the pin mark, in power units. */
        const val MARK_WINDOW = 0.075f
    }
}
