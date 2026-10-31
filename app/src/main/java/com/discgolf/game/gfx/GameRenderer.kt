package com.discgolf.game.gfx

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.discgolf.game.core.DiscFlight
import com.discgolf.game.core.FlightState
import com.discgolf.game.core.GameController
import com.discgolf.game.core.Hole
import com.discgolf.game.core.Phase
import com.discgolf.game.core.Surface
import com.discgolf.game.core.Vec3
import com.discgolf.game.core.clamp
import com.discgolf.game.core.headingToDir
import com.discgolf.game.core.lerp
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the course, the golfer and the disc. It only ever reads from [GameController]; all game logic
 * lives in the controller, which the activity updates once per frame.
 */
class GameRenderer(private val context: android.content.Context, private val ctl: GameController) : GLSurfaceView.Renderer, Painter {

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
    private lateinit var sky: Sky
    private lateinit var terrainShader: TerrainShader
    private var terrain: TerrainRender? = null
    private val invVp = FloatArray(16)
    private val sun = floatArrayOf(0.45f, 0.80f, 0.40f)
    private lateinit var golfer: GolferModel
    private lateinit var modelShader: ModelShader
    private lateinit var skinned: SkinnedGolfer
    private val handDisc = FloatArray(16)
    private val lastHand = FloatArray(3)
    private lateinit var library: ModelLibrary
    private val decor = HashMap<Hole, List<Decor>>()

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
        GLES30.glClearColor(HORIZON_R, HORIZON_G, HORIZON_B, 1f)
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

        // A new GL context (first start, or coming back to the app) has none of the old GPU objects.
        terrain = null
        sky = Sky()
        terrainShader = TerrainShader()
        meshes = MeshSet()
        golfer = GolferModel(meshes)
        modelShader = ModelShader()
        skinned = SkinnedGolfer(context.applicationContext)
        library = ModelLibrary(context.applicationContext)
        decor.clear()
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        Matrix.perspectiveM(proj, 0, 50f, width.toFloat() / max(1, height).toFloat(), 0.5f, 700f)
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
        Matrix.invertM(invVp, 0, vp, 0)
        sky.draw(invVp, camPos.x, camPos.y, camPos.z, sun, clock)

