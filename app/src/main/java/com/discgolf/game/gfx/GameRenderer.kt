package com.discgolf.game.gfx

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.discgolf.game.core.DiscFlight
import com.discgolf.game.core.FlightState
import com.discgolf.game.core.GameController
import com.discgolf.game.core.Hole
import com.discgolf.game.core.Phase
import com.discgolf.game.core.Vec3
import com.discgolf.game.core.clamp
import com.discgolf.game.core.headingToDir
import com.discgolf.game.core.lerp
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the course, the golfer and the disc. It only ever reads from [GameController]; all game logic
 * lives in the controller, which the activity updates once per frame.
 */
class GameRenderer(private val ctl: GameController) : GLSurfaceView.Renderer, Painter {

    private var prog = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLight = 0
    private var uCam = 0
    private var uFog = 0
    private var uStripe = 0
    private var uEmis = 0

    private lateinit var meshes: MeshSet
    private lateinit var golfer: GolferModel

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val ms = MatStack()
    private val pose = Pose()

    private var camPos = Vec3(0f, 6f, -10f)
    private var camLook = Vec3(0f, 1f, 10f)
    private var camReady = false
    private var lastNanos = 0L
    private var clock = 0f
    private var stripe = 0f

    private val trail = ArrayList<Vec3>()
    private var trailFor: DiscFlight? = null
    private var golferHeading = 0f

    // ------------------------------------------------------------------
    // GL lifecycle
    // ------------------------------------------------------------------

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(SKY_R, SKY_G, SKY_B, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        prog = linkProgram(VERTEX_SRC, FRAGMENT_SRC)
        uMvp = GLES30.glGetUniformLocation(prog, "uMVP")
        uModel = GLES30.glGetUniformLocation(prog, "uModel")
        uColor = GLES30.glGetUniformLocation(prog, "uColor")
        uLight = GLES30.glGetUniformLocation(prog, "uLightDir")
        uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
        uFog = GLES30.glGetUniformLocation(prog, "uFogColor")
        uStripe = GLES30.glGetUniformLocation(prog, "uStripe")
        uEmis = GLES30.glGetUniformLocation(prog, "uEmissive")

        meshes = MeshSet()
        golfer = GolferModel(meshes)
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(proj, 0, 56f, width.toFloat() / max(1, height).toFloat(), 0.5f, 700f)
        // The world is X-right / Z-forward, which is mirrored compared with OpenGL's right-handed eye space.
        // Flip X on screen so that +X (the golfer's right) really appears on the right.
        Matrix.scaleM(proj, 0, -1f, 1f, 1f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastNanos = now
        clock += dt

        updateCamera(dt)
        Matrix.setLookAtM(view, 0, camPos.x, camPos.y, camPos.z, camLook.x, camLook.y, camLook.z, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, 0.45f, 0.80f, 0.40f)
        GLES30.glUniform3f(uCam, camPos.x, camPos.y, camPos.z)
        GLES30.glUniform3f(uFog, SKY_R, SKY_G, SKY_B)

        val hole = ctl.hole
        val flight = ctl.flight
        updateTrail(flight)

        // ---- opaque pass
        GLES30.glDisable(GLES30.GL_BLEND)
        drawGround(hole)
        drawTrees(hole)
        drawBasket(hole)
        drawGolfer()
        drawDisc(flight)

        // ---- transparent pass
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        drawWater(hole)
        drawShadows(hole, flight)
        drawGuide()
        drawTrail()
        GLES30.glDepthMask(true)
    }

    // ------------------------------------------------------------------
    // Painter
    // ------------------------------------------------------------------

    override fun draw(mesh: Mesh, m: FloatArray, r: Float, g: Float, b: Float, a: Float, emissive: Float) {
        Matrix.multiplyMM(mvp, 0, vp, 0, m, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, m, 0)
        GLES30.glUniform4f(uColor, r, g, b, a)
        GLES30.glUniform1f(uEmis, emissive)
        GLES30.glUniform1f(uStripe, stripe)
        mesh.draw()
    }

    private fun xf(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, rotY: Float = 0f): FloatArray {
        ms.identity().translate(x, y, z)
        if (rotY != 0f) ms.rotate(rotY, 0f, 1f, 0f)
        return ms.scale(sx, sy, sz).top
    }

    // ------------------------------------------------------------------
    // Camera
    // ------------------------------------------------------------------

    private fun mix(a: Vec3, b: Vec3, t: Float) = Vec3(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.z, b.z, t))

