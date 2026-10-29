package com.discgolf.game.ui

import android.content.Context
import android.graphics.Color
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
