package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Flight-model checks: distance bands per disc class, the S-curve, shape controls, wind, and putting percentages. */
class PhysicsTest {

    private class Res(val air: Float, val total: Float, val x: Float, val maxRight: Float, val apex: Float)

    private fun mid() = PlayerProfile("Mid").also { it.applyArchetype(Archetype.ALLROUND) }

    private fun fly(
        d: DiscModel, shape: ShotShape = ShotShape.FLAT, power: Float = 1f, elev: Float = 0f,
        wind: Vec3 = Vec3.ZERO, p: PlayerProfile = mid(),
    ): Res {
        val env = object : FlightEnvironment {
            override val wind = wind
            override fun frictionAt(p: Vec3) = 1f
            override fun resolve(flight: DiscFlight) {}
        }
        val f = Launcher.launch(p, ThrowSpec(Vec3.ZERO, 0f, d, shape, false, elevOffsetDeg = elev), env, power, 0f, false, power >= SwingMeter.PERFECT_POWER)
        var n = 0; var maxR = 0f; var apex = 0f
        while (f.state == FlightState.FLYING && n++ < 20000) { f.step(Launcher.SIM_DT); maxR = maxOf(maxR, f.pos.x); apex = maxOf(apex, f.pos.y) }
        val air = f.landing?.z ?: f.pos.z
        while (!f.isDone && n++ < 40000) f.step(Launcher.SIM_DT)
        return Res(air, f.pos.z, f.pos.x, maxR, apex)
    }

    @Test fun everyDiscClassFliesItsBandForAMidSkillGolfer() {
        fun band(d: DiscModel, lo: Float, hi: Float) {
            val r = fly(d).total
            assertTrue("${d.displayName} flew $r, wanted $lo..$hi", r in lo..hi)
        }
        band(DiscModel.DART, 50f, 72f)
        band(DiscModel.ANVIL, 80f, 100f)
        band(DiscModel.DRIFTER, 75f, 100f)
        band(DiscModel.FALCON, 110f, 140f)
        band(DiscModel.ARROW, 105f, 140f)
        band(DiscModel.HAMMER, 130f, 170f)
        band(DiscModel.COMET, 130f, 170f)
        band(DiscModel.WRAITH, 130f, 170f)
    }

    @Test fun classesAreOrderedByDistance() {
        val put = fly(DiscModel.DART).total
        val midr = fly(DiscModel.ANVIL).total
        val fair = fly(DiscModel.FALCON).total
        val dist = fly(DiscModel.WRAITH).total
        assertTrue("$put $midr $fair $dist", put < midr && midr < fair && fair < dist)
    }

    @Test fun understableDiscsTurnRightThenFadeBackLeft() {
        val arrow = fly(DiscModel.ARROW)
        assertTrue("arrow should drift right first (max ${arrow.maxRight})", arrow.maxRight > 3f)
        assertTrue("then hook back (ends ${arrow.x})", arrow.x < arrow.maxRight - 1.5f)
        val drifter = fly(DiscModel.DRIFTER)
        assertTrue("drifter turns right (max ${drifter.maxRight})", drifter.maxRight > 3f)
        assertTrue("and finishes right of centre but not past its turn (${drifter.x})", drifter.x in 0f..drifter.maxRight + 0.1f)
    }

    @Test fun overstableDiscsFadeHardLeft() {
        for (d in listOf(DiscModel.FALCON, DiscModel.HAMMER, DiscModel.WRAITH)) {
            val flat = fly(d)
            val hyzer = fly(d, ShotShape.HYZER)
            assertTrue("${d.displayName} flat should finish left (${flat.x}) and never turn much (${flat.maxRight})", flat.x < -3f && flat.maxRight < 4f)
            assertTrue("${d.displayName} hyzer should hook hard (${hyzer.x})", hyzer.x < -20f && hyzer.x < flat.x - 8f)
        }
    }

