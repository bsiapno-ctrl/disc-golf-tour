package com.discgolf.game.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class HudLayoutTest {
    @Test fun panelsNeverIntersectAcrossScreens() {
        val densities = floatArrayOf(1f, 2f, 2.625f, 3f)
        val ratios = floatArrayOf(1.1f, 1.3f, 1.6f, 1.78f, 2.0f, 2.17f, 2.4f)
        var wDp = 640
        while (wDp <= 2200) {
            for (den in densities) for (ratio in ratios) {
                val w = wDp * den; val h = w / ratio
                val d = HudLayout.scale(w, h, den)
                val L = HudLayout.compute(w, h, d)
                val all = L.all()
                for (i in all.indices) {
                    val (n, rc) = all[i]
                    assertTrue("$n off screen at ${wDp}dp r=$ratio: $rc", rc.l >= 0 && rc.t >= 0 && rc.r <= w + 0.5f && rc.b <= h + 0.5f
                        || n == "action" || n == "stick" || n == "relStick")
                    for (j in i + 1 until all.size) {
                        val (m, o) = all[j]
                        assertTrue("$n $rc overlaps $m $o at ${wDp}dp r=$ratio den=$den", !rc.intersects(o))
                    }
                }
            }
            wDp += 40
        }
    }

    @Test fun topPanelsKeepScreenCentreClearForGolfer() {
        for (wDp in 640..2200 step 40) for (ratio in floatArrayOf(1.3f, 1.78f, 2.17f, 2.4f)) {
            val w = wDp * 2f; val h = w / ratio
            val L = HudLayout.compute(w, h, HudLayout.scale(w, h, 2f))
            for ((n, rc) in L.all()) if (rc.t < h * 0.5f && n != "action" && n != "stick" && n != "relStick") {
                assertTrue("$n $rc enters centre band at ${wDp}dp r=$ratio", rc.r <= w * 0.30f + 1f || rc.l >= w * 0.70f - 1f)
            }
        }
    }

    @Test fun swingDialStaysRightOfCentreBand() {
        for (wDp in 640..2200 step 40) for (ratio in floatArrayOf(1.1f, 1.3f, 1.78f, 2.17f, 2.4f)) {
            val w = wDp * 2f; val h = w / ratio
            val L = HudLayout.compute(w, h, HudLayout.scale(w, h, 2f))
            for ((n, rc) in listOf("meter" to L.meter, "meterHint" to L.meterHint, "pinChip" to L.pinChip)) {
                assertTrue("$n $rc enters centre band at ${wDp}dp r=$ratio", rc.l >= w * 0.60f)
            }
            assertTrue("dial not square ${L.meter}", kotlin.math.abs(L.meter.w - L.meter.h) < 0.5f)
        }
    }

    @Test fun releaseStickSitsBetweenThrowPanelAndDial() {
        for (wDp in 640..2200 step 40) for (ratio in floatArrayOf(1.1f, 1.3f, 1.78f, 2.17f, 2.4f)) {
            val w = wDp * 2f; val h = w / ratio
            val L = HudLayout.compute(w, h, HudLayout.scale(w, h, 2f))
            assertTrue("relStick ${L.relStick} vs panel ${L.throwPanel}", L.relStick.l >= L.throwPanel.r)
            assertTrue("relStick ${L.relStick} vs dial ${L.meter}", L.relStick.r <= L.meter.l)
            assertTrue("relStick ${L.relStick} vs action", !L.relStick.intersects(L.action))
            assertTrue("relStick bottom ${L.relStick} h=$h", L.relStick.b <= h && L.relStick.t >= 0f)
        }
    }

    @Test fun stepBarSitsAboveTheDialClearOfEverything() {
        for (wDp in 640..2200 step 40) for (ratio in floatArrayOf(1.1f, 1.3f, 1.78f, 2.17f, 2.4f)) for (den in floatArrayOf(1f, 2f, 3f)) {
            val w = wDp * den; val h = w / ratio
            val L = HudLayout.compute(w, h, HudLayout.scale(w, h, den))
            val B = HudLayout.stepBar(L.meter, HudLayout.scale(w, h, den))
            assertTrue("stepBar $B off screen", B.l >= 0 && B.t >= 0 && B.r <= w && B.b <= h)
            assertTrue("stepBar $B enters centre band at ${wDp}dp r=$ratio", B.l >= w * 0.60f)
            for ((n, rc) in L.all()) assertTrue("stepBar $B overlaps $n $rc at ${wDp}dp r=$ratio den=$den", !B.intersects(rc))
        }
    }
}
