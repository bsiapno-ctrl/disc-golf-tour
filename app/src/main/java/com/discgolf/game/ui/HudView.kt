package com.discgolf.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.discgolf.game.core.DiscModel
import com.discgolf.game.core.GameController
import com.discgolf.game.core.Phase
import com.discgolf.game.core.ProgressEvent
import com.discgolf.game.core.Stat
import com.discgolf.game.core.SwingMeter
import com.discgolf.game.core.ThrowTiming
import com.discgolf.game.core.clamp
import com.discgolf.game.core.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything drawn on top of the 3D view: hole info, wind, the disc card, the swing meter, popups and banners.
 * It is also the touch controller: hold the big button to charge, release to lock power, tap to stop the accuracy marker.
 */
class HudView(context: Context, private val ctl: GameController) : View(context) {

    var onAction: (Boolean) -> Unit = {}
    var onDisc: (Int) -> Unit = {}
    var onShape: (Int) -> Unit = {}
    var onResume: () -> Unit = {}
    var onQuit: () -> Unit = {}
    var paused = false
    /** Slow-motion factor for the swing and flight (1, 0.5, 0.25); the activity applies it. */
    var timeScale = 1f
    var onTimeScale: (Float) -> Unit = {}
    private val tsOptions = floatArrayOf(1f, 0.5f, 0.25f)
    private val tsBtns = Array(3) { RectF() }
    var showCard = false

    /** -1..1 aim stick command (0 when released). Read by the activity every frame. */
    val touchAim: Float get() = if (angleMode) 0f else stick.output
    /** -1..1 aim-pitch command (stick up = aim higher). */
    val touchAimY: Float get() = if (angleMode) 0f else stick.outputY
    private val stick = AimStick().also { it.labels = arrayOf("L", "R", "UP", "DOWN"); it.accent = 0xFFFFD84A.toInt() }
    /** The one stick has two modes: AIM (heading / pitch) and ANGLE (hyzer-anhyzer / nose). ANGLE is forced during RELEASE_ANGLE. */
    private var manualAngle = false
    val angleMode: Boolean get() = ctl.phase == Phase.RELEASE_ANGLE || (manualAngle && ctl.phase == Phase.AIMING)
    private val chipRect = RectF()
    private val puttChipRect = RectF()
    private val AIM_LABELS = arrayOf("L", "R", "UP", "DOWN")
    private val ANGLE_LABELS = arrayOf("HYZER", "ANHY", "NOSE UP", "DOWN")
    /** -1..1 release commands from the same stick while in ANGLE mode (x right = anhyzer, y up = nose up). */
    val touchRelX: Float get() = if (angleMode) stick.output else 0f
    val touchRelY: Float get() = if (angleMode) stick.outputY else 0f

    private class Pop(val ev: ProgressEvent, var age: Float = 0f)
    private val pops = ArrayList<Pop>()

    /** One design unit in pixels. Scaled so the layout (drawn for about 760 x 360 dp) fits whatever screen this is. */
    private var d = Ui.dp(context, 1f)
    private val baseDp = Ui.dp(context, 1f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val r2 = RectF()
    private val path = Path()

    // Hit areas (recomputed on layout)
    private val actionC = FloatArray(3)
    private val cardRect = RectF()
    private val discPrev = RectF()
    private val discNext = RectF()
    private val filterRect = RectF()
    private val shapePrev = RectF()
    private val shapeNext = RectF()
    private val menuRect = RectF()
    private val handBtn = RectF()
    private val cardBtn = RectF()
    private val resumeBtn = RectF()
    private val quitBtn = RectF()

    private var actionPointer = -1

    init { isFocusable = false }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private var L: HudRects = HudLayout.compute(1f, 1f, 1f)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val wf = w.toFloat(); val hf = h.toFloat()
        d = HudLayout.scale(wf, hf, baseDp)
        L = HudLayout.compute(wf, hf, d)   // all panel rects come from this pure, unit-tested function
        actionC[0] = L.actionCx; actionC[1] = L.actionCy; actionC[2] = L.actionR
        stick.layout(70 * d, hf - 56 * d, 46 * d)
        chipRect.set(70 * d - 62 * d, hf - 122 * d, 70 * d + 62 * d, hf - 104 * d)
        HudLayout.puttChip(hf, d).let { puttChipRect.set(it.l, it.t, it.r, it.b) }
        cardRect.set(L.discCard.l, L.discCard.t, L.discCard.r, L.discCard.b)
        discPrev.set(cardRect.left, cardRect.top, cardRect.left + 40 * d, cardRect.bottom - 30 * d)
        discNext.set(cardRect.right - 40 * d, cardRect.top, cardRect.right, cardRect.bottom - 30 * d)
        filterRect.set(cardRect.left + 88 * d, cardRect.top + 28 * d, discNext.left, cardRect.top + 43 * d)
        shapePrev.set(cardRect.left, cardRect.bottom - 34 * d, cardRect.left + 70 * d, cardRect.bottom)
        shapeNext.set(cardRect.right - 70 * d, cardRect.bottom - 34 * d, cardRect.right, cardRect.bottom)
        // Throw panel: backhand / forehand, launch angle and release tilt, to the right of the aim stick.
        val tx0 = L.throwPanel.l + 5 * d; val tx1 = L.throwPanel.r - 5 * d; val rowH = 24 * d; val ty = L.throwPanel.t + 5 * d
        handBtn.set(tx0, ty, tx1, ty + rowH)
        menuRect.set(L.menu.l, L.menu.t, L.menu.r, L.menu.b)
        cardBtn.set(L.cardBtn.l, L.cardBtn.t, L.cardBtn.r, L.cardBtn.b)
        resumeBtn.set(wf / 2f - 110 * d, hf / 2f - 18 * d, wf / 2f + 110 * d, hf / 2f + 32 * d)
        for (i in 0..2) { val x0 = wf / 2f - 111 * d + i * 76 * d; tsBtns[i].set(x0, hf / 2f - 82 * d, x0 + 70 * d, hf / 2f - 46 * d) }
        quitBtn.set(wf / 2f - 110 * d, hf / 2f + 44 * d, wf / 2f + 110 * d, hf / 2f + 94 * d)
    }

    // ------------------------------------------------------------------
    // Per-frame
    // ------------------------------------------------------------------

    fun tick(dt: Float) {
        for (e in ctl.consumePopups()) pops.add(Pop(e))
        val it = pops.iterator()
        while (it.hasNext()) { val x = it.next(); x.age += dt; if (x.age > 3.6f) it.remove() }
    }

    // ------------------------------------------------------------------
    // Touch
    // ------------------------------------------------------------------

