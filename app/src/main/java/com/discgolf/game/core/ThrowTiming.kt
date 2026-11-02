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
    /** How a putt is taken. Putts never use the run-up. STAND = feet planted (power, release). JUMP = a hop putt (power, jump, release). */
    enum class PuttStyle { STAND, JUMP }

    /** The jump-timing sweet zone is centred at the top of the hop, a little before the end of the sweep. */
    const val JUMP_CENTER = 0.65f
    /** Most extra reach (fraction of power) a perfectly timed hop adds, and most a badly mistimed one costs. */
    const val JUMP_MAX_BONUS = 0.05f
    const val JUMP_MAX_PENALTY = 0.12f
    /** How much of the jump miss leaks into the release error (-1..1 scale). */
    const val JUMP_LEAK = 0.20f
    /** Pause (s) after the power lock before the jump marker starts moving. */
    const val JUMP_START_DELAY = 0.25f
    /** A jump putt makes the release zone this much narrower and its marker this much faster. */
    const val JUMP_ZONE_MUL = 0.82f
    const val JUMP_SPEED_MUL = 0.88f

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

    private fun styleZone(isPutt: Boolean, style: PuttStyle) = if (isPutt && style == PuttStyle.JUMP) JUMP_ZONE_MUL else 1f
    private fun styleSpeed(isPutt: Boolean, style: PuttStyle) = if (isPutt && style == PuttStyle.JUMP) JUMP_SPEED_MUL else 1f

    /** Half-width of the release-angle sweet zone, as a fraction of the bar (0.5 would be the whole bar). A jump putt has a harsher (narrower) zone. */
    fun releaseHalfWidth(accuracy01: Float, power01: Float, isPutt: Boolean = false, circle1: Boolean = false, style: PuttStyle = PuttStyle.STAND): Float =
        lerp(0.100f, 0.170f, accuracy01) * (1f - 0.30f * c01(power01)) * puttZone(isPutt, circle1) * styleZone(isPutt, style)

    /** Seconds for the release marker to cross the whole bar. A jump putt's marker is faster. */
    fun releaseSweepSeconds(accuracy01: Float, power01: Float, isPutt: Boolean = false, circle1: Boolean = false, style: PuttStyle = PuttStyle.STAND): Float =
        lerp(1.10f, 1.60f, accuracy01) * (1f - 0.30f * c01(power01)) * puttSlow(isPutt, circle1) * styleSpeed(isPutt, style)

    /** Half-width of the jump-timing sweet zone (jump putts only). Wider with Accuracy, narrower with power. */
    fun jumpHalfWidth(accuracy01: Float, power01: Float): Float =
        lerp(0.100f, 0.170f, accuracy01) * (1f - 0.25f * c01(power01))

    /** Seconds for the jump marker to cross its bar. Short: the hop is quick. */
    fun jumpSweepSeconds(accuracy01: Float, power01: Float): Float =
        lerp(1.00f, 1.40f, accuracy01) * (1f - 0.20f * c01(power01))

    /** Power multiplier from the jump tap: a good hop adds up to 5% reach, a mistimed one costs up to 12%. */
    fun jumpPowerMul(quality: Float, error: Float): Float =
        if (quality > 0f) 1f + JUMP_MAX_BONUS * quality else 1f - JUMP_MAX_PENALTY * abs(error)

    /** Combined -1..1 error for a jump putt: the release miss plus the leak from a mistimed jump. */
    fun puttCombinedError(releaseError: Float, jumpError: Float): Float =
        (releaseError + JUMP_LEAK * jumpError).coerceIn(-1f, 1f)

    /** Half-width of the run-up sweet zone. */
    fun runupHalfWidth(accuracy01: Float, power01: Float): Float =
        lerp(0.080f, 0.140f, accuracy01) * (1f - 0.25f * c01(power01))

    /** Seconds for the run-up marker to go from the start to the end of its bar. */
    fun runupSweepSeconds(accuracy01: Float, power01: Float): Float =
        lerp(1.50f, 2.00f, accuracy01) * (1f - 0.20f * c01(power01))

    /** How long (s) the sweet zone is open for a tap if the marker moves at constant speed. A fairness figure for tuning. */
    fun releaseWindowSeconds(accuracy01: Float, power01: Float, isPutt: Boolean = false, circle1: Boolean = false, style: PuttStyle = PuttStyle.STAND): Float =
        2f * releaseHalfWidth(accuracy01, power01, isPutt, circle1, style) * releaseSweepSeconds(accuracy01, power01, isPutt, circle1, style)

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