        val hole = ctl.hole
        val ground = terrain?.takeIf { it.hole === hole } ?: run {
            terrain?.release()
            TerrainRender(hole).also { terrain = it }
        }
        GLES30.glDisable(GLES30.GL_BLEND)
        terrainShader.draw(ground, vp, camPos, sun)

        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, sun[0], sun[1], sun[2])
        GLES30.glUniform3f(uCam, camPos.x, camPos.y, camPos.z)
        GLES30.glUniform3f(uFog, HORIZON_R, HORIZON_G, HORIZON_B)

        val flight = ctl.flight
        updateTrail(flight)

        // ---- opaque pass
        drawGround(hole)
        drawBasket(hole)
        drawGolfer()
        drawDisc(flight)
        drawScenery(hole)

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
                tp = ctl.lie - d * 3.6f + right * 0.7f + up * 1.5f
                tl = ctl.lie + d * 14f + up * 0.9f
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
        tp = Vec3(tp.x, max(tp.y, h.groundAt(tp.x, tp.z) + 0.9f), tp.z)

        if (!camReady) { camPos = tp; camLook = tl; camReady = true; return }
        val f = 1f - exp(-k * dt)
        camPos = mix(camPos, tp, f)
        camLook = mix(camLook, tl, f)
    }

    // ------------------------------------------------------------------
    // World
    // ------------------------------------------------------------------

    private fun segHeadingDeg(a: Vec3, b: Vec3) = Math.toDegrees(atan2((b.x - a.x).toDouble(), (b.z - a.z).toDouble())).toFloat()

    /** The ground itself is the terrain mesh; this adds the tee pad on top of it. */
    private fun drawGround(h: Hole) {
        val dir0 = segHeadingDeg(h.path[0], h.path[1])
        draw(meshes.cube, xf(h.tee.x, h.tee.y + 0.02f, h.tee.z, 2.4f, 0.04f, 5f, dir0), 0.30f, 0.32f, 0.36f)
    }


    private class Decor(val model: String, val x: Float, val z: Float, val scale: Float, val rot: Float, val tint: Float)

    private val treeModels = arrayOf(
        "MapleTree_1", "MapleTree_2", "MapleTree_3", "MapleTree_4", "MapleTree_5",
        "BirchTree_1", "BirchTree_2", "BirchTree_3", "BirchTree_4", "BirchTree_5")

    /** Bushes, flower clumps and grass tufts along the edges of the hole, chosen once per hole. */
    private fun decorFor(h: Hole): List<Decor> = decor.getOrPut(h) {
        val rnd = java.util.Random(900L + h.number * 77L)
        val out = ArrayList<Decor>()
        val bushes = arrayOf("Bush", "Bush_Large", "Bush_Flowers", "Bush_Large_Flowers")
        val flowers = arrayOf("Flower_1_Clump", "Flower_2_Clump", "Flower_3_Clump", "Flower_4_Clump", "Flower_5_Clump")
        val n = (h.length / 3.2f).toInt().coerceIn(40, 260)
        for (i in 0 until n) {
            val (p, d) = h.pointAt(rnd.nextFloat() * h.length)
            val side = if (rnd.nextBoolean()) 1f else -1f
            val off = h.fairwayWidth * 0.5f + 1.5f + rnd.nextFloat() * 9f
            val x = p.x + d.z * off * side; val z = p.z - d.x * off * side
            if (h.surfaceAt(x, z) == Surface.WATER) continue
            if (h.trees.any { val dx = it.x - x; val dz = it.z - z; dx * dx + dz * dz < it.trunkR * it.trunkR * 9f }) continue
            val r = rnd.nextFloat()
            val model = if (r < 0.45f) bushes[rnd.nextInt(bushes.size)] else if (r < 0.80f) flowers[rnd.nextInt(flowers.size)] else if (r < 0.9f) "Grass_Large" else "Grass_Small"
            out += Decor(model, x, z, 0.9f + rnd.nextFloat() * 0.9f, rnd.nextFloat() * 360f, 0.85f + rnd.nextFloat() * 0.3f)
        }
        out
    }

    private fun drawModel(name: String, x: Float, y: Float, z: Float, scale: Float, rotY: Float, tint: Float, sway: Float) {
        val model = library.get(name) ?: return
        ms.identity().translate(x, y, z).rotate(rotY, 0f, 1f, 0f).scale(scale, scale, scale)
        val m = ms.top
        Matrix.multiplyMM(mvp, 0, vp, 0, m, 0)
        modelShader.draw(model, mvp, m, tint, tint, tint, sway)
    }

    private fun drawScenery(h: Hole) {
        GLES30.glDisable(GLES30.GL_BLEND)
        modelShader.begin(sun, camPos.x, camPos.y, camPos.z, clock)
        // Trees: the game rules know each tree's height and spread, so the picture is scaled to match.
        for ((idx, t) in h.trees.withIndex()) {
            val dx = t.x - camPos.x; val dz = t.z - camPos.z
            if (dx * dx + dz * dz > 380f * 380f) continue
            val name = treeModels[(idx * 7 + t.variant * 3) % treeModels.size]
            val model = library.get(name)
            val y0 = h.groundAt(t.x, t.z) - 0.1f
            val tint = 0.88f + ((idx * 37) % 30) / 100f
            if (model == null) { useMainProgram(); drawProceduralTree(h, idx, t); modelShader.begin(sun, camPos.x, camPos.y, camPos.z, clock); continue }
            val scale = t.height / model.height * 0.95f
            drawModel(name, t.x, y0, t.z, scale, (idx * 83 % 360).toFloat(), tint, 0.25f * scale)
        }
        for (d in decorFor(h)) {
            val dx = d.x - camPos.x; val dz = d.z - camPos.z
            if (dx * dx + dz * dz > 160f * 160f) continue
            drawModel(d.model, d.x, h.groundAt(d.x, d.z) - 0.02f, d.z, d.scale, d.rot, d.tint, 0.05f)
        }
        drawNearGrass(h)
        useMainProgram()
    }

    /** Grass tufts in the rough around the camera: a fixed grid, jittered by a hash, so they never flicker. */
    private fun drawNearGrass(h: Hole) {
        val cell = 1.7f
        val cx = Math.floor((camPos.x / cell).toDouble()).toInt(); val cz = Math.floor((camPos.z / cell).toDouble()).toInt()
        val fx = camLook.x - camPos.x; val fz = camLook.z - camPos.z
        val fl = sqrt(fx * fx + fz * fz).coerceAtLeast(0.01f)
        val r = 10
        for (ix in cx - r..cx + r) for (iz in cz - r..cz + r) {
            var k = ix * 73856093 xor iz * 19349663
            k = k xor (k ushr 13); k *= 1274126177; k = k xor (k ushr 16)
            if ((k and 0xFF) > 150) continue
            val x = (ix + ((k shr 8) and 0xFF) / 255f) * cell
            val z = (iz + ((k shr 16) and 0xFF) / 255f) * cell
            val dx = x - camPos.x; val dz = z - camPos.z
            val d2 = dx * dx + dz * dz
            if (d2 > 17f * 17f || (dx * fx + dz * fz) / fl < -2f) continue
            if (h.surfaceAt(x, z) == Surface.FAIRWAY || h.surfaceAt(x, z) == Surface.WATER) continue
            val tx = x - h.tee.x; val tz = z - h.tee.z
            if (tx * tx + tz * tz < 9f) continue
            val bx = x - h.basket.x; val bz = z - h.basket.z
            if (bx * bx + bz * bz < 56f) continue
            val name = if ((k ushr 24) and 1 == 0) "Grass_Large" else "Grass_Small"
            drawModel(name, x, h.groundAt(x, z) - 0.02f, z, 1.1f + ((k ushr 4) and 7) * 0.09f, ((k ushr 20) and 0xFF) * 1.4f, 0.9f + ((k ushr 10) and 15) / 60f, 0.03f)
        }
    }

    private fun useMainProgram() {
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, sun[0], sun[1], sun[2])
        GLES30.glUniform3f(uCam, camPos.x, camPos.y, camPos.z)
        GLES30.glUniform3f(uFog, HORIZON_R, HORIZON_G, HORIZON_B)
    }

    private fun drawProceduralTree(h: Hole, idx: Int, t: com.discgolf.game.core.Tree) {
        val m = meshes
        run {
            val y0 = h.groundAt(t.x, t.z) - 0.15f   // sunk a little so trunks never float on a slope
            val shade = 0.80f + ((idx * 37) % 40) / 100f
            val warm = ((idx * 53) % 17) / 100f      // a little hue variety from tree to tree
            val ht = t.height
            val cr = t.canopyR
            draw(m.cyl, xf(t.x, y0 - 0.05f, t.z, t.trunkR * 1.5f, ht * 0.12f, t.trunkR * 1.5f), 0.30f, 0.20f, 0.12f)   // root flare
            draw(m.cyl, xf(t.x, y0, t.z, t.trunkR, ht * 0.5f, t.trunkR), 0.36f, 0.24f, 0.14f)
            if (t.variant == 0) {
                for (k in 0 until 6) {
                    val f = k / 5f
                    val r = cr * (1.12f - 0.80f * f)
                    val sway = (((idx * 7 + k * 13) % 9) - 4) * 0.02f * cr
                    draw(m.cone, xf(t.x + sway, y0 + ht * (0.16f + 0.14f * k), t.z - sway, r, ht * 0.30f, r, k * 41f + idx * 13f),
                        (0.07f + 0.05f * f + warm * 0.25f) * shade, (0.27f + 0.10f * f) * shade, (0.13f + 0.04f * f) * shade)
                }
            } else {
                val blossom = t.variant == 2
                // Several overlapping puffs: darker underneath, lighter on top, like a painted canopy.
                val puffs = 6
                for (k in 0 until puffs) {
                    val a = k * 2.4f + idx
                    val ring = if (k == 0) 0f else 0.55f * cr
                    val px = t.x + cos(a) * ring; val pz = t.z + sin(a) * ring
                    val py = y0 + ht * (0.58f + 0.07f * (k % 3) + (if (k == 0) 0.10f else 0f))
                    val rr = cr * (if (k == 0) 0.95f else 0.62f)
                    val lit = 0.80f + 0.30f * (py - y0) / ht
                    if (blossom) draw(m.sphere, xf(px, py, pz, rr, rr * 0.8f, rr), (0.96f) * lit * shade.coerceAtMost(1f), 0.62f * lit, 0.74f * lit)
                    else draw(m.sphere, xf(px, py, pz, rr, rr * 0.8f, rr), (0.14f + warm) * lit * shade, 0.45f * lit * shade, 0.14f * lit * shade)
                }
            }
        }
    }

    private fun drawBasket(h: Hole) {
        val m = meshes
        val b = h.basket
        val y = b.y
        draw(m.cyl, xf(b.x, y + 0f, b.z, 0.30f, 0.04f, 0.30f), 0.25f, 0.27f, 0.30f)
        draw(m.cyl, xf(b.x, y + 0f, b.z, 0.035f, 1.40f, 0.035f), 0.70f, 0.72f, 0.76f)
        draw(m.cyl, xf(b.x, y + 1.30f, b.z, 0.055f, 0.14f, 0.055f), 0.98f, 0.80f, 0.10f)
        draw(m.ringThin, xf(b.x, y + 1.38f, b.z, 0.34f, 0.34f, 0.34f), 0.80f, 0.82f, 0.86f)
        draw(m.ringThin, xf(b.x, y + 0.62f, b.z, 0.30f, 0.30f, 0.30f), 0.80f, 0.82f, 0.86f)
        draw(m.cyl, xf(b.x, y + 0.55f, b.z, 0.30f, 0.03f, 0.30f), 0.55f, 0.58f, 0.62f)
        for (k in 0 until 12) {
            val a = (2.0 * PI * k / 12).toFloat()
            draw(m.cyl, xf(b.x + cos(a) * 0.32f, y + 0.62f, b.z + sin(a) * 0.32f, 0.008f, 0.76f, 0.008f), 0.85f, 0.86f, 0.88f)
        }
        // A tall flag so the pin can be found from the tee.
        draw(m.cyl, xf(b.x + 0.7f, y + 0f, b.z, 0.02f, 3.4f, 0.02f), 0.9f, 0.9f, 0.9f)
        draw(m.cube, xf(b.x + 0.7f, y + 3.15f, b.z + 0.3f, 0.03f, 0.32f, 0.6f), 0.95f, 0.35f, 0.15f)
    }

    private fun drawGolfer() {
        val phase = ctl.phase
        val m = ctl.meter
        val fh = ctl.forehand
        if (drawSkinnedGolfer()) return
        when (phase) {
            Phase.INTRO, Phase.AIMING -> {
                ThrowAnimation.address(pose, fh, clock)
                golferHeading = ctl.aimHeading
            }
            Phase.CHARGING, Phase.ACCURACY -> {
                ThrowAnimation.windup(pose, fh, m?.power ?: 0f)
                golferHeading = ctl.aimHeading
            }
            Phase.FLIGHT -> {
                val t = clamp(ctl.swingClock / GameController.SWING_TIME, 0f, 1f)
                ThrowAnimation.swing(pose, fh, m?.power ?: 0f, t, GameController.RELEASE_TIME / GameController.SWING_TIME)
            }
            Phase.HOLE_DONE -> {
                ThrowAnimation.address(pose, fh, clock)
                pose.armYaw = 165f + sin(clock * 9f) * 8f; pose.armPitch = -60f; pose.elbow = 10f
            }
            else -> {
                ThrowAnimation.swing(pose, fh, m?.power ?: 0f, 1f, GameController.RELEASE_TIME / GameController.SWING_TIME)
            }
        }
        pose.discTilt = (ctl.releaseTiltDeg * 0.7f).coerceIn(-45f, 45f)
        val holding = when (phase) {
            Phase.INTRO, Phase.AIMING, Phase.CHARGING, Phase.ACCURACY, Phase.FLIGHT -> ctl.disc
            else -> null
        }
        val at = when (phase) {
            Phase.INTRO, Phase.AIMING, Phase.CHARGING, Phase.ACCURACY -> ctl.lie
            else -> ctl.throwOrigin
        }
        val hd = Math.toDegrees(golferHeading.toDouble()).toFloat()
        golfer.draw(this, ctl.profile.look, ctl.profile.handedness.sign, at.x, at.y, at.z, hd, pose, holding)
    }

    /** The rigged, animated golfer. Returns false if it could not be loaded, so the simple golfer is drawn instead. */
    private fun drawSkinnedGolfer(): Boolean {
        val look = ctl.profile.look
        if (!skinned.available(look)) return false
        val phase = ctl.phase
        val power = when (phase) {
            Phase.CHARGING, Phase.ACCURACY, Phase.FLIGHT -> ctl.meter?.power ?: 0f
            else -> 0f
        }
        val swing = when (phase) {
            Phase.FLIGHT -> max(ctl.swingClock, 0f)
            Phase.RESULT, Phase.HOLE_DONE, Phase.ROUND_DONE -> 99f
            else -> -1f
        }
        val at = when (phase) {
            Phase.INTRO, Phase.AIMING, Phase.CHARGING, Phase.ACCURACY -> ctl.lie
            else -> ctl.throwOrigin
        }
        if (phase == Phase.INTRO || phase == Phase.AIMING) golferHeading = ctl.aimHeading
        if (phase == Phase.CHARGING || phase == Phase.ACCURACY) golferHeading = ctl.aimHeading
        val hd = Math.toDegrees(golferHeading.toDouble()).toFloat()
        val tilt = (ctl.releaseTiltDeg * 0.7f).coerceIn(-45f, 45f)
        val held = skinned.draw(vp, sun, camPos.x, camPos.y, camPos.z, look, ctl.profile.handedness.sign > 0f,
            at.x, at.y, at.z, hd, ctl.forehand, power, swing, GameController.RELEASE_TIME, tilt, handDisc)
        useMainProgram()
        if (held) {
            val c = ctl.disc.colorRgb
            lastHand[0] = handDisc[12]; lastHand[1] = handDisc[13]; lastHand[2] = handDisc[14]
            ms.load(handDisc).scale(0.105f, 0.105f, 0.105f)
            drawRgb(meshes.puck, ms.top, c)
        }
        return true
    }

    private val basis = FloatArray(16)

    private fun drawDisc(f: DiscFlight?) {
        if (f == null) return
        if (ctl.phase == Phase.AIMING || ctl.phase == Phase.CHARGING || ctl.phase == Phase.ACCURACY || ctl.phase == Phase.INTRO || !ctl.released) return
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
        // Hand-off: the disc leaves the fingertips and eases onto the flight path instead of popping.
        val e = (f.time / 0.12f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        basis[12] = lastHand[0] + (f.pos.x - lastHand[0]) * e
        basis[13] = lastHand[1] + (f.pos.y + (if (flying) 0f else 0.02f) - lastHand[1]) * e
        basis[14] = lastHand[2] + (f.pos.z - lastHand[2]) * e
        val spin = if (flying) clock * 1700f else 0f
        val disc = ctl.disc
        // Drawn bigger than life so it reads from the chase camera.
        val big = if (flying) 2.6f else 1.6f
        val c = disc.colorRgb
        ms.load(basis).rotate(spin, 0f, 1f, 0f).scale(0.105f * big, 0.105f * big, 0.105f * big)
        drawRgb(meshes.puck, ms.top, c)
    }

    // ---- transparent things

    private fun drawWater(h: Hole) {
        for (p in h.ponds) {
            draw(meshes.disc, xf(p.cx, p.level + 0.03f, p.cz, p.rx * 1.02f, 1f, p.rz * 1.02f), 0.10f, 0.40f, 0.62f, 0.55f)
            draw(meshes.disc, xf(p.cx, p.level + 0.04f, p.cz, p.rx * 0.97f, 1f, p.rz * 0.97f), 0.12f, 0.46f, 0.70f, 0.78f)
            draw(meshes.disc, xf(p.cx, p.level + 0.05f, p.cz, p.rx * 0.70f, 1f, p.rz * 0.70f), 0.20f, 0.56f, 0.80f, 0.40f)
        }
    }

    /** A flat disc lying on the ground, tilted to the slope there, [lift] metres above the surface. */
    private fun xfGround(h: Hole, x: Float, z: Float, lift: Float, sx: Float, sz: Float): FloatArray {
        val n = h.terrain.normalAt(x, z)
        ms.identity().translate(x, h.groundAt(x, z) + lift, z)
        // Rotate "up" onto the surface normal: axis = up x n.
        val ax = n.z; val az = -n.x
        val len = sqrt(ax * ax + az * az)
        if (len > 1e-4f) ms.rotate(Math.toDegrees(acos(n.y.coerceIn(-1f, 1f)).toDouble()).toFloat(), ax / len, 0f, az / len)
        return ms.scale(sx, 1f, sz).top
    }

    private fun drawShadows(h: Hole, f: DiscFlight?) {
        for (t in h.trees) {
            val dx = t.x - camPos.x; val dz = t.z - camPos.z
            if (dx * dx + dz * dz > 200f * 200f) continue
            draw(meshes.disc, xfGround(h, t.x + 0.8f, t.z + 0.6f, 0.05f, t.canopyR * 0.95f, t.canopyR * 0.95f), 0f, 0f, 0f, 0.20f)
        }
        // Golfer
        val at = if (ctl.phase == Phase.INTRO || ctl.phase == Phase.AIMING || ctl.phase == Phase.CHARGING || ctl.phase == Phase.ACCURACY) ctl.lie else ctl.throwOrigin
        draw(meshes.disc, xfGround(h, at.x, at.z, 0.05f, 0.5f, 0.5f), 0f, 0f, 0f, 0.28f)
        if (f != null && ctl.phase != Phase.AIMING && ctl.phase != Phase.CHARGING && ctl.phase != Phase.ACCURACY) {
            val above = max(0f, f.pos.y - h.groundAt(f.pos.x, f.pos.z))
            val s = 0.28f + 0.02f * above
            draw(meshes.disc, xfGround(h, f.pos.x, f.pos.z, 0.06f, s, s), 0f, 0f, 0f, 0.35f / (1f + 0.15f * above))
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
        val gy = ctl.hole.groundAt(e.x, e.z)
        draw(meshes.ringThin, xf(e.x, gy + 0.08f, e.z, 1.3f, 1.3f, 1.3f), r, gr, b, 0.95f, 1f)
        draw(meshes.disc, xf(e.x, gy + 0.07f, e.z, 1.2f, 1f, 1.2f), r, gr, b, 0.28f, 1f)
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

    companion object {
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
    float fogAmt = smoothstep($FOG_START, $FOG_END, d);
    fragColor = vec4(mix(lit, uFogColor, fogAmt), uColor.a);
}
"""
    }
}
