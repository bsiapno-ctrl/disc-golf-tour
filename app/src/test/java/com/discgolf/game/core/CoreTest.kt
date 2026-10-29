package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Plain-JVM tests for the game logic. Run with: ./gradlew test */
class CoreTest {

    private fun golfer(arch: Archetype = Archetype.ALLROUND, hand: Handedness = Handedness.RIGHT) =
        PlayerProfile("Test").also { it.applyArchetype(arch); it.handedness = hand }

    private fun land(p: PlayerProfile, disc: DiscModel, shape: ShotShape, power: Float = 1f): Vec3 {
        val spec = ThrowSpec(Vec3.ZERO, 0f, disc, shape, false)
        val f = Launcher.launch(p, spec, EmptyField, power, 0f, false, false)
        var n = 0
        while (f.state == FlightState.FLYING && n++ < 20000) f.step(Launcher.SIM_DT)
        return f.landing ?: f.pos
    }

    @Test fun midrangeCarryIsInAPlayableRange() {
        val d = land(golfer(), DiscModel.ANVIL, ShotShape.FLAT).z
        assertTrue("carry was $d", d in 45f..95f)
    }

    @Test fun moreArmMeansMoreDistance() {
        val weak = golfer(Archetype.SNIPER)
        val strong = golfer(Archetype.BOMBER)
        assertTrue(land(strong, DiscModel.FALCON, ShotShape.FLAT).z > land(weak, DiscModel.FALCON, ShotShape.FLAT).z + 8f)
    }

    @Test fun hyzerCurvesLeftAndAnhyzerRightForARighty() {
        val p = golfer()
        assertTrue(land(p, DiscModel.ANVIL, ShotShape.HYZER).x < -3f)
        assertTrue(land(p, DiscModel.ANVIL, ShotShape.ANHYZER).x > 3f)
    }

    @Test fun leftiesMirrorEveryCurve() {
        val r = golfer(hand = Handedness.RIGHT)
        val l = golfer(hand = Handedness.LEFT)
        assertEquals(land(r, DiscModel.ANVIL, ShotShape.HYZER).x, -land(l, DiscModel.ANVIL, ShotShape.HYZER).x, 0.05f)
    }

    @Test fun powerForDistanceHitsTheTarget() {
        val p = golfer()
        val spec = ThrowSpec(Vec3.ZERO, 0f, DiscModel.ANVIL, ShotShape.FLAT, false)
        val pw = Launcher.powerForDistance(p, spec, EmptyField, 40f)
        assertTrue(pw in 0f..1f)
        assertEquals(40f, Launcher.carry(p, spec, EmptyField, pw), 0.5f)
    }

    @Test fun meterChargesThenSweepsThenFinishes() {
        val m = SwingMeter(accuracy01 = 0.5f, touch01 = 0.5f, isPutt = false)
        m.press()
        repeat(30) { m.update(1f / 60f) }
        assertTrue(m.power in 0.2f..0.8f)
        m.release()
        assertEquals(SwingMeter.Phase.ACCURACY, m.phase)
        val locked = m.power
        repeat(10) { m.update(1f / 60f) }
        assertEquals(locked, m.power, 1e-6f)
        m.press()
        assertTrue(m.isFinished)
    }

    @Test fun holdingTooLongOvercooks() {
        val m = SwingMeter(0.5f, 0.0f, false)
        m.press()
        repeat(600) { m.update(1f / 60f) }
        assertTrue(m.overcooked)
        assertEquals(1f, m.power, 1e-6f)
    }

    @Test fun tappingTheMiddleIsPerfectAndEdgesAreMisses() {
        val a = SwingMeter(0.3f, 0.3f, false, autoAccuracy = false)
        a.press(); repeat(20) { a.update(1f / 60f) }; a.release()
        // run the marker to the middle (0.5 of the sweep)
        var t = 0f
        while (a.marker < 0.5f) { a.update(1f / 240f); t += 1f / 240f }
        a.press()
        assertTrue(a.hitSweetSpot)
        assertEquals(0f, a.accuracyError, 1e-6f)

        val b = SwingMeter(0.3f, 0.3f, false)
        b.press(); repeat(20) { b.update(1f / 60f) }; b.release()
        b.press()  // tapped immediately: far left
        assertFalse(b.hitSweetSpot)
        assertTrue(b.accuracyError < -0.5f)
    }

