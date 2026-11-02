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
                        || n == "action" || n == "stick")
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
            for ((n, rc) in L.all()) if (rc.t < h * 0.5f && n != "action" && n != "stick") {
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

    @Test fun obChoicePopupFitsAndButtonsDoNotOverlap() {
        for (wDp in 640..2200 step 40) for (ratio in floatArrayOf(1.1f, 1.3f, 1.78f, 2.17f, 2.4f)) for (den in floatArrayOf(1f, 2f, 3f)) {
            val w = wDp * den; val h = w / ratio
            val d = HudLayout.scale(w, h, den)
            val o = HudLayout.obChoice(w, h, d)
            val L = HudLayout.compute(w, h, d)
            val parts = listOf("banner" to o.banner, "lie" to o.lieBtn, "rethrow" to o.rethrowBtn)
            for ((n, rc) in parts) {
                assertTrue("$n $rc off screen", rc.l >= 0 && rc.t >= 0 && rc.r <= w && rc.b <= h)
                if (n != "banner") assertTrue("$n too small for touch: $rc", rc.w >= 44 * d && rc.h >= 44 * d)
                for (name in listOf("holeBanner", "scorePanel", "windPanel", "menu", "cardBtn", "distChip", "minimap")) {
                    val other = L.all().firstOrNull { it.first == name }?.second ?: continue
                    assertTrue("$n $rc overlaps $name $other at ${wDp}dp r=$ratio", !rc.intersects(other))
                }
            }
            assertTrue("buttons overlap", !o.lieBtn.intersects(o.rethrowBtn))
            assertTrue("banner overlaps buttons", !o.banner.intersects(o.lieBtn) && !o.banner.intersects(o.rethrowBtn))
        }
    }

    @Test fun puttStepLabelsSkipTheRunup() {
        assertTrue(HudLayout.stepLabels(false, false) == listOf("1 POWER", "2 RUN-UP", "3 RELEASE"))
        assertTrue(HudLayout.stepLabels(false, true) == listOf("1 POWER", "2 RUN-UP", "3 RELEASE"))   // style ignored off the green
        assertTrue(HudLayout.stepLabels(true, false) == listOf("1 POWER", "2 RELEASE"))
        assertTrue(HudLayout.stepLabels(true, true) == listOf("1 POWER", "2 JUMP", "3 RELEASE"))
    }

    @Test fun puttStyleChipClearOfOtherPanels() {
        for (wDp in 640..2200 step 80) for (den in floatArrayOf(1f, 2f, 3f)) for (ratio in floatArrayOf(1.3f, 1.78f, 2.17f)) {
            val w = wDp * den; val h = w / ratio
            val d = HudLayout.scale(w, h, den)
            val chip = HudLayout.puttChip(h, d)
            for ((n, rc) in HudLayout.compute(w, h, d).all()) assertTrue("chip overlaps $n at ${wDp}dp r=$ratio", !chip.intersects(rc))
        }
    }
}
