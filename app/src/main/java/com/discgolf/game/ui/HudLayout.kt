package com.discgolf.game.ui

import kotlin.math.max
import kotlin.math.min

/** Plain rectangle (pixels) so the HUD layout can be unit-tested on the JVM without android.graphics. */
class HRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    fun centerXf() = (l + r) / 2f
    fun intersects(o: HRect) = l < o.r && o.l < r && t < o.b && o.t < b
    override fun toString() = "[$l,$t,$r,$b]"
}

/** Every HUD panel's bounds. [minimap] is null when there is no room for it. */
class HudRects(
    val holeBanner: HRect, val distChip: HRect, val scorePanel: HRect, val windPanel: HRect,
    val menu: HRect, val cardBtn: HRect, val minimap: HRect?, val discCard: HRect, val throwPanel: HRect,
    val meter: HRect, val meterHint: HRect, val pinChip: HRect, val stick: HRect, val action: HRect,
    val actionCx: Float, val actionCy: Float, val actionR: Float,
) {
    fun all(): List<Pair<String, HRect>> = listOfNotNull(
        "holeBanner" to holeBanner, "distChip" to distChip, "scorePanel" to scorePanel, "windPanel" to windPanel,
        "menu" to menu, "cardBtn" to cardBtn, minimap?.let { "minimap" to it }, "discCard" to discCard,
        "throwPanel" to throwPanel, "meter" to meter, "meterHint" to meterHint, "pinChip" to pinChip, "stick" to stick, "action" to action,
    )
}

/** Pure layout for the in-game HUD. All sizes are design units times [d]. */
object HudLayout {
    /** Same scale rule HudView has always used: design for 760 x 360 dp, clamp the zoom. */
    fun scale(w: Float, h: Float, baseDp: Float): Float =
        baseDp * min(w / baseDp / 760f, h / baseDp / 360f).coerceIn(0.5f, 1.6f)

    /** The "1 POWER - 2 RUN-UP - 3 RELEASE" step indicator, floating just above the swing dial's pin chip. */
    fun stepBar(meter: HRect, d: Float): HRect =
        HRect(meter.centerXf() - 88 * d, meter.t - 72 * d, meter.centerXf() + 68 * d, meter.t - 52 * d)

    fun compute(w: Float, h: Float, d: Float): HudRects {
        val acx = w - 82 * d; val acy = h - 100 * d; val ar = 64 * d
        val mapTop = 100 * d
        val mapBottom = min(mapTop + 190 * d, acy - ar - 6 * d)
        val map = if (mapBottom - mapTop < 90 * d) null else HRect(w - 124 * d, mapTop, w - 12 * d, mapBottom)
        // Stick hit zone is radius * 1.35 around (70, h-56) with radius 46.
        // Round swing dial sits left of the action button, right of the 60% x line so the golfer's body stays visible.
        val dcx = w - 214 * d; val dR = 48 * d
        val sr = 46 * d * 1.35f
        return HudRects(
            holeBanner = HRect(12 * d, 10 * d, 222 * d, 70 * d),
            distChip = HRect(w - 228 * d, 108 * d, w - 130 * d, 136 * d),
            scorePanel = HRect(12 * d, 76 * d, 222 * d, 124 * d),
            windPanel = HRect(w - 228 * d, 10 * d, w - 136 * d, 102 * d),
            menu = HRect(w - 126 * d, 10 * d, w - 12 * d, 46 * d),
            cardBtn = HRect(w - 126 * d, 52 * d, w - 12 * d, 88 * d),
            minimap = map,
            discCard = HRect(8 * d, h - 228 * d, 220 * d, h - 124 * d),
            throwPanel = HRect(140 * d, h - 40 * d, 292 * d, h - 4 * d),
            meter = HRect(dcx - dR, h - 126 * d, dcx + dR, h - 126 * d + 2 * dR),
            meterHint = HRect(w - 276 * d, h - 24 * d, w - 152 * d, h - 6 * d),
            pinChip = HRect(w - 264 * d, h - 150 * d, w - 164 * d, h - 132 * d),
            stick = HRect(70 * d - sr, h - 56 * d - sr, 70 * d + sr, h - 56 * d + sr),
            action = HRect(acx - ar, acy - ar, acx + ar, acy + ar),
            actionCx = acx, actionCy = acy, actionR = ar,
        )
    }
}
