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

    // ---------------------------------------------------------------- terrain

    private fun slopeTerrain(dhdx: Float, dhdz: Float): GridTerrain {
        val n = 41; val cell = 10f
        val hs = FloatArray(n * n)
        for (iz in 0 until n) for (ix in 0 until n) hs[iz * n + ix] = dhdx * (-200f + ix * cell) + dhdz * (-200f + iz * cell)
        return GridTerrain(-200f, -200f, cell, n, n, hs)
    }

    private fun landOn(t: Terrain, disc: DiscModel, shape: ShotShape): Vec3 {
        val p = golfer()
        val origin = Vec3(0f, t.heightAt(0f, 0f), 0f)
        val env = WindOnly(Vec3.ZERO, t)
        val f = Launcher.launch(p, ThrowSpec(origin, 0f, disc, shape, false), env, 1f, 0f, false, false)
        var n = 0
        while (!f.isDone && n++ < 40000) f.step(Launcher.SIM_DT)
        return f.pos
    }

    @Test fun gridTerrainInterpolatesHeightAndSlope() {
        val t = slopeTerrain(0f, 0.1f)
        assertEquals(0.5f, t.heightAt(3f, 5f), 1e-3f)
        assertEquals(0.1f, t.gradientAt(3f, 5f).z, 1e-4f)
        assertEquals(0f, t.gradientAt(3f, 5f).x, 1e-4f)
        // beyond the edge it holds the edge height instead of failing
        assertEquals(t.heightAt(200f, 0f), t.heightAt(900f, 0f), 1e-4f)
    }

    @Test fun uphillShortensTheThrowAndDownhillLengthensIt() {
        val flat = landOn(FlatTerrain, DiscModel.ANVIL, ShotShape.FLAT).z
        val up = landOn(slopeTerrain(0f, 0.04f), DiscModel.ANVIL, ShotShape.FLAT).z
        val down = landOn(slopeTerrain(0f, -0.04f), DiscModel.ANVIL, ShotShape.FLAT).z
        assertTrue("uphill $up vs flat $flat", up < flat - 3f)
        assertTrue("downhill $down vs flat $flat", down > flat + 3f)
    }

    @Test fun aRollingDiscDriftsDownASideSlope() {
        val flat = landOn(FlatTerrain, DiscModel.ANVIL, ShotShape.ANHYZER).x
        val slope = landOn(slopeTerrain(-0.12f, 0f), DiscModel.ANVIL, ShotShape.ANHYZER).x   // ground falls toward +x
        assertTrue("slope $slope vs flat $flat", slope > flat + 1f)
    }

    @Test fun aDiscStopsOnAnySlopeInTheGame() {
        for (s in listOf(0.05f, 0.15f, 0.30f)) {
            val end = landOn(slopeTerrain(s, 0f), DiscModel.FALCON, ShotShape.FLAT)
            assertTrue("did not come to rest on a $s slope", Math.abs(end.x) < 400f)
        }
    }

    @Test fun builtInCourseHasTwoLayoutsAndTeesSitOnTheGround() {
        val c = Courses.builtIn()
        assertEquals(2, c.layouts.size)
        val full = c.layouts[0]; val rec = c.layouts[1]
        assertEquals(9, full.holes.size)
        assertEquals(9, rec.holes.size)
        assertTrue(rec.length < full.length * 0.9f)
        for (h in full.holes) {
            assertEquals(h.terrain.heightAt(h.tee.x, h.tee.z), h.tee.y, 1e-4f)
            assertEquals(h.terrain.heightAt(h.basket.x, h.basket.z), h.basket.y, 1e-4f)
        }
        assertTrue(full.holes.any { Math.abs(it.basket.y - it.tee.y) > 0.5f })   // some holes really climb or drop
    }

    @Test fun pondsHoldWaterAndSitInABowl() {
        for (h in Courses.defaultCourse()) for (p in h.ponds) {
            assertTrue("pond bed above water on hole ${h.number}", h.groundAt(p.cx, p.cz) < p.level - 0.3f)
            assertTrue("pond bank below water on hole ${h.number}", h.groundAt(p.cx + p.rx * 1.25f, p.cz) > p.level)
        }
    }

    @Test fun teeAndBasketGroundIsFlatEnoughToStandOn() {
        for (h in Courses.defaultCourse()) {
            for (spot in listOf(h.tee, h.basket)) {
                val g = h.terrain.gradientAt(spot.x, spot.z)
                assertTrue("slope ${g.lengthXZ()} at hole ${h.number}", g.lengthXZ() < 0.12f)
            }
        }
    }

    // ---------------------------------------------------------------- course files

    private val sampleCourse = """
    {
      "name": "Test Park", "location": "Nowhere",
      "terrain": { "x0": 0, "z0": 0, "cell": 50, "nx": 4, "nz": 4,
        "heights": [100,100,100,100, 101,101,101,101, 102,102,102,102, 103,103,103,103] },
      "holes": [
        { "number": 1, "name": "Long Walk", "fairwayWidth": 12,
          "tees": { "Blue": [75, 10], "Red": [75, 50] }, "baskets": { "A": [75, 130] } },
        { "number": 2, "name": "The Bend",
          "tees": { "Blue": [20, 20] }, "baskets": { "A": [60, 110] }, "via": [[20, 70]],
          "ponds": [[40, 90, 5, 5]], "trees": [[30, 60, 9, 2.0]] }
      ],
      "layouts": [
        { "name": "Pro", "description": "all the way back", "holes": [ {"hole": 1, "tee": "Blue"}, {"hole": 2, "tee": "Blue", "par": 4} ] },
        { "name": "Rec", "holes": [ {"hole": 1, "tee": "Red"} ] }
      ]
    }
    """

    @Test fun courseFileGivesLayoutsThatShareTheLand() {
        val c = CourseFile.parse(sampleCourse)
        assertEquals("test-park", c.id)
        assertEquals(listOf("Pro", "Rec"), c.layouts.map { it.name })
        val pro = c.layouts[0]; val rec = c.layouts[1]
        assertEquals(2, pro.holes.size)
        assertEquals(1, rec.holes.size)
        assertTrue(pro.holes[0].length > rec.holes[0].length + 30f)
        assertEquals(4, pro.holes[1].par)
        assertEquals(3, rec.holes[0].par.coerceAtMost(3))
        // heights come from the grid: z=10 -> about 100.2, z=130 -> about 102.6
        assertEquals(100.2f, pro.holes[0].tee.y, 0.05f)
        assertEquals(102.6f, pro.holes[0].basket.y, 0.05f)
        assertTrue(pro.holes[1].path.size == 3)        // tee, via, basket
        assertEquals(1, pro.holes[1].trees.size)       // trees given in the file are used as they are
        assertTrue(pro.holes[0].trees.isNotEmpty())    // holes without trees get some
    }

    @Test fun courseFileWithoutLayoutsPlaysEveryHole() {
        val c = CourseFile.parse("""{"name":"Mini","holes":[{"number":2,"tees":{"T":[0,0]},"baskets":{"B":[0,70]}},{"number":1,"tees":{"T":[0,0]},"baskets":{"B":[0,50]}}]}""")
        assertEquals(1, c.layouts.size)
        assertEquals(listOf(1, 2), c.layouts[0].holes.map { it.number })
        assertEquals(50f, c.layouts[0].holes[0].length, 0.01f)
    }

    @Test fun courseFileRejectsBrokenInput() {
        fun bad(text: String) = try { CourseFile.parse(text); false } catch (e: CourseFile.FormatException) { true }
        assertTrue(bad("not json"))
        assertTrue(bad("""{"holes":[]}"""))
        assertTrue(bad("""{"name":"x","holes":[]}"""))
        assertTrue(bad("""{"name":"x","holes":[{"number":1,"tees":{"T":[0,0]},"baskets":{"B":[0,50]}}],"layouts":[{"name":"L","holes":[{"hole":9}]}]}"""))
        assertTrue(bad("""{"name":"x","terrain":{"nx":3,"nz":3,"heights":[1,2]},"holes":[{"number":1,"tees":{"T":[0,0]},"baskets":{"B":[0,50]}}]}"""))
    }

    @Test fun aRoundOnTheRecreationalLayoutFinishes() {
        val course = Courses.builtIn().layouts[1].holes
        val p = golfer()
        val g = GameController(p, course, seed = 7L)
        var guard = 0
        var target = 0f
        while (!g.finished && guard++ < 400_000) {
            val dt = 1f / 60f
            when (g.phase) {
                Phase.INTRO, Phase.RESULT, Phase.HOLE_DONE -> { g.update(dt); g.pressAction() }
                Phase.AIMING -> { g.update(dt); if (g.phaseTime > 0.2f) { target = minOf(g.pinPower, 1f); g.pressAction() } }
                Phase.CHARGING -> { g.update(dt); if (g.phase == Phase.CHARGING && g.meter!!.power >= target) g.releaseAction() }
                Phase.ACCURACY -> { g.update(dt); if (g.phase == Phase.ACCURACY && g.meter!!.marker >= 0.5f) g.pressAction() }
                Phase.FLIGHT -> g.update(dt)
                Phase.ROUND_DONE -> {}
            }
        }
        assertTrue(g.finished)
        assertTrue(g.card.all { it in 1..10 })
    }

    @Test fun holesMayPointInAnyDirection() {
        val json = """{"name":"T","holes":[{"number":1,"tees":{"A":[0,0]},"baskets":{"A":[0,-100]}},
            {"number":2,"tees":{"A":[0,0]},"baskets":{"A":[-100,0]}}]}"""
        val c = CourseFile.parse(json)
        for (h in c.layouts[0].holes) {
            val (mid, _) = h.pointAt(h.length / 2f)
            assertEquals(Surface.FAIRWAY, h.surfaceAt(mid.x, mid.z))
            assertEquals(Surface.OUT_OF_BOUNDS, h.surfaceAt(h.tee.x - (h.basket.x - h.tee.x) * 0.5f, h.tee.z - (h.basket.z - h.tee.z) * 0.5f))
        }
    }

    private fun flyEnd(spec: ThrowSpec, power: Float = 0.8f): Vec3 {
        val p = PlayerProfile()
        val f = Launcher.launch(p, spec, EmptyField, power, 0f, false, false)
        var n = 0
        while (!f.isDone && n < 20000) { f.step(Launcher.SIM_DT); n++ }
        return f.pos
    }

    @Test fun forehandMirrorsTheCurve() {
        val base = ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.HYZER, false)
        val bh = flyEnd(base)
        val fh = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.HYZER, false, forehand = true))
        assertTrue("backhand hyzer ends left, forehand ends right: ${bh.x} vs ${fh.x}", bh.x * fh.x < 0f)
    }

    @Test fun launchAngleChangesTheFlight() {
        val low = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.FLAT, false, elevOffsetDeg = -3f))
        val high = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.FLAT, false, elevOffsetDeg = 8f))
        assertTrue("higher launch should not land at the same place", Math.abs(high.z - low.z) > 2f)
    }

    @Test fun hyzerAndAnhyzerCurveOppositeWays() {
        val hy = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.HYZER, false))
        val an = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.ANHYZER, false))
        assertTrue("hyzer ${hy.x}, anhyzer ${an.x}", hy.x * an.x < 0f)
    }

    @Test fun rollerRollsFarther() {
        val flat = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.FLAT, false), 1f)
        val roller = flyEnd(ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.ROLLER, false), 1f)
        assertTrue("roller z=${roller.z} flat z=${flat.z}", roller.z > flat.z * 0.6f && roller.z > 20f)
    }
}
