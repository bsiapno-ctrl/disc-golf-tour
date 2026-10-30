package com.discgolf.game.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.discgolf.game.core.GameController
import com.discgolf.game.core.Phase
import com.discgolf.game.core.ProgressEvent
import com.discgolf.game.core.Stat
import com.discgolf.game.core.SwingMeter
import com.discgolf.game.core.clamp
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
    var showCard = false

    /** -1, 0, +1 while an aim button is held. Read by the activity every frame. */
    var touchAim = 0
        private set

    private class Pop(val ev: ProgressEvent, var age: Float = 0f)
    private val pops = ArrayList<Pop>()

    private val d = Ui.dp(context, 1f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val r2 = RectF()
    private val path = Path()

    // Hit areas (recomputed on layout)
    private val actionC = FloatArray(3)
    private val aimL = FloatArray(3)
    private val aimR = FloatArray(3)
    private val cardRect = RectF()
    private val discPrev = RectF()
    private val discNext = RectF()
    private val shapePrev = RectF()
    private val shapeNext = RectF()
    private val menuRect = RectF()
    private val cardBtn = RectF()
    private val resumeBtn = RectF()
    private val quitBtn = RectF()

    private var actionPointer = -1
    private var leftPointer = -1
    private var rightPointer = -1

    init { isFocusable = false }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val wf = w.toFloat(); val hf = h.toFloat()
        actionC[0] = wf - 96 * d; actionC[1] = hf - 100 * d; actionC[2] = 70 * d
        aimL[0] = 52 * d; aimL[1] = hf - 52 * d; aimL[2] = 36 * d
        aimR[0] = 52 * d + 84 * d; aimR[1] = hf - 52 * d; aimR[2] = 36 * d
        cardRect.set(12 * d, hf - 188 * d, 12 * d + 236 * d, hf - 100 * d)
        discPrev.set(cardRect.left, cardRect.top, cardRect.left + 40 * d, cardRect.bottom - 30 * d)
        discNext.set(cardRect.right - 40 * d, cardRect.top, cardRect.right, cardRect.bottom - 30 * d)
        shapePrev.set(cardRect.left, cardRect.bottom - 34 * d, cardRect.left + 70 * d, cardRect.bottom)
        shapeNext.set(cardRect.right - 70 * d, cardRect.bottom - 34 * d, cardRect.right, cardRect.bottom)
        menuRect.set(wf - 126 * d, 10 * d, wf - 12 * d, 46 * d)
        cardBtn.set(wf - 126 * d, 52 * d, wf - 12 * d, 88 * d)
        resumeBtn.set(wf / 2f - 110 * d, hf / 2f - 18 * d, wf / 2f + 110 * d, hf / 2f + 32 * d)
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
            MotionEvent.ACTION_CANCEL -> { up(actionPointer); up(leftPointer); up(rightPointer) }
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (paused) {
            if (resumeBtn.contains(x, y)) onResume()
            else if (quitBtn.contains(x, y)) onQuit()
            return
        }
        if (ctl.phase == Phase.ROUND_DONE) { if (ctl.phaseTime > 1.0f) onQuit(); return }
        if (menuRect.contains(x, y)) { paused = true; return }
        if (cardBtn.contains(x, y)) { showCard = !showCard; return }
        if (showCard) { showCard = false; return }

        when (ctl.phase) {
            Phase.AIMING -> {
                when {
                    inCircle(aimL, x, y) -> { leftPointer = id; touchAim = -1 }
                    inCircle(aimR, x, y) -> { rightPointer = id; touchAim = 1 }
                    discPrev.contains(x, y) -> onDisc(-1)
                    discNext.contains(x, y) -> onDisc(1)
                    shapePrev.contains(x, y) -> onShape(-1)
                    shapeNext.contains(x, y) -> onShape(1)
                    x > width * 0.62f && y > height * 0.3f && actionPointer < 0 -> { actionPointer = id; onAction(true) }
                }
            }
            Phase.ACCURACY -> onAction(true)          // tap anywhere
            Phase.INTRO, Phase.RESULT, Phase.HOLE_DONE -> onAction(true)
            else -> Unit
        }
    }

    private fun up(id: Int) {
        if (id < 0) return
        if (id == actionPointer) { actionPointer = -1; onAction(false) }
        if (id == leftPointer) { leftPointer = -1; touchAim = if (rightPointer >= 0) 1 else 0 }
        if (id == rightPointer) { rightPointer = -1; touchAim = if (leftPointer >= 0) -1 else 0 }
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private fun text(c: Canvas, s: String, x: Float, y: Float, sizeDp: Float, color: Int, align: Paint.Align = Paint.Align.LEFT, display: Boolean = true) {
        p.style = Paint.Style.FILL
        p.typeface = if (display) Ui.DISPLAY else Ui.BODY
        p.textSize = sizeDp * d
        p.textAlign = align
        p.setShadowLayer(3f * d, 0f, 1.5f * d, Color.argb(190, 0, 0, 0))
        p.color = color
        c.drawText(s, x, y, p)
        p.clearShadowLayer()
    }

    private fun panel(c: Canvas, rect: RectF, alpha: Int = 215, stroke: Int = Ui.GOLD) {
        p.style = Paint.Style.FILL
        p.color = Color.argb(alpha, 8, 22, 48)
        c.drawRoundRect(rect, 12 * d, 12 * d, p)
        // glossy top half
        r2.set(rect.left + 2 * d, rect.top + 2 * d, rect.right - 2 * d, rect.centerY())
        p.color = Color.argb(alpha / 6, 255, 255, 255)
        c.drawRoundRect(r2, 10 * d, 10 * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2 * d
        p.color = stroke
        c.drawRoundRect(rect, 12 * d, 12 * d, p)
        p.style = Paint.Style.FILL
    }

    private fun pill(c: Canvas, rect: RectF, top: Int, bottom: Int) {
        p.style = Paint.Style.FILL
        p.color = bottom
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, p)
        r2.set(rect.left, rect.top, rect.right, rect.centerY())
        p.color = top
        c.drawRoundRect(r2, rect.height() / 2f, rect.height() / 2f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Color.argb(220, 255, 255, 255)
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, p)
        p.style = Paint.Style.FILL
    }

    private fun roundButton(c: Canvas, cx: Float, cy: Float, rad: Float, label: String, active: Boolean, color: Int) {
        p.style = Paint.Style.FILL
        p.color = Ui.darken(color, 0.55f)
        c.drawCircle(cx, cy + 4 * d, rad, p)
        p.color = if (active) Ui.darken(color, 0.85f) else color
        c.drawCircle(cx, cy, rad, p)
        p.color = Color.argb(70, 255, 255, 255)
        c.drawCircle(cx, cy - rad * 0.28f, rad * 0.68f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Color.WHITE
        c.drawCircle(cx, cy, rad, p)
        p.style = Paint.Style.FILL
        text(c, label, cx, cy + 7 * d, 20f, Ui.NAVY, Paint.Align.CENTER)
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

        drawTopLeft(c)
        drawTopCentre(c, w)
        drawWind(c, w)
        drawMenuButtons(c)

        if (phase == Phase.AIMING || phase == Phase.CHARGING || phase == Phase.ACCURACY) {
            drawDiscCard(c)
            drawAimButtons(c)
            drawMeter(c, w, h)
            val label = when (phase) {
                Phase.AIMING -> "HOLD"
                Phase.CHARGING -> "RELEASE"
                else -> "TAP!"
            }
            roundButton(c, actionC[0], actionC[1], actionC[2], label, phase != Phase.AIMING, if (phase == Phase.ACCURACY) Ui.ORANGE else Ui.GOLD)
        }

        when (phase) {
            Phase.INTRO -> drawIntro(c, w, h)
            Phase.RESULT -> drawResult(c, w, h)
            Phase.HOLE_DONE -> drawHoleDone(c, w, h)
            else -> Unit
        }

        drawPopups(c, w)
        if (showCard) drawScorecard(c, w, h, false)
        if (paused) drawPause(c, w, h)
    }

    private fun drawTopLeft(c: Canvas) {
        val hole = ctl.hole
        r.set(12 * d, 10 * d, 12 * d + 250 * d, 10 * d + 84 * d)
        panel(c, r)
        text(c, "HOLE ${hole.number}  ·  PAR ${hole.par}", r.left + 14 * d, r.top + 26 * d, 17f, Ui.GOLD)
        text(c, hole.name.uppercase(), r.left + 14 * d, r.top + 46 * d, 12f, Ui.METAL, display = false)
        text(c, "${ctl.distanceToPin.toInt()} m", r.left + 14 * d, r.top + 74 * d, 28f, Ui.WHITE)
        text(c, "TO PIN", r.left + 112 * d, r.top + 74 * d, 12f, Ui.METAL, display = false)
        text(c, "THROW ${ctl.strokes + if (ctl.phase == Phase.HOLE_DONE || ctl.phase == Phase.RESULT) 0 else 1}", r.right - 12 * d, r.top + 74 * d, 14f, Ui.GOLD, Paint.Align.RIGHT)
    }

    private fun drawTopCentre(c: Canvas, w: Float) {
        r.set(w / 2f - 90 * d, 10 * d, w / 2f + 90 * d, 44 * d)
        panel(c, r, 190)
        val rel = ctl.relativeToPar()
        val s = if (rel == 0) "E" else if (rel > 0) "+$rel" else "$rel"
        text(c, "ROUND  $s", r.centerX(), r.top + 24 * d, 17f, if (rel < 0) Ui.GREEN else if (rel > 0) Ui.ORANGE else Ui.WHITE, Paint.Align.CENTER)
        text(c, "LV ${ctl.profile.level}  ${ctl.profile.name.uppercase()}", r.centerX(), r.bottom + 16 * d, 12f, Ui.METAL, Paint.Align.CENTER, display = false)
    }

    private fun drawWind(c: Canvas, w: Float) {
        val cx = w - 150 * d - 40 * d
        val cy = 52 * d
        // Move left of the menu buttons.
        val wx = ctl.wind.x; val wz = ctl.wind.z
        val speed = sqrt(wx * wx + wz * wz)
        val hd = ctl.aimHeading
        val fx = sin(hd); val fz = cos(hd)
        val rx = cos(hd); val rz = -sin(hd)
        val along = wx * fx + wz * fz
        val across = wx * rx + wz * rz
        val ang = atan2(across, along)   // 0 = blowing away from you (tailwind), clockwise positive
        p.style = Paint.Style.FILL
        p.color = Color.argb(215, 8, 22, 48)
        c.drawCircle(cx, cy, 36 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Ui.GOLD
        c.drawCircle(cx, cy, 36 * d, p)
        c.save()
        c.rotate(Math.toDegrees(ang.toDouble()).toFloat(), cx, cy)
        val len = (10f + min(speed, 6f) * 3.2f) * d
        p.style = Paint.Style.STROKE; p.strokeWidth = 4 * d; p.color = Ui.CYAN
        c.drawLine(cx, cy + len * 0.6f, cx, cy - len, p)
        triangle(c, cx, cy - len - 2 * d, 6 * d, true, Ui.CYAN)
        c.restore()
        text(c, String.format("%.1f m/s", speed), cx, cy + 56 * d, 12f, Ui.METAL, Paint.Align.CENTER, display = false)
        val kind = when {
            speed < 0.6f -> "CALM"
            Math.abs(along) > Math.abs(across) -> if (along > 0) "TAILWIND" else "HEADWIND"
            else -> if (across > 0) "FROM LEFT" else "FROM RIGHT"
        }
        text(c, kind, cx, cy + 70 * d, 11f, Ui.WHITE, Paint.Align.CENTER, display = false)
    }

    private fun drawMenuButtons(c: Canvas) {
        pill(c, menuRect, Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
        text(c, "MENU", menuRect.centerX(), menuRect.centerY() + 6 * d, 15f, Ui.WHITE, Paint.Align.CENTER)
        pill(c, cardBtn, Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
        text(c, "CARD", cardBtn.centerX(), cardBtn.centerY() + 6 * d, 15f, Ui.WHITE, Paint.Align.CENTER)
    }

    private fun drawDiscCard(c: Canvas) {
        val disc = ctl.disc
        panel(c, cardRect)
        // disc swatch
        p.style = Paint.Style.FILL
        p.color = (0xFF000000.toInt() or disc.colorRgb)
        c.drawCircle(cardRect.left + 64 * d, cardRect.top + 34 * d, 20 * d, p)
        p.color = Color.argb(90, 255, 255, 255)
        c.drawCircle(cardRect.left + 64 * d, cardRect.top + 28 * d, 12 * d, p)
        text(c, "◀", discPrev.centerX(), discPrev.centerY() + 8 * d, 20f, Ui.GOLD, Paint.Align.CENTER)
        text(c, "▶", discNext.centerX(), discNext.centerY() + 8 * d, 20f, Ui.GOLD, Paint.Align.CENTER)
        text(c, disc.displayName.uppercase(), cardRect.left + 94 * d, cardRect.top + 30 * d, 18f, Ui.WHITE)
        text(c, if (ctl.isPutt) "PUTT" else disc.kind.uppercase(), cardRect.left + 94 * d, cardRect.top + 48 * d, 12f, Ui.METAL, display = false)
        val nums = "${disc.speed} | ${disc.glide} | ${disc.turn.toInt()} | ${disc.fade.toInt()}"
        text(c, nums, cardRect.centerX(), cardRect.top + 68 * d, 15f, Ui.GOLD, Paint.Align.CENTER, display = false)
        text(c, "◀", shapePrev.left + 20 * d, shapePrev.centerY() + 6 * d, 16f, Ui.GOLD, Paint.Align.CENTER)
        text(c, "▶", shapeNext.right - 20 * d, shapeNext.centerY() + 6 * d, 16f, Ui.GOLD, Paint.Align.CENTER)
        text(c, ctl.shape.label.uppercase(), cardRect.centerX(), shapePrev.centerY() + 6 * d, 15f, Ui.CYAN, Paint.Align.CENTER)
    }

    private fun drawAimButtons(c: Canvas) {
        val a = ctl.phase == Phase.AIMING
        roundButton(c, aimL[0], aimL[1], aimL[2], "◀", touchAim < 0, if (a) Ui.METAL else Ui.METAL_DARK)
        roundButton(c, aimR[0], aimR[1], aimR[2], "▶", touchAim > 0, if (a) Ui.METAL else Ui.METAL_DARK)
    }

    private fun drawMeter(c: Canvas, w: Float, h: Float) {
        val phase = ctl.phase
        val m = ctl.meter
        val left = 270 * d
        val right = w - 190 * d
        val bw = right - left
        val power = if (m != null && (phase == Phase.CHARGING || phase == Phase.ACCURACY)) m.power else 0f

        // ---- accuracy bar (stacked above)
        val ay = h - 112 * d
        r.set(left, ay, right, ay + 24 * d)
        panel(c, r, if (phase == Phase.ACCURACY) 230 else 120, if (phase == Phase.ACCURACY) Ui.ORANGE else Ui.METAL_DARK)
        if (m != null && phase == Phase.ACCURACY) {
            val half = m.sweetHalfWidth * bw
            r2.set(left + bw / 2f - half, ay + 3 * d, left + bw / 2f + half, ay + 21 * d)
            p.style = Paint.Style.FILL; p.color = Ui.GOLD
            c.drawRoundRect(r2, 6 * d, 6 * d, p)
            val mx = left + clamp(m.marker, 0f, 1f) * bw
            triangle(c, mx, ay - 5 * d, 7 * d, false, Ui.WHITE)
            p.color = Ui.WHITE; p.strokeWidth = 3 * d; p.style = Paint.Style.STROKE
            c.drawLine(mx, ay, mx, ay + 24 * d, p)
            p.style = Paint.Style.FILL
        }
        text(c, "ACCURACY", left + 8 * d, ay - 4 * d, 11f, Ui.METAL, display = false)

        // ---- power bar
        val py = h - 72 * d
        r.set(left, py, right, py + 38 * d)
        panel(c, r, 230)
        val fill = bw * power
        // coloured zones, clipped to the fill
        c.save()
        c.clipRect(left, py, left + fill, py + 38 * d)
        p.style = Paint.Style.FILL
        val z1 = left + bw * 0.60f; val z2 = left + bw * 0.88f
        p.color = Color.parseColor("#2FC16A"); c.drawRect(left, py + 3 * d, z1, py + 35 * d, p)
        p.color = Color.parseColor("#F5C518"); c.drawRect(z1, py + 3 * d, z2, py + 35 * d, p)
        p.color = Color.parseColor("#E8453C"); c.drawRect(z2, py + 3 * d, right, py + 35 * d, p)
        p.color = Color.argb(70, 255, 255, 255)
        c.drawRect(left, py + 3 * d, right, py + 17 * d, p)
        c.restore()
        // perfect zone outline
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Color.WHITE
        c.drawRect(left + bw * 0.97f, py + 3 * d, right, py + 35 * d, p)
        p.style = Paint.Style.FILL
        // ticks
        for (i in 1..9) {
            val tx = left + bw * i / 10f
            p.color = Color.argb(90, 255, 255, 255)
            c.drawRect(tx - 1 * d, py + 28 * d, tx + 1 * d, py + 35 * d, p)
        }
        // pin marker
        val pin = ctl.pinPower
        val pinX = left + min(pin, 1f) * bw
        triangle(c, pinX, py - 6 * d, 7 * d, false, Ui.GOLD)
        text(c, if (pin > 1.001f) "PIN OUT OF RANGE" else "PIN", pinX, py - 16 * d, 12f, if (pin > 1.001f) Ui.ORANGE else Ui.GOLD, if (pin > 0.85f) Paint.Align.RIGHT else Paint.Align.CENTER, display = false)
        text(c, "POWER ${(power * 100).toInt()}%", left + 8 * d, py + 56 * d, 11f, Ui.METAL, display = false)

        val hint = when (phase) {
            Phase.AIMING -> "AIM, THEN PRESS & HOLD — RELEASE TO SET POWER"
            Phase.CHARGING -> if (m != null && m.power >= 1f) "TOO LATE!" else "RELEASE AT THE PIN MARK"
            else -> "TAP TO STOP THE MARKER IN THE GOLD"
        }
        text(c, hint, (left + right) / 2f, py + 56 * d, 12f, Ui.WHITE, Paint.Align.CENTER, display = false)
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
        r.set(w / 2f - 160 * d, h / 2f - 100 * d, w / 2f + 160 * d, h / 2f + 112 * d)
        panel(c, r, 240)
        text(c, "PAUSED", w / 2f, r.top + 44 * d, 28f, Ui.GOLD, Paint.Align.CENTER)
        pill(c, resumeBtn, Color.parseColor("#FFD84A"), Ui.GOLD_DARK)
        text(c, "RESUME", resumeBtn.centerX(), resumeBtn.centerY() + 7 * d, 20f, Ui.NAVY, Paint.Align.CENTER)
        pill(c, quitBtn, Color.parseColor("#3A68B0"), Color.parseColor("#14396F"))
        text(c, "QUIT ROUND", quitBtn.centerX(), quitBtn.centerY() + 7 * d, 20f, Ui.WHITE, Paint.Align.CENTER)
    }
}
