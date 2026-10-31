package com.discgolf.game.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Look and feel: a glossy early-2000s console sports game. Deep navy panels, brushed-metal greys,
 * a gold highlight and chunky italic type.
 */
object Ui {
    val NAVY = Color.parseColor("#0B1E3F")
    val NAVY_MID = Color.parseColor("#14396F")
    val NAVY_LIGHT = Color.parseColor("#2B5CA8")
    val GOLD = Color.parseColor("#F2B705")
    val GOLD_DARK = Color.parseColor("#B07F00")
    val ORANGE = Color.parseColor("#E8602C")
    val METAL = Color.parseColor("#C9D3E0")
    val METAL_DARK = Color.parseColor("#7C889B")
    val WHITE = Color.WHITE
    val GREEN = Color.parseColor("#3FD07A")
    val RED = Color.parseColor("#E8453C")
    val CYAN = Color.parseColor("#5CE1FF")

    val DISPLAY: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
    val BODY: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    fun dp(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    fun dpi(ctx: Context, v: Float): Int = dp(ctx, v).toInt()

    /** Full-screen navy backdrop with a lighter glow toward the top. */
    fun backdrop(): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(NAVY_MID, NAVY, Color.parseColor("#050E20")),
    )

    fun panelBg(ctx: Context, fill: Int = Color.argb(215, 8, 22, 48), stroke: Int = GOLD, radiusDp: Float = 14f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(ctx, radiusDp)
            setStroke(dpi(ctx, 2f), stroke)
        }
    }

    fun buttonBg(ctx: Context, top: Int, bottom: Int, radiusDp: Float = 12f): StateListDrawable {
        fun g(t: Int, b: Int) = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t, b)).apply {
            cornerRadius = dp(ctx, radiusDp)
            setStroke(dpi(ctx, 2f), Color.argb(200, 255, 255, 255))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), g(darken(top), darken(bottom)))
            addState(intArrayOf(), g(top, bottom))
        }
    }

    fun darken(c: Int, f: Float = 0.78f) = Color.rgb(
        (Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt(),
    )

    /** A chunky glossy button. */
    fun button(ctx: Context, text: String, primary: Boolean = true, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text.uppercase()
            typeface = DISPLAY
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(if (primary) NAVY else WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(2f, 0f, 1.5f, if (primary) Color.argb(120, 255, 255, 255) else Color.argb(160, 0, 0, 0))
            background = if (primary) buttonBg(ctx, Color.parseColor("#FFD84A"), GOLD_DARK)
            else buttonBg(ctx, NAVY_LIGHT, NAVY_MID)
            setPadding(dpi(ctx, 22f), dpi(ctx, 12f), dpi(ctx, 22f), dpi(ctx, 12f))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    fun label(ctx: Context, text: String, sp: Float = 16f, color: Int = WHITE, display: Boolean = false): TextView {
        return TextView(ctx).apply {
            this.text = text
            typeface = if (display) DISPLAY else BODY
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
        }
    }

    fun spacer(ctx: Context, heightDp: Float): View = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dpi(ctx, heightDp))
    }
}

// ---------------------------------------------------------------------------------------------
// Bright "sunny fairway" menu kit (added for the start menu and golfer creator).
// ---------------------------------------------------------------------------------------------