    private fun updateCamera(dt: Float) {
        val h = ctl.hole
        val up = Vec3(0f, 1f, 0f)
        var tp: Vec3
        var tl: Vec3
        var k = 5f

        when (ctl.phase) {
            Phase.INTRO -> {
                val dir0 = (h.path[1] - h.path[0]).let { Vec3(it.x, 0f, it.z) }.normalized()
                val fwd = Vec3(h.basket.x - h.tee.x, 0f, h.basket.z - h.tee.z).normalized()
                val t0 = clamp(ctl.phaseTime / 3.0f, 0f, 1f)
                val t = t0 * t0 * (3f - 2f * t0)
                val a1 = h.basket + fwd * 10f + up * 16f
                val a2 = h.tee - dir0 * 5.6f + up * 2.4f
                val l2 = h.tee + dir0 * 16f + up * 1.0f
                tp = mix(a1, a2, t)
                tl = mix(h.basket, l2, t)
                k = 14f
            }
            Phase.AIMING, Phase.CHARGING, Phase.ACCURACY -> {
                val d = headingToDir(ctl.aimHeading)
                val right = Vec3(d.z, 0f, -d.x)
                tp = ctl.lie - d * 5.6f + right * 0.9f + up * 2.4f
                tl = ctl.lie + d * 16f + up * 1.0f
            }
            Phase.FLIGHT -> {
                val f = ctl.flight
                if (f == null) {
                    tp = ctl.lie + up * 3f; tl = ctl.lie
                } else {
                    var vd = Vec3(f.vel.x, 0f, f.vel.z).normalized()
                    if (vd == Vec3.ZERO) vd = headingToDir(ctl.aimHeading)
                    tp = f.pos - vd * 11f + up * max(2.4f, f.pos.y * 0.5f + 2.2f)
                    tl = f.pos + vd * 9f
                    k = 3.5f
                }
            }
            Phase.RESULT, Phase.HOLE_DONE -> {
                val base = ctl.flight?.pos ?: ctl.lie
                val d = Vec3(base.x - ctl.throwOrigin.x, 0f, base.z - ctl.throwOrigin.z).normalized()
                    .let { if (it == Vec3.ZERO) headingToDir(ctl.aimHeading) else it }
                tp = base - d * 7f + up * 3.2f
                tl = base + d * 5f
                k = 2.5f
            }
            Phase.ROUND_DONE -> {
                val a = clock * 0.3f
                tp = h.basket + Vec3(cos(a) * 14f, 6f, sin(a) * 14f)
                tl = h.basket
                k = 3f
            }
        }
        tp = Vec3(tp.x, max(tp.y, 0.9f), tp.z)

        if (!camReady) { camPos = tp; camLook = tl; camReady = true; return }
        val f = 1f - exp(-k * dt)
        camPos = mix(camPos, tp, f)
        camLook = mix(camLook, tl, f)
    }

    // ------------------------------------------------------------------
    // World
    // ------------------------------------------------------------------

    private fun segHeadingDeg(a: Vec3, b: Vec3) = Math.toDegrees(atan2((b.x - a.x).toDouble(), (b.z - a.z).toDouble())).toFloat()

