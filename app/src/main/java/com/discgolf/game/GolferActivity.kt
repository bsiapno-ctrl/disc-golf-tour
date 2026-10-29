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
import com.discgolf.game.ui.PortraitView
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
        goImmersive()
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

    private fun dp(v: Float) = Ui.dpi(this, v)

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.background = Ui.backdrop()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20f), dp(14f), dp(20f), dp(14f))
        }
        root.addView(row, FrameLayout.LayoutParams(-1, -1))

        // ---- left: portrait, name, hand
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.panelBg(this@GolferActivity)
            setPadding(dp(12f), dp(12f), dp(12f), dp(12f))
        }
        portrait = PortraitView(this).apply { look = p.look.copy() }
        left.addView(portrait, LinearLayout.LayoutParams(dp(190f), dp(210f)))

        val name = EditText(this).apply {
            setText(p.name)
            typeface = Ui.DISPLAY
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Ui.WHITE)
            setHintTextColor(Ui.METAL_DARK)
            hint = "NAME"
            gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            filters = arrayOf(InputFilter.LengthFilter(14))
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { p.name = s?.toString()?.trim().orEmpty() }
            })
        }
        left.addView(name, LinearLayout.LayoutParams(dp(190f), -2).apply { topMargin = dp(6f) })

        val hand = Ui.label(this, "", 14f, Ui.GOLD).apply { gravity = Gravity.CENTER; setPadding(0, dp(6f), 0, dp(6f)) }
        val handBtn = Ui.button(this, "Switch hand", false) {
            p.handedness = if (p.handedness == Handedness.RIGHT) Handedness.LEFT else Handedness.RIGHT
            refresh()
        }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(dp(10f), dp(6f), dp(10f), dp(6f)) }
        refreshers += { hand.text = p.handedness.label.uppercase() }
        left.addView(hand)
        left.addView(handBtn, LinearLayout.LayoutParams(-2, -2))
        row.addView(left, LinearLayout.LayoutParams(dp(222f), -1).apply { rightMargin = dp(16f) })

        // ---- right: scrolling column
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4f), dp(2f), dp(10f), dp(10f))
        }
        val scroll = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false }
        row.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))

        if (creating) buildCreateHeader(col) else buildCardHeader(col)
        col.addView(sectionTitle("Ratings"))
        for (s in Stat.values()) col.addView(statRow(s))
        col.addView(sectionTitle("Look"))
        buildLookPickers(col)
        if (!creating) buildBag(col)
        if (!creating) buildCareer(col)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(Ui.button(this, if (creating) "Start career" else "Done", true) { finishAndSave() }, LinearLayout.LayoutParams(dp(240f), -2))
        if (creating) {
            actions.addView(Ui.button(this, "Cancel", false) { finish() }, LinearLayout.LayoutParams(dp(150f), -2).apply { leftMargin = dp(12f) })
        } else {
            actions.addView(Ui.button(this, "Delete golfer", false) { confirmDelete() }, LinearLayout.LayoutParams(dp(190f), -2).apply { leftMargin = dp(12f) })
        }
        col.addView(actions, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18f) })

        refresh()
        return root
    }

    private fun sectionTitle(t: String) = Ui.label(this, t.uppercase(), 15f, Ui.GOLD, true).apply { setPadding(0, dp(14f), 0, dp(4f)) }

    // ---- headers

    private fun buildCreateHeader(col: LinearLayout) {
        col.addView(Ui.label(this, "CREATE YOUR GOLFER", 26f, Ui.WHITE, true))
        col.addView(sectionTitle("Pick a build"))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipViews = ArrayList<TextView>()
        for (a in Archetype.values()) {
            val chip = Ui.button(this, a.label, false) {
                p.applyArchetype(a)
                refresh()
            }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(dp(10f), dp(8f), dp(10f), dp(8f)) }
            chipViews += chip
            chips.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8f) })
        }
        refreshers += {
            Archetype.values().forEachIndexed { i, a ->
                chipViews[i].alpha = if (p.archetype == a) 1f else 0.55f
            }
        }
        col.addView(chips)
        val blurb = Ui.label(this, "", 13f, Ui.METAL).apply { setPadding(0, dp(4f), 0, 0) }
        refreshers += { blurb.text = p.archetype.blurb }
        col.addView(blurb)
        val pts = Ui.label(this, "", 16f, Ui.GREEN)
        pts.setPadding(0, dp(8f), 0, 0)
        refreshers += { pts.text = "POINTS TO SPEND: ${pointsLeft()}" }
        col.addView(pts)
    }

    private fun buildCardHeader(col: LinearLayout) {
        col.addView(Ui.label(this, "GOLFER CARD", 26f, Ui.WHITE, true))
        val lvl = Ui.label(this, "", 18f, Ui.GOLD, true)
        refreshers += { lvl.text = "LEVEL ${p.level}   ·   OVERALL ${p.overall()}" }
        col.addView(lvl)
        col.addView(XpBar(this) { p.xp.toFloat() / p.xpForNextLevel() }, LinearLayout.LayoutParams(dp(300f), dp(12f)).apply { topMargin = dp(4f) })
        val xpText = Ui.label(this, "", 12f, Ui.METAL)
        refreshers += { xpText.text = "${p.xp} / ${p.xpForNextLevel()} XP to level ${p.level + 1}" }
        col.addView(xpText)
        val pts = Ui.label(this, "", 16f, Ui.GREEN).apply { setPadding(0, dp(6f), 0, 0) }
        refreshers += { pts.text = if (p.unspentPoints > 0) "${p.unspentPoints} POINTS TO SPEND — tap + on a rating" else "Level up to earn more points" }
        col.addView(pts)
    }

    // ---- creation points

    private fun pointsLeft(): Int {
        val base = p.archetype.base.sum()
        return PlayerProfile.CREATE_POINTS - (p.stats.sum() - base)
    }

    // ---- rating rows

    private fun statRow(s: Stat): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val label = Ui.label(this, s.label.uppercase(), 15f, Ui.WHITE, true)
        row.addView(label, LinearLayout.LayoutParams(dp(96f), -2))
        val bar = StatBar(this, { p.stat(s) / 99f }, { (p.statXp[s.ordinal] / p.statXpNeeded(p.stat(s))).coerceIn(0f, 1f) }, !creating)
        row.addView(bar, LinearLayout.LayoutParams(0, dp(24f), 1f).apply { leftMargin = dp(6f); rightMargin = dp(8f) })
        val value = Ui.label(this, "", 20f, Ui.GOLD, true).apply { gravity = Gravity.CENTER }
        row.addView(value, LinearLayout.LayoutParams(dp(40f), -2))

        val minus = Ui.button(this, "−", false) {
            if (creating && p.stats[s.ordinal] > p.archetype.base[s.ordinal]) { p.stats[s.ordinal]--; refresh() }
        }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setPadding(0, dp(2f), 0, dp(2f)) }
        val plus = Ui.button(this, "+", true) {
            if (creating) {
                if (pointsLeft() > 0 && p.stats[s.ordinal] < PlayerProfile.CREATE_STAT_CAP) { p.stats[s.ordinal]++; refresh() }
            } else if (p.spendPoint(s)) refresh()
        }.apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setPadding(0, dp(2f), 0, dp(2f)) }
        if (creating) row.addView(minus, LinearLayout.LayoutParams(dp(40f), dp(36f)).apply { leftMargin = dp(4f) })
        row.addView(plus, LinearLayout.LayoutParams(dp(40f), dp(36f)).apply { leftMargin = dp(4f) })

        refreshers += {
            value.text = "${p.stat(s)}"
            bar.invalidate()
            plus.alpha = if (creating) (if (pointsLeft() > 0 && p.stat(s) < PlayerProfile.CREATE_STAT_CAP) 1f else 0.3f)
            else (if (p.unspentPoints > 0 && p.stat(s) < PlayerProfile.MAX_STAT) 1f else 0.3f)
            minus.alpha = if (p.stat(s) > p.archetype.base[s.ordinal]) 1f else 0.3f
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(row)
        wrap.addView(Ui.label(this, s.blurb, 11f, Ui.METAL_DARK).apply { setPadding(dp(2f), 0, 0, dp(6f)) })
        return wrap
    }

    // ---- look pickers

    private fun buildLookPickers(col: LinearLayout) {
        val l = p.look
        fun picker(title: String, count: Int, get: () -> Int, set: (Int) -> Unit, text: (Int) -> String, swatch: ((Int) -> Int)? = null) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(Ui.label(this, title.uppercase(), 14f, Ui.METAL, true), LinearLayout.LayoutParams(dp(96f), -2))
            val prev = Ui.button(this, "◀", false) { set((get() + count - 1) % count); refresh() }
                .apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, dp(4f), 0, dp(4f)) }
            val next = Ui.button(this, "▶", false) { set((get() + 1) % count); refresh() }
                .apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); setPadding(0, dp(4f), 0, dp(4f)) }
            val sw = View(this)
            val value = Ui.label(this, "", 15f, Ui.WHITE, true).apply { gravity = Gravity.CENTER }
            row.addView(prev, LinearLayout.LayoutParams(dp(44f), dp(34f)))
            if (swatch != null) row.addView(sw, LinearLayout.LayoutParams(dp(26f), dp(26f)).apply { leftMargin = dp(8f) })
            row.addView(value, LinearLayout.LayoutParams(dp(110f), -2))
            row.addView(next, LinearLayout.LayoutParams(dp(44f), dp(34f)))
            refreshers += {
                value.text = text(get())
                if (swatch != null) sw.background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF000000.toInt() or swatch(get())); cornerRadius = dp(6f).toFloat(); setStroke(dp(2f), Ui.WHITE)
                }
            }
            col.addView(row, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6f) })
        }
        picker("Skin", Appearance.SKIN_COUNT, { l.skinTone }, { l.skinTone = it }, { "Tone ${it + 1}" }) { Appearance.SKIN[it] }
        picker("Hair", Appearance.HAIR_STYLES.size, { l.hairStyle }, { l.hairStyle = it }, { Appearance.HAIR_STYLES[it] })
        picker("Hair colour", Appearance.HAIR_COLOR_COUNT, { l.hairColor }, { l.hairColor = it }, { "Colour ${it + 1}" }) { Appearance.HAIR[it] }
        picker("Headwear", Appearance.HEADWEAR.size, { l.headwear }, { l.headwear = it }, { Appearance.HEADWEAR[it] })
        picker("Shirt", Appearance.SHIRT_COUNT, { l.shirtColor }, { l.shirtColor = it }, { "Style ${it + 1}" }) { Appearance.SHIRT[it] }
        picker("Shorts", Appearance.SHORTS_COUNT, { l.shortsColor }, { l.shortsColor = it }, { "Style ${it + 1}" }) { Appearance.SHORTS[it] }
        picker("Build", Appearance.BUILDS.size, { l.build }, { l.build = it }, { Appearance.BUILDS[it] })
    }

    // ---- bag & career (card mode)

    private fun buildBag(col: LinearLayout) {
        col.addView(sectionTitle("Your bag"))
        for (d in DiscModel.values()) {
            val have = d.unlockLevel <= p.level
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val dot = View(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(if (have) (0xFF000000.toInt() or d.colorRgb) else Color.parseColor("#3A4658"))
                }
            }
            line.addView(dot, LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { rightMargin = dp(8f) })
            val nums = "${d.speed} | ${d.glide} | ${d.turn.toInt()} | ${d.fade.toInt()}"
            val txt = if (have) "${d.displayName}  (${d.kind})   $nums" else "${d.displayName}  — unlocks at level ${d.unlockLevel}"
            line.addView(Ui.label(this, txt, 14f, if (have) Ui.WHITE else Ui.METAL_DARK))
            col.addView(line, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(3f) })
        }
        col.addView(Ui.label(this, "Numbers: speed | glide | turn | fade", 11f, Ui.METAL_DARK))
    }

    private fun buildCareer(col: LinearLayout) {
        col.addView(sectionTitle("Career"))
        val best = if (p.bestRelPar == PlayerProfile.NO_ROUND) "—" else if (p.bestRelPar == 0) "E" else if (p.bestRelPar > 0) "+${p.bestRelPar}" else "${p.bestRelPar}"
        val lines = listOf(
            "Rounds played: ${p.roundsPlayed}",
            "Best round: $best",
            "Birdies or better: ${p.birdies}",
            "Aces: ${p.holesInOne}",
            "Longest throw: ${p.longestThrow.toInt()} m",
            "Total throws: ${p.totalThrows}",
        )
        for (s in lines) col.addView(Ui.label(this, s, 14f, Ui.WHITE))
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
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#06132B")
            r.set(0f, 0f, w, h); c.drawRoundRect(r, 6f, 6f, p)
            val v = value().coerceIn(0f, 1f)
            // green to gold with rating
            p.color = Color.rgb((60 + 190 * v).toInt(), (200 - 20 * v).toInt(), (110 - 100 * v).toInt())
            r.set(2f, 2f, 2f + (w - 4f) * v, h - 2f); c.drawRoundRect(r, 5f, 5f, p)
            p.color = Color.argb(70, 255, 255, 255)
            r.set(2f, 2f, 2f + (w - 4f) * v, h / 2f); c.drawRoundRect(r, 5f, 5f, p)
            if (showXp) {
                p.color = Ui.CYAN
                r.set(2f, h - 5f, 2f + (w - 4f) * xp(), h - 2f); c.drawRect(r, p)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = Ui.METAL_DARK
            r.set(0f, 0f, w, h); c.drawRoundRect(r, 6f, 6f, p)
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
