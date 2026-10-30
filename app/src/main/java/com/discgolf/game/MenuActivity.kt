package com.discgolf.game

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.discgolf.game.ui.PortraitView
import com.discgolf.game.ui.Ui

/** Title screen: play a round, edit your golfer, or read the controls. */
class MenuActivity : Activity() {

    private var askedToCreate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        try {
            val crash = java.io.File(filesDir, App.CRASH_FILE)
            if (crash.exists()) {
                showCrash(crash.readText()) { crash.delete(); recreate() }
                return
            }
            val profile = SaveStore.load(this)
            if (profile == null && !askedToCreate) {
                askedToCreate = true
                startActivity(Intent(this, GolferActivity::class.java))
            }
            setContentView(buildUi())
        } catch (t: Throwable) {
            val sw = java.io.StringWriter()
            t.printStackTrace(java.io.PrintWriter(sw))
            showCrash("Menu failed to start:\n\n$sw") { recreate() }
        }
    }

    /** Shows a crash report on screen so it can be read and sent back. */
    private fun showCrash(text: String, onContinue: () -> Unit) {
        val tv = android.widget.TextView(this).apply {
            this.text = "The game crashed last time. Please send this text to Claude:\n\n$text"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#200000"))
            addView(android.widget.Button(this@MenuActivity).apply {
                this.text = "Clear and try again"; setOnClickListener { onContinue() }
            })
            addView(android.widget.ScrollView(this@MenuActivity).apply { addView(tv) })
        }
        setContentView(col)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    private fun goImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    private fun buildUi(): View {
        val profile = SaveStore.load(this)
        val root = FrameLayout(this)
        root.background = Ui.backdrop()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(Ui.dpi(this@MenuActivity, 32f), Ui.dpi(this@MenuActivity, 16f), Ui.dpi(this@MenuActivity, 32f), Ui.dpi(this@MenuActivity, 16f))
        }
        root.addView(row, FrameLayout.LayoutParams(-1, -1))

        if (profile != null) {
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = Ui.panelBg(this@MenuActivity)
                setPadding(Ui.dpi(this@MenuActivity, 14f), Ui.dpi(this@MenuActivity, 14f), Ui.dpi(this@MenuActivity, 14f), Ui.dpi(this@MenuActivity, 14f))
            }
            val portrait = PortraitView(this).apply { look = profile.look }
            left.addView(portrait, LinearLayout.LayoutParams(Ui.dpi(this, 170f), Ui.dpi(this, 200f)))
            left.addView(Ui.label(this, profile.name.uppercase(), 22f, Ui.WHITE, true).apply { gravity = Gravity.CENTER })
            left.addView(Ui.label(this, "LEVEL ${profile.level}   ·   OVR ${profile.overall()}", 14f, Ui.GOLD).apply { gravity = Gravity.CENTER })
            left.addView(XpBar(this, profile.xp.toFloat() / profile.xpForNextLevel()), LinearLayout.LayoutParams(Ui.dpi(this, 170f), Ui.dpi(this, 10f)).apply { topMargin = Ui.dpi(this@MenuActivity, 6f) })
            if (profile.unspentPoints > 0) {
                left.addView(Ui.label(this, "${profile.unspentPoints} POINTS TO SPEND", 12f, Ui.GREEN).apply { gravity = Gravity.CENTER })
            }
            row.addView(left, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Ui.dpi(this@MenuActivity, 36f) })
        }

        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        right.addView(Ui.label(this, "DISC GOLF", 54f, Ui.GOLD, true))
        right.addView(Ui.label(this, "TOUR", 54f, Ui.WHITE, true).apply { setPadding(Ui.dpi(this@MenuActivity, 60f), 0, 0, 0) })
        right.addView(Ui.label(this, "HOLD  ·  RELEASE  ·  TAP  ·  THROW", 14f, Ui.METAL).apply { setPadding(0, Ui.dpi(this@MenuActivity, 4f), 0, Ui.dpi(this@MenuActivity, 18f)) })

        val bw = Ui.dpi(this, 280f)
        fun add(v: View) = right.addView(v, LinearLayout.LayoutParams(bw, -2).apply { topMargin = Ui.dpi(this@MenuActivity, 10f) })

        if (profile != null) {
            val (course, layout) = CourseLibrary.selected(this)
            add(Ui.button(this, "Play round", true) { startActivity(Intent(this, GameActivity::class.java)) })
            add(Ui.button(this, "Course: ${course.name}", false) { showCoursePicker() }.apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f) })
            add(Ui.label(this, "${layout.name.uppercase()}  ·  ${layout.holes.size} HOLES  ·  PAR ${layout.par}  ·  ${layout.length.toInt()} M", 12f, Ui.METAL).apply { gravity = Gravity.CENTER })
            add(Ui.button(this, "My golfer", false) { startActivity(Intent(this, GolferActivity::class.java)) })
        } else {
            add(Ui.button(this, "Create golfer", true) { startActivity(Intent(this, GolferActivity::class.java)) })
        }
        add(Ui.button(this, "How to play", false) { showHelp() })
        row.addView(right)
        return root
    }

    private fun showCoursePicker() {
        val loaded = CourseLibrary.load(this, force = true)
        val choices = ArrayList<Pair<com.discgolf.game.core.Course, com.discgolf.game.core.Layout>>()
        for (c in loaded.courses) for (l in c.layouts) choices += c to l
        val labels = choices.map { (c, l) -> "${c.name}  ·  ${l.name}\n${l.holes.size} holes, par ${l.par}, ${l.length.toInt()} m" }.toMutableList()
        labels += "Add your own courses…"
        AlertDialog.Builder(this)
            .setTitle("Choose a course")
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < choices.size) {
                    SaveStore.saveCourseChoice(this, choices[i].first.id, choices[i].second.name)
                    setContentView(buildUi())
                } else showCourseHelp(loaded.problems)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCourseHelp(problems: List<String>) {
        val dir = CourseLibrary.userDir(this)?.absolutePath ?: "(not available on this device)"
        val msg = "Copy a course file (.json) into this folder on the phone, then open this list again:\n\n$dir\n" +
            "\nCourses made with the import tool (tools/import_course.py) work as they are." +
            (if (problems.isEmpty()) "" else "\n\nSome files could not be loaded:\n" + problems.joinToString("\n") { "• $it" })
        AlertDialog.Builder(this).setTitle("Your courses").setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun showHelp() {
        val msg = """
            1. AIM with the arrows (or the left stick), then pick a disc and a shot shape: hyzer curves left, anhyzer curves right.
            2. PRESS AND HOLD the big button. The power bar fills. RELEASE at the gold PIN mark to throw exactly that far. Hold too long and the throw overcooks.
            3. TAP when the accuracy marker crosses the gold zone. A bigger Accuracy rating means a bigger zone.
            4. Water and out of bounds cost a stroke. Slow discs drop into the chains; hard ones kick out.
            5. Every throw trains your golfer. Power, Accuracy, Putting, Touch and Spin rise as you use them. Level up to unlock new discs and to earn points to spend.

            Controller: A = throw, stick = aim, D-pad = disc / shape, Y = scorecard, Start = pause.
        """.trimIndent()
        AlertDialog.Builder(this).setTitle("How to play").setMessage(msg).setPositiveButton("Got it", null).show()
    }

    /** Thin XP progress bar. */
    private class XpBar(ctx: android.content.Context, private val frac: Float) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val h = height.toFloat()
            p.color = Color.parseColor("#06132B")
            r.set(0f, 0f, width.toFloat(), h)
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.color = Ui.GOLD
            r.set(0f, 0f, width * frac.coerceIn(0f, 1f), h)
            c.drawRoundRect(r, h / 2, h / 2, p)
        }
    }
}
