package com.discgolf.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import com.discgolf.game.core.Appearance
import kotlin.math.sin

/**
 * A showcase card for the golfer: colourful rotating ring, sunburst backdrop and a bust that bobs
 * and blinks. Drawn live so the creator reacts to every skin / hair / colour / headwear choice.
 */
class PortraitView(context: Context) : View(context) {

    var look: Appearance = Appearance()
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val clip = Path()
    private val t0 = System.nanoTime()

    private fun c(rgb: Int, shade: Float = 1f): Int = Color.rgb(
        (((rgb shr 16) and 0xFF) * shade).toInt().coerceIn(0, 255),
        (((rgb shr 8) and 0xFF) * shade).toInt().coerceIn(0, 255),
        ((rgb and 0xFF) * shade).toInt().coerceIn(0, 255),
    )

    private fun outline(canvas: Canvas, u: Float, color: Int, drawer: () -> Unit) {
        // draws a shape's dark edge by stroking after the caller fills
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * u; p.color = color; p.strokeJoin = Paint.Join.ROUND
        drawer()
        p.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val t = (System.nanoTime() - t0) / 1e9f
        val cx = w / 2f
        val u = minOf(w, h * 0.9f) / 100f   // 100 units across
        val corner = 14f * u
        val ringW = 3.2f * u

        p.style = Paint.Style.FILL; p.shader = null

        // ring (rotating sweep gradient)
        rect.set(0f, 0f, w, h)
        canvas.save(); canvas.rotate(t * 25f, cx, h / 2f)
        p.shader = SweepGradient(
            cx, h / 2f,
            intArrayOf(Color.parseColor("#FFD84A"), Color.parseColor("#FF6F3C"), Color.parseColor("#FF4FA3"),
                Color.parseColor("#6A5CFF"), Color.parseColor("#2EC5FF"), Color.parseColor("#6BE36A"), Color.parseColor("#FFD84A")),
            null,
        )
        // draw oversize so rotation never exposes corners; clip to rounded rect afterwards
        clip.reset(); clip.addRoundRect(rect, corner, corner, Path.Direction.CW)
        canvas.restore()
        canvas.save(); canvas.clipPath(clip)
        canvas.save(); canvas.rotate(t * 25f, cx, h / 2f)
        val big = maxOf(w, h)
        canvas.drawRect(cx - big, h / 2f - big, cx + big, h / 2f + big, p)
        canvas.restore()
        p.shader = null
        canvas.restore()

        // inner card
        rect.set(ringW, ringW, w - ringW, h - ringW)
        clip.reset(); clip.addRoundRect(rect, corner - ringW * 0.6f, corner - ringW * 0.6f, Path.Direction.CW)
        canvas.save(); canvas.clipPath(clip)
        p.shader = RadialGradient(cx, h * 0.45f, h * 0.7f, Color.parseColor("#9AE0FF"), Color.parseColor("#2C7BD0"), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, p); p.shader = null

        // sunburst rays
        p.color = Color.argb(40, 255, 255, 255)
        val rot = t * 6f
        for (i in 0 until 12) {
            val a = Math.toRadians((rot + i * 30.0))
            val a2 = Math.toRadians((rot + i * 30.0 + 12.0))
            path.reset(); path.moveTo(cx, h * 0.45f)
            path.lineTo(cx + (big * 1.5f * Math.cos(a)).toFloat(), h * 0.45f + (big * 1.5f * Math.sin(a)).toFloat())
            path.lineTo(cx + (big * 1.5f * Math.cos(a2)).toFloat(), h * 0.45f + (big * 1.5f * Math.sin(a2)).toFloat())
            path.close(); canvas.drawPath(path, p)
        }
        // soft ground glow
        p.color = Color.argb(70, 255, 255, 255)
        canvas.drawCircle(cx, h * 0.46f, 30f * u, p)

        val skin = Appearance.SKIN[look.skinTone.coerceIn(0, Appearance.SKIN_COUNT - 1)]
        val hair = Appearance.HAIR[look.hairColor.coerceIn(0, Appearance.HAIR_COLOR_COUNT - 1)]
        val shirt = Appearance.SHIRT[look.shirtColor.coerceIn(0, Appearance.SHIRT_COUNT - 1)]
        val build = when (look.build) { 0 -> 0.86f; 2 -> 1.16f; else -> 1f }
        val edge = c(skin, 0.5f)

        // idle motion
        val bob = sin(t * 2.4f) * 0.9f * u
        val breathe = sin(t * 2.4f + 0.6f) * 0.5f * u
        val blink = (t % 3.4f) > 3.25f
        canvas.save()
        canvas.translate(0f, bob)

        val headCy = h * 0.40f
        val headRx = 15f * u
        val headRy = 18f * u

        // shoulders / shirt
        p.color = c(shirt)
        rect.set(cx - 34f * u * build, h * 0.66f + breathe, cx + 34f * u * build, h + 14f * u)
        canvas.drawRoundRect(rect, 18f * u, 18f * u, p)
        outline(canvas, u, c(shirt, 0.55f)) { canvas.drawRoundRect(rect, 18f * u, 18f * u, p) }
        p.color = Color.argb(60, 255, 255, 255)
        rect.set(cx - 28f * u * build, h * 0.675f + breathe, cx - 8f * u, h * 0.72f + breathe)
        canvas.drawRoundRect(rect, 3f * u, 3f * u, p)
        // neck
        p.color = c(skin, 0.88f)
        rect.set(cx - 6f * u, headCy + headRy * 0.6f, cx + 6f * u, h * 0.70f + breathe)
        canvas.drawRect(rect, p)
        // collar
        p.color = c(shirt, 0.78f)
        path.reset()
        path.moveTo(cx - 10f * u, h * 0.655f + breathe); path.lineTo(cx, h * 0.735f + breathe); path.lineTo(cx + 10f * u, h * 0.655f + breathe)
        path.lineTo(cx + 5f * u, h * 0.652f + breathe); path.lineTo(cx, h * 0.69f + breathe); path.lineTo(cx - 5f * u, h * 0.652f + breathe); path.close()
        canvas.drawPath(path, p)

        // gentle head tilt
        canvas.save(); canvas.rotate(sin(t * 1.2f) * 1.6f, cx, h * 0.62f)

        // hair behind head
        p.color = c(hair)
        when (look.hairStyle) {
            3 -> { rect.set(cx - headRx - 4f * u, headCy - 6f * u, cx + headRx + 4f * u, headCy + 30f * u); canvas.drawRoundRect(rect, 8f * u, 8f * u, p) }
            4 -> {
                rect.set(cx + headRx - 3f * u, headCy - 4f * u, cx + headRx + 12f * u, headCy + 26f * u); canvas.drawOval(rect, p)
                canvas.drawCircle(cx + headRx + 2f * u, headCy - 8f * u, 4f * u, p)
            }
            else -> Unit
        }

        // ears + head
        p.color = c(skin, 0.92f)
        canvas.drawCircle(cx - headRx, headCy + 2f * u, 3.6f * u, p)
        canvas.drawCircle(cx + headRx, headCy + 2f * u, 3.6f * u, p)
        p.color = c(skin)
        rect.set(cx - headRx, headCy - headRy, cx + headRx, headCy + headRy)
        canvas.drawOval(rect, p)
        outline(canvas, u, edge) { canvas.drawOval(rect, p) }
        // cheek light
        p.color = Color.argb(45, 255, 255, 255)
        rect.set(cx - headRx * 0.8f, headCy - headRy * 0.7f, cx - headRx * 0.1f, headCy - headRy * 0.1f)
        canvas.drawOval(rect, p)

        // face
        val eyeY = headCy + 1f * u
        if (blink) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.3f * u; p.color = Color.parseColor("#1A1A1A")
            canvas.drawLine(cx - 8f * u, eyeY, cx - 4f * u, eyeY, p)
            canvas.drawLine(cx + 4f * u, eyeY, cx + 8f * u, eyeY, p)
            p.style = Paint.Style.FILL
        } else {
            for (sx in intArrayOf(-1, 1)) {
                val ex = cx + sx * 6f * u
                p.color = Color.WHITE; rect.set(ex - 3f * u, eyeY - 3.2f * u, ex + 3f * u, eyeY + 3.2f * u); canvas.drawOval(rect, p)
                p.color = Color.parseColor("#1A1A1A"); canvas.drawCircle(ex, eyeY + 0.2f * u, 2.1f * u, p)
                p.color = Color.WHITE; canvas.drawCircle(ex + 0.8f * u, eyeY - 0.6f * u, 0.7f * u, p)
            }
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * u; p.strokeCap = Paint.Cap.ROUND
        p.color = c(hair, 0.8f).let { if (look.hairColor == 5) c(hair, 0.6f) else it }
        canvas.drawLine(cx - 9f * u, eyeY - 5.2f * u, cx - 3.5f * u, eyeY - 5.8f * u, p)
        canvas.drawLine(cx + 3.5f * u, eyeY - 5.8f * u, cx + 9f * u, eyeY - 5.2f * u, p)
        // nose
        p.color = c(skin, 0.72f); p.strokeWidth = 1.2f * u
        canvas.drawLine(cx, eyeY + 1.5f * u, cx - 1.2f * u, eyeY + 5.2f * u, p)
        canvas.drawLine(cx - 1.2f * u, eyeY + 5.2f * u, cx + 0.8f * u, eyeY + 5.4f * u, p)
        // smile
        p.color = Color.parseColor("#8A3B2E"); p.strokeWidth = 1.5f * u
        rect.set(cx - 5.5f * u, headCy + 5f * u, cx + 5.5f * u, headCy + 13f * u)
        canvas.drawArc(rect, 20f, 140f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        p.style = Paint.Style.FILL
        // blush
        p.color = Color.argb(60, 255, 90, 90)
        canvas.drawCircle(cx - 10f * u, eyeY + 6f * u, 2.6f * u, p)
        canvas.drawCircle(cx + 10f * u, eyeY + 6f * u, 2.6f * u, p)

        // hair on top
        p.color = c(hair)
        val capTop = headCy - headRy
        when (look.hairStyle) {
            0 -> { rect.set(cx - headRx * 1.02f, capTop - 1f * u, cx + headRx * 1.02f, headCy - 6f * u); canvas.drawArc(rect, 180f, 180f, true, p) }
            1, 3, 4 -> {
                rect.set(cx - headRx * 1.08f, capTop - 4f * u, cx + headRx * 1.08f, headCy + 2f * u)
                canvas.drawArc(rect, 180f, 180f, true, p)
                canvas.drawRect(cx - headRx * 1.06f, headCy - 4f * u, cx - headRx * 0.8f, headCy + 6f * u, p)
                canvas.drawRect(cx + headRx * 0.8f, headCy - 4f * u, cx + headRx * 1.06f, headCy + 6f * u, p)
            }
            2 -> {
                rect.set(cx - headRx * 1.02f, capTop - 1f * u, cx + headRx * 1.02f, headCy - 6f * u)
                canvas.drawArc(rect, 180f, 180f, true, p)
                for (k in -2..2) {
                    path.reset()
                    path.moveTo(cx + k * 6f * u - 3.5f * u, capTop + 3f * u)
                    path.lineTo(cx + k * 6.5f * u, capTop - 11f * u)
                    path.lineTo(cx + k * 6f * u + 3.5f * u, capTop + 3f * u)
                    path.close()
                    canvas.drawPath(path, p)
                }
            }
            else -> {
                // bald shine
                p.color = Color.argb(70, 255, 255, 255)
                rect.set(cx - 8f * u, capTop + 1.5f * u, cx - 1f * u, capTop + 6f * u); canvas.drawOval(rect, p)
            }
        }
        if (look.hairStyle in 0..4) {
            p.color = Color.argb(55, 255, 255, 255)
            rect.set(cx - 9f * u, capTop + 0.5f * u, cx - 1f * u, capTop + 4f * u); canvas.drawOval(rect, p)
        }

        // headwear (uses the shirt colour so the outfit matches)
        val cap = c(shirt, 0.95f)
        when (look.headwear) {
            1, 2 -> {
                p.color = cap
                rect.set(cx - headRx * 1.1f, capTop - 6f * u, cx + headRx * 1.1f, headCy - 2f * u)
                canvas.drawArc(rect, 180f, 180f, true, p)
                outline(canvas, u, c(shirt, 0.55f)) { canvas.drawArc(rect, 180f, 180f, true, p) }
                p.color = Color.argb(70, 255, 255, 255)
                rect.set(cx - headRx * 0.8f, capTop - 4.5f * u, cx - headRx * 0.1f, capTop + 0.5f * u); canvas.drawOval(rect, p)
                p.color = c(shirt, 0.72f)
                if (look.headwear == 1) {
                    rect.set(cx - headRx * 1.2f, headCy - 7f * u, cx + headRx * 1.2f, headCy - 2f * u)
                    canvas.drawRoundRect(rect, 3f * u, 3f * u, p)
                } else {
                    // worn backwards: strap tab and short side brims
                    rect.set(cx - 3f * u, capTop - 8f * u, cx + 3f * u, capTop - 5f * u)
                    canvas.drawRoundRect(rect, 1.5f * u, 1.5f * u, p)
                    rect.set(cx + headRx * 0.2f, headCy - 9f * u, cx + headRx * 1.5f, headCy - 4f * u)
                    canvas.drawRoundRect(rect, 3f * u, 3f * u, p)
                }
            }
            3 -> {
                p.color = cap
                rect.set(cx - headRx * 1.08f, headCy - 9f * u, cx + headRx * 1.08f, headCy - 4f * u)
                canvas.drawRect(rect, p)
                p.color = Color.argb(70, 255, 255, 255)
                canvas.drawRect(cx - headRx * 1.08f, headCy - 9f * u, cx + headRx * 1.08f, headCy - 7.5f * u, p)
                p.color = c(shirt, 0.72f)
                rect.set(cx - headRx * 1.2f, headCy - 4f * u, cx + headRx * 1.2f, headCy - 0.5f * u)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, p)
            }
            else -> Unit
        }
        canvas.restore() // tilt
        canvas.restore() // bob

        // card gloss
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, Color.argb(60, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h * 0.45f, p)
        p.shader = null
        canvas.restore() // inner clip

        // thin inner edge
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * u; p.color = Color.argb(160, 5, 30, 70)
        rect.set(ringW, ringW, w - ringW, h - ringW)
        canvas.drawRoundRect(rect, corner - ringW * 0.6f, corner - ringW * 0.6f, p)
        p.style = Paint.Style.FILL

        postInvalidateOnAnimation()
    }
}
