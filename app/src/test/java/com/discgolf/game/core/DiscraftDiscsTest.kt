package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Discraft molds carry their published Speed/Glide/Turn/Fade; their simulated flights must follow those numbers. */
class DiscraftDiscsTest {

    private class Fl(val total: Float, val x: Float, val maxRight: Float, val landX: Float)

    private fun player(hand: Handedness = Handedness.RIGHT, pro: Boolean = false) =
        PlayerProfile("T").also {
            it.applyArchetype(Archetype.ALLROUND)
            it.handedness = hand
            if (pro) for (s in Stat.values()) it.stats[s.ordinal] = 80
        }

    private fun fly(d: DiscModel, p: PlayerProfile = player(), shape: ShotShape = ShotShape.FLAT): Fl {
        val f = Launcher.launch(p, ThrowSpec(Vec3.ZERO, 0f, d, shape, false), EmptyField, 1f, 0f, false, true)
        var n = 0; var maxR = 0f
        while (f.state == FlightState.FLYING && n++ < 20000) { f.step(Launcher.SIM_DT); maxR = maxOf(maxR, f.pos.x * p.handedness.sign) }
        while (!f.isDone && n++ < 40000) f.step(Launcher.SIM_DT)
        return Fl(f.pos.z, f.pos.x * p.handedness.sign, maxR, (f.landing?.x ?: f.pos.x) * p.handedness.sign)
    }

    private val discraft get() = DiscModel.values().filter { it.discraft }

    @Test fun dataFileCountsMatchTheEnum() {
        assertTrue("expected 40+ Discraft discs, got ${discraft.size}", discraft.size >= 40)
        assertEquals(discraft.size, discraft.map { it.displayName }.toSet().size)
        for (d in discraft) {
            assertTrue("${d.displayName} speed", d.speed in 2..13)
            assertTrue("${d.displayName} glide", d.glide in 1..7)
            assertTrue("${d.displayName} turn", d.turn in -5f..1f)
            assertTrue("${d.displayName} fade", d.fade in 0f..5f)
        }
        for (k in DiscModel.kindOrder) assertTrue("no $k", discraft.any { it.kind == k })
    }

    @Test fun distanceIsMonotoneInSpeedClass() {
        val means = discraft.groupBy { it.speed }.toSortedMap().mapValues { e -> e.value.map { fly(it, player(pro = true)).total }.average() }
        var last = 0.0
        for ((sp, m) in means) {
            assertTrue("speed $sp mean $m must beat previous $last (all: $means)", m > last)
            last = m
        }
        // a disc four or more speeds faster always out-flies a slower one
        for (a in discraft) for (b in discraft) if (b.speed >= a.speed + 4)
            assertTrue("${b.displayName} (${b.speed}) should out-fly ${a.displayName} (${a.speed})", fly(b, player(pro = true)).total > fly(a, player(pro = true)).total)
    }

    @Test fun classDistancesAreSensible() {
        fun band(label: String, ds: List<DiscModel>, lo: Float, hi: Float, pro: Boolean) {
            for (d in ds) { val t = fly(d, player(pro = pro)).total; assertTrue("$label ${d.displayName} flew $t, wanted $lo..$hi", t in lo..hi) }
        }
        band("putter", discraft.filter { it.speed <= 4 && it.isPutter }, 40f, 90f, false)
        band("midrange", discraft.filter { it.kind == "Midrange" }, 70f, 110f, false)
        band("12-speed pro", discraft.filter { it.speed == 12 }, 120f, 155f, true)
        band("13-speed pro", discraft.filter { it.speed == 13 }, 125f, 162f, true)
        band("fairway", discraft.filter { it.kind == "Fairway" }, 80f, 145f, false)
    }

    @Test fun turnAndFadeSignsBendTheRightWay() {
        for (d in discraft) {
            val r = fly(d)
            // 12-13 speed discs need more arm than a mid-skill golfer has to turn over, so only check the rest
            if (d.turn <= -2f && d.speed <= 11) assertTrue("${d.displayName} (turn ${d.turn}) should drift right, max ${r.maxRight}", r.maxRight > 2f)
            if (d.turn + d.fade >= 3f && d.speed >= 5) assertTrue("${d.displayName} should finish left, ended ${r.x}", r.x < -3f)
            if (d.fade >= 2f && d.speed >= 5) assertTrue("${d.displayName} should fade back from its max ${r.maxRight} (ended ${r.x})", r.x < r.maxRight - 1f)
            val lh = fly(d, player(Handedness.LEFT))
            assertEquals("${d.displayName} lefty mirrors (air)", r.landX, lh.landX, 0.5f)
        }
    }

    @Test fun moreTurnMeansFurtherRightAtTheSameSpeed() {
        val heat = fly(DiscModel.HEAT); val cic = fly(DiscModel.CICADA)
        for ((a, b) in listOf(DiscModel.MALTA to DiscModel.METEOR, DiscModel.SWARM to DiscModel.BUZZZ_SS, DiscModel.CHALLENGER_OS to DiscModel.CHALLENGER_SS)) {
            val oa = fly(a); val ob = fly(b)
            assertTrue("${b.displayName} (turn ${b.turn}) max ${ob.maxRight} vs ${a.displayName} (turn ${a.turn}) ${oa.maxRight}", ob.maxRight > oa.maxRight + 0.5f)
            assertTrue("${a.displayName} should finish left of ${b.displayName}", oa.x < ob.x)
        }
        assertTrue("heat ${heat.maxRight} cicada ${cic.maxRight}", heat.maxRight > 3f && cic.maxRight > 0f)
    }

    @Test fun stabilityLabelsFollowTheNumbers() {
        assertEquals("UNDERSTABLE", DiscModel.HEAT.stability)
        assertEquals("OVERSTABLE", DiscModel.ZONE.stability)
        assertEquals("STABLE", DiscModel.BUZZZ.stability)
        val sk = DiscModel.HEAT.flightSketch()
        assertTrue(sk.maxOf { it.first } > 0.3f && sk.last().first <= sk.maxOf { it.first })
        assertTrue(DiscModel.FLICK.flightSketch().last().first < -0.3f)
    }

    @Test fun newPlayerStartsWithEveryTypeAndBagGrows() {
        val bag = DiscModel.unlockedFor(1)
        for (k in DiscModel.kindOrder) assertTrue("level-1 bag lacks $k", bag.any { it.kind == k })
        assertTrue(bag.size in 6..14)
        var last = 0
        for (lv in 1..PlayerProfile.MAX_LEVEL) { val n = DiscModel.unlockedFor(lv).size; assertTrue(n >= last); last = n }
        assertEquals(DiscModel.values().size, DiscModel.unlockedFor(PlayerProfile.MAX_LEVEL).size)
    }
}
