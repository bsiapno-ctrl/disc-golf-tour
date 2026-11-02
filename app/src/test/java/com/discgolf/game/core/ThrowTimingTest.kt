package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrowTimingTest {
    private val accs = floatArrayOf(0f, 0.5f, 1f)
    private val powers = floatArrayOf(0f, 0.5f, 1f)

    @Test fun releaseZoneGrowsWithAccuracyAndShrinksWithPower() {
        for (p in powers) assertTrue(ThrowTiming.releaseHalfWidth(1f, p) > ThrowTiming.releaseHalfWidth(0f, p))
        for (a in accs) assertTrue(ThrowTiming.releaseHalfWidth(a, 1f) < ThrowTiming.releaseHalfWidth(a, 0f))
    }

    @Test fun releaseMarkerIsSlowerWithAccuracyAndFasterWithPower() {
        for (p in powers) assertTrue(ThrowTiming.releaseSweepSeconds(1f, p) > ThrowTiming.releaseSweepSeconds(0f, p))
        for (a in accs) assertTrue(ThrowTiming.releaseSweepSeconds(a, 1f) < ThrowTiming.releaseSweepSeconds(a, 0f))
    }

    @Test fun runupDifficultyScalesTheSameWay() {
        for (p in powers) {
            assertTrue(ThrowTiming.runupHalfWidth(1f, p) > ThrowTiming.runupHalfWidth(0f, p))
            assertTrue(ThrowTiming.runupSweepSeconds(1f, p) > ThrowTiming.runupSweepSeconds(0f, p))
        }
        for (a in accs) {
            assertTrue(ThrowTiming.runupHalfWidth(a, 1f) < ThrowTiming.runupHalfWidth(a, 0f))
            assertTrue(ThrowTiming.runupSweepSeconds(a, 1f) < ThrowTiming.runupSweepSeconds(a, 0f))
        }
    }

    @Test fun hardestCaseIsStillHumanlyPossible() {
        // rookie, full power: window the thumb must hit
        assertTrue(ThrowTiming.releaseWindowSeconds(0f, 1f) >= 0.09f)
        assertTrue(ThrowTiming.releaseHalfWidth(0f, 1f) >= 0.06f)
        // master, soft touch: easily generous but never the whole bar
        assertTrue(ThrowTiming.releaseHalfWidth(1f, 0f) < 0.25f)
    }

    @Test fun puttsAndCircleOneAreMoreGenerous() {
        assertTrue(ThrowTiming.releaseHalfWidth(0.5f, 0.3f, isPutt = true, circle1 = true) > ThrowTiming.releaseHalfWidth(0.5f, 0.3f, isPutt = true))
        assertTrue(ThrowTiming.releaseHalfWidth(0.5f, 0.3f, isPutt = true) > ThrowTiming.releaseHalfWidth(0.5f, 0.3f))
        assertTrue(ThrowTiming.releaseSweepSeconds(0.5f, 0.3f, isPutt = true, circle1 = true) > ThrowTiming.releaseSweepSeconds(0.5f, 0.3f))
    }

    @Test fun missErrorIsZeroInZoneSignedOutsideAndMonotonic() {
        val c = 0.5f; val h = 0.1f
        assertEquals(0f, ThrowTiming.missError(0.5f, c, h), 0f)
        assertEquals(0f, ThrowTiming.missError(0.59f, c, h), 0f)
        assertTrue(ThrowTiming.missError(0.2f, c, h) < 0f)
        assertTrue(ThrowTiming.missError(0.8f, c, h) > 0f)
        assertTrue(ThrowTiming.missError(0.7f, c, h) < ThrowTiming.missError(0.9f, c, h))
        assertEquals(-1f, ThrowTiming.missError(0f, c, h), 1e-5f)
        assertEquals(1f, ThrowTiming.missError(1f, c, h), 1e-5f)
        // asymmetric run-up bar: the whole late side still tops out at 1
        assertEquals(1f, ThrowTiming.missError(1f, ThrowTiming.RUNUP_CENTER, 0.1f), 1e-5f)
    }

    @Test fun runupBonusAndPenalty() {
        val best = ThrowTiming.runupPowerMul(1f, 0f, false)
        assertEquals(1f + ThrowTiming.RUNUP_MAX_BONUS, best, 1e-6f)
        assertTrue(ThrowTiming.runupPowerMul(0.4f, 0f, false) in 1f..best)
        assertEquals(1f - ThrowTiming.RUNUP_MAX_PENALTY, ThrowTiming.runupPowerMul(0f, -1f, false), 1e-6f)
        assertEquals(1f, ThrowTiming.runupPowerMul(1f, 0f, true), 0f)            // no bonus on putts
        assertTrue(ThrowTiming.runupPowerMul(0f, 1f, true) > ThrowTiming.runupPowerMul(0f, 1f, false)) // softer penalty
        assertEquals(ThrowTiming.MAX_EFFECTIVE_POWER, ThrowTiming.effectivePower(1f, 1.04f), 1e-6f)
        assertEquals(0.5f, ThrowTiming.effectivePower(0.5f, 1f), 1e-6f)
    }

    @Test fun combinedErrorLeaksRunupAndClamps() {
        assertEquals(0f, ThrowTiming.combinedError(0f, 0f), 0f)
        assertEquals(ThrowTiming.RUNUP_LEAK * 0.5f, ThrowTiming.combinedError(0f, 0.5f), 1e-6f)
        assertEquals(1f, ThrowTiming.combinedError(0.9f, 1f), 0f)
        assertEquals(-ThrowTiming.ANGLE_DEG_PER_ERROR, ThrowTiming.angleDeviationDeg(-1f), 1e-6f)
    }

    private fun meterAt(acc: Float, hold: Int, isPutt: Boolean = false): SwingMeter {
        val m = SwingMeter(acc, 0f, isPutt)
        m.press(); repeat(hold) { m.update(1f / 60f) }; m.release()
        return m
    }

    @Test fun meterSizesItsBarsFromTheLockedPower() {
        val soft = meterAt(0.5f, 15); val hard = meterAt(0.5f, 70)
        assertTrue(hard.power > soft.power)
        assertTrue(hard.releaseHalfWidth < soft.releaseHalfWidth)
        assertTrue(hard.releaseSweepSeconds < soft.releaseSweepSeconds)
        assertEquals(ThrowTiming.releaseHalfWidth(0.5f, hard.power), hard.releaseHalfWidth, 1e-6f)
        assertEquals(ThrowTiming.runupSweepSeconds(0.5f, hard.power), hard.runupSweepSeconds, 1e-6f)
    }

    @Test fun phasesAdvancePowerThenRunupThenRelease() {
        val m = meterAt(0.5f, 40)
        assertEquals(SwingMeter.Phase.RUNUP, m.phase)
        m.press()                      // inside the start delay: ignored
        assertEquals(SwingMeter.Phase.RUNUP, m.phase)
        m.update(ThrowTiming.RUNUP_START_DELAY + 0.01f)
        assertFalse(m.inStartDelay)
        m.press()
        assertEquals(SwingMeter.Phase.RELEASE, m.phase)
        assertTrue(m.inStartDelay)
        m.press()
        assertEquals(SwingMeter.Phase.RELEASE, m.phase)
        m.update(ThrowTiming.RELEASE_START_DELAY + 0.01f)
        m.press()
        assertTrue(m.isFinished)
    }

    @Test fun perfectRunupAddsPowerAndNoAngleError() {
        val m = meterAt(0.5f, 40)
        val base = m.power
        while (m.runupMarker < ThrowTiming.RUNUP_CENTER) m.update(1f / 240f)
        m.press()
        assertTrue(m.runupHit)
        assertTrue(m.effectivePower > base)
        assertEquals(0f, m.runupError, 0f)
        while (m.releaseMarker < 0.5f) m.update(1f / 240f)
        m.press()
        assertEquals(0f, m.accuracyError, 1e-6f)
        assertEquals(0f, m.angleDeviationDeg, 1e-6f)
    }

    @Test fun earlyRunupLosesPowerAndPullsAnhyzerLateRunupPullsHyzer() {
        val early = meterAt(0.5f, 40)
        early.update(ThrowTiming.RUNUP_START_DELAY + 0.05f); early.press()
        assertFalse(early.runupHit)
        assertTrue(early.effectivePower < early.power)
        assertTrue(early.runupError < 0f)
        // perfect release afterwards: only the leak remains, on the anhyzer (negative) side
        while (early.releaseMarker < 0.5f) early.update(1f / 240f)
        early.press()
        assertTrue(early.accuracyError < 0f && early.angleDeviationDeg < 0f)

        val late = meterAt(0.5f, 40)
        while (late.phase == SwingMeter.Phase.RUNUP) late.update(1f / 60f)
        assertEquals(SwingMeter.Phase.RELEASE, late.phase)   // marker ran out: counted as a very late step
        assertTrue(late.runupError > 0f)
        assertTrue(late.effectivePower < late.power)
    }

    @Test fun releaseTapSignAndSweetSpot() {
        fun finish(tapAt: Float): SwingMeter {
            val m = meterAt(0.5f, 40)
            while (m.runupMarker < ThrowTiming.RUNUP_CENTER) m.update(1f / 240f)
            m.press()
            while (m.releaseMarker < tapAt) m.update(1f / 240f)
            m.press()
            return m
        }
        assertTrue(finish(0.5f).hitSweetSpot)
        val left = finish(0.12f); val right = finish(0.9f)
        assertFalse(left.hitSweetSpot); assertFalse(right.hitSweetSpot)
        assertTrue(left.accuracyError < 0f && left.angleDeviationDeg < 0f)   // anhyzer side
        assertTrue(right.accuracyError > 0f && right.angleDeviationDeg > 0f) // hyzer side
    }

    @Test fun autoAccuracySkipsTheTimingSteps() {
        val m = SwingMeter(0.5f, 0f, false, autoAccuracy = true)
        m.press(); repeat(30) { m.update(1f / 60f) }; m.release()
        assertTrue(m.isFinished)
        assertEquals(0f, m.accuracyError, 0f)
        assertEquals(m.power, m.effectivePower, 0f)
    }

    @Test fun printDifficultyTable() {
        val sb = StringBuilder("acc  power | relHalf relSweep relWindow | runHalf runSweep\n")
        for (a in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) for (p in floatArrayOf(0.25f, 0.5f, 0.75f, 1f))
            sb.append(String.format("%.2f %.2f | %.3f %.2fs %.3fs | %.3f %.2fs\n", a, p,
                ThrowTiming.releaseHalfWidth(a, p), ThrowTiming.releaseSweepSeconds(a, p), ThrowTiming.releaseWindowSeconds(a, p),
                ThrowTiming.runupHalfWidth(a, p), ThrowTiming.runupSweepSeconds(a, p)))
        println(sb)
    }
}