/** Animated sky, drifting clouds, rolling hills and a striped fairway, all drawn in Canvas. */
class SceneView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()
    private var sky: android.graphics.Shader? = null
    private val t0 = System.nanoTime()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sky = android.graphics.LinearGradient(
            0f, 0f, 0f, h * 0.75f,
            intArrayOf(Color.parseColor("#2E9BEA"), Color.parseColor("#7CCBFF"), Color.parseColor("#DDF4FF")),
            floatArrayOf(0f, 0.6f, 1f), android.graphics.Shader.TileMode.CLAMP,
        )
    }

    private fun cloud(c: android.graphics.Canvas, x: Float, y: Float, s: Float) {
        p.color = Color.argb(235, 255, 255, 255)
        c.drawCircle(x, y, 18f * s, p)
        c.drawCircle(x + 20f * s, y - 9f * s, 23f * s, p)
        c.drawCircle(x + 45f * s, y - 2f * s, 18f * s, p)
        c.drawRoundRect(x - 18f * s, y, x + 63f * s, y + 18f * s, 9f * s, 9f * s, p)
    }

    private fun hill(c: android.graphics.Canvas, w: Float, h: Float, base: Float, amp: Float, freq: Float, phase: Float, color: Int) {
        path.reset(); path.moveTo(0f, h)
        var x = 0f
        while (x <= w + 12f) {
            path.lineTo(x, base + amp * kotlin.math.sin(x / w * freq * 6.2832f + phase))
            x += 12f
        }
        path.lineTo(w + 12f, h); path.close()
        p.color = color; c.drawPath(path, p)
    }

    override fun onDraw(c: android.graphics.Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val t = (System.nanoTime() - t0) / 1e9f
        p.style = Paint.Style.FILL
        p.shader = sky; c.drawRect(0f, 0f, w, h, p); p.shader = null
        val u = h / 360f
        // sun
        val sx = w * 0.82f; val sy = h * 0.2f
        p.color = Color.argb(60, 255, 244, 170); c.drawCircle(sx, sy, 62f * u, p)
        p.color = Color.argb(110, 255, 244, 170); c.drawCircle(sx, sy, 44f * u, p)
        p.color = Color.parseColor("#FFF3A0"); c.drawCircle(sx, sy, 30f * u, p)
        // clouds
        val span = w + 200f * u
        for (i in 0 until 4) {
            val speed = (6f + i * 3f) * u
            val x = ((t * speed + i * span / 4f) % span) - 100f * u
            cloud(c, x, h * (0.12f + 0.11f * ((i * 5) % 4)), (0.8f + 0.15f * (i % 3)) * u)
        }
        hill(c, w, h, h * 0.58f, 16f * u, 1.3f, 0.5f, Color.parseColor("#8FD9A8"))
        hill(c, w, h, h * 0.68f, 20f * u, 1.0f, 2.4f, Color.parseColor("#5CC66E"))
        hill(c, w, h, h * 0.8f, 14f * u, 1.6f, 4.0f, Color.parseColor("#3FB04F"))
        // mown stripes on the fairway
        val top = h * 0.86f
        p.color = Color.parseColor("#37A346"); c.drawRect(0f, top, w, h, p)
        p.color = Color.parseColor("#44B854")
        var x = -40f * u
        while (x < w) { c.drawRect(x, top, x + 30f * u, h, p); x += 60f * u }
        // far flag
        val fx = w * 0.62f; val fy = h * 0.655f
        p.color = Color.WHITE; c.drawRect(fx, fy - 26f * u, fx + 1.6f * u, fy, p)
        p.color = Color.parseColor("#E8453C")
        path.reset(); path.moveTo(fx + 1.6f * u, fy - 26f * u); path.lineTo(fx + 14f * u, fy - 21f * u); path.lineTo(fx + 1.6f * u, fy - 16f * u); path.close()
        c.drawPath(path, p)
        postInvalidateOnAnimation()
    }
}

/** Big chunky logo lettering: gradient fill, thick dark outline and a drop shadow. */
class LogoView(ctx: Context, private val lines: List<Pair<String, Int>>, private val sizePx: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.DISPLAY; textSize = sizePx; letterSpacing = 0.02f
    }
    private val indent = sizePx * 0.55f
    private val lineH get() = sizePx * 1.0f

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        var mw = 0f
        lines.forEachIndexed { i, l -> mw = maxOf(mw, p.measureText(l.first) + if (i > 0) indent else 0f) }
        val pad = sizePx * 0.3f
        setMeasuredDimension(
            resolveSize((mw + pad * 2).toInt(), wSpec),
            resolveSize((lineH * lines.size + pad * 2).toInt(), hSpec),
        )
    }

    override fun onDraw(c: android.graphics.Canvas) {
        val pad = sizePx * 0.3f
        c.save(); c.rotate(-3f, width / 2f, height / 2f)
        lines.forEachIndexed { i, l ->
            val x = pad + if (i > 0) indent else 0f
            val y = pad + lineH * (i + 0.82f)
            p.shader = null
            p.style = Paint.Style.FILL_AND_STROKE; p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = sizePx * 0.26f; p.color = Color.argb(110, 5, 30, 70)
            c.drawText(l.first, x + sizePx * 0.06f, y + sizePx * 0.1f, p)
            p.strokeWidth = sizePx * 0.2f; p.color = Color.parseColor("#12306B")
            c.drawText(l.first, x, y, p)
            p.style = Paint.Style.FILL; p.strokeWidth = 0f
            p.shader = android.graphics.LinearGradient(0f, y - sizePx * 0.75f, 0f, y, Color.WHITE, l.second, android.graphics.Shader.TileMode.CLAMP)
            c.drawText(l.first, x, y, p)
            p.shader = null
        }
        c.restore()
    }
}

