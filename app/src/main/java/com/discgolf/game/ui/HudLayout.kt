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
    val meter: HRect, val stick: HRect, val action: HRect,
    val actionCx: Float, val actionCy: Float, val actionR: Float,
) {
    fun all(): List<Pair<String, HRect>> = listOfNotNull(
        "holeBanner" to holeBanner, "distChip" to distChip, "scorePanel" to scorePanel, "windPanel" to windPanel,
        "menu" to menu, "cardBtn" to cardBtn, minimap?.let { "minimap" to it }, "discCard" to discCard,
        "throwPanel" to throwPanel, "meter" to meter, "stick" to stick, "action" to action,
    )
}

/** Pure layout for the in-game HUD. All sizes are design units times [d]. */
object HudLayout {
    /** Same scale rule HudView has always used: design for 760 x 360 dp, clamp the zoom. */
    fun scale(w: Float, h: Float, baseDp: Float): Float =
        baseDp * min(w / baseDp / 760f, h / baseDp / 360f).coerceIn(0.5f, 1.6f)

    fun compute(w: Float, h: Float, d: Float): HudRects {
        val acx = w - 96 * d; val acy = h - 100 * d; val ar = 70 * d
        val mapTop = 100 * d
        val mapBottom = min(mapTop + 190 * d, acy - ar - 10 * d)
        val map = if (mapBottom - mapTop < 90 * d) null else HRect(w - 124 * d, mapTop, w - 12 * d, mapBottom)
        // Stick hit zone is radius * 1.35 around (70, h-56) with radius 46.
        val sr = 46 * d * 1.35f
        return HudRects(
            holeBanner = HRect(12 * d, 10 * d, 222 * d, 70 * d),
            distChip = HRect(12 * d, 76 * d, 150 * d, 104 * d),
            scorePanel = HRect(w / 2f - 100 * d, 10 * d, w / 2f + 100 * d, 62 * d),
            windPanel = HRect(w - 228 * d, 10 * d, w - 136 * d, 102 * d),
            menu = HRect(w - 126 * d, 10 * d, w - 12 * d, 46 * d),
            cardBtn = HRect(w - 126 * d, 52 * d, w - 12 * d, 88 * d),
            minimap = map,
            discCard = HRect(12 * d, h - 228 * d, 248 * d, h - 124 * d),
            throwPanel = HRect(182 * d, h - 98 * d, 328 * d, h - 10 * d),
            meter = HRect(342 * d, h - 128 * d, max(342 * d + 120 * d, w - 190 * d), h - 14 * d),
            stick = HRect(70 * d - sr, h - 56 * d - sr, 70 * d + sr, h - 56 * d + sr),
            action = HRect(acx - ar, acy - ar, acx + ar, acy + ar),
            actionCx = acx, actionCy = acy, actionR = ar,
        )
    }
}
