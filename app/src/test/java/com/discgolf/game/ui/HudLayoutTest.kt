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
}
