package com.discgolf.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.discgolf.game.core.Appearance

/** A bust portrait of the golfer, drawn live so the creator screen reacts as you change things. */
class PortraitView(context: Context) : View(context) {

    var look: Appearance = Appearance()
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()

    private fun c(rgb: Int, shade: Float = 1f): Int = Color.rgb(
        (((rgb shr 16) and 0xFF) * shade).toInt().coerceAtMost(255),
        (((rgb shr 8) and 0xFF) * shade).toInt().coerceAtMost(255),
        ((rgb and 0xFF) * shade).toInt().coerceAtMost(255),
    )

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f
        val u = minOf(w, h * 0.9f) / 100f   // 100 units across

        // backdrop: spotlight on navy
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#0E2850")
        canvas.drawRoundRect(0f, 0f, w, h, 16f, 16f, p)
        p.color = Color.parseColor("#1D4A8C")
        canvas.drawCircle(cx, h * 0.46f, 40f * u, p)
        p.color = Color.parseColor("#2C66B4")
        canvas.drawCircle(cx, h * 0.46f, 27f * u, p)

        val skin = Appearance.SKIN[look.skinTone.coerceIn(0, Appearance.SKIN_COUNT - 1)]
        val hair = Appearance.HAIR[look.hairColor.coerceIn(0, Appearance.HAIR_COLOR_COUNT - 1)]
        val shirt = Appearance.SHIRT[look.shirtColor.coerceIn(0, Appearance.SHIRT_COUNT - 1)]
        val build = when (look.build) { 0 -> 0.86f; 2 -> 1.16f; else -> 1f }

        val headCy = h * 0.40f
        val headRx = 15f * u
        val headRy = 18f * u

        // shoulders / shirt
        p.color = c(shirt)
        rect.set(cx - 34f * u * build, h * 0.66f, cx + 34f * u * build, h + 10f * u)
        canvas.drawRoundRect(rect, 18f * u, 18f * u, p)
        p.color = c(shirt, 0.82f)
        path.reset()
        path.moveTo(cx - 9f * u, h * 0.66f); path.lineTo(cx, h * 0.72f); path.lineTo(cx + 9f * u, h * 0.66f); path.close()
        canvas.drawPath(path, p)

        // neck
        p.color = c(skin, 0.88f)
        rect.set(cx - 6f * u, headCy + headRy * 0.6f, cx + 6f * u, h * 0.70f)
        canvas.drawRect(rect, p)

        // hair behind head
        p.color = c(hair)
        when (look.hairStyle) {
            3 -> { rect.set(cx - headRx - 4f * u, headCy - 6f * u, cx + headRx + 4f * u, headCy + 30f * u); canvas.drawRoundRect(rect, 8f * u, 8f * u, p) }
            4 -> { rect.set(cx + headRx - 3f * u, headCy - 4f * u, cx + headRx + 12f * u, headCy + 26f * u); canvas.drawOval(rect, p) }
            else -> Unit
        }

        // ears + head
        p.color = c(skin, 0.92f)
        canvas.drawCircle(cx - headRx, headCy + 2f * u, 3.4f * u, p)
        canvas.drawCircle(cx + headRx, headCy + 2f * u, 3.4f * u, p)
        p.color = c(skin)
        rect.set(cx - headRx, headCy - headRy, cx + headRx, headCy + headRy)
        canvas.drawOval(rect, p)

        // face
        p.color = Color.parseColor("#1A1A1A")
        canvas.drawCircle(cx - 6f * u, headCy + 1f * u, 1.8f * u, p)
        canvas.drawCircle(cx + 6f * u, headCy + 1f * u, 1.8f * u, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f * u
        p.color = c(hair, 0.8f)
        canvas.drawLine(cx - 9f * u, headCy - 4f * u, cx - 3.5f * u, headCy - 4.6f * u, p)
        canvas.drawLine(cx + 3.5f * u, headCy - 4.6f * u, cx + 9f * u, headCy - 4f * u, p)
        p.color = Color.parseColor("#7A3B2E")
        rect.set(cx - 5f * u, headCy + 6f * u, cx + 5f * u, headCy + 13f * u)
        canvas.drawArc(rect, 20f, 140f, false, p)
        p.style = Paint.Style.FILL

        // hair on top
        p.color = c(hair)
        val capTop = headCy - headRy
        when (look.hairStyle) {
            0 -> { rect.set(cx - headRx * 1.02f, capTop - 1f * u, cx + headRx * 1.02f, headCy - 4f * u); canvas.drawArc(rect, 180f, 180f, true, p) }
            1, 3, 4 -> {
                rect.set(cx - headRx * 1.08f, capTop - 4f * u, cx + headRx * 1.08f, headCy + 2f * u)
                canvas.drawArc(rect, 180f, 180f, true, p)
                canvas.drawRect(cx - headRx * 1.06f, headCy - 4f * u, cx - headRx * 0.8f, headCy + 6f * u, p)
                canvas.drawRect(cx + headRx * 0.8f, headCy - 4f * u, cx + headRx * 1.06f, headCy + 6f * u, p)
            }
            2 -> {
                rect.set(cx - headRx * 1.02f, capTop - 1f * u, cx + headRx * 1.02f, headCy - 4f * u)
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
            else -> Unit
        }

        // headwear
        val cap = c(shirt, 0.92f)
        p.color = cap
        when (look.headwear) {
            1, 2 -> {
                rect.set(cx - headRx * 1.1f, capTop - 6f * u, cx + headRx * 1.1f, headCy - 2f * u)
                canvas.drawArc(rect, 180f, 180f, true, p)
                p.color = c(shirt, 0.72f)
                if (look.headwear == 1) {
                    rect.set(cx - headRx * 1.15f, headCy - 7f * u, cx + headRx * 1.15f, headCy - 2f * u)
                    canvas.drawRoundRect(rect, 3f * u, 3f * u, p)
                } else {
                    rect.set(cx + headRx * 0.2f, headCy - 9f * u, cx + headRx * 1.5f, headCy - 4f * u)
                    canvas.drawRoundRect(rect, 3f * u, 3f * u, p)
                }
            }
            3 -> {
                rect.set(cx - headRx * 1.08f, headCy - 9f * u, cx + headRx * 1.08f, headCy - 4f * u)
                canvas.drawRect(rect, p)
                p.color = c(shirt, 0.72f)
                rect.set(cx - headRx * 1.1f, headCy - 4f * u, cx + headRx * 1.1f, headCy - 0.5f * u)
                canvas.drawRoundRect(rect, 2f * u, 2f * u, p)
            }
            else -> Unit
        }
    }
}
