package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObChoiceTest {
    private fun hole(ponds: List<Pond>) =
        Hole(1, "T", 3, listOf(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 100f)), 20f, emptyList(), ponds)

    @Test fun liePointIsDryLandJustBeforeThePond() {
        val h = hole(listOf(Pond(0f, 50f, 10f, 10f)))   // water z in 40..60
        val lie = GameController.obLiePoint(h, Vec3(0f, 0f, 5f), Vec3(0f, 0f, 50f))
        assertTrue("lie z=${lie.z}", lie.z in 38f..40f)
        assertTrue(h.surfaceAt(lie.x, lie.z) != Surface.WATER)
    }

    @Test fun liePointAtSidelineStaysInBounds() {
        val h = hole(emptyList())
        val lie = GameController.obLiePoint(h, Vec3(0f, 0f, 30f), Vec3(120f, 0f, 30f))
        assertTrue(h.surfaceAt(lie.x, lie.z) != Surface.OUT_OF_BOUNDS)
        assertTrue("x=${lie.x}", lie.x in 55f..62f)
    }

    @Test fun liePointNeverBeyondThrowOriginAndRethrowDiffers() {
        val h = hole(listOf(Pond(0f, 20f, 10f, 10f)))
        val from = Vec3(0f, 0f, 10.4f)
        val lie = GameController.obLiePoint(h, from, Vec3(0f, 0f, 20f))
        assertTrue(lie.z >= from.z - 1e-3f)
        assertTrue(lie.z <= 10.4f + 0.2f)
    }

    @Test fun choosingOutsideObPhaseDoesNothing() {
        val g = GameController(PlayerProfile("T").also { it.applyArchetype(Archetype.ALLROUND) }, seed = 3L)
        val before = g.strokes
        g.chooseOb(true)
        assertEquals(before, g.strokes)
        assertTrue(g.phase != Phase.OB_CHOICE)
    }

    @Test fun obThrowAddsPenaltyAndRequiresChoice() {
        // Aim straight into the left sideline with full power until some throw ends OB (seeded search).
        val ponds = listOf(Pond(0f, 30f, 60f, 25f))   // pond covering the whole fairway ahead
        val h = hole(ponds)
        val g = GameController(PlayerProfile("T").also { it.applyArchetype(Archetype.ALLROUND) }, listOf(h), seed = 9L, autoAccuracy = true)
        var guard = 0
        val dt = 1f / 60f
        while (g.phase != Phase.OB_CHOICE && guard++ < 100_000) {
            when (g.phase) {
                Phase.INTRO, Phase.RESULT -> { g.update(dt); g.pressAction() }
                Phase.AIMING -> { g.update(dt); if (g.phaseTime > 0.2f) g.pressAction() }
                Phase.POWER -> { g.update(dt); if (g.phase == Phase.POWER && g.meter!!.power >= 0.5f) g.releaseAction() }
                Phase.RUNUP -> { g.update(dt); if (g.phase == Phase.RUNUP && g.meter!!.runupMarker >= ThrowTiming.RUNUP_CENTER) g.pressAction() }
                Phase.RELEASE_ANGLE -> { g.update(dt); if (g.phase == Phase.RELEASE_ANGLE && g.meter!!.releaseMarker >= 0.5f) g.pressAction() }
                else -> g.update(dt)
            }
        }
        assertEquals(Phase.OB_CHOICE, g.phase)
        assertEquals(2, g.strokes)                       // throw + penalty
        // Time and taps never skip the choice.
        repeat(1000) { g.update(0.1f); g.pressAction() }
        assertEquals(Phase.OB_CHOICE, g.phase)
        assertTrue(g.obLie.distanceXZ(g.hole.basket) != g.obRethrow.distanceXZ(g.hole.basket) || g.obLie != g.obRethrow)
        g.chooseOb(true)
        assertEquals(Phase.AIMING, g.phase)
        assertEquals(2, g.strokes)
        assertEquals(g.obLie.x, g.lie.x, 1e-4f)
    }
}
