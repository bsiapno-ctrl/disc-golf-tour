package com.discgolf.game

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.discgolf.game.core.Appearance
import com.discgolf.game.core.Archetype
import com.discgolf.game.core.DiscModel
import com.discgolf.game.core.Handedness
import com.discgolf.game.core.PlayerProfile
import com.discgolf.game.core.Stat
import com.discgolf.game.ui.GlossButton
import com.discgolf.game.ui.PortraitView
import com.discgolf.game.ui.SceneView
import kotlin.math.min
import com.discgolf.game.ui.Ui

/**
 * Two screens in one:
 *  - Create mode (no saved golfer): pick a build, spend 10 starting points, style your golfer.
 *  - Card mode: see your level, spend level-up points, check XP bars for each rating, restyle your golfer.
 */
class GolferActivity : Activity() {

    private lateinit var p: PlayerProfile
    private var creating = false
    private lateinit var portrait: PortraitView
    private val refreshers = ArrayList<() -> Unit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = SaveStore.load(this)
        creating = saved == null
        p = saved ?: PlayerProfile("Rookie").also { it.applyArchetype(Archetype.ALLROUND) }
        setContentView(buildUi())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun refresh() {
        portrait.look = p.look.copy()
        refreshers.forEach { it() }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private var k = 1f
    private fun dp(v: Float) = Ui.dpi(this, v * k)
    private fun lbl(text: String, sp: Float, color: Int = Ui.WHITE, display: Boolean = false) =
        Ui.label(this, text, sp * k, color, display)
    private fun gloss(text: String, tone: Int, sizeDp: Float, onClick: () -> Unit) =
        GlossButton(this, text, tone, dp(sizeDp).toFloat(), onClick)
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.panelBg(this@GolferActivity, Color.argb(205, 8, 26, 60), Color.WHITE, 12f * k)
        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
    }