    private fun inCircle(c: FloatArray, x: Float, y: Float): Boolean {
        val dx = x - c[0]; val dy = y - c[1]
        return dx * dx + dy * dy <= c[2] * c[2] * 1.4f
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(id, e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(id)
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                stick.move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> { up(actionPointer); up(stick.pointer) }
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (paused) {
            if (resumeBtn.contains(x, y)) onResume()
            else if (quitBtn.contains(x, y)) onQuit()
            else for (i in 0..2) if (tsBtns[i].contains(x, y)) { timeScale = tsOptions[i]; onTimeScale(timeScale) }
            return
        }
        if (ctl.phase == Phase.ROUND_DONE) { if (ctl.phaseTime > 1.0f) onQuit(); return }
        if (menuRect.contains(x, y)) { paused = true; return }
        if (cardBtn.contains(x, y)) { showCard = !showCard; return }
        if (showCard) { showCard = false; return }

        when (ctl.phase) {
            Phase.AIMING -> {
                when {
                    ctl.isPutt && puttChipRect.contains(x, y) -> ctl.togglePuttStyle()
                    chipRect.contains(x, y) -> manualAngle = !manualAngle
                    !stick.active && stick.hitsFixed(x, y) -> stick.down(id, x, y, false)
                    discPrev.contains(x, y) -> onDisc(-1)
                    discNext.contains(x, y) -> onDisc(1)
                    filterRect.contains(x, y) -> ctl.cycleDiscFilter()
                    shapePrev.contains(x, y) -> onShape(-1)
                    shapeNext.contains(x, y) -> onShape(1)
                    handBtn.contains(x, y) -> ctl.toggleForehand()
                    !stick.active && x < width * 0.35f && y > height * 0.3f -> stick.down(id, x, y, true)
                    x > width * 0.62f && y > height * 0.3f && actionPointer < 0 -> { actionPointer = id; onAction(true) }
                }
            }
            Phase.RELEASE_ANGLE -> if (!stick.active && stick.hitsFixed(x, y)) stick.down(id, x, y, false) else onAction(true)
            Phase.RUNUP, Phase.JUMP -> onAction(true)   // tap anywhere
            Phase.INTRO, Phase.RESULT, Phase.HOLE_DONE -> onAction(true)
            Phase.OB_CHOICE -> {
                val o = HudLayout.obChoice(width.toFloat(), height.toFloat(), d)
                if (o.lieBtn.contains(x, y)) ctl.chooseOb(true)
                else if (o.rethrowBtn.contains(x, y)) ctl.chooseOb(false)
            }
            else -> Unit
        }
    }

    private fun up(id: Int) {
        if (id < 0) return
        if (id == actionPointer) { actionPointer = -1; onAction(false) }
        stick.up(id)
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private fun text(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, display: Boolean = true, maxW: Float = 0f) {
        p.typeface = if (display) Ui.DISPLAY else Ui.BODY
        p.textSize = sizeDp * d
        if (maxW > 0f) { val tw = p.measureText(s); if (tw > maxW) p.textSize = sizeDp * d * maxW / tw }
        p.textAlign = align
        // chunky cartoon outline, then drop shadow, then fill
        p.style = Paint.Style.STROKE
        p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = max(2f, sizeDp * 0.22f) * d
        p.color = Color.argb(235, 6, 16, 40)
        c.drawText(s, x, y, p)
        p.style = Paint.Style.FILL
        p.setShadowLayer(2f * d, 0f, 2f * d, Color.argb(160, 0, 0, 0))
        p.color = color
        c.drawText(s, x, y, p)
        p.clearShadowLayer()
    }

    private fun panel(c: Canvas, rect: RectF, alpha: Int = 215, stroke: Int = Ui.GOLD) {
        val rad = 16 * d
        // drop shadow
        r2.set(rect.left, rect.top + 4 * d, rect.right, rect.bottom + 4 * d)
        p.style = Paint.Style.FILL
        p.color = Color.argb(110, 0, 0, 0)
        c.drawRoundRect(r2, rad, rad, p)
        // body with vertical gradient
        p.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.argb(alpha, 34, 78, 150), Color.argb(alpha, 8, 22, 52), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, p)
        p.shader = null
        // gloss band
        r2.set(rect.left + 3 * d, rect.top + 3 * d, rect.right - 3 * d, rect.top + rect.height() * 0.42f)
        p.color = Color.argb(alpha / 5, 255, 255, 255)
        c.drawRoundRect(r2, rad - 3 * d, rad - 3 * d, p)
        // thick light outline, coloured inner rim
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4 * d
        p.color = Color.argb(240, 255, 255, 255)
        c.drawRoundRect(rect, rad, rad, p)
        r2.set(rect); r2.inset(2.5f * d, 2.5f * d)
        p.strokeWidth = 2 * d
        p.color = stroke
        c.drawRoundRect(r2, rad - 2 * d, rad - 2 * d, p)
        p.style = Paint.Style.FILL
    }

    private fun pill(c: Canvas, rect: RectF, top: Int, bottom: Int) {
        val rad = rect.height() / 2f
        r2.set(rect.left, rect.top + 3 * d, rect.right, rect.bottom + 3 * d)
        p.style = Paint.Style.FILL
        p.color = Color.argb(110, 0, 0, 0)
        c.drawRoundRect(r2, rad, rad, p)
        p.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, p)
        p.shader = null
        r2.set(rect.left + 4 * d, rect.top + 2 * d, rect.right - 4 * d, rect.centerY())
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRoundRect(r2, rad, rad, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE
        c.drawRoundRect(rect, rad, rad, p)
        p.style = Paint.Style.FILL
    }

    private fun roundButton(c: Canvas, cx: Float, cy: Float, rad: Float, label: String, active: Boolean, color: Int) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(110, 0, 0, 0)
        c.drawCircle(cx, cy + 7 * d, rad, p)
        p.color = Ui.darken(color, 0.5f)
        c.drawCircle(cx, cy + (if (active) 2 else 5) * d, rad, p)
        val base = if (active) Ui.darken(color, 0.85f) else color
        p.shader = RadialGradient(cx, cy - rad * 0.4f, rad * 1.5f, Ui.darken(base, 1.0f), Ui.darken(base, 0.7f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy + (if (active) 3 else 0) * d, rad, p)
        p.shader = null
        p.color = Color.argb(90, 255, 255, 255)
        c.drawOval(cx - rad * 0.62f, cy - rad * 0.9f, cx + rad * 0.62f, cy - rad * 0.12f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 4 * d; p.color = Color.WHITE
        c.drawCircle(cx, cy + (if (active) 3 else 0) * d, rad, p)
        p.style = Paint.Style.FILL
        text(c, label, cx, cy + 7 * d, 20f, Color.WHITE, Paint.Align.CENTER)
    }

    /** Chunky left/right arrow: dark outline, gold fill (no font glyphs). */
    private fun arrowTri(c: Canvas, x: Float, y: Float, sz: Float, right: Boolean) {
        val k = if (right) 1f else -1f
        path.reset(); path.moveTo(x + k * sz, y); path.lineTo(x - k * sz * 0.7f, y - sz); path.lineTo(x - k * sz * 0.7f, y + sz); path.close()
        p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = 3 * d; p.color = Color.argb(235, 6, 16, 40)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL; p.color = Ui.GOLD
        c.drawPath(path, p)
    }

    private fun triangle(c: Canvas, x: Float, y: Float, size: Float, up: Boolean, color: Int) {
        path.reset()
        if (up) { path.moveTo(x, y - size); path.lineTo(x - size, y + size); path.lineTo(x + size, y + size) }
        else { path.moveTo(x, y + size); path.lineTo(x - size, y - size); path.lineTo(x + size, y - size) }
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }

    // ------------------------------------------------------------------
    // Draw
    // ------------------------------------------------------------------

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val hole = ctl.hole
        val phase = ctl.phase

        if (phase == Phase.ROUND_DONE) { drawRoundDone(c, w, h); drawPopups(c, w); return }

        drawHoleBanner(c)
        drawDistChip(c)
        drawScorePanel(c, w)
        drawWind(c)
        drawMenuButtons(c)
        drawMinimap(c, h)

        if (phase == Phase.AIMING || phase.isThrowStep) {
            drawDiscCard(c)
            drawAimButtons(c)
            drawThrowPanel(c)
            drawMeter(c)
            val label = when (phase) {
                Phase.AIMING -> "HOLD"
                Phase.POWER -> "RELEASE"
                Phase.RUNUP -> "STEP!"
                Phase.JUMP -> "JUMP!"
                else -> "TAP!"
            }
            roundButton(c, actionC[0], actionC[1], actionC[2], label, phase != Phase.AIMING, if (phase == Phase.RUNUP || phase == Phase.JUMP || phase == Phase.RELEASE_ANGLE) Ui.ORANGE else Ui.GOLD)
        }

        when (phase) {
            Phase.INTRO -> drawIntro(c, w, h)
            Phase.RESULT -> drawResult(c, w, h)
            Phase.OB_CHOICE -> drawObChoice(c, w, h)
            Phase.HOLE_DONE -> drawHoleDone(c, w, h)
            else -> Unit
        }

        drawPopups(c, w)
        if (showCard) drawScorecard(c, w, h, false)
        if (paused) drawPause(c, w, h)
    }

    // ------------------------------------------------------------------
    // Cartoon panels
    // ------------------------------------------------------------------

    /** Chunky toon plate: dark outline, bright vertical gradient, gloss, thick white rim. */
    private fun toon(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, rad: Float, top: Int, bottom: Int) {
        r2.set(x0, y0 + 4 * d, x1, y1 + 4 * d)
        p.style = Paint.Style.FILL; p.color = Color.argb(100, 0, 0, 0)
        c.drawRoundRect(r2, rad, rad, p)
        r2.set(x0, y0, x1, y1)
        p.style = Paint.Style.STROKE; p.strokeWidth = 7 * d; p.color = Color.argb(235, 6, 16, 40)
        c.drawRoundRect(r2, rad, rad, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, y0, 0f, y1, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r2, rad, rad, p)
        p.shader = null
        p.color = Color.argb(60, 255, 255, 255)
        r2.set(x0 + 4 * d, y0 + 3 * d, x1 - 4 * d, y0 + (y1 - y0) * 0.45f)
        c.drawRoundRect(r2, rad * 0.8f, rad * 0.8f, p)
        r2.set(x0, y0, x1, y1)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE
        c.drawRoundRect(r2, rad, rad, p)
        p.style = Paint.Style.FILL
    }

    private fun toon(c: Canvas, rc: HRect, rad: Float, top: Int, bottom: Int) = toon(c, rc.l, rc.t, rc.r, rc.b, rad, top, bottom)

    /** Dark inset slot used as the track of the meters. */
    private fun trough(c: Canvas, l: Float, t: Float, rt: Float, b: Float) {
        val rad = (b - t) / 2f
        r2.set(l, t, rt, b)
        p.style = Paint.Style.FILL; p.color = Color.argb(240, 5, 12, 30)
        c.drawRoundRect(r2, rad, rad, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE
        c.drawRoundRect(r2, rad, rad, p)
        p.style = Paint.Style.FILL
    }

    /** Rounded little stat chip: tiny label over a chunky value. */
    private fun statChip(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, label: String, value: String) {
        toon(c, x0, y0, x1, y1, 8 * d, Ui.darken(color, 1f), Ui.darken(color, 0.6f))
        text(c, label, (x0 + x1) / 2f, y0 + 9 * d, 7f, Ui.WHITE, Paint.Align.CENTER, display = false)
        text(c, value, (x0 + x1) / 2f, y1 - 5 * d, 14f, Ui.WHITE, Paint.Align.CENTER, maxW = (x1 - x0) - 6 * d)
    }

    /** Fat arrow pointing up from (cx,cy); caller rotates the canvas. */
    private fun bigArrow(c: Canvas, cx: Float, cy: Float, len: Float, half: Float, color: Int) {
        path.reset()
        val head = half * 2.2f
        path.moveTo(cx, cy - len)
        path.lineTo(cx + half * 1.8f, cy - len + head)
        path.lineTo(cx + half * 0.7f, cy - len + head)
        path.lineTo(cx + half * 0.7f, cy + len * 0.55f)
        path.lineTo(cx - half * 0.7f, cy + len * 0.55f)
        path.lineTo(cx - half * 0.7f, cy - len + head)
        path.lineTo(cx - half * 1.8f, cy - len + head)
        path.close()
        p.style = Paint.Style.FILL; p.color = color
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = 2.5f * d; p.color = Color.argb(240, 6, 16, 40)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    /** Angle (radians, clockwise, 0 = straight ahead of the current aim) of a world vector. */
    private fun relAngle(vx: Float, vz: Float): Float {
        val hd = ctl.aimHeading
        val along = vx * sin(hd) + vz * cos(hd)
        val across = vx * cos(hd) - vz * sin(hd)
        return atan2(across, along)
    }

    // (1) hole / par banner with the big hole-number badge
    private fun drawHoleBanner(c: Canvas) {
        val hole = ctl.hole
        val b = L.holeBanner
        toon(c, b, 16 * d, Color.parseColor("#3C86E6"), Color.parseColor("#14439A"))
        val bcx = b.l + 31 * d; val bcy = (b.t + b.b) / 2f; val br = 23 * d
        p.style = Paint.Style.FILL; p.color = Color.argb(110, 0, 0, 0)
        c.drawCircle(bcx, bcy + 3 * d, br, p)
        p.shader = RadialGradient(bcx, bcy - br * 0.5f, br * 1.6f, Color.parseColor("#FFE680"), Ui.GOLD_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(bcx, bcy, br, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE
        c.drawCircle(bcx, bcy, br, p)
        p.strokeWidth = 1.5f * d; p.color = Color.argb(200, 6, 16, 40)
        c.drawCircle(bcx, bcy, br - 4 * d, p)
        p.style = Paint.Style.FILL
        text(c, "${hole.number}", bcx, bcy + 9 * d, 26f, Ui.WHITE, Paint.Align.CENTER, maxW = 2 * br * 0.8f)
        val tx = b.l + 62 * d
        // little flag icon on the right of the banner
        val fx = b.r - 18 * d; val fy = b.t + 12 * d
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = Color.WHITE; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(fx, fy, fx, fy + 24 * d, p)
        p.strokeCap = Paint.Cap.BUTT; p.style = Paint.Style.FILL
        path.reset(); path.moveTo(fx, fy); path.lineTo(fx + 14 * d, fy + 5 * d); path.lineTo(fx, fy + 11 * d); path.close()
        p.color = Ui.RED; c.drawPath(path, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Color.WHITE; p.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, p); p.style = Paint.Style.FILL
        text(c, "PAR ${hole.par}", tx, b.t + 27 * d, 20f, Ui.GOLD, maxW = fx - 6 * d - tx)
        val sub = if (ctl.isPutt) (if (ctl.distanceToPin <= 4f) "CHAINS" else "PUTT · CIRCLE ${ctl.puttCircle}") else hole.name.uppercase()
        text(c, sub, tx, b.t + 47 * d, 12f, if (ctl.isPutt) Ui.CYAN else Ui.WHITE, display = false, maxW = b.r - 12 * d - tx)
    }

    // (6) distance-to-pin chip with a compass arrow toward the basket
    private fun drawDistChip(c: Canvas) {
        val b = L.distChip
        toon(c, b, 14 * d, Color.parseColor("#33C46E"), Color.parseColor("#12703C"))
        val lie = ctl.lie; val bk = ctl.hole.basket
        val ang = relAngle(bk.x - lie.x, bk.z - lie.z)
        val cx = b.l + 17 * d; val cy = (b.t + b.b) / 2f
        c.save()
        c.rotate(Math.toDegrees(ang.toDouble()).toFloat(), cx, cy)
        bigArrow(c, cx, cy, 9 * d, 3.2f * d, Ui.WHITE)
        c.restore()
        text(c, "${ctl.distanceToPin.toInt()} m", b.l + 34 * d, cy + 7 * d, 16f, Ui.WHITE, maxW = 38 * d)
        text(c, "PIN", b.r - 10 * d, cy + 4 * d, 9f, Color.parseColor("#D9FFE6"), Paint.Align.RIGHT, display = false)
        // elevation chip: basket height minus lie height
        val e = L.elevChip
        toon(c, e, 9 * d, Color.parseColor("#3C86E6"), Color.parseColor("#14439A"))
        text(c, HudLayout.elevText(ctl.elevationToPin), e.centerXf(), (e.t + e.b) / 2f + 4 * d, 11f, Ui.WHITE, Paint.Align.CENTER, display = false, maxW = e.w - 6 * d)
    }

    // (5) strokes / par / total-vs-par panel
    private fun drawScorePanel(c: Canvas, w: Float) {
        val b = L.scorePanel
        toon(c, b, 14 * d, Color.parseColor("#3C86E6"), Color.parseColor("#14439A"))
        val throwNo = ctl.strokes + if (ctl.phase == Phase.HOLE_DONE || ctl.phase == Phase.RESULT || ctl.phase == Phase.OB_CHOICE) 0 else 1
        val rel = ctl.relativeToPar()
        val relS = if (rel == 0) "E" else if (rel > 0) "+$rel" else "$rel"
        val relCol = if (rel < 0) Ui.GREEN else if (rel > 0) Ui.ORANGE else Ui.WHITE
        val cw = b.w / 3f
        val labels = arrayOf("THROW", "PAR", "TOTAL")
        val values = arrayOf("$throwNo", "${ctl.hole.par}", relS)
        val cols = intArrayOf(Ui.GOLD, Ui.WHITE, relCol)
        for (i in 0..2) {
            val cx = b.l + cw * (i + 0.5f)
            text(c, labels[i], cx, b.t + 15 * d, 9f, Ui.METAL, Paint.Align.CENTER, display = false)
            text(c, values[i], cx, b.t + 39 * d, 24f, cols[i], Paint.Align.CENTER, maxW = cw - 10 * d)
            if (i > 0) {
                p.style = Paint.Style.FILL; p.color = Color.argb(120, 255, 255, 255)
                c.drawRect(b.l + cw * i - 1 * d, b.t + 9 * d, b.l + cw * i + 1 * d, b.b - 9 * d, p)
            }
        }
    }

    // (2) wind compass dial: arrow rotates relative to your aim, speed number underneath
    private fun drawWind(c: Canvas) {
        val b = L.windPanel
        val wx = ctl.wind.x; val wz = ctl.wind.z
        val speed = sqrt(wx * wx + wz * wz)
        val ang = relAngle(wx, wz)   // 0 = tailwind (blowing away from you), clockwise positive
        val hd = ctl.aimHeading
        val along = wx * sin(hd) + wz * cos(hd)
        val across = wx * cos(hd) - wz * sin(hd)
        toon(c, b, 16 * d, Color.parseColor("#3C86E6"), Color.parseColor("#14439A"))
        val cx = b.centerXf(); val cy = b.t + 37 * d; val rad = 29 * d
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy - rad * 0.4f, rad * 1.5f, Color.parseColor("#FFFFFF"), Color.parseColor("#9FC4F0"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.argb(240, 6, 16, 40)
        c.drawCircle(cx, cy, rad, p)
        // compass ticks: bold at the four points, small between
        p.strokeWidth = 2 * d
        for (k in 0 until 8) {
            val a = k * 0.7853982f
            val i0 = rad * (if (k % 2 == 0) 0.74f else 0.84f)
            p.color = Color.argb(200, 20, 57, 111)
            c.drawLine(cx + sin(a) * i0, cy - cos(a) * i0, cx + sin(a) * rad * 0.94f, cy - cos(a) * rad * 0.94f, p)
        }
        p.style = Paint.Style.FILL
        val col = if (speed < 0.6f) Ui.METAL_DARK else if (speed < 3f) Color.parseColor("#1FA2D8") else if (speed < 5f) Ui.GOLD_DARK else Ui.RED
        c.save()
        c.rotate(Math.toDegrees(ang.toDouble()).toFloat(), cx, cy)
        val len = (10f + min(speed, 6f) * 2.2f) * d
        bigArrow(c, cx, cy, len, 4f * d, col)
        c.restore()
        p.color = Ui.NAVY; c.drawCircle(cx, cy, 3 * d, p)
        text(c, String.format("%.1f m/s", speed), cx, b.t + 79 * d, 15f, Ui.WHITE, Paint.Align.CENTER, maxW = b.w - 10 * d)
        val kind = when {
            speed < 0.6f -> "CALM"
            Math.abs(along) > Math.abs(across) -> if (along > 0) "TAILWIND" else "HEADWIND"
            else -> if (across > 0) "FROM LEFT" else "FROM RIGHT"
        }
        text(c, kind, cx, b.t + 91 * d, 10f, Ui.GOLD, Paint.Align.CENTER, display = false, maxW = b.w - 10 * d)
    }

    private val miniPath = Path()
    private val miniRect = RectF()

    /** Top-down map of the hole, turned so the basket is "up" from your lie. */
    private fun drawMinimap(c: Canvas, h: Float) {
        val hole = ctl.hole
        val mm = L.minimap ?: return
        miniRect.set(mm.l, mm.t, mm.r, mm.b)
        panel(c, miniRect, 200)
        val lie = ctl.lie
        val up = (hole.basket - lie).flat().normalized()
        val ux = up.x; val uz = up.z
        val rx = uz; val rz = -ux
        fun uOf(x: Float, z: Float) = (x - lie.x) * ux + (z - lie.z) * uz
        fun vOf(x: Float, z: Float) = (x - lie.x) * rx + (z - lie.z) * rz
        var uMin = 0f; var uMax = 1f; var vMax = 10f
        val pts = ArrayList<Vec3>(hole.path); pts += lie
        for (q in pts) {
            uMin = min(uMin, uOf(q.x, q.z)); uMax = max(uMax, uOf(q.x, q.z)); vMax = max(vMax, abs(vOf(q.x, q.z)))
        }
        val pad = hole.fairwayWidth * 0.9f + 8f
        val inner = RectF(miniRect.left + 6 * d, miniRect.top + 6 * d, miniRect.right - 6 * d, miniRect.bottom - 6 * d)
        val s = min(inner.height() / (uMax - uMin + pad * 2f), inner.width() / (vMax * 2f + pad * 2f))
        val cx = inner.centerX()
        val cu = (uMin + uMax) / 2f
        fun px(x: Float, z: Float) = cx + vOf(x, z) * s
        fun py(x: Float, z: Float) = inner.centerY() - (uOf(x, z) - cu) * s

        c.save()
        miniRect.inset(2 * d, 2 * d)
        path.reset(); path.addRoundRect(miniRect, 10 * d, 10 * d, Path.Direction.CW)
        c.clipPath(path)
        miniRect.inset(-2 * d, -2 * d)
        p.style = Paint.Style.FILL; p.color = Color.rgb(58, 110, 52)
        c.drawRect(miniRect, p)
        // fairway ribbon
        miniPath.reset()
        hole.path.forEachIndexed { i, q -> if (i == 0) miniPath.moveTo(px(q.x, q.z), py(q.x, q.z)) else miniPath.lineTo(px(q.x, q.z), py(q.x, q.z)) }
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        p.strokeWidth = hole.fairwayWidth * s; p.color = Color.rgb(126, 190, 84)
        c.drawPath(miniPath, p)
        p.strokeCap = Paint.Cap.BUTT
        // water
        p.style = Paint.Style.FILL; p.color = Color.rgb(70, 160, 220)
        for (pd in hole.ponds) {
            path.reset()
            for (k in 0 until 24) {
                val a = k / 24f * 6.2832f
                val x = pd.cx + cos(a) * pd.rx; val z = pd.cz + sin(a) * pd.rz
                if (k == 0) path.moveTo(px(x, z), py(x, z)) else path.lineTo(px(x, z), py(x, z))
            }
            path.close(); c.drawPath(path, p)
        }
        // trees as dark dots
        p.color = Color.argb(200, 24, 70, 34)
        for (t in hole.trees) { val x = px(t.x, t.z); val y = py(t.x, t.z); if (miniRect.contains(x, y)) c.drawCircle(x, y, max(1.2f * d, t.canopyR * s * 0.6f), p) }
        // aim line
        val ax = sin(ctl.aimHeading); val az = cos(ctl.aimHeading)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Ui.GOLD
        c.drawLine(px(lie.x, lie.z), py(lie.x, lie.z), px(lie.x + ax * 60f, lie.z + az * 60f), py(lie.x + ax * 60f, lie.z + az * 60f), p)
        // basket and lie
        p.style = Paint.Style.FILL
        p.color = Color.WHITE; c.drawCircle(px(hole.basket.x, hole.basket.z), py(hole.basket.x, hole.basket.z), 4.5f * d, p)
        p.color = Ui.ORANGE; c.drawCircle(px(hole.basket.x, hole.basket.z), py(hole.basket.x, hole.basket.z), 3f * d, p)
        p.color = Color.WHITE; c.drawCircle(px(lie.x, lie.z), py(lie.x, lie.z), 5f * d, p)
        p.color = Ui.GOLD; c.drawCircle(px(lie.x, lie.z), py(lie.x, lie.z), 3.5f * d, p)
        c.restore()
    }

    private fun drawMenuButtons(c: Canvas) {
        pill(c, menuRect, Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
        text(c, "MENU", menuRect.centerX(), menuRect.centerY() + 6 * d, 15f, Ui.WHITE, Paint.Align.CENTER)
        pill(c, cardBtn, Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
        text(c, "CARD", cardBtn.centerX(), cardBtn.centerY() + 6 * d, 15f, Ui.WHITE, Paint.Align.CENTER)
    }

    // (3) disc card: colour swatch, name, four flight-number pills, shape selector
    private fun drawDiscCard(c: Canvas) {
        val disc = ctl.disc
        toon(c, cardRect.left, cardRect.top, cardRect.right, cardRect.bottom, 16 * d, Color.parseColor("#3C86E6"), Color.parseColor("#14439A"))
        val sx = cardRect.left + 62 * d; val sy = cardRect.top + 27 * d; val sr = 18 * d
        p.style = Paint.Style.FILL; p.color = Color.argb(110, 0, 0, 0)
        c.drawOval(sx - sr, sy - sr * 0.5f + 5 * d, sx + sr, sy + sr * 0.5f + 5 * d + sr * 0.4f, p)
        val rgb = 0xFF000000.toInt() or disc.colorRgb
        p.color = Ui.darken(rgb, 0.55f)
        c.drawOval(sx - sr, sy - sr * 0.55f + 3 * d, sx + sr, sy + sr * 0.55f + 3 * d, p)
        p.color = rgb
        c.drawOval(sx - sr, sy - sr * 0.55f, sx + sr, sy + sr * 0.55f, p)
        p.color = Color.argb(110, 255, 255, 255)
        c.drawOval(sx - sr * 0.55f, sy - sr * 0.4f, sx + sr * 0.25f, sy - sr * 0.1f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = Color.WHITE
        c.drawOval(sx - sr, sy - sr * 0.55f, sx + sr, sy + sr * 0.55f, p)
        p.style = Paint.Style.FILL
        p.style = Paint.Style.FILL; p.color = Color.argb(90, 6, 16, 40)
        c.drawCircle(discPrev.centerX(), cardRect.top + 27 * d, 13 * d, p)
        c.drawCircle(discNext.centerX(), cardRect.top + 27 * d, 13 * d, p)
        arrowTri(c, discPrev.centerX(), cardRect.top + 27 * d, 7 * d, false)
        arrowTri(c, discNext.centerX(), cardRect.top + 27 * d, 7 * d, true)
        val nx = cardRect.left + 90 * d
        val nw = discNext.left - nx - 2 * d
        text(c, disc.displayName.uppercase(), nx, cardRect.top + 24 * d, 17f, Ui.WHITE, maxW = nw)
        val kindLbl = (if (ctl.discFilter != null) "ONLY " else "") + (if (ctl.isPutt) "PUTT" else disc.kind.uppercase()) + " \u00B7 " + disc.stability
        text(c, kindLbl, nx, cardRect.top + 38 * d, 10f, Color.parseColor("#CFE3FF"), display = false, maxW = nw)
        drawFlightSketch(c, disc, cardRect.left + 8 * d, cardRect.top + 46 * d, cardRect.left + 36 * d, cardRect.top + 68 * d)
        // four chunky flight-number pills between the arrows
        val labels = arrayOf("SPEED", "GLIDE", "TURN", "FADE")
        val vals = arrayOf("${disc.speed}", "${disc.glide}", "${disc.turn.toInt()}", "${disc.fade.toInt()}")
        val cols = intArrayOf(Color.parseColor("#F08A2C"), Color.parseColor("#22A8DC"), Color.parseColor("#2FB968"), Color.parseColor("#9A5BE0"))
        val px0 = cardRect.left + 40 * d; val px1 = cardRect.right - 40 * d
        val gap = 4 * d
        val pwid = (px1 - px0 - gap * 3) / 4f
        val py0 = cardRect.top + 44 * d; val py1 = cardRect.top + 69 * d
        for (i in 0..3) {
            val x0 = px0 + i * (pwid + gap)
            statChip(c, x0, py0, x0 + pwid, py1, cols[i], labels[i], vals[i])
        }
        arrowTri(c, shapePrev.left + 20 * d, shapePrev.centerY() + 2 * d, 7 * d, false)
        arrowTri(c, shapeNext.right - 20 * d, shapeNext.centerY() + 2 * d, 7 * d, true)
        text(c, ctl.shape.label.uppercase(), cardRect.centerX(), shapePrev.centerY() + 8 * d, 15f, Ui.CYAN, Paint.Align.CENTER, maxW = cardRect.width() - 150 * d)
    }

    /** Tiny top-down flight path (right-hand backhand): bottom = thrower, up = forward. */
    private fun drawFlightSketch(c: Canvas, disc: DiscModel, x0: Float, y0: Float, x1: Float, y1: Float) {
        p.style = Paint.Style.FILL; p.color = Color.argb(90, 6, 16, 40)
        c.drawRoundRect(x0, y0, x1, y1, 5 * d, 5 * d, p)
        val pts = disc.flightSketch(12)
        val cx = (x0 + x1) / 2f; val w = (x1 - x0) * 0.5f - 3 * d
        val h = (y1 - y0) - 8 * d
        path.reset()
        pts.forEachIndexed { i, (lat, fwd) ->
            val px = cx + (lat * 1.6f).coerceIn(-1f, 1f) * w
            val py = y1 - 4 * d - fwd * h
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.strokeCap = Paint.Cap.ROUND; p.color = Ui.GOLD
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawThrowPanel(c: Canvas) {
        val live = ctl.phase == Phase.AIMING
        val a = if (live) 255 else 120
        r.set(L.throwPanel.l, L.throwPanel.t, L.throwPanel.r, L.throwPanel.b)
        panel(c, r, 150, Ui.GOLD)
        // backhand / forehand switch
        r.set(handBtn)
        pill(c, r, Color.parseColor(if (ctl.forehand) "#D9792B" else "#3A68B0"), Color.parseColor(if (ctl.forehand) "#8F4A12" else "#14396F"))
        text(c, if (ctl.forehand) "FOREHAND" else "BACKHAND", r.centerX(), r.centerY() + 5 * d, 13f, Ui.WHITE, Paint.Align.CENTER)
    }

    private fun smallButton(c: Canvas, rect: RectF, label: String, alpha: Int) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(alpha, 20, 57, 111)
        c.drawRoundRect(rect, 8 * d, 8 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Color.argb(alpha, 255, 255, 255)
        c.drawRoundRect(rect, 8 * d, 8 * d, p)
        p.style = Paint.Style.FILL
        text(c, label, rect.centerX(), rect.centerY() + 6 * d, 16f, Ui.WHITE, Paint.Align.CENTER)
    }

    private fun drawAimButtons(c: Canvas) {
        val on = ctl.phase == Phase.AIMING
        val ang = angleMode
        stick.labels = if (ang) ANGLE_LABELS else AIM_LABELS
        stick.accent = if (ang) 0xFF6FE0FF.toInt() else 0xFFFFD84A.toInt()
        stick.draw(c, p, d, on || ctl.phase == Phase.RELEASE_ANGLE)
        if (ctl.isPutt) drawPuttStyleChip(c)
        // mode chip (tap toggles AIM / ANGLE) with the live readout of the active mode
        val col = if (ang) Ui.CYAN else Ui.GOLD
        p.style = Paint.Style.FILL; p.color = 0xCC061028.toInt()
        c.drawRoundRect(chipRect, 9 * d, 9 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = col
        c.drawRoundRect(chipRect, 9 * d, 9 * d, p)
        p.style = Paint.Style.FILL
        val tilt = ctl.releaseTiltDeg
        val tl = if (tilt > 0.5f) String.format("HYZ %.0f°", tilt) else if (tilt < -0.5f) String.format("ANHY %.0f°", -tilt) else "FLAT"
        val s = if (ang) tl + String.format(" NOSE %.0f°", ctl.launchAngleDeg)
                else String.format("H %+.0f° V %+.0f°", ctl.aimOffsetDeg, ctl.aimPitchDeg)
        text(c, (if (ang) "ANGLE " else "AIM ") + s, chipRect.centerX(), chipRect.centerY() + 4 * d, 11f, col, Paint.Align.CENTER, display = false, maxW = chipRect.width() - 6 * d)
    }

    /** STAND / JUMP putt-style chip: shown for putts, tappable while aiming. */
    private fun drawPuttStyleChip(c: Canvas) {
        val jump = ctl.puttStyle == ThrowTiming.PuttStyle.JUMP
        val col = if (jump) Ui.ORANGE else Ui.CYAN
        p.style = Paint.Style.FILL; p.color = 0xCC061028.toInt()
        c.drawRoundRect(puttChipRect, 9 * d, 9 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = col
        c.drawRoundRect(puttChipRect, 9 * d, 9 * d, p)
        p.style = Paint.Style.FILL
        text(c, if (jump) "JUMP" else "STAND", puttChipRect.centerX(), puttChipRect.centerY() + 4 * d, 11f, col, Paint.Align.CENTER, display = false, maxW = puttChipRect.width() - 6 * d)
    }

    // (4) Hot Shots style swing dial: power ring fills clockwise, inner arc holds the accuracy needle, glossy cap shows % power
    private fun drawMeter(c: Canvas) {
        val phase = ctl.phase
        val m = ctl.meter
        val M = L.meter
        val power = if (m != null && phase.isThrowStep) m.power else 0f
        val runPhase = phase == Phase.RUNUP || phase == Phase.JUMP
        val relPhase = phase == Phase.RELEASE_ANGLE
        val accPhase = runPhase || relPhase
        drawStepBar(c)
        val cx = M.centerXf(); val cy = (M.t + M.b) / 2f; val R = M.w / 2f

        // body: chunky outline, navy face, white rim
        p.style = Paint.Style.FILL; p.color = Color.argb(100, 0, 0, 0)
        c.drawCircle(cx, cy + 4 * d, R, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 7 * d; p.color = Color.argb(235, 6, 16, 40)
        c.drawCircle(cx, cy, R, p)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, cy - R, 0f, cy + R, Color.parseColor("#2A5CAB"), Color.parseColor("#0A1E44"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, R, p)
        p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = if (accPhase) Ui.ORANGE else Color.WHITE
        c.drawCircle(cx, cy, R, p)

        // power ring: green / yellow / red zones, faint preview everywhere, bright where filled
        val rr = R - 9 * d; val sw = 11 * d
        r.set(cx - rr, cy - rr, cx + rr, cy + rr)
        p.strokeWidth = sw; p.strokeCap = Paint.Cap.BUTT
        val zs = floatArrayOf(0f, 0.60f, 0.88f, 1f)
        val zc = intArrayOf(Color.rgb(47, 193, 106), Color.rgb(245, 197, 24), Color.rgb(232, 69, 60))
        if (runPhase && m != null) {
            // STEP 2 ring: a dark track with the run-up sweet zone (cyan) and the marker riding round it.
            p.color = Color.argb(200, 6, 16, 40); c.drawArc(r, 0f, 360f, false, p)
            val jmp = phase == Phase.JUMP
            val zc0 = if (jmp) ThrowTiming.JUMP_CENTER else ThrowTiming.RUNUP_CENTER; val zh = if (jmp) m.jumpHalfWidth else m.runupHalfWidth
            p.color = Color.argb(255, 60, 220, 255)
            c.drawArc(r, -90f + 360f * (zc0 - zh), 360f * zh * 2f, false, p)
            p.strokeWidth = 2 * d; p.color = Color.WHITE
            r2.set(cx - rr - sw / 2f, cy - rr - sw / 2f, cx + rr + sw / 2f, cy + rr + sw / 2f)
            c.drawArc(r2, -90f + 360f * (zc0 - zh), 360f * zh * 2f, false, p)
            p.strokeWidth = sw
        } else for (pass in 0..1) for (i in 0..2) {
            val a0 = zs[i]; val a1 = if (pass == 1) min(zs[i + 1], power) else zs[i + 1]
            if (a1 <= a0) continue
            val alpha = if (pass == 0) 70 else if (relPhase) 120 else 255
            p.color = Color.argb(alpha, Color.red(zc[i]), Color.green(zc[i]), Color.blue(zc[i]))
            c.drawArc(r, -90f + 360f * a0, 360f * (a1 - a0), false, p)
        }
        if (runPhase && m != null) {
            val ma = Math.toRadians((-90f + 360f * clamp(if (phase == Phase.JUMP) m.jumpMarker else m.runupMarker, 0f, 1f)).toDouble())
            val mc = cos(ma).toFloat(); val ms = sin(ma).toFloat()
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 6 * d; p.color = Color.argb(240, 6, 16, 40)
            c.drawLine(cx + mc * (rr - sw), cy + ms * (rr - sw), cx + mc * (rr + sw), cy + ms * (rr + sw), p)
            p.strokeWidth = 3 * d; p.color = if (m.inStartDelay) Color.argb(150, 255, 255, 255) else Color.WHITE
            c.drawLine(cx + mc * (rr - sw), cy + ms * (rr - sw), cx + mc * (rr + sw), cy + ms * (rr + sw), p)
            p.strokeCap = Paint.Cap.BUTT
        }
        // PERFECT segment: white-rimmed arc at the very end of the ring
        val pr = rr + sw / 2f + 1 * d; val pr2 = rr - sw / 2f - 1 * d
        p.strokeWidth = 2.5f * d; p.color = Color.WHITE
        r2.set(cx - pr, cy - pr, cx + pr, cy + pr); c.drawArc(r2, -90f + 360f * 0.97f, 360f * 0.03f, false, p)
        r2.set(cx - pr2, cy - pr2, cx + pr2, cy + pr2); c.drawArc(r2, -90f + 360f * 0.97f, 360f * 0.03f, false, p)
        p.strokeCap = Paint.Cap.BUTT
        // pin marker: the power to release at
        val pin = if (phase == Phase.POWER || phase == Phase.AIMING) ctl.pinPower else 0f
        val out = pin > 1.001f
        val pa = Math.toRadians((-90f + 360f * min(pin, 1f)).toDouble())
        val pc = cos(pa).toFloat(); val ps = sin(pa).toFloat()
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 6 * d; p.color = Color.argb(240, 6, 16, 40)
        c.drawLine(cx + pc * (rr - sw * 0.8f), cy + ps * (rr - sw * 0.8f), cx + pc * (rr + sw * 0.8f), cy + ps * (rr + sw * 0.8f), p)
        p.strokeWidth = 3 * d; p.color = if (out) Ui.ORANGE else Ui.GOLD
        c.drawLine(cx + pc * (rr - sw * 0.8f), cy + ps * (rr - sw * 0.8f), cx + pc * (rr + sw * 0.8f), cy + ps * (rr + sw * 0.8f), p)
        p.strokeCap = Paint.Cap.BUTT

        // accuracy arc (top, 120 degrees): gold sweet spot with the needle
        val ar = rr - sw / 2f - 8 * d
        r2.set(cx - ar, cy - ar, cx + ar, cy + ar)
        val a0 = 210f; val span = 120f
        p.style = Paint.Style.STROKE; p.strokeWidth = 7 * d; p.color = Color.argb(if (accPhase) 200 else 90, 6, 16, 40)
        c.drawArc(r2, a0, span, false, p)
        if (m != null && accPhase) {
            // STEP 3 arc: the narrow release-angle zone (gold). Left end = anhyzer, right end = hyzer.
            val half = m.releaseHalfWidth * span
            p.strokeWidth = 5 * d; p.color = Color.argb(if (relPhase) 255 else 90, 255, 214, 60)
            c.drawArc(r2, a0 + span / 2f - half, half * 2f, false, p)
        }
        if (relPhase) {
            val la = Math.toRadians(a0.toDouble()); val ra = Math.toRadians((a0 + span).toDouble())
            val lr = ar - 11 * d
            text(c, "ANHY", cx + cos(la).toFloat() * lr + 4 * d, cy + sin(la).toFloat() * lr + 12 * d, 7f, Ui.CYAN, Paint.Align.CENTER, display = false)
            text(c, "HYZ", cx + cos(ra).toFloat() * lr - 4 * d, cy + sin(ra).toFloat() * lr + 12 * d, 7f, Ui.CYAN, Paint.Align.CENTER, display = false)
        }
        // cap
        val capR = R * 0.40f
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - capR * 0.3f, cy - capR * 0.4f, capR * 1.4f, Color.parseColor("#FFE98A"), if (accPhase) Ui.ORANGE else Ui.GOLD_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy + 2 * d, capR, p)
        p.shader = null
        if (relPhase && m != null) {
            val na = Math.toRadians((a0 + clamp(m.releaseMarker, 0f, 1f) * span).toDouble())
            val nc = cos(na).toFloat(); val ns = sin(na).toFloat()
            p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = 6 * d; p.color = Color.argb(240, 6, 16, 40)
            c.drawLine(cx + nc * capR * 0.6f, cy + ns * capR * 0.6f, cx + nc * (ar + 5 * d), cy + ns * (ar + 5 * d), p)
            p.strokeWidth = 3 * d; p.color = Color.WHITE
            c.drawLine(cx + nc * capR * 0.6f, cy + ns * capR * 0.6f, cx + nc * (ar + 5 * d), cy + ns * (ar + 5 * d), p)
            p.strokeCap = Paint.Cap.BUTT
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.argb(235, 6, 16, 40)
        c.drawCircle(cx, cy + 2 * d, capR, p)
        p.style = Paint.Style.FILL; p.color = Color.argb(110, 255, 255, 255)
        r2.set(cx - capR * 0.7f, cy + 2 * d - capR * 0.85f, cx + capR * 0.7f, cy + 2 * d - capR * 0.1f)
        c.drawOval(r2, p)
        text(c, "${(power * 100).toInt()}%", cx, cy + 2 * d + 5 * d, 14f, Ui.NAVY, Paint.Align.CENTER, maxW = capR * 1.7f)

        if (out) {
            val K = L.pinChip
            toon(c, K, 8 * d, Color.parseColor("#FF9A3C"), Color.parseColor("#C2570F"))
            text(c, "PIN OUT OF RANGE", K.centerXf(), K.t + K.h / 2f + 4 * d, 9f, Ui.WHITE, Paint.Align.CENTER, display = false, maxW = K.w - 8 * d)
        }

        val hint = when (phase) {
            Phase.AIMING -> "HOLD, THEN RELEASE"
            Phase.POWER -> if (m != null && m.power >= 1f) "TOO LATE!" else "1 · RELEASE AT THE MARK"
            Phase.RUNUP -> if (m != null && m.inStartDelay) "2 · RUN UP..." else "2 · TAP IN THE CYAN"
            Phase.JUMP -> if (m != null && m.inStartDelay) "2 · CROUCH..." else "2 · JUMP IN THE CYAN"
            else -> if (m != null && m.inStartDelay) "3 · READY..." else "3 · TAP IN THE GOLD"
        }
        val H = L.meterHint
        toon(c, H, 9 * d, Color.parseColor("#1B3E7A"), Color.parseColor("#0A1E44"))
        text(c, hint, H.centerXf(), H.t + H.h / 2f + 4 * d, 11f, Ui.WHITE, Paint.Align.CENTER, display = false, maxW = H.w - 12 * d)
    }

    /** "1 POWER · 2 RUN-UP · 3 RELEASE" with the active step lit, finished steps green and the rest dim. */
    private fun drawStepBar(c: Canvas) {
        val B = HudLayout.stepBar(L.meter, d)
        val phase = ctl.phase
        val labels = HudLayout.stepLabels(ctl.isPutt, ctl.puttStyle == ThrowTiming.PuttStyle.JUMP)
        val n = labels.size
        val cur = when (phase) { Phase.POWER -> 0; Phase.RUNUP, Phase.JUMP -> 1; Phase.RELEASE_ANGLE -> n - 1; else -> -1 }
        val gap = 4 * d
        val pw = (B.w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val x0 = B.l + i * (pw + gap)
            val rc = HRect(x0, B.t, x0 + pw, B.b)
            val active = i == cur
            val done = cur > i
            val top = if (active) Ui.GOLD else if (done) Color.parseColor("#2FC16A") else Color.parseColor("#1B3E7A")
            val bot = if (active) Ui.GOLD_DARK else if (done) Color.parseColor("#1A7A42") else Color.parseColor("#0A1E44")
            toon(c, rc, 8 * d, top, bot)
            text(c, labels[i], rc.centerXf(), rc.t + rc.h / 2f + 3.5f * d, if (active) 9f else 8f,
                if (active) Ui.NAVY else if (done) Ui.WHITE else Color.argb(170, 255, 255, 255), Paint.Align.CENTER, display = false, maxW = pw - 4 * d)
        }
    }

    private fun drawIntro(c: Canvas, w: Float, h: Float) {
        val hole = ctl.hole
        val t = ctl.phaseTime
        val a = if (t < 0.4f) t / 0.4f else if (t > 2.6f) max(0f, (3.2f - t) / 0.6f) else 1f
        c.save()
        r.set(w / 2f - 220 * d, h * 0.32f, w / 2f + 220 * d, h * 0.32f + 96 * d)
        p.style = Paint.Style.FILL
        p.color = Color.argb((200 * a).toInt(), 8, 22, 48)
        c.drawRoundRect(r, 14 * d, 14 * d, p)
        p.color = Color.argb((255 * a).toInt(), 242, 183, 5)
        c.drawRect(r.left + 14 * d, r.top + 54 * d, r.right - 14 * d, r.top + 56 * d, p)
        val gold = Color.argb((255 * a).toInt(), 242, 183, 5)
        val white = Color.argb((255 * a).toInt(), 255, 255, 255)
        text(c, "HOLE ${hole.number}", w / 2f, r.top + 40 * d, 26f, gold, Paint.Align.CENTER)
        text(c, "${hole.name.uppercase()}  ·  PAR ${hole.par}  ·  ${hole.length.toInt()} m", w / 2f, r.top + 80 * d, 16f, white, Paint.Align.CENTER, display = false)
        c.restore()
    }

    private fun drawResult(c: Canvas, w: Float, h: Float) {
        val rep = ctl.report ?: return
        r.set(w / 2f - 190 * d, h * 0.22f, w / 2f + 190 * d, h * 0.22f + 92 * d)
        panel(c, r, 205)
        text(c, rep.headline.uppercase(), w / 2f, r.top + 40 * d, 26f, Ui.WHITE, Paint.Align.CENTER)
        val sub = "${rep.distance.toInt()} m thrown  ·  ${rep.toPin.toInt()} m to pin"
        text(c, sub, w / 2f, r.top + 64 * d, 14f, Ui.METAL, Paint.Align.CENTER, display = false)
        val tag = when {
            rep.perfectPower && rep.sweetSpot -> "PERFECT POWER + PERFECT ACCURACY"
            rep.perfectPower -> "PERFECT POWER"
            rep.sweetSpot -> "PERFECT ACCURACY"
            else -> ""
        }
        if (tag.isNotEmpty()) text(c, tag, w / 2f, r.top + 84 * d, 12f, Ui.GOLD, Paint.Align.CENTER, display = false)
    }

    private fun drawObChoice(c: Canvas, w: Float, h: Float) {
        val o = HudLayout.obChoice(w, h, d)
        r.set(o.banner.l, o.banner.t, o.banner.r, o.banner.b)
        panel(c, r, 225, Ui.ORANGE)
        text(c, "OUT OF BOUNDS +1", w / 2f, r.centerY() + 9 * d, 24f, Ui.ORANGE, Paint.Align.CENTER)
        val items = listOf(Triple(o.lieBtn, "PLAY FROM LIE", ctl.obLieToPin), Triple(o.rethrowBtn, "RETHROW", ctl.obRethrowToPin))
        for ((b, label, dist) in items) {
            r.set(b.l, b.t, b.r, b.b)
            panel(c, r, 235, Ui.GOLD)
            text(c, label, r.centerX(), r.top + 38 * d, 20f, Ui.WHITE, Paint.Align.CENTER, maxW = r.width() - 12 * d)
            text(c, "${dist.toInt()} m to pin", r.centerX(), r.top + 70 * d, 16f, Ui.GOLD, Paint.Align.CENTER, display = false, maxW = r.width() - 12 * d)
        }
    }

    private fun drawHoleDone(c: Canvas, w: Float, h: Float) {
        val hole = ctl.hole
        val name = ctl.headline
        val rel = ctl.strokes - hole.par
        val col = when {
            ctl.strokes == 1 || rel < 0 -> Ui.GREEN
            rel == 0 -> Ui.WHITE
            else -> Ui.ORANGE
        }
        r.set(w / 2f - 230 * d, h * 0.20f, w / 2f + 230 * d, h * 0.20f + 130 * d)
        panel(c, r, 225)
        text(c, name.uppercase(), w / 2f, r.top + 60 * d, 40f, col, Paint.Align.CENTER)
        text(c, "${ctl.strokes} strokes  ·  par ${hole.par}", w / 2f, r.top + 92 * d, 17f, Ui.METAL, Paint.Align.CENTER, display = false)
        if (ctl.phaseTime > 0.8f) text(c, "TAP TO CONTINUE", w / 2f, r.top + 118 * d, 13f, Ui.GOLD, Paint.Align.CENTER, display = false)
    }

    private fun drawPopups(c: Canvas, w: Float) {
        var y = 120f * d
        for (pop in pops.takeLast(5)) {
            val a = if (pop.age < 0.25f) pop.age / 0.25f else if (pop.age > 3.0f) max(0f, (3.6f - pop.age) / 0.6f) else 1f
            val color = when (pop.ev.kind) {
                ProgressEvent.Kind.STAT_UP -> Ui.GREEN
                ProgressEvent.Kind.LEVEL_UP -> Ui.GOLD
                ProgressEvent.Kind.UNLOCK -> Ui.CYAN
                else -> Ui.WHITE
            }
            val tw = 36 * d + pop.ev.text.length * 9.5f * d
            r.set(w / 2f - tw / 2f, y, w / 2f + tw / 2f, y + 30 * d)
            p.style = Paint.Style.FILL
            p.color = Color.argb((210 * a).toInt(), 8, 22, 48)
            c.drawRoundRect(r, 15 * d, 15 * d, p)
            p.color = Color.argb((255 * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            r2.set(r.left, r.top, r.left + 8 * d, r.bottom)
            c.drawRoundRect(r2, 4 * d, 4 * d, p)
            text(c, pop.ev.text, w / 2f, r.top + 21 * d, 15f, Color.argb((255 * a).toInt(), Color.red(color), Color.green(color), Color.blue(color)), Paint.Align.CENTER)
            y += 36 * d
        }
    }

    private fun drawScorecard(c: Canvas, w: Float, h: Float, final: Boolean) {
        val course = ctl.course
        val n = course.size
        // Long layouts (18 holes) shrink the columns to fit the screen.
        val cw = minOf(52 * d, (w * 0.96f - 98 * d) / (n + 1))
        val pw = 14 * d * 2 + 70 * d + cw * (n + 1)
        val ph = if (final) 250 * d else 170 * d
        r.set(w / 2f - pw / 2f, h / 2f - ph / 2f - (if (final) 30 * d else 0f), w / 2f + pw / 2f, h / 2f + ph / 2f - (if (final) 30 * d else 0f))
        panel(c, r, 235)
        val x0 = r.left + 14 * d + 70 * d
        text(c, "SCORECARD", r.left + 14 * d, r.top + 28 * d, 18f, Ui.GOLD)
        val y1 = r.top + 62 * d
        val y2 = y1 + 30 * d
        val y3 = y2 + 34 * d
        text(c, "HOLE", r.left + 14 * d, y1, 13f, Ui.METAL, display = false)
        text(c, "PAR", r.left + 14 * d, y2, 13f, Ui.METAL, display = false)
        text(c, "SCORE", r.left + 14 * d, y3, 13f, Ui.METAL, display = false)
        var par = 0; var tot = 0
        for (i in 0 until n) {
            val x = x0 + cw * i + cw / 2f
            text(c, "${i + 1}", x, y1, 14f, Ui.WHITE, Paint.Align.CENTER, display = false)
            text(c, "${course[i].par}", x, y2, 14f, Ui.METAL, Paint.Align.CENTER, display = false)
            val s = ctl.card[i]
            if (s > 0) {
                par += course[i].par; tot += s
                val col = if (s == 1 || s < course[i].par) Ui.GREEN else if (s == course[i].par) Ui.WHITE else Ui.ORANGE
                text(c, "$s", x, y3, 18f, col, Paint.Align.CENTER)
            }
        }
        val tx = x0 + cw * n + cw / 2f
        text(c, "TOT", tx, y1, 14f, Ui.GOLD, Paint.Align.CENTER, display = false)
        text(c, "${course.sumOf { it.par }}", tx, y2, 14f, Ui.METAL, Paint.Align.CENTER, display = false)
        text(c, "$tot", tx, y3, 18f, Ui.GOLD, Paint.Align.CENTER)
        if (!final) text(c, "TAP ANYWHERE TO CLOSE", r.centerX(), r.bottom - 12 * d, 12f, Ui.METAL, Paint.Align.CENTER, display = false)
    }

    private fun drawRoundDone(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(110, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, p)
        drawScorecard(c, w, h, true)
        val prof = ctl.profile
        val rel = ctl.card.sum() - ctl.totalPar
        val s = if (rel == 0) "EVEN PAR" else if (rel > 0) "+$rel" else "$rel"
        val y = h / 2f + 130 * d
        text(c, "ROUND COMPLETE  $s", w / 2f, y - 50 * d, 24f, Ui.WHITE, Paint.Align.CENTER)
        val statLine = Stat.values().joinToString("   ") { "${it.label.take(3).uppercase()} ${prof.stat(it)}" }
        text(c, "LEVEL ${prof.level}   ·   $statLine", w / 2f, y - 18 * d, 15f, Ui.GOLD, Paint.Align.CENTER, display = false)
        if (prof.unspentPoints > 0) text(c, "${prof.unspentPoints} POINTS TO SPEND ON YOUR GOLFER CARD", w / 2f, y + 6 * d, 14f, Ui.GREEN, Paint.Align.CENTER, display = false)
        if (ctl.phaseTime > 1f) text(c, "TAP TO FINISH", w / 2f, y + 34 * d, 14f, Ui.METAL, Paint.Align.CENTER, display = false)
    }

    private fun drawPause(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(150, 0, 0, 0)
        c.drawRect(0f, 0f, w, h, p)
        r.set(w / 2f - 160 * d, h / 2f - 150 * d, w / 2f + 160 * d, h / 2f + 112 * d)
        panel(c, r, 240)
        text(c, "PAUSED", w / 2f, r.top + 40 * d, 28f, Ui.GOLD, Paint.Align.CENTER)
        text(c, "SLOW MOTION", w / 2f, tsBtns[0].top - 5 * d, 10f, Color.parseColor("#CFE3FF"), Paint.Align.CENTER, display = false)
        val tsLabels = arrayOf("1x", "0.5x", "0.25x")
        for (i in 0..2) {
            val on = abs(timeScale - tsOptions[i]) < 0.01f
            if (on) pill(c, tsBtns[i], Color.parseColor("#FFD84A"), Ui.GOLD_DARK) else pill(c, tsBtns[i], Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
            text(c, tsLabels[i], tsBtns[i].centerX(), tsBtns[i].centerY() + 6 * d, 16f, if (on) Ui.NAVY else Ui.WHITE, Paint.Align.CENTER)
        }
        pill(c, resumeBtn, Color.parseColor("#FFD84A"), Ui.GOLD_DARK)
        text(c, "RESUME", resumeBtn.centerX(), resumeBtn.centerY() + 7 * d, 20f, Ui.NAVY, Paint.Align.CENTER)
        pill(c, quitBtn, Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
        text(c, "QUIT ROUND", quitBtn.centerX(), quitBtn.centerY() + 7 * d, 20f, Ui.WHITE, Paint.Align.CENTER)
    }
}