/** Glossy rounded button with a real pressed state. tone: 0 gold, 1 blue, 2 green, 3 red. */
class GlossButton(ctx: Context, label: String, private val tone: Int = 0, sizePx: Float = 40f, onClick: () -> Unit = {}) : View(ctx) {
    var label: String = label
        set(v) { field = v; requestLayout(); invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Ui.DISPLAY; textSize = sizePx; textAlign = Paint.Align.CENTER }
    private val r = android.graphics.RectF()
    private val padH = sizePx * 0.7f
    private val padV = sizePx * 0.4f

    init { isClickable = true; setOnClickListener { onClick() } }

    private fun cols(): Triple<Int, Int, Int> = when (tone) {
        1 -> Triple(Color.parseColor("#6FD3FF"), Color.parseColor("#1E7FD6"), Color.parseColor("#0B3F7A"))
        2 -> Triple(Color.parseColor("#9BEA6A"), Color.parseColor("#2FA83A"), Color.parseColor("#14591E"))
        3 -> Triple(Color.parseColor("#FF8A70"), Color.parseColor("#D8352B"), Color.parseColor("#7A140F"))
        else -> Triple(Color.parseColor("#FFE066"), Color.parseColor("#F29A05"), Color.parseColor("#7A4A00"))
    }

    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        p.style = Paint.Style.FILL
        setMeasuredDimension(
            resolveSize((p.measureText(label.uppercase()) + padH * 2).toInt(), wSpec),
            resolveSize((p.textSize + padV * 2).toInt(), hSpec),
        )
    }

    override fun onDraw(c: android.graphics.Canvas) {
        val (top, bot, edge) = cols()
        val w = width.toFloat(); val h = height.toFloat()
        val lift = h * 0.08f
        val down = if (isPressed) lift else 0f
        val rad = h * 0.5f
        p.shader = null; p.style = Paint.Style.FILL
        // base shadow / thickness
        p.color = edge
        r.set(0f, lift, w, h); c.drawRoundRect(r, rad, rad, p)
        c.save(); c.translate(0f, down)
        val bh = h - lift
        r.set(0f, 0f, w, bh)
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, bh, if (isPressed) Ui.darken(top, 0.88f) else top, if (isPressed) Ui.darken(bot, 0.88f) else bot, android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p); p.shader = null
        // gloss
        p.color = Color.argb(if (isPressed) 50 else 95, 255, 255, 255)
        r.set(bh * 0.18f, bh * 0.08f, w - bh * 0.18f, bh * 0.5f); c.drawRoundRect(r, bh * 0.25f, bh * 0.25f, p)
        // rim
        p.style = Paint.Style.STROKE; p.strokeWidth = maxOf(2f, h * 0.05f); p.color = edge
        r.set(0f, 0f, w, bh); c.drawRoundRect(r, rad, rad, p)
        // text with outline
        val ty = bh / 2f - (p.descent() + p.ascent()) / 2f
        p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = p.textSize * 0.16f; p.color = edge
        p.style = Paint.Style.FILL_AND_STROKE
        c.drawText(label.uppercase(), w / 2f, ty, p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        c.drawText(label.uppercase(), w / 2f, ty, p)
        c.restore()
    }
}
