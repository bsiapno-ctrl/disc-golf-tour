package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aim pitch (loft): auto pitch from height difference, and flights that clear a ridge only when lofted. */
class AimPitchTest {
    private val ridgeStart = 40f
    private val ridgeTop = 7f

    private class Run(val apex: Float, val hang: Float, val range: Float, val hitZ: Float)

    /** Flat ground, then a steep ridge face at z = 40 rising to a plateau [ridgeTop] high. */
    private fun env(ridge: Boolean) = object : FlightEnvironment {
        override val wind = Vec3.ZERO
        override fun frictionAt(p: Vec3) = 1f
        override fun resolve(flight: DiscFlight) {}
        override fun groundAt(x: Float, z: Float) = if (ridge && z >= ridgeStart) ridgeTop else 0f
    }

    private fun fly(pitch: Float, ridge: Boolean, power: Float = 0.8f): Run {
        val p = PlayerProfile("T").also { it.applyArchetype(Archetype.ALLROUND) }
        val f = Launcher.launch(p, ThrowSpec(Vec3.ZERO, 0f, DiscModel.FALCON, ShotShape.FLAT, false, aimPitchDeg = pitch), env(ridge), power, 0f, false, false)
        var n = 0; var apex = 0f
        while (f.state == FlightState.FLYING && n++ < 20000) { f.step(Launcher.SIM_DT); apex = maxOf(apex, f.pos.y) }
        val land = f.landing ?: f.pos
        return Run(apex, f.time, land.z, land.z)
    }

    @Test fun autoPitchFollowsHeightDifference() {
        assertEquals(0f, Launcher.autoPitchDeg(0f, 80f), 1e-4f)
        assertEquals(Math.toDegrees(Math.atan2(6.0, 60.0)).toFloat(), Launcher.autoPitchDeg(6f, 60f), 1e-3f)
        assertTrue(Launcher.autoPitchDeg(-6f, 60f) < 0f)
        assertEquals(Launcher.MAX_PITCH_DEG, Launcher.autoPitchDeg(200f, 20f), 1e-4f)
        assertEquals(Launcher.MIN_PITCH_DEG, Launcher.autoPitchDeg(-200f, 20f), 1e-4f)
    }

    @Test fun morePitchGivesHigherApexAndLongerHang() {
        val lo = fly(0f, false); val mid = fly(10f, false); val hi = fly(25f, false)
        assertTrue("apex ${lo.apex} ${mid.apex} ${hi.apex}", lo.apex < mid.apex && mid.apex < hi.apex)
        assertTrue("hang ${lo.hang} ${mid.hang} ${hi.hang}", lo.hang < mid.hang && mid.hang < hi.hang)
        assertTrue("a steep loft loses distance: ${hi.range} vs ${lo.range}", hi.range < lo.range)
    }

    @Test fun pitchDownFliesLowerAndFlatter() {
        assertTrue(fly(-8f, false).apex < fly(0f, false).apex)
    }

    @Test fun clearsRidgeOnlyWithPitch() {
        val flat = fly(0f, true)
        assertTrue("unpitched throw should die on the ridge face, landed ${flat.range}", flat.range < ridgeStart + 1.5f)
        val lofted = fly(14f, true)
        assertTrue("lofted throw should clear to the plateau, landed ${lofted.range}", lofted.range > ridgeStart + 3f)
    }
}
