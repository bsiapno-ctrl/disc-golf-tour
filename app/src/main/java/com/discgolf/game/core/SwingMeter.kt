package com.discgolf.game.core

/**
 * The three-step throw meter, adapted to a single button (or tap).
 *
 *  1. POWER: HOLD the button and the power bar fills. RELEASE to lock the power. Past 100% it overcooks.
 *  2. RUN-UP: the golfer runs up and a marker rides along a timing bar. TAP to plant the step in the sweet zone.
 *     Good timing adds a little power; bad timing loses some and nudges the release off line.
 *     PUTTS never run up: a STAND putt goes POWER -> RELEASE; a JUMP putt goes POWER -> JUMP (tap at the top of the hop) -> RELEASE.
 *  3. RELEASE ANGLE: a second, tighter bar. TAP to lock the release angle. Left of the middle is anhyzer, right is hyzer.
 *     The zone is narrower and the marker faster the more power was used, and easier with the Accuracy rating.
 *
 * Touch slows the power bar. All the zone sizes and speeds come from [ThrowTiming].
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
    /** A short putt inside circle 1: the timing zones are more generous and the markers slower. */
    private val circle1: Boolean = false,
    /** Putt style (only used when [isPutt]). Putts never run up: STAND goes power -> release, JUMP goes power -> jump -> release. */
    private val puttStyle: ThrowTiming.PuttStyle = ThrowTiming.PuttStyle.STAND,
) {
    enum class Phase { IDLE, CHARGING, RUNUP, JUMP, RELEASE, DONE }

    private val jumpPutt = isPutt && puttStyle == ThrowTiming.PuttStyle.JUMP
    private val style get() = if (isPutt) puttStyle else ThrowTiming.PuttStyle.STAND

    var phase = Phase.IDLE
        private set

    /** 0..1. Live while charging, frozen after release. This is the power the player chose. */
    var power = 0f
        private set

    /** Power after the run-up bonus or penalty. Equals [power] until the run-up is tapped. */
    var effectivePower = 0f
        private set

    /** 0..1 position of the run-up marker, and of the release marker. */
    var runupMarker = 0f
        private set
    var releaseMarker = 0f
        private set
    /** Jump putts only: marker, zone, sweep time and result of the jump-timing step. */
    var jumpMarker = 0f
        private set
    var jumpHalfWidth: Float = ThrowTiming.jumpHalfWidth(accuracy01, 0f)
        private set
    var jumpSweepSeconds: Float = ThrowTiming.jumpSweepSeconds(accuracy01, 0f)
        private set
    var jumpError = 0f
        private set
    var jumpHit = false
        private set

    /** Marker of whichever timing step is live (0 outside them). */
    val marker: Float get() = when (phase) { Phase.RUNUP -> runupMarker; Phase.JUMP -> jumpMarker; Phase.RELEASE, Phase.DONE -> releaseMarker; else -> 0f }

    /** Zone sizes. Valid once the power is locked (they depend on it); before then they show the power-0 sizes. */
    var releaseHalfWidth: Float = ThrowTiming.releaseHalfWidth(accuracy01, 0f, isPutt, circle1, style)
        private set
    var releaseSweepSeconds: Float = ThrowTiming.releaseSweepSeconds(accuracy01, 0f, isPutt, circle1, style)
        private set
    var runupHalfWidth: Float = ThrowTiming.runupHalfWidth(accuracy01, 0f)
        private set
    var runupSweepSeconds: Float = ThrowTiming.runupSweepSeconds(accuracy01, 0f)
        private set

    /** Same name the HUD used for the single accuracy zone: now the release-angle zone. */
    val sweetHalfWidth: Float get() = releaseHalfWidth

    var overcooked = false
        private set
    var perfectPower = false
        private set

    /** Run-up result: -1..1 miss (0 inside the zone; negative = early), hit flag, and 0..1 quality of a hit. */
    var runupError = 0f
        private set
    var runupHit = false
        private set
    var runupQuality = 0f
        private set
    /** Multiplier the run-up put on the power (above 1 = bonus). */
    var powerMul = 1f
        private set

    /** Release result: -1..1 miss of the release-angle bar. 0 inside the zone. Negative = anhyzer side, positive = hyzer side. */
    var releaseError = 0f
        private set

    /** -1..1 error handed to the launcher (release miss plus the leak from a mistimed run-up). */
    var accuracyError = 0f
        private set
    /** True when the release-angle tap landed in the sweet zone. */
    var hitSweetSpot = false
        private set

    /** Hyzer (+) / anhyzer (-) deviation, in degrees, the throw ends up with from the timing. */
    val angleDeviationDeg: Float get() = ThrowTiming.angleDeviationDeg(accuracyError)

    private var holdAtPeak = 0f
    /** Seconds spent in the current timing phase, to run the start delay. */
    private var stepClock = 0f

    /** Seconds for the bar to go from empty to full. Slower with Touch, and slower again for putts. */
    val fillSeconds: Float = 1.25f * (1f + 0.5f * touch01) * (if (isPutt) 1.6f else 1f)

    val isFinished get() = phase == Phase.DONE

    /** True while the live timing marker is waiting out its start delay (taps are ignored). */
    val inStartDelay: Boolean get() = when (phase) {
        Phase.RUNUP -> stepClock < ThrowTiming.RUNUP_START_DELAY
        Phase.JUMP -> stepClock < ThrowTiming.JUMP_START_DELAY
        Phase.RELEASE -> stepClock < ThrowTiming.RELEASE_START_DELAY
        else -> false
    }

    fun reset() {
        phase = Phase.IDLE; power = 0f; effectivePower = 0f; runupMarker = 0f; releaseMarker = 0f; jumpMarker = 0f; jumpError = 0f; jumpHit = false
        overcooked = false; perfectPower = false; runupError = 0f; runupHit = false; runupQuality = 0f
        powerMul = 1f; releaseError = 0f; accuracyError = 0f; hitSweetSpot = false; holdAtPeak = 0f; stepClock = 0f
    }

    /** Button went down. */
    fun press() {
        when (phase) {
            Phase.IDLE -> { phase = Phase.CHARGING; power = 0f }
            Phase.RUNUP -> if (!inStartDelay) stopRunup(false)
            Phase.JUMP -> if (!inStartDelay) stopJump()
            Phase.RELEASE -> if (!inStartDelay) stopRelease()
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
            Phase.RUNUP -> {
                stepClock += dt
                if (!inStartDelay) {
                    runupMarker += dt / runupSweepSeconds
                    if (runupMarker >= 1f) { runupMarker = 1f; stopRunup(true) }
                }
            }
            Phase.JUMP -> {
                stepClock += dt
                if (!inStartDelay) {
                    jumpMarker += dt / jumpSweepSeconds
                    if (jumpMarker >= 1f) { jumpMarker = 1f; stopJump() }
                }
            }
            Phase.RELEASE -> {
                stepClock += dt
                if (!inStartDelay) {
                    releaseMarker += dt / releaseSweepSeconds
                    if (releaseMarker >= 1f) { releaseMarker = 1f; stopRelease() }
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
        effectivePower = power
        // The timing bars are sized by the power that was just locked.
        releaseHalfWidth = ThrowTiming.releaseHalfWidth(accuracy01, power, isPutt, circle1, style)
        releaseSweepSeconds = ThrowTiming.releaseSweepSeconds(accuracy01, power, isPutt, circle1, style)
        runupHalfWidth = ThrowTiming.runupHalfWidth(accuracy01, power)
        runupSweepSeconds = ThrowTiming.runupSweepSeconds(accuracy01, power)
        jumpHalfWidth = ThrowTiming.jumpHalfWidth(accuracy01, power)
        jumpSweepSeconds = ThrowTiming.jumpSweepSeconds(accuracy01, power)
        if (autoAccuracy) {
            runupHit = true; runupQuality = 1f; hitSweetSpot = true; accuracyError = 0f; phase = Phase.DONE
        } else if (isPutt) {
            // Putts skip the run-up entirely.
            releaseMarker = 0f; stepClock = 0f
            if (jumpPutt) { phase = Phase.JUMP; jumpMarker = 0f } else phase = Phase.RELEASE
        } else {
            phase = Phase.RUNUP
            runupMarker = 0f; stepClock = 0f
        }
    }

    /** [timedOut] = the marker ran off the end of the bar without a tap. */
    private fun stopRunup(timedOut: Boolean) {
        val c = ThrowTiming.RUNUP_CENTER
        runupError = ThrowTiming.missError(runupMarker, c, runupHalfWidth)
        runupQuality = ThrowTiming.hitQuality(runupMarker, c, runupHalfWidth)
        runupHit = runupQuality > 0f
        powerMul = ThrowTiming.runupPowerMul(runupQuality, runupError, isPutt)
        effectivePower = ThrowTiming.effectivePower(power, powerMul)
        phase = Phase.RELEASE
        releaseMarker = 0f; stepClock = 0f
    }

    private fun stopJump() {
        val q = ThrowTiming.hitQuality(jumpMarker, ThrowTiming.JUMP_CENTER, jumpHalfWidth)
        jumpError = ThrowTiming.missError(jumpMarker, ThrowTiming.JUMP_CENTER, jumpHalfWidth)
        jumpHit = q > 0f
        powerMul = ThrowTiming.jumpPowerMul(q, jumpError)
        effectivePower = ThrowTiming.effectivePower(power, powerMul)
        phase = Phase.RELEASE
        releaseMarker = 0f; stepClock = 0f
    }

    private fun stopRelease() {
        releaseError = ThrowTiming.missError(releaseMarker, ThrowTiming.RELEASE_CENTER, releaseHalfWidth)
        hitSweetSpot = releaseError == 0f
        accuracyError = if (isPutt) ThrowTiming.puttCombinedError(releaseError, jumpError) else ThrowTiming.combinedError(releaseError, runupError)
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
