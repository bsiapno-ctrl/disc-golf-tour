package com.discgolf.game

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.discgolf.game.core.GameController
import com.discgolf.game.core.Phase
import com.discgolf.game.core.PlayerProfile
import com.discgolf.game.gfx.GameRenderer
import com.discgolf.game.ui.HudView
import kotlin.math.abs
import kotlin.math.min

/**
 * The round itself. One GLSurfaceView for the 3D scene, a transparent HUD view on top, and a frame loop on the
 * UI thread that feeds touch / gamepad / keyboard input into the [GameController].
 *
 * Controller layout:
 *   A / Space / right trigger ....... hold to charge power, release to lock it, press again to stop the accuracy marker
 *   Left stick or keyboard A/D ...... aim
 *   D-pad left/right or L1/R1 ....... change disc
 *   D-pad up/down ................... change shot shape (hyzer / flat / anhyzer)
 *   Y ............................... scorecard        Start / B / Back ... pause
 */
class GameActivity : Activity() {

    private lateinit var glView: GLSurfaceView
    private lateinit var hud: HudView
    private lateinit var ctl: GameController
    private lateinit var profile: PlayerProfile

    private var lastNanos = 0L
    private var looping = false
    private var stickX = 0f
    private var keyAim = 0
    private var aimHold = 0f
    private var triggerDown = false
    private var hatX = 0
    private var hatY = 0
    private var lastPhase = Phase.INTRO

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!looping) return
            val dt = if (lastNanos == 0L) 0f else ((frameTimeNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = frameTimeNanos
            tick(dt)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = SaveStore.load(this)
        if (saved == null) { finish(); return }
        profile = saved
        ctl = GameController(profile)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        goImmersive()

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 24, 0)
            setRenderer(GameRenderer(ctl))
            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
        hud = HudView(this, ctl).apply {
            onAction = { down -> handleAction(down) }
            onDisc = { ctl.nextDisc(it) }
            onShape = { ctl.nextShape(it) }
            onResume = { paused = false }
            onQuit = { endRound() }
        }
        val root = FrameLayout(this)
        root.addView(glView, FrameLayout.LayoutParams(-1, -1))
        root.addView(hud, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    override fun onResume() {
        super.onResume()
        if (!::glView.isInitialized) return
        glView.onResume()
        looping = true
        lastNanos = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    override fun onPause() {
        super.onPause()
        if (!::glView.isInitialized) return
        looping = false
        hud.paused = true
        glView.onPause()
        SaveStore.save(this, profile)
    }

    // ------------------------------------------------------------------
    // Frame
    // ------------------------------------------------------------------

    private fun tick(dt: Float) {
        if (!hud.paused) {
            val aim = (hud.touchAim + keyAim).toFloat() + stickX
            if (aim != 0f) {
                aimHold += dt
                val speed = 0.6f + min(aimHold, 1.5f) * 0.5f
                ctl.aim(aim.coerceIn(-1f, 1f) * speed * dt)
            } else aimHold = 0f

            ctl.update(dt)

            val ph = ctl.phase
            if (ph != lastPhase && (ph == Phase.HOLE_DONE || ph == Phase.ROUND_DONE)) SaveStore.save(this, profile)
            lastPhase = ph
        }
        hud.tick(dt)
        hud.invalidate()
        glView.requestRender()
    }

    private fun handleAction(down: Boolean) {
        if (hud.paused) return
        if (ctl.finished) { if (down && ctl.phaseTime > 1f) endRound(); return }
        if (down) ctl.pressAction() else ctl.releaseAction()
    }

    private fun endRound() {
        SaveStore.save(this, profile)
        finish()
    }

    // ------------------------------------------------------------------
    // Gamepad / keyboard
    // ------------------------------------------------------------------

    private fun fromPad(e: KeyEvent) = e.isFromSource(InputDevice.SOURCE_GAMEPAD) || e.isFromSource(InputDevice.SOURCE_JOYSTICK)

    override fun onKeyDown(keyCode: Int, e: KeyEvent): Boolean {
        if (!::hud.isInitialized) return super.onKeyDown(keyCode, e)
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (e.repeatCount == 0) handleAction(true)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_B -> { if (e.repeatCount == 0) hud.paused = !hud.paused; return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { if (e.repeatCount == 0) hud.showCard = !hud.showCard; return true }
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_Q -> { if (e.repeatCount == 0) ctl.nextDisc(-1); return true }
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_E -> { if (e.repeatCount == 0) ctl.nextDisc(1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (fromPad(e)) { if (e.repeatCount == 0) ctl.nextDisc(-1) } else keyAim = -1
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (fromPad(e)) { if (e.repeatCount == 0) ctl.nextDisc(1) } else keyAim = 1
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> { if (e.repeatCount == 0) ctl.nextShape(1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> { if (e.repeatCount == 0) ctl.nextShape(-1); return true }
            KeyEvent.KEYCODE_A -> { keyAim = -1; return true }
            KeyEvent.KEYCODE_D -> { keyAim = 1; return true }
        }
        return super.onKeyDown(keyCode, e)
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean {
        if (!::hud.isInitialized) return super.onKeyUp(keyCode, e)
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { handleAction(false); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (!fromPad(e)) keyAim = 0; return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (!fromPad(e)) keyAim = 0; return true }
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D -> { keyAim = 0; return true }
        }
        return super.onKeyUp(keyCode, e)
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (!::hud.isInitialized) return super.onGenericMotionEvent(e)
        if (e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && e.action == MotionEvent.ACTION_MOVE) {
            val x = e.getAxisValue(MotionEvent.AXIS_X)
            stickX = if (abs(x) < 0.18f) 0f else x

            val hx = Math.round(e.getAxisValue(MotionEvent.AXIS_HAT_X))
            val hy = Math.round(e.getAxisValue(MotionEvent.AXIS_HAT_Y))
            if (hx != hatX) { if (hx != 0) ctl.nextDisc(hx); hatX = hx }
            if (hy != hatY) { if (hy < 0) ctl.nextShape(1) else if (hy > 0) ctl.nextShape(-1); hatY = hy }

            val trig = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS))
            if (!triggerDown && trig > 0.6f) { triggerDown = true; handleAction(true) }
            else if (triggerDown && trig < 0.3f) { triggerDown = false; handleAction(false) }
            return true
        }
        return super.onGenericMotionEvent(e)
    }

    @Deprecated("Back toggles the pause menu")
    override fun onBackPressed() {
        if (!::hud.isInitialized) { super.onBackPressed(); return }
        hud.paused = !hud.paused
    }
}