    @Test fun accuracyRatingWidensTheSweetSpot() {
        val low = SwingMeter(0.1f, 0.5f, false).sweetHalfWidth
        val high = SwingMeter(0.9f, 0.5f, false).sweetHalfWidth
        assertTrue(high > low * 2f)
    }

    @Test fun touchRatingSlowsThePowerBar() {
        assertTrue(SwingMeter(0.5f, 1f, false).fillSeconds > SwingMeter(0.5f, 0f, false).fillSeconds)
    }

    @Test fun usingAStatRaisesIt() {
        val p = golfer()
        val before = p.stat(Stat.PUTTING)
        var ups = 0
        repeat(40) { ups += p.addStatUse(Stat.PUTTING, 10f).size }
        assertTrue(p.stat(Stat.PUTTING) > before)
        assertEquals(p.stat(Stat.PUTTING) - before, ups)
    }

    @Test fun statsNeverPass99() {
        val p = golfer()
        p.addStatUse(Stat.POWER, 1_000_000f)
        assertEquals(99, p.stat(Stat.POWER))
    }

    @Test fun levellingGivesPointsAndUnlocksDiscs() {
        val p = golfer()
        val events = p.addXp(p.xpForNextLevel())
        assertEquals(2, p.level)
        assertEquals(PlayerProfile.POINTS_PER_LEVEL, p.unspentPoints)
        assertTrue(events.any { it.kind == ProgressEvent.Kind.UNLOCK })
        assertTrue(DiscModel.FALCON in DiscModel.unlockedFor(p.level))
        val before = p.stat(Stat.POWER)
        assertTrue(p.spendPoint(Stat.POWER))
        assertEquals(before + 1, p.stat(Stat.POWER))
    }

    @Test fun profileSurvivesSaveAndLoad() {
        val p = golfer(Archetype.BOMBER, Handedness.LEFT)
        p.name = "Ace Ventura"
        p.look = Appearance(3, 4, 5, 2, 7, 3, 2)
        p.addXp(500)
        val q = ProfileCodec.decode(ProfileCodec.encode(p))
        assertNotNull(q)
        assertEquals(p.name, q!!.name)
        assertEquals(p.level, q.level)
        assertEquals(p.handedness, q.handedness)
        assertEquals(p.look, q.look)
        assertTrue(p.stats.contentEquals(q.stats))
    }

    @Test fun everyHoleHasATeeBasketAndFairness() {
        val course = Courses.defaultCourse()
        assertEquals(9, course.size)
        for (h in course) {
            assertTrue(h.length > 40f)
            assertEquals(Surface.FAIRWAY, h.surfaceAt(h.tee.x, h.tee.z + 1f))
            assertTrue("no trees on the tee of hole ${h.number}", h.trees.none { Math.hypot((it.x - h.tee.x).toDouble(), (it.z - h.tee.z).toDouble()) < 5.5 })
        }
    }

    @Test fun aFullScriptedRoundFinishesWithEveryHoleScored() {
        val p = golfer()
        val g = GameController(p, seed = 42L)
        val rnd = Random(1)
        var target = 0f
        var tapAt = 0.5f
        var guard = 0
        while (!g.finished && guard++ < 400_000) {
            val dt = 1f / 60f
            when (g.phase) {
                Phase.INTRO, Phase.RESULT, Phase.HOLE_DONE -> { g.update(dt); g.pressAction() }
                Phase.AIMING -> {
                    g.update(dt)
                    if (g.phaseTime > 0.2f) { target = minOf(g.pinPower, 1f); g.pressAction() }
                }
                Phase.CHARGING -> {
                    g.update(dt)
                    if (g.phase == Phase.CHARGING && g.meter!!.power >= target) g.releaseAction()
                    tapAt = 0.5f + rnd.nextGaussian().toFloat() * 0.05f
                }
                Phase.ACCURACY -> { g.update(dt); if (g.phase == Phase.ACCURACY && g.meter!!.marker >= tapAt) g.pressAction() }
                Phase.FLIGHT -> g.update(dt)
                Phase.ROUND_DONE -> {}
            }
        }
        assertTrue("round did not finish", g.finished)
        assertTrue(g.card.all { it in 1..(10) })
        assertEquals(1, p.roundsPlayed)
        assertTrue(p.xp > 0 || p.level > 1)
        assertTrue(p.totalThrows >= 9)
    }
}