    private fun drawGround(h: Hole) {
        val m = meshes
        stripe = 1f
        draw(m.cube, xf(0f, -0.05f, 0f, 3000f, 0.1f, 3000f), 0.24f, 0.45f, 0.20f)
        val path = h.path
        val w = h.fairwayWidth
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val len = a.distanceXZ(b)
            draw(m.cube, xf((a.x + b.x) / 2f, 0.01f, (a.z + b.z) / 2f, w, 0.02f, len, segHeadingDeg(a, b)), 0.36f, 0.64f, 0.29f)
        }
        for (p in path) draw(m.disc, xf(p.x, 0.0105f, p.z, w / 2f, 1f, w / 2f), 0.36f, 0.64f, 0.29f)
        // Putting green around the basket
        draw(m.disc, xf(h.basket.x, 0.013f, h.basket.z, 7f, 1f, 7f), 0.42f, 0.72f, 0.33f)
        stripe = 0f
        // Tee pad
        val dir0 = segHeadingDeg(path[0], path[1])
        draw(m.cube, xf(h.tee.x, 0.02f, h.tee.z, 2.4f, 0.04f, 5f, dir0), 0.30f, 0.32f, 0.36f)
    }

    private fun drawTrees(h: Hole) {
        val m = meshes
        for ((idx, t) in h.trees.withIndex()) {
            val dx = t.x - camPos.x; val dz = t.z - camPos.z
            if (dx * dx + dz * dz > 380f * 380f) continue
            val shade = 0.85f + ((idx * 37) % 30) / 100f
            draw(m.cyl, xf(t.x, 0f, t.z, t.trunkR, t.height * 0.45f, t.trunkR), 0.33f, 0.22f, 0.13f)
            if (t.variant == 0) {
                draw(m.cone, xf(t.x, t.height * 0.26f, t.z, t.canopyR, t.height * 0.42f, t.canopyR), 0.10f * shade, 0.34f * shade, 0.16f * shade)
                draw(m.cone, xf(t.x, t.height * 0.46f, t.z, t.canopyR * 0.78f, t.height * 0.40f, t.canopyR * 0.78f), 0.12f * shade, 0.38f * shade, 0.18f * shade)
                draw(m.cone, xf(t.x, t.height * 0.66f, t.z, t.canopyR * 0.55f, t.height * 0.36f, t.canopyR * 0.55f), 0.14f * shade, 0.42f * shade, 0.20f * shade)
            } else {
                draw(m.sphere, xf(t.x, t.height * 0.68f, t.z, t.canopyR * 0.95f, t.height * 0.30f, t.canopyR * 0.95f), 0.18f * shade, 0.45f * shade, 0.15f * shade)
                draw(m.sphere, xf(t.x + t.canopyR * 0.3f, t.height * 0.82f, t.z, t.canopyR * 0.65f, t.height * 0.2f, t.canopyR * 0.65f), 0.22f * shade, 0.52f * shade, 0.18f * shade)
            }
        }
    }

    private fun drawBasket(h: Hole) {
        val m = meshes
        val b = h.basket
        draw(m.cyl, xf(b.x, 0f, b.z, 0.30f, 0.04f, 0.30f), 0.25f, 0.27f, 0.30f)
        draw(m.cyl, xf(b.x, 0f, b.z, 0.035f, 1.40f, 0.035f), 0.70f, 0.72f, 0.76f)
        draw(m.cyl, xf(b.x, 1.30f, b.z, 0.055f, 0.14f, 0.055f), 0.98f, 0.80f, 0.10f)
        draw(m.ringThin, xf(b.x, 1.38f, b.z, 0.34f, 0.34f, 0.34f), 0.80f, 0.82f, 0.86f)
        draw(m.ringThin, xf(b.x, 0.62f, b.z, 0.30f, 0.30f, 0.30f), 0.80f, 0.82f, 0.86f)
        draw(m.cyl, xf(b.x, 0.55f, b.z, 0.30f, 0.03f, 0.30f), 0.55f, 0.58f, 0.62f)
        for (k in 0 until 12) {
            val a = (2.0 * PI * k / 12).toFloat()
            draw(m.cyl, xf(b.x + cos(a) * 0.32f, 0.62f, b.z + sin(a) * 0.32f, 0.008f, 0.76f, 0.008f), 0.85f, 0.86f, 0.88f)
        }
        // A tall flag so the pin can be found from the tee.
        draw(m.cyl, xf(b.x + 0.7f, 0f, b.z, 0.02f, 3.4f, 0.02f), 0.9f, 0.9f, 0.9f)
        draw(m.cube, xf(b.x + 0.7f, 3.15f, b.z + 0.3f, 0.03f, 0.32f, 0.6f), 0.95f, 0.35f, 0.15f)
    }

    private fun drawGolfer() {
        val phase = ctl.phase
        val m = ctl.meter
        val holding = when (phase) {
            Phase.INTRO, Phase.AIMING, Phase.CHARGING, Phase.ACCURACY -> ctl.disc
            else -> null
        }
        when (phase) {
            Phase.INTRO, Phase.AIMING -> {
                pose.armYaw = 22f + sin(clock * 1.6f) * 2f; pose.bodyYaw = 0f; pose.crouch = 0f
                golferHeading = ctl.aimHeading
            }
            Phase.CHARGING, Phase.ACCURACY -> {
                val p = m?.power ?: 0f
                pose.armYaw = lerp(30f, 125f, p); pose.bodyYaw = lerp(0f, 38f, p); pose.crouch = p * 0.6f
                golferHeading = ctl.aimHeading
            }
            Phase.FLIGHT -> {
                val p = m?.power ?: 0f
                val k = clamp(ctl.swingClock / 0.26f, 0f, 1f)
                val e = 1f - (1f - k) * (1f - k)
                pose.armYaw = lerp(lerp(30f, 125f, p), -45f, e)
                pose.bodyYaw = lerp(lerp(0f, 38f, p), -28f, e)
                pose.crouch = lerp(p * 0.6f, 0.1f, e)
            }
            Phase.HOLE_DONE -> {
                pose.armYaw = 165f + sin(clock * 9f) * 8f; pose.bodyYaw = 0f; pose.crouch = 0f
            }
            else -> { pose.armYaw = -45f; pose.bodyYaw = -28f; pose.crouch = 0.1f }
        }
        val at = when (phase) {
            Phase.INTRO, Phase.AIMING, Phase.CHARGING, Phase.ACCURACY -> ctl.lie
            else -> ctl.throwOrigin
        }
        val hd = Math.toDegrees(golferHeading.toDouble()).toFloat()
        golfer.draw(this, ctl.profile.look, ctl.profile.handedness.sign, at.x, at.z, hd, pose, holding)
    }

    private val basis = FloatArray(16)

    private fun drawDisc(f: DiscFlight?) {
        if (f == null) return
        if (ctl.phase == Phase.AIMING || ctl.phase == Phase.CHARGING || ctl.phase == Phase.ACCURACY || ctl.phase == Phase.INTRO) return
        val flying = f.state == FlightState.FLYING
        val n = if (flying) f.normal() else Vec3(0f, 1f, 0f)
        var f0 = Vec3(f.vel.x, 0f, f.vel.z).normalized()
        if (f0 == Vec3.ZERO) f0 = headingToDir(ctl.aimHeading)
        val right = n.cross(f0).normalized()
        val fwd = right.cross(n)
        Matrix.setIdentityM(basis, 0)
        basis[0] = right.x; basis[1] = right.y; basis[2] = right.z
        basis[4] = n.x; basis[5] = n.y; basis[6] = n.z
        basis[8] = fwd.x; basis[9] = fwd.y; basis[10] = fwd.z
        basis[12] = f.pos.x; basis[13] = f.pos.y + if (flying) 0f else 0.02f; basis[14] = f.pos.z
        val spin = if (flying) clock * 1700f else 0f
        val disc = ctl.disc
        // Drawn bigger than life so it reads from the chase camera.
        val big = if (flying) 2.6f else 1.6f
        val c = disc.colorRgb
        ms.load(basis).rotate(spin, 0f, 1f, 0f).translate(0f, -0.02f * big, 0f).scale(0.105f * big, 0.035f * big, 0.105f * big)
        drawRgb(meshes.cyl, ms.top, c)
        ms.load(basis).rotate(spin, 0f, 1f, 0f).translate(0f, 0.005f * big, 0f).scale(0.075f * big, 0.04f * big, 0.075f * big)
        drawRgb(meshes.cyl, ms.top, c, 1.2f)
    }

    // ---- transparent things

    private fun drawWater(h: Hole) {
        for (p in h.ponds) {
            draw(meshes.disc, xf(p.cx, 0.035f, p.cz, p.rx, 1f, p.rz), 0.10f, 0.42f, 0.70f, 0.82f)
            draw(meshes.disc, xf(p.cx, 0.04f, p.cz, p.rx * 0.82f, 1f, p.rz * 0.82f), 0.16f, 0.52f, 0.80f, 0.5f)
        }
    }

    private fun drawShadows(h: Hole, f: DiscFlight?) {
        for (t in h.trees) {
            val dx = t.x - camPos.x; val dz = t.z - camPos.z
            if (dx * dx + dz * dz > 200f * 200f) continue
            draw(meshes.disc, xf(t.x + 0.8f, 0.04f, t.z + 0.6f, t.canopyR * 0.95f, 1f, t.canopyR * 0.95f), 0f, 0f, 0f, 0.20f)
        }
        // Golfer
        val at = if (ctl.phase == Phase.INTRO || ctl.phase == Phase.AIMING || ctl.phase == Phase.CHARGING || ctl.phase == Phase.ACCURACY) ctl.lie else ctl.throwOrigin
        draw(meshes.disc, xf(at.x, 0.045f, at.z, 0.5f, 1f, 0.5f), 0f, 0f, 0f, 0.28f)
        if (f != null && ctl.phase != Phase.AIMING && ctl.phase != Phase.CHARGING && ctl.phase != Phase.ACCURACY) {
            val s = 0.28f + 0.02f * f.pos.y
            draw(meshes.disc, xf(f.pos.x, 0.05f, f.pos.z, s, 1f, s), 0f, 0f, 0f, 0.35f / (1f + 0.15f * f.pos.y))
        }
    }

    private fun drawGuide() {
        val ph = ctl.phase
        if (ph != Phase.AIMING && ph != Phase.CHARGING && ph != Phase.ACCURACY) return
        val g = ctl.guide
        if (g.size < 2) return
        val inRange = ctl.pinPower <= 1.0f
        val r = if (inRange) 0.25f else 1f
        val gr = if (inRange) 0.95f else 0.6f
        val b = if (inRange) 1f else 0.15f
        for (i in 0 until g.size - 1) {
            val p = g[i]
            val s = 0.14f + 0.002f * camPos.distanceTo(p)
            draw(meshes.sphere, xf(p.x, p.y, p.z, s, s, s), r, gr, b, 0.85f, 1f)
        }
        val e = g.last()
        draw(meshes.ringThin, xf(e.x, 0.06f, e.z, 1.3f, 1.3f, 1.3f), r, gr, b, 0.95f, 1f)
        draw(meshes.disc, xf(e.x, 0.055f, e.z, 1.2f, 1f, 1.2f), r, gr, b, 0.28f, 1f)
    }

    private fun updateTrail(f: DiscFlight?) {
        if (f !== trailFor) { trail.clear(); trailFor = f }
        if (f == null || f.state != FlightState.FLYING) return
        val last = trail.lastOrNull()
        if (last == null || last.distanceTo(f.pos) > 0.8f) {
            trail.add(f.pos)
            if (trail.size > 300) trail.removeAt(0)
        }
    }

    private fun drawTrail() {
        for (p in trail) draw(meshes.sphere, xf(p.x, p.y, p.z, 0.08f, 0.08f, 0.08f), 1f, 1f, 1f, 0.8f, 1f)
    }

    // ------------------------------------------------------------------
    // Shaders
    // ------------------------------------------------------------------

    private fun linkProgram(vs: String, fs: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Shader link failed: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Shader compile failed: " + GLES30.glGetShaderInfoLog(s))
        return s
    }

    companion object {
        const val SKY_R = 0.62f
        const val SKY_G = 0.78f
        const val SKY_B = 0.94f

        private const val VERTEX_SRC = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vNormal;
out vec3 vWorld;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = normalize(transpose(inverse(mat3(uModel))) * aNormal);
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

        private const val FRAGMENT_SRC = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
uniform vec4 uColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform vec3 uFogColor;
uniform float uStripe;
uniform float uEmissive;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vNormal);
    float diff = max(dot(n, normalize(uLightDir)), 0.0);
    vec3 sky = vec3(0.70, 0.80, 0.98);
    vec3 gnd = vec3(0.34, 0.38, 0.26);
    vec3 amb = mix(gnd, sky, n.y * 0.5 + 0.5) * 0.62;
    vec3 base = uColor.rgb;
    if (uStripe > 0.5) {
        float s = step(0.5, fract(vWorld.z / 5.0));
        base *= 1.0 - 0.07 * s;
    }
    vec3 lit = base * (amb + vec3(1.0, 0.96, 0.88) * diff * 0.78);
    lit = mix(lit, base, uEmissive);
    float d = length(vWorld - uCamPos);
    float fogAmt = smoothstep(140.0, 520.0, d);
    fragColor = vec4(mix(lit, uFogColor, fogAmt), uColor.a);
}
"""
    }
}
