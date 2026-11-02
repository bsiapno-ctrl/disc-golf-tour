package com.discgolf.game.core

import kotlin.math.abs
import kotlin.math.pow

/**
 * Pure difficulty and scoring maths for the three-step throw (POWER, RUN-UP, RELEASE).
 * No state and no Android types, so it is unit-tested directly.
 *
 * Every function takes the golfer's Accuracy stat as a 0..1 fraction and the locked power 0..1.
 * Higher Accuracy makes the timing zones wider and the markers slower (easier).
 * Higher power makes the zones narrower and the markers faster (harder): a full-power drive is the hardest release.
 */
object ThrowTiming {
    /** The run-up sweet zone is centred late in the sweep: the golfer plants the last step just before the release. */
    const val RUNUP_CENTER = 0.75f
    /** The release-angle sweet zone is dead centre; left of it is anhyzer, right of it is hyzer. */
    const val RELEASE_CENTER = 0.5f
    /** Most extra power (fraction) a perfectly timed run-up adds. */
    const val RUNUP_MAX_BONUS = 0.04f
    /** Most power (fraction) a badly mistimed run-up costs. */
    const val RUNUP_MAX_PENALTY = 0.10f
    /** How much of the run-up miss leaks into the release error (-1..1 scale). */
    const val RUNUP_LEAK = 0.30f
    /** Degrees of release-angle deviation per unit of accuracy error (matches the launcher's bank term). */
    const val ANGLE_DEG_PER_ERROR = 8f
    /** Hard ceiling on power after the run-up bonus. */
    const val MAX_EFFECTIVE_POWER = 1.04f

    /** Pre-sweep pause (s) after the power lock, and after the run-up tap, before the next marker starts moving. */
    const val RUNUP_START_DELAY = 0.30f
    const val RELEASE_START_DELAY = 0.35f

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
    private fun c01(v: Float) = v.coerceIn(0f, 1f)

    private fun puttZone(isPutt: Boolean, circle1: Boolean) = if (!isPutt) 1f else if (circle1) 1.25f else 1.1f
    private fun puttSlow(isPutt: Boolean, circle1: Boolean) = if (!isPutt) 1f else if (circle1) 1.35f else 1.15f

    /** Half-width of the release-angle sweet zone, as a fraction of the bar (0.5 would be the whole bar). */
    fun releaseHalfWidth(accuracy01: Float, power01: Float, isPutt: Boolean = false, circle1: Boolean = false): Float =
        lerp(0.100f, 0.170f, accuracy01) * (1f - 0.30f * c01(power01)) * puttZone(isPutt, circle1)

    /** Seconds for the release marker to cross the whole bar. */
    fun releaseSweepSeconds(accuracy01: Float, power01: Float, isPutt: Boolean = false, circle1: Boolean = false): Float =
        lerp(1.10f, 1.60f, accuracy01) * (1f - 0.30f * c01(power01)) * puttSlow(isPutt, circle1)

    /** Half-width of the run-up sweet zone. */
    fun runupHalfWidth(accuracy01: Float, power01: Float): Float =
        lerp(0.080f, 0.140f, accuracy01) * (1f - 0.25f * c01(power01))

    /** Seconds for the run-up marker to go from the start to the end of its bar. */
    fun runupSweepSeconds(accuracy01: Float, power01: Float): Float =
        lerp(1.50f, 2.00f, accuracy01) * (1f - 0.20f * c01(power01))

    /** How long (s) the sweet zone is open for a tap if the marker moves at constant speed. A fairness figure for tuning. */
    fun releaseWindowSeconds(accuracy01: Float, power01: Float, isPutt: Boolean = false, circle1: Boolean = false): Float =
        2f * releaseHalfWidth(accuracy01, power01, isPutt, circle1) * releaseSweepSeconds(accuracy01, power01, isPutt, circle1)

    /** Signed -1..1 miss for a tap at [marker] against a zone ([center] +- [half]). 0 inside the zone; negative = early. */
    fun missError(marker: Float, center: Float, half: Float): Float {
        val off = marker - center
        val mag = abs(off)
        if (mag <= half) return 0f
        val span = if (off < 0f) center else 1f - center
        val e = c01((mag - half) / (span - half).coerceAtLeast(1e-3f)).toDouble().pow(1.3).toFloat()
        return if (off < 0f) -e else e
    }

    /** 1 at the very middle of the zone, 0.4 at its edge, 0 outside. */
    fun hitQuality(marker: Float, center: Float, half: Float): Float {
        val mag = abs(marker - center)
        return if (mag > half) 0f else 1f - 0.6f * (mag / half)
    }

    /** Power multiplier from the run-up tap. A good step adds up to 4%; a mistimed one costs up to 10% (less for putts). */
    fun runupPowerMul(quality: Float, error: Float, isPutt: Boolean): Float {
        if (quality > 0f) return if (isPutt) 1f else 1f + RUNUP_MAX_BONUS * quality
        return 1f - RUNUP_MAX_PENALTY * abs(error) * (if (isPutt) 0.4f else 1f)
    }

    /** Power after the run-up, clamped to 0..[MAX_EFFECTIVE_POWER]. */
    fun effectivePower(power01: Float, mul: Float): Float = (power01 * mul).coerceIn(0f, MAX_EFFECTIVE_POWER)

    /** Combined -1..1 error handed to the launcher: the release miss plus the leak from a mistimed run-up. */
    fun combinedError(releaseError: Float, runupError: Float): Float =
        (releaseError + RUNUP_LEAK * runupError).coerceIn(-1f, 1f)

    /** The hyzer (+) / anhyzer (-) deviation in degrees that a combined error puts on the release. */
    fun angleDeviationDeg(combinedError: Float): Float = combinedError * ANGLE_DEG_PER_ERROR
}