    private fun buildUi(): View {
        val dm = resources.displayMetrics
        val wDp = dm.widthPixels / dm.density; val hDp = dm.heightPixels / dm.density
        val tall = hDp > wDp
        k = (if (tall) min(wDp / 360f, hDp / 640f) else min(wDp / 640f, hDp / 360f)).coerceIn(0.5f, 1.9f)
        refreshers.clear()

        val root = FrameLayout(this)
        root.addView(SceneView(this), FrameLayout.LayoutParams(-1, -1))
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))

        // ---- header (title, points / level, build chips)
        if (creating) buildCreateHeader(page) else buildCardHeader(page)

        // ---- body
        val ratings = panel()
        ratings.addView(sectionTitle("Ratings"))
        for (st in Stat.values()) ratings.addView(statRow(st, tall))
        val look = panel()
        look.addView(sectionTitle("Look"))
        buildLookPickers(look)
        if (!creating) buildBag(look)
        if (!creating) buildCareer(look)

        if (tall) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(identity(dp(150f)), LinearLayout.LayoutParams(-1, -2))
            col.addView(ratings, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            col.addView(look, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f); bottomMargin = dp(8f) })
            val sv = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
            page.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6f) })
        } else {
            val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            body.addView(identity(0), LinearLayout.LayoutParams(dp(150f), -1))
            body.addView(scrolled(ratings), LinearLayout.LayoutParams(0, -1, 1.1f).apply { leftMargin = dp(8f) })
            body.addView(scrolled(look), LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(8f) })
            page.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(6f) })
        }

        // ---- fixed footer: always visible
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        actions.addView(gloss(if (creating) "Start career" else "Done", 0, 22f) { finishAndSave() }, LinearLayout.LayoutParams(dp(230f), dp(44f)))
        if (creating) {
            actions.addView(gloss("Cancel", 1, 16f) { finish() }, LinearLayout.LayoutParams(dp(130f), dp(38f)).apply { leftMargin = dp(12f) })
        } else {
            actions.addView(gloss("Delete golfer", 3, 15f) { confirmDelete() }, LinearLayout.LayoutParams(dp(170f), dp(38f)).apply { leftMargin = dp(12f) })
        }
        page.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6f) })

        refresh()
        return root
    }

    private fun scrolled(inner: LinearLayout): View = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        addView(inner, FrameLayout.LayoutParams(-1, -2))
    }

    /** Showcase card plus name and hand. fixedH = 0 means fill the available height. */
    private fun identity(fixedH: Int): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        portrait = PortraitView(this).apply { look = p.look.copy() }
        if (fixedH > 0) col.addView(portrait, LinearLayout.LayoutParams(dp(120f), fixedH))
        else col.addView(portrait, LinearLayout.LayoutParams(-1, 0, 1f))

        val name = EditText(this).apply {
            setText(p.name)
            typeface = Ui.DISPLAY
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f * k)
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.METAL_DARK)
            hint = "NAME"
            gravity = Gravity.CENTER
            background = Ui.panelBg(this@GolferActivity, Color.argb(215, 8, 26, 60), Ui.GOLD, 10f * k)
            setPadding(dp(6f), dp(3f), dp(6f), dp(3f))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(14))
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { p.name = s?.toString()?.trim().orEmpty() }
            })
        }
        col.addView(name, LinearLayout.LayoutParams(-1, dp(32f)).apply { topMargin = dp(6f) })

        val hand = lbl("", 11f, Ui.WHITE).apply { gravity = Gravity.CENTER; setSingleLine(true); setShadowLayer(3f, 0f, 1f, Color.argb(200, 5, 30, 70)) }
        refreshers += { hand.text = p.handedness.label.uppercase() }
        val handBtn = gloss("Switch hand", 1, 13f) {
            p.handedness = if (p.handedness == Handedness.RIGHT) Handedness.LEFT else Handedness.RIGHT
            refresh()
        }
        val hr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hr.addView(hand, LinearLayout.LayoutParams(0, -2, 1f))
        hr.addView(handBtn, LinearLayout.LayoutParams(-2, dp(26f)))
        col.addView(hr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5f) })
        return col
    }

    private fun sectionTitle(t: String) = lbl(t.uppercase(), 13f, Ui.GOLD, true).apply { setPadding(0, 0, 0, dp(3f)) }

    // ---- headers

    private fun titleShadow(tv: TextView) = tv.apply { setShadowLayer(dp(3f).toFloat(), 0f, dp(1.5f).toFloat(), Color.argb(230, 5, 30, 70)) }

    private fun buildCreateHeader(page: LinearLayout) {
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(titleShadow(lbl("CREATE YOUR GOLFER", 22f, Ui.WHITE, true)), LinearLayout.LayoutParams(0, -2, 1f))
        val pts = lbl("", 14f, Ui.GREEN, true).apply {
            background = Ui.panelBg(this@GolferActivity, Color.argb(215, 8, 26, 60), Ui.GREEN, 10f * k)
            setPadding(dp(10f), dp(2f), dp(10f), dp(2f))
        }
        refreshers += { pts.text = "POINTS TO SPEND: ${pointsLeft()}" }
        top.addView(pts)
        page.addView(top, LinearLayout.LayoutParams(-1, -2))

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val chipViews = ArrayList<View>()
        for (a in Archetype.values()) {
            val chip = gloss(a.label, 1, 13f) { p.applyArchetype(a); refresh() }
            chipViews += chip
            chips.addView(chip, LinearLayout.LayoutParams(-2, dp(26f)).apply { rightMargin = dp(6f) })
        }
        val blurb = lbl("", 11f, Ui.WHITE).apply { setShadowLayer(3f, 0f, 1f, Color.argb(220, 5, 30, 70)); setPadding(dp(4f), 0, 0, 0) }
        refreshers += {
            Archetype.values().forEachIndexed { i, a -> chipViews[i].alpha = if (p.archetype == a) 1f else 0.55f }
            blurb.text = p.archetype.blurb
        }
        val hs = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chipRow.addView(hs, LinearLayout.LayoutParams(-2, -2))
        chipRow.addView(blurb, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(chipRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4f) })
    }

    private fun buildCardHeader(page: LinearLayout) {
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(titleShadow(lbl("GOLFER CARD", 22f, Ui.WHITE, true)), LinearLayout.LayoutParams(0, -2, 1f))
        val lvl = lbl("", 15f, Ui.GOLD, true).apply { setShadowLayer(3f, 0f, 1f, Color.argb(220, 5, 30, 70)) }
        refreshers += { lvl.text = "LEVEL ${p.level}   ·   OVERALL ${p.overall()}" }
        top.addView(lvl)
        page.addView(top, LinearLayout.LayoutParams(-1, -2))
        val xr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        xr.addView(XpBar(this) { p.xp.toFloat() / p.xpForNextLevel() }, LinearLayout.LayoutParams(dp(180f), dp(10f)))
        val xpText = lbl("", 11f, Ui.WHITE).apply { setPadding(dp(8f), 0, 0, 0); setShadowLayer(3f, 0f, 1f, Color.argb(220, 5, 30, 70)) }
        refreshers += {
            xpText.text = "${p.xp} / ${p.xpForNextLevel()} XP  ·  " +
                (if (p.unspentPoints > 0) "${p.unspentPoints} POINTS TO SPEND, tap +" else "level up to earn points")
        }
        xr.addView(xpText)
        page.addView(xr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3f) })
    }

    // ---- creation points

    private fun pointsLeft(): Int {
        val base = p.archetype.base.sum()
        return PlayerProfile.CREATE_POINTS - (p.stats.sum() - base)
    }

    // ---- rating rows

    private fun statRow(s: Stat, showBlurb: Boolean): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(lbl(s.label.uppercase(), 11f, Ui.WHITE, true).apply { setSingleLine(true) }, LinearLayout.LayoutParams(dp(58f), -2))
        val bar = StatBar(this, { p.stat(s) / 99f }, { (p.statXp[s.ordinal] / p.statXpNeeded(p.stat(s))).coerceIn(0f, 1f) }, !creating)
        row.addView(bar, LinearLayout.LayoutParams(0, dp(20f), 1f).apply { leftMargin = dp(2f); rightMargin = dp(2f) })
        val value = lbl("", 16f, Ui.GOLD, true).apply { gravity = Gravity.CENTER }
        row.addView(value, LinearLayout.LayoutParams(dp(26f), -2))

        val minus = gloss("-", 3, 16f) {
            if (creating && p.stats[s.ordinal] > p.archetype.base[s.ordinal]) { p.stats[s.ordinal]--; refresh() }
        }
        val plus = gloss("+", 2, 16f) {
            if (creating) {
                if (pointsLeft() > 0 && p.stats[s.ordinal] < PlayerProfile.CREATE_STAT_CAP) { p.stats[s.ordinal]++; refresh() }
            } else if (p.spendPoint(s)) refresh()
        }
        if (creating) row.addView(minus, LinearLayout.LayoutParams(dp(28f), dp(24f)).apply { leftMargin = dp(1f) })
        row.addView(plus, LinearLayout.LayoutParams(dp(28f), dp(24f)).apply { leftMargin = dp(2f) })

        refreshers += {
            value.text = "${p.stat(s)}"
            bar.invalidate()
            plus.alpha = if (creating) (if (pointsLeft() > 0 && p.stat(s) < PlayerProfile.CREATE_STAT_CAP) 1f else 0.35f)
            else (if (p.unspentPoints > 0 && p.stat(s) < PlayerProfile.MAX_STAT) 1f else 0.35f)
            minus.alpha = if (p.stat(s) > p.archetype.base[s.ordinal]) 1f else 0.35f
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        if (showBlurb) wrap.addView(lbl(s.blurb, 10f, Ui.METAL).apply { setPadding(dp(2f), 0, 0, 0) })
        wrap.setPadding(0, 0, 0, dp(4f))
        return wrap
    }

    // ---- look pickers

    private fun buildLookPickers(col: LinearLayout) {
        val l = p.look
        fun picker(title: String, count: Int, get: () -> Int, set: (Int) -> Unit, text: (Int) -> String, swatch: ((Int) -> Int)? = null) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(lbl(title.uppercase(), 11f, Ui.METAL, true).apply { setSingleLine(true) }, LinearLayout.LayoutParams(dp(64f), -2))
            val prev = gloss("<", 1, 14f) { set((get() + count - 1) % count); refresh() }
            val next = gloss(">", 1, 14f) { set((get() + 1) % count); refresh() }
            val sw = View(this)
            val value = lbl("", 12f, Ui.WHITE, true).apply { gravity = Gravity.CENTER; setSingleLine(true) }
            row.addView(prev, LinearLayout.LayoutParams(dp(26f), dp(22f)))
            if (swatch != null) row.addView(sw, LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { leftMargin = dp(4f) })
            row.addView(value, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(next, LinearLayout.LayoutParams(dp(26f), dp(22f)))
            refreshers += {
                value.text = text(get())
                if (swatch != null) sw.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF000000.toInt() or swatch(get())); cornerRadius = dp(5f).toFloat(); setStroke(dp(1.5f), Ui.WHITE)
                }
            }
            col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4f) })
        }
        picker("Skin", Appearance.SKIN_COUNT, { l.skinTone }, { l.skinTone = it }, { "Tone ${it + 1}" }) { Appearance.SKIN[it] }
        picker("Hair", Appearance.HAIR_STYLES.size, { l.hairStyle }, { l.hairStyle = it }, { Appearance.HAIR_STYLES[it] })
        picker("Hair col.", Appearance.HAIR_COLOR_COUNT, { l.hairColor }, { l.hairColor = it }, { "Colour ${it + 1}" }) { Appearance.HAIR[it] }
        picker("Headwear", Appearance.HEADWEAR.size, { l.headwear }, { l.headwear = it }, { Appearance.HEADWEAR[it] })
        picker("Shirt", Appearance.SHIRT_COUNT, { l.shirtColor }, { l.shirtColor = it }, { "Style ${it + 1}" }) { Appearance.SHIRT[it] }
        picker("Shorts", Appearance.SHORTS_COUNT, { l.shortsColor }, { l.shortsColor = it }, { "Style ${it + 1}" }) { Appearance.SHORTS[it] }
        picker("Build", Appearance.BUILDS.size, { l.build }, { l.build = it }, { Appearance.BUILDS[it] })
    }

    // ---- bag & career (card mode)

    private fun buildBag(col: LinearLayout) {
        col.addView(sectionTitle("Your bag").apply { setPadding(0, dp(8f), 0, dp(3f)) })
        for (d in DiscModel.values()) {
            val have = d.unlockLevel <= p.level
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val dot = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (have) (0xFF000000.toInt() or d.colorRgb) else Color.parseColor("#3A4658"))
                }
            }
            line.addView(dot, LinearLayout.LayoutParams(dp(12f), dp(12f)).apply { rightMargin = dp(6f) })
            val nums = "${d.speed} | ${d.glide} | ${d.turn.toInt()} | ${d.fade.toInt()}"
            val txt = if (have) "${d.displayName}  (${d.kind})   $nums" else "${d.displayName}  — unlocks at level ${d.unlockLevel}"
            line.addView(lbl(txt, 11f, if (have) Ui.WHITE else Ui.METAL))
            col.addView(line, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(3f) })
        }
        col.addView(lbl("Numbers: speed | glide | turn | fade", 10f, Ui.METAL))
    }

    private fun buildCareer(col: LinearLayout) {
        col.addView(sectionTitle("Career").apply { setPadding(0, dp(8f), 0, dp(3f)) })
        val best = if (p.bestRelPar == PlayerProfile.NO_ROUND) "—" else if (p.bestRelPar == 0) "E" else if (p.bestRelPar > 0) "+${p.bestRelPar}" else "${p.bestRelPar}"
        val lines = listOf(
            "Rounds played: ${p.roundsPlayed}",
            "Best round: $best",
            "Birdies or better: ${p.birdies}",
            "Aces: ${p.holesInOne}",
            "Longest throw: ${p.longestThrow.toInt()} m",
            "Total throws: ${p.totalThrows}",
        )
        for (s in lines) col.addView(lbl(s, 12f, Ui.WHITE))
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun finishAndSave() {
        if (p.name.isBlank()) p.name = "Rookie"
        SaveStore.save(this, p)
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete golfer?")
            .setMessage("This erases ${p.name}, their stats and their progress. It cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> SaveStore.clear(this); finish() }
            .setNegativeButton("Keep", null)
            .show()
    }

    @Deprecated("Back saves a golfer card; create mode cancels")
    override fun onBackPressed() {
        if (!creating) finishAndSave() else super.onBackPressed()
    }

    // ------------------------------------------------------------------
    // Custom views
    // ------------------------------------------------------------------

    private class StatBar(
        ctx: android.content.Context,
        private val value: () -> Float,
        private val xp: () -> Float,
        private val showXp: Boolean,
    ) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            val rad = h / 2f
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#06132B")
            r.set(0f, 0f, w, h); c.drawRoundRect(r, rad, rad, p)
            val v = value().coerceIn(0f, 1f)
            val inset = h * 0.14f
            val fw = (w - inset * 2) * v
            if (fw > 1f) {
                val lo = Color.rgb((60 + 190 * v).toInt(), (200 - 20 * v).toInt(), (110 - 100 * v).toInt())
                p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, lo, Ui.darken(lo, 0.7f), android.graphics.Shader.TileMode.CLAMP)
                r.set(inset, inset, inset + fw, h - inset); c.drawRoundRect(r, rad, rad, p)
                p.shader = null
                p.color = Color.argb(90, 255, 255, 255)
                r.set(inset + h * 0.2f, inset, inset + fw - h * 0.2f, h * 0.5f); c.drawRoundRect(r, rad, rad, p)
            }
            // segment ticks every 10 points
            p.color = Color.argb(70, 6, 19, 43)
            for (i in 1 until 10) c.drawRect(w * i / 10f - 1f, inset, w * i / 10f + 1f, h - inset, p)
            if (showXp) {
                p.color = Ui.CYAN
                r.set(inset, h - inset - h * 0.14f, inset + (w - inset * 2) * xp(), h - inset); c.drawRoundRect(r, 2f, 2f, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Color.WHITE
            r.set(1f, 1f, w - 1f, h - 1f); c.drawRoundRect(r, rad, rad, p)
        }
    }

    private class XpBar(ctx: android.content.Context, private val frac: () -> Float) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#06132B")
            r.set(0f, 0f, width.toFloat(), h); c.drawRoundRect(r, h / 2, h / 2, p)
            p.color = Ui.GOLD
            r.set(0f, 0f, width * frac().coerceIn(0f, 1f), h); c.drawRoundRect(r, h / 2, h / 2, p)
        }
    }
}