    @Test fun moreUnderstableDiscsTurnFurtherAndOverstableOnesFadeMore() {
        val drifter = fly(DiscModel.DRIFTER); val anvil = fly(DiscModel.ANVIL)
        assertTrue("drifter ${drifter.maxRight} vs anvil ${anvil.maxRight}", drifter.maxRight > anvil.maxRight + 5f && drifter.maxRight > 8f)
        val comet = fly(DiscModel.COMET); val wraith = fly(DiscModel.WRAITH)
        assertTrue("comet ${comet.maxRight} finish ${comet.x}", comet.maxRight > 5f && comet.x > wraith.x + 10f)
        assertTrue("wraith fades left ${wraith.x}", wraith.x < -10f)
    }

    @Test fun anhyzerTurnsFurtherAndHyzerFadesMoreOnEveryDisc() {
        for (d in DiscModel.values()) {
            val flat = fly(d).x
            assertTrue("${d.displayName} anhyzer", fly(d, ShotShape.ANHYZER).x > flat + 3f)
            assertTrue("${d.displayName} hyzer", fly(d, ShotShape.HYZER).x < flat - 5f)
        }
    }

    @Test fun parThreeDistancesAreReachableWithMidrangesAtSensiblePower() {
        val p = mid()
        for (dist in listOf(45f, 60f, 75f, 85f)) {
            val spec = ThrowSpec(Vec3.ZERO, 0f, DiscModel.ANVIL, ShotShape.FLAT, false)
            val pw = Launcher.powerForDistance(p, spec, EmptyField, dist)
            assertTrue("anvil $dist m needs power $pw", pw <= 1f)
            if (dist <= 60f) assertTrue("anvil $dist m should not need full power ($pw)", pw < 0.85f)
        }
        val dr = Launcher.powerForDistance(p, ThrowSpec(Vec3.ZERO, 0f, DiscModel.DRIFTER, ShotShape.FLAT, false), EmptyField, 70f)
        assertTrue("drifter 70 m power $dr", dr <= 1f)
    }

    @Test fun powerGivesMonotonicallyMoreAirDistance() {
        for (d in DiscModel.values()) {
            var last = -1f
            for (pw in listOf(0.3f, 0.5f, 0.7f, 0.9f, 1f)) {
                val a = fly(d, power = pw).air
                assertTrue("${d.displayName} at $pw: $a <= $last", a > last)
                last = a
            }
        }
    }

    @Test fun aFlatPutterGoesStraight() {
        val r = fly(DiscModel.DART)
        assertTrue("dart ended ${r.x}", Math.abs(r.x) < 5f)
    }

    @Test fun lowPowerThrowsAreShorterAndFadeEarly() {
        for (d in listOf(DiscModel.ANVIL, DiscModel.FALCON, DiscModel.WRAITH)) {
            val full = fly(d); val soft = fly(d, power = 0.5f)
            assertTrue("${d.displayName}: soft ${soft.total} vs full ${full.total}", soft.total < full.total * 0.5f)
            assertTrue("${d.displayName} soft throw should not drift right (${soft.x})", soft.x <= 0.5f)
        }
    }

    @Test fun shapeControlsBendTheFlightTheExpectedWay() {
        for (d in DiscModel.values()) {
            val hy = fly(d, ShotShape.HYZER).x
            val flat = fly(d).x
            val an = fly(d, ShotShape.ANHYZER).x
            assertTrue("${d.displayName}: hyzer $hy < flat $flat < anhyzer $an", hy < flat - 3f && an > flat + 3f)
        }
    }

    @Test fun elevationOffsetChangesHeightAndRollerStaysLow() {
        val low = fly(DiscModel.FALCON, elev = -2f)
        val high = fly(DiscModel.FALCON, elev = 6f)
        assertTrue("apex low ${low.apex} high ${high.apex}", high.apex > low.apex + 3f)
        val roller = fly(DiscModel.FALCON, ShotShape.ROLLER)
        assertTrue("roller apex ${roller.apex}", roller.apex < 3.5f)
        assertTrue("roller rolls out (air ${roller.air}, total ${roller.total})", roller.total > roller.air + 15f)
    }

