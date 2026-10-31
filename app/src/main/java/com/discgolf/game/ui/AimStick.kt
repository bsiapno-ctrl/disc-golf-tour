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

/**
 * Joystick-style aim control. Horizontal drag only. Either a fixed base, or a floating base that
 * appears under the thumb. Knob follows the finger (clamped to the base) and springs back on release.
 */
class AimStick {
    var cx = 0f; var cy = 0f; var radius = 40f
    var pointer = -1
        private set
    var floating = false
        private set
    private var knobX = 0f   // -1..1
    private var fx = 0f; var fy = 0f

    val active get() = pointer >= 0
    /** Aim turn command in -1..1. */
    val output get() = if (active) AimCurve.response(knobX) else 0f

    fun layout(x: Float, y: Float, r: Float) { cx = x; cy = y; radius = r }

    fun hitsFixed(x: Float, y: Float): Boolean = hypot(x - cx, y - cy) <= radius * 1.35f

    fun down(id: Int, x: Float, y: Float, float: Boolean) {
        pointer = id; floating = float
        if (float) { fx = x; fy = y; knobX = 0f } else { fx = cx; fy = cy; knobX = dev(x) }
    }

    private fun dev(x: Float) = ((x - fx) / radius).coerceIn(-1f, 1f)

    fun move(id: Int, x: Float) { if (id == pointer) knobX = dev(x) }

    fun up(id: Int): Boolean { if (id != pointer) return false; pointer = -1; knobX = 0f; return true }

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
        // tick marks at centre
        p.strokeWidth = 2f * d; p.color = Ui.METAL_DARK
        c.drawLine(bx, by - radius * 0.35f, bx, by + radius * 0.35f, p)
        val kx = bx + knobX * radius * 0.75f
        val kr = radius * 0.46f
        p.style = Paint.Style.FILL; p.color = if (active) Ui.GOLD else metal
        c.drawCircle(kx, by, kr, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3.5f * d; p.color = Ui.NAVY
        c.drawCircle(kx, by, kr, p)
        p.strokeWidth = 2f * d; p.color = 0x88FFFFFF.toInt()
        c.drawCircle(kx, by - kr * 0.12f, kr * 0.62f, p)
        p.style = Paint.Style.FILL
    }
}
