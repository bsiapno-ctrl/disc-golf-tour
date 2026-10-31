package com.discgolf.game.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AimCurveTest {
    @Test fun deadZoneIsZero() {
        assertEquals(0f, AimCurve.response(0f), 0f)
        assertEquals(0f, AimCurve.response(AimCurve.DEAD * 0.9f), 0f)
    }
    @Test fun fullDeflectionIsOne() {
        assertEquals(1f, AimCurve.response(1f), 1e-5f)
        assertEquals(-1f, AimCurve.response(-3f), 1e-5f)
    }
    @Test fun symmetricAndMonotonic() {
        var prev = 0f
        for (i in 1..100) {
            val x = i / 100f
            val y = AimCurve.response(x)
            assertEquals(-y, AimCurve.response(-x), 1e-6f)
            assertTrue(y >= prev); prev = y
        }
    }
    @Test fun fineControlNearCentre() {
        // a quarter deflection must give well under a quarter of the full rate
        assertTrue(AimCurve.response(0.25f) < 0.12f)
        assertTrue(AimCurve.response(0.5f) < 0.35f)
    }
}