    @Test fun windIsSmallButVisible() {
        val calm = fly(DiscModel.FALCON).total
        val head = fly(DiscModel.FALCON, wind = Vec3(0f, 0f, -4f)).total
        val side = fly(DiscModel.FALCON, wind = Vec3(4f, 0f, 0f))
        val calmX = fly(DiscModel.FALCON).x
        assertTrue("headwind $head vs calm $calm", head < calm - 2f && head > calm * 0.6f)
        assertTrue("crosswind pushes ${side.x} vs ${calmX}", Math.abs(side.x - calmX) in 2f..30f)
    }

    @Test fun rollingStopsAndNonRollersDoNotRunAway() {
        for (d in DiscModel.values()) {
            val r = fly(d)
            assertTrue("${d.displayName} rolled ${r.total - r.air} m", r.total - r.air in 0f..12f)
        }
    }

    private fun puttRate(distance: Float, seed: Long): Float {
        val p = mid()
        val spec = ThrowSpec(Vec3.ZERO, 0f, DiscModel.DART, ShotShape.FLAT, true)
        val pw = Launcher.powerForPutt(p, spec, EmptyField, distance, 0f)
        val hole = Hole(1, "t", 3, listOf(Vec3(0f, 0f, 0f), Vec3(0f, 0f, distance)), 20f, emptyList(), emptyList(), FlatTerrain)
        val rnd = Random(seed)
        var made = 0
        val n = 600
        repeat(n) {
            val env = HoleEnvironment(hole, Vec3.ZERO, rnd, p.pct(Stat.PUTTING))
            // A mid-skill thumb: the accuracy marker lands within a few percent of the middle, power within ~3%.
            val sweet = 0.055f + (0.12f - 0.055f) * p.pct(Stat.ACCURACY) * (if (distance <= 10f) 1.25f else 1f)
            val off = (rnd.nextGaussian().toFloat() * 0.06f).coerceIn(-0.5f, 0.5f)
            val mag = Math.abs(off)
            val e = if (mag <= sweet) 0f else Math.pow(((mag - sweet) / (0.5f - sweet)).toDouble(), 1.3).toFloat()
            val power = (pw + rnd.nextGaussian().toFloat() * 0.03f).coerceIn(0f, 1f)
            val f = Launcher.launch(p, spec, env, power, if (off < 0) -e else e, false, false, rnd.nextFloat() * 2f - 1f)
            var steps = 0
            while (!f.isDone && steps++ < 9000) f.step(Launcher.SIM_DT)
            if (f.holed) made++
        }
        return made.toFloat() / n
    }

    @Test fun circleOneAndTwoPuttingRatesAreReasonable() {
        val c1 = listOf(3f, 5f, 8f, 10f).map { puttRate(it, 11L) }.average().toFloat()
        val c2 = listOf(12f, 15f, 18f, 20f).map { puttRate(it, 12L) }.average().toFloat()
        assertTrue("circle 1 rate $c1", c1 in 0.75f..0.95f)
        assertTrue("circle 2 rate $c2", c2 in 0.25f..0.60f)
        assertTrue("tap-ins are near certain: ${puttRate(2f, 13L)}", puttRate(2f, 13L) > 0.85f)
    }

    @Test fun aDiscLandingInWaterSplashesAndStops() {
        val pond = Pond(0f, 40f, 12f, 12f)
        val hole = Hole(1, "w", 3, listOf(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 80f)), 20f, emptyList(), listOf(pond), FlatTerrain)
        val p = mid()
        val spec = ThrowSpec(Vec3.ZERO, 0f, DiscModel.ANVIL, ShotShape.FLAT, false)
        val pw = Launcher.powerForDistance(p, spec, EmptyField, 40f)
        val f = Launcher.launch(p, spec, HoleEnvironment(hole, Vec3.ZERO, Random(1), 0.5f), pw, 0f, false, false)
        var n = 0
        while (!f.isDone && n++ < 20000) f.step(Launcher.SIM_DT)
        assertTrue("expected a splash: ${f.events}", FlightEvent.SPLASH in f.events)
        assertEquals(FlightState.STOPPED, f.state)
    }
}
