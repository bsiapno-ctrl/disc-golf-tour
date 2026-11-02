package com.discgolf.game.ui

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/** Pure response curve for the aim stick (unit-tested). Deflection and output are both in -1..1. */
object AimCurve {
    const val DEAD = 0.06f
    /** Output at full deflection is 1; just past the dead zone it is a small fraction, giving fine control. */
    fun response(deflection: Float): Float {
        val a = abs(deflection.coerceIn(-1f, 1f))
        if (a <= DEAD) return 0f
        val t = (a - DEAD) / (1f - DEAD)
        // cubic-ish blend: gentle near centre, steep toward the rim
        val o = 0.12f * t + 0.88f * t * t * t
        return sign(deflection) * o
    }
}

/** Clamp a 2D deflection radially into the unit circle (unit-tested). Returns (x, y). */
fun clampRadial(x: Float, y: Float): Pair<Float, Float> {
    val m = hypot(x, y)
    return if (m > 1f) Pair(x / m, y / m) else Pair(x, y)
}

/**
 * Joystick-style aim control. Full 2D: x turns the aim, y (up = +) moves the aim point. Either a fixed base, or a floating base that
 * appears under the thumb. Knob follows the finger (clamped to the base) and springs back on release.
 */
class AimStick {
    var cx = 0f; var cy = 0f; var radius = 40f
    var pointer = -1
        private set
    var floating = false
        private set
    private var knobX = 0f   // -1..1, right = +
    private var knobY = 0f   // -1..1, screen-down = +
    private var fx = 0f; var fy = 0f

    /** Raw knob deflection after the response curve, x right = +, y up = + (0 when released). */
    val rawX get() = knobX
    val rawY get() = -knobY
    val active get() = pointer >= 0
    /** Aim turn command in -1..1 (right = +). */
    val output get() = if (active) AimCurve.response(knobX) else 0f
    /** Aim-point command in -1..1 (up = + = further down the hole). */
    val outputY get() = if (active) AimCurve.response(-knobY) else 0f

    fun layout(x: Float, y: Float, r: Float) { cx = x; cy = y; radius = r }

    fun hitsFixed(x: Float, y: Float): Boolean = hypot(x - cx, y - cy) <= radius * 1.35f

    fun down(id: Int, x: Float, y: Float, float: Boolean) {
        pointer = id; floating = float
        if (float) { fx = x; fy = y; knobX = 0f; knobY = 0f } else { fx = cx; fy = cy; dev(x, y) }
    }

    private fun dev(x: Float, y: Float) {
        val (dx, dy) = clampRadial((x - fx) / radius, (y - fy) / radius)
        knobX = dx; knobY = dy
    }

    fun move(id: Int, x: Float, y: Float) { if (id == pointer) dev(x, y) }

    fun up(id: Int): Boolean { if (id != pointer) return false; pointer = -1; knobX = 0f; knobY = 0f; return true }

    /** Axis-end labels drawn outside the circle: left, right, up, down (empty = none). */
    var labels = arrayOf("", "", "", "")
    var accent = 0

    private fun axisCross(c: Canvas, p: Paint, d: Float, bx: Float, by: Float) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = 0x88FFFFFF.toInt()
        val r = radius * 0.86f
        c.drawLine(bx - r, by, bx + r, by, p)
        c.drawLine(bx, by - r, bx, by + r, p)
        // arrowheads at the four axis ends
        val a = 6f * d
        p.style = Paint.Style.FILL; p.color = 0xCCFFFFFF.toInt()
        val path = android.graphics.Path()
        for (k in 0 until 4) {
            val dx = intArrayOf(-1, 1, 0, 0)[k].toFloat(); val dy = intArrayOf(0, 0, -1, 1)[k].toFloat()
            val tx = bx + dx * (r + a); val ty = by + dy * (r + a)
            path.reset()
            path.moveTo(tx, ty)
            path.lineTo(bx + dx * r - dy * a, by + dy * r + dx * a)
            path.lineTo(bx + dx * r + dy * a, by + dy * r - dx * a)
            path.close()
            c.drawPath(path, p)
        }
        // end labels: 12dp bold text on a dark pill so they read on grass; drawn inside the ring for up/down, outside for left/right
        p.textSize = 12f * d; p.typeface = Ui.BODY; p.isFakeBoldText = true; p.textAlign = Paint.Align.CENTER
        val off = radius + 6f * d
        pillLabel(c, p, d, labels[0], bx - off, by, 1)
        pillLabel(c, p, d, labels[1], bx + off, by, -1)
        pillLabel(c, p, d, labels[2], bx, by - radius * 0.56f, 0)
        pillLabel(c, p, d, labels[3], bx, by + radius * 0.56f, 0)
        p.isFakeBoldText = false
        p.style = Paint.Style.FILL
    }

    /** side: 1 = label extends left of x, -1 = extends right of x, 0 = centred. */
    private fun pillLabel(c: Canvas, p: Paint, d: Float, s: String, x: Float, y: Float, side: Int) {
        if (s.isEmpty()) return
        val w = p.measureText(s) + 10f * d; val h = 17f * d
        val cx = x - side * w / 2f
        val rf = android.graphics.RectF(cx - w / 2f, y - h / 2f, cx + w / 2f, y + h / 2f)
        p.style = Paint.Style.FILL; p.color = 0xCC061028.toInt()
        c.drawRoundRect(rf, h / 2f, h / 2f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = if (accent != 0) accent else 0xFFFFFFFF.toInt()
        c.drawRoundRect(rf, h / 2f, h / 2f, p)
        p.style = Paint.Style.FILL
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(s, cx, y + 4.2f * d, p)
    }

    fun draw(c: Canvas, p: Paint, d: Float, enabled: Boolean) {
        if (floating && !active) return
        val bx = if (floating) fx else cx
        val by = if (floating) fy else cy
        val metal = if (enabled) Ui.METAL else Ui.METAL_DARK
        p.style = Paint.Style.FILL; p.color = 0x99081630.toInt()
        c.drawCircle(bx, by, radius, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f * d; p.color = Ui.NAVY
        c.drawCircle(bx, by, radius, p)
        p.strokeWidth = 2.5f * d; p.color = metal
        c.drawCircle(bx, by, radius - 2f * d, p)
        // 8 subtle direction ticks on the ring
        p.strokeWidth = 2f * d; p.color = 0x55FFFFFF.toInt()
        for (i in 0 until 8) {
            val a = i * (Math.PI / 4).toFloat()
            val ca = kotlin.math.cos(a); val sa = kotlin.math.sin(a)
            c.drawLine(bx + ca * radius * 0.82f, by + sa * radius * 0.82f, bx + ca * radius * 0.94f, by + sa * radius * 0.94f, p)
        }
        axisCross(c, p, d, bx, by)
        val kx = bx + knobX * radius * 0.75f
        val ky = by + knobY * radius * 0.75f
        val kr = radius * 0.46f
        p.style = Paint.Style.FILL; p.color = if (active) Ui.GOLD else metal
        c.drawCircle(kx, ky, kr, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f * d; p.color = Ui.NAVY
        c.drawCircle(kx, ky, kr, p)
        p.strokeWidth = 2f * d; p.color = 0x88FFFFFF.toInt()
        c.drawCircle(kx, ky - kr * 0.12f, kr * 0.62f, p)
        p.style = Paint.Style.FILL
    }
}
