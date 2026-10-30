package com.discgolf.game.gfx

import com.discgolf.game.core.Appearance
import com.discgolf.game.core.DiscModel

/** What the renderer needs to be able to do for a model: draw a mesh with a transform and a colour. */
interface Painter {
    fun draw(mesh: Mesh, m: FloatArray, r: Float, g: Float, b: Float, a: Float = 1f, emissive: Float = 0f)
}

fun Painter.drawRgb(mesh: Mesh, m: FloatArray, rgb: Int, shade: Float = 1f, a: Float = 1f, emissive: Float = 0f) {
    val r = ((rgb shr 16) and 0xFF) / 255f * shade
    val g = ((rgb shr 8) and 0xFF) / 255f * shade
    val b = (rgb and 0xFF) / 255f * shade
    draw(mesh, m, r.coerceAtMost(1f), g.coerceAtMost(1f), b.coerceAtMost(1f), a, emissive)
}

class MeshSet {
    val cube = Shapes.cube()
    val cyl = Shapes.cylinder()
    val puck = Shapes.golfDisc()
    val cone = Shapes.cone()
    val sphere = Shapes.sphere()
    val disc = Shapes.disc()
    val ringThin = Shapes.torus(0.03f)
    val ringThick = Shapes.torus(0.12f)
}

/** How the golfer's body is posed this frame. Angles are in degrees. */
class Pose(
    /** Throwing arm (shoulder) yaw: 0 = pointing forward, +90 = out to the throwing side, +135 = reached back, -45 = across the body. */
    var armYaw: Float = 20f,
    /** Elbow bend: 0 = straight, 90 = forearm at a right angle. The forearm bends toward the body. */
    var elbow: Float = 40f,
    /** How far the throwing arm is lowered from horizontal. */
    var armPitch: Float = 28f,
    /** Shoulder turn. Positive coils the throwing shoulder backward. */
    var bodyYaw: Float = 0f,
    /** Hip turn, which leads the shoulders. */
    var hipYaw: Float = 0f,
    /** 0..1 knee bend. */
    var crouch: Float = 0f,
    /** 0..1 how far the lead foot has stepped forward. */
    var step: Float = 0f,
    /** Forward lean of the torso. */
    var lean: Float = 0f,
    /** Roll of the disc in the hand: positive = hyzer (throwing-side edge up). */
    var discTilt: Float = 0f,
    /** False once the disc has been let go. */
    var holdingDisc: Boolean = true,
)

/** Works out the pose for each moment of the throw, for a backhand or a forehand. */
object ThrowAnimation {
    private class Key(val t: Float, val armYaw: Float, val elbow: Float, val body: Float, val hip: Float, val step: Float, val lean: Float)

    private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** Standing ready: the disc held in front of the body. */
    fun address(o: Pose, forehand: Boolean, clock: Float) {
        val breathe = kotlin.math.sin(clock * 1.6f) * 1.5f
        o.armYaw = (if (forehand) 14f else 24f) + breathe; o.elbow = if (forehand) 72f else 42f
        o.armPitch = if (forehand) 20f else 28f
        o.bodyYaw = 0f; o.hipYaw = 0f; o.crouch = 0f; o.step = 0f; o.lean = 0f; o.holdingDisc = true
    }

    /** Winding up while the power bar fills: [power] 0..1. */
    fun windup(o: Pose, forehand: Boolean, power: Float) {
        val p = smooth(power)
        o.holdingDisc = true
        o.armPitch = if (forehand) 20f else 28f
        if (forehand) {
            o.armYaw = mix(14f, 26f, p); o.elbow = mix(72f, 102f, p)
            o.bodyYaw = mix(0f, 28f, p); o.hipYaw = mix(0f, 15f, p)
        } else {
            o.armYaw = mix(24f, 135f, p); o.elbow = mix(42f, 12f, p)
            o.bodyYaw = mix(0f, 40f, p); o.hipYaw = mix(0f, 22f, p)
        }
        o.crouch = 0.55f * p; o.step = 0f; o.lean = mix(0f, if (forehand) 5f else 9f, p)
    }

    /** The throw itself. [t] runs 0..1 over the whole swing; the disc leaves at [releaseT]. */
    fun swing(o: Pose, forehand: Boolean, power: Float, t: Float, releaseT: Float) {
        val start = Pose().also { windup(it, forehand, power) }
        val keys = if (forehand) listOf(
            Key(0f, start.armYaw, start.elbow, start.bodyYaw, start.hipYaw, 0f, start.lean),
            Key(0.20f, 36f, 108f, 6f, -8f, 0.7f, 8f),
            Key(releaseT, 64f, 16f, -20f, -24f, 1f, 10f),
            Key(0.70f, 96f, 30f, -32f, -30f, 1f, 8f),
            Key(1f, 102f, 42f, -30f, -26f, 1f, 4f),
        ) else listOf(
            Key(0f, start.armYaw, start.elbow, start.bodyYaw, start.hipYaw, 0f, start.lean),
            Key(0.20f, 62f, 84f, 6f, -10f, 0.7f, 10f),
            Key(releaseT, 4f, 8f, -22f, -26f, 1f, 11f),
            Key(0.70f, -46f, 44f, -36f, -32f, 1f, 8f),
            Key(1f, -52f, 56f, -32f, -28f, 1f, 4f),
        )
        var i = 0
        while (i < keys.size - 2 && t > keys[i + 1].t) i++
        val a = keys[i]; val b = keys[i + 1]
        val u = smooth((t - a.t) / (b.t - a.t))
        o.armYaw = mix(a.armYaw, b.armYaw, u); o.elbow = mix(a.elbow, b.elbow, u)
        o.bodyYaw = mix(a.body, b.body, u); o.hipYaw = mix(a.hip, b.hip, u)
        o.step = mix(a.step, b.step, u); o.lean = mix(a.lean, b.lean, u)
        o.armPitch = if (forehand) 20f else 28f
        o.crouch = mix(0.55f * smooth(power), 0.15f, smooth(t * 1.6f))
        o.holdingDisc = t < releaseT
    }
}

/**
 * A chunky low-poly golfer in the spirit of early-2000s sports games: a few boxes and spheres,
 * fully driven by the [Appearance] from the character creator.
 */
class GolferModel(private val ms0: MeshSet) {
    private val ms = MatStack()

    /** [x],[y],[z] feet position; [headingDeg] is the direction the golfer faces; [hand] is +1 right-handed, -1 left-handed. */
    fun draw(
        p: Painter, look: Appearance, hand: Float,
        x: Float, y: Float, z: Float, headingDeg: Float, pose: Pose, holding: DiscModel?,
    ) {
        val skin = Appearance.SKIN[look.skinTone.coerceIn(0, Appearance.SKIN_COUNT - 1)]
        val hair = Appearance.HAIR[look.hairColor.coerceIn(0, Appearance.HAIR_COLOR_COUNT - 1)]
        val shirt = Appearance.SHIRT[look.shirtColor.coerceIn(0, Appearance.SHIRT_COUNT - 1)]
        val shorts = Appearance.SHORTS[look.shortsColor.coerceIn(0, Appearance.SHORTS_COUNT - 1)]
        val w = when (look.build) { 0 -> 0.9f; 2 -> 1.15f; else -> 1f }
        val m = ms0

        ms.identity().translate(x, y, z).rotate(headingDeg, 0f, 1f, 0f)
        if (hand < 0f) ms.scale(-1f, 1f, 1f)
        val drop = -0.10f * pose.crouch
        ms.translate(0f, drop, 0f)

        // Legs: the hips turn, and the lead (non-throwing side) foot steps toward the target.
        ms.push().rotate(pose.hipYaw, 0f, 1f, 0f)
        for (side in floatArrayOf(-1f, 1f)) {
            val lead = if (side < 0f) pose.step else 0f
            ms.push().translate(side * 0.11f * w, 0f, 0.30f * lead).rotate(-18f * lead, 1f, 0f, 0f)
            ms.push().translate(0f, 0.24f, 0f).scale(0.13f * w, 0.48f, 0.14f)
            p.drawRgb(m.cube, ms.top, skin, 0.95f); ms.pop()
            ms.push().translate(0f, 0.70f, 0f).scale(0.17f * w, 0.44f, 0.19f)
            p.drawRgb(m.cube, ms.top, shorts); ms.pop()
            ms.push().translate(0f, 0.04f, 0.05f).scale(0.15f * w, 0.09f, 0.30f)
            p.drawRgb(m.cube, ms.top, 0xF2F2F2); ms.pop()
            ms.pop()
        }
        ms.pop()

        // Torso group: shoulders turn against the hips and lean into the throw.
        ms.push().translate(0f, 0.95f, 0f).rotate(pose.bodyYaw, 0f, 1f, 0f).rotate(pose.lean, 1f, 0f, 0f)
        ms.push().translate(0f, 0.29f, 0f).scale(0.44f * w, 0.58f, 0.25f)
        p.drawRgb(m.cube, ms.top, shirt); ms.pop()
        // belt-line shading
        ms.push().translate(0f, 0.04f, 0f).scale(0.45f * w, 0.07f, 0.26f)
        p.drawRgb(m.cube, ms.top, shorts, 1.1f); ms.pop()

        // Head: eyes stay on the target while the shoulders turn.
        ms.push().translate(0f, 0.76f, 0.01f).rotate(-pose.bodyYaw * 0.55f, 0f, 1f, 0f)
        ms.push().scale(0.125f, 0.145f, 0.13f); p.drawRgb(m.sphere, ms.top, skin); ms.pop()
        drawHair(p, look.hairStyle, hair)
        drawHeadwear(p, look.headwear, shirt)
        ms.pop()

        // Off arm: held out for balance, swinging opposite to the throwing arm.
        drawArm(p, -1f, w, 18f + 0.25f * pose.armYaw, 26f, 24f, skin, shirt, null, 0f)
        // Throwing arm
        drawArm(p, 1f, w, pose.armYaw, pose.elbow, pose.armPitch, skin, shirt, if (pose.holdingDisc) holding else null, pose.discTilt)

        ms.pop() // torso group
    }

    /** One arm: shoulder swing (yaw), lowered by [pitch], bent at the elbow, with an optional disc in the hand. */
    private fun drawArm(p: Painter, side: Float, w: Float, yaw: Float, elbow: Float, pitch: Float, skin: Int, shirt: Int, disc: DiscModel?, tilt: Float) {
        val m = ms0
        ms.push().translate(side * 0.27f * w, 0.52f, 0f)
        ms.rotate(yaw * side, 0f, 1f, 0f).rotate(pitch, 1f, 0f, 0f)
        // sleeve and upper arm
        ms.push().translate(0f, 0f, 0.07f).scale(0.115f, 0.115f, 0.16f); p.drawRgb(m.cube, ms.top, shirt); ms.pop()
        ms.push().translate(0f, 0f, 0.15f).scale(0.098f, 0.098f, 0.30f); p.drawRgb(m.cube, ms.top, skin, 0.95f); ms.pop()
        // elbow joint, then forearm bending toward the body
        ms.translate(0f, 0f, 0.30f).rotate(-elbow * side, 0f, 1f, 0f)
        ms.push().scale(0.105f, 0.105f, 0.105f); p.drawRgb(m.sphere, ms.top, skin, 0.95f); ms.pop()
        ms.push().translate(0f, 0f, 0.14f).scale(0.088f, 0.088f, 0.28f); p.drawRgb(m.cube, ms.top, skin, 0.95f); ms.pop()
        ms.push().translate(0f, 0f, 0.30f).scale(0.075f, 0.075f, 0.075f); p.drawRgb(m.sphere, ms.top, skin); ms.pop()
        if (disc != null) {
            // Held level, rolled by the release tilt so hyzer and anhyzer can be seen before the throw.
            ms.push().translate(0f, 0f, 0.40f).rotate(-pitch, 1f, 0f, 0f).rotate(tilt, 0f, 0f, 1f).translate(0f, 0.02f, 0f).scale(0.19f, 0.032f, 0.19f)
            p.drawRgb(m.cyl, ms.top, disc.colorRgb); ms.pop()
        }
        ms.pop()
    }

    private fun drawHair(p: Painter, style: Int, hair: Int) {
        val m = ms0
        when (style) {
            0 -> { ms.push().translate(0f, 0.045f, 0f).scale(0.133f, 0.12f, 0.138f); p.drawRgb(m.sphere, ms.top, hair); ms.pop() }
            1 -> {
                ms.push().translate(0f, 0.05f, -0.005f).scale(0.14f, 0.13f, 0.148f); p.drawRgb(m.sphere, ms.top, hair); ms.pop()
                ms.push().translate(0f, -0.01f, -0.07f).scale(0.14f, 0.12f, 0.08f); p.drawRgb(m.sphere, ms.top, hair); ms.pop()
            }
            2 -> {
                ms.push().translate(0f, 0.045f, 0f).scale(0.133f, 0.1f, 0.138f); p.drawRgb(m.sphere, ms.top, hair); ms.pop()
                for (k in 0 until 5) {
                    val a = k * 72f
                    ms.push().translate(0f, 0.12f, 0f).rotate(a, 0f, 1f, 0f).translate(0.05f, 0f, 0f)
                        .rotate(-25f, 0f, 0f, 1f).scale(0.045f, 0.13f, 0.045f)
                    p.drawRgb(m.cone, ms.top, hair); ms.pop()
                }
            }
            3 -> {
                ms.push().translate(0f, 0.05f, -0.005f).scale(0.145f, 0.13f, 0.15f); p.drawRgb(m.sphere, ms.top, hair); ms.pop()
                ms.push().translate(0f, -0.13f, -0.09f).scale(0.25f, 0.34f, 0.07f); p.drawRgb(m.cube, ms.top, hair); ms.pop()
            }
            4 -> {
                ms.push().translate(0f, 0.05f, -0.005f).scale(0.14f, 0.13f, 0.148f); p.drawRgb(m.sphere, ms.top, hair); ms.pop()
                ms.push().translate(0f, -0.02f, -0.15f).rotate(25f, 1f, 0f, 0f).scale(0.045f, 0.24f, 0.045f); p.drawRgb(m.cyl, ms.top, hair); ms.pop()
            }
            else -> Unit
        }
    }

    private fun drawHeadwear(p: Painter, style: Int, color: Int) {
        val m = ms0
        when (style) {
            1, 2 -> {
                ms.push().translate(0f, 0.07f, 0f).scale(0.142f, 0.1f, 0.148f); p.drawRgb(m.sphere, ms.top, color, 0.9f); ms.pop()
                val dir = if (style == 1) 1f else -1f
                ms.push().translate(0f, 0.045f, 0.15f * dir).scale(0.2f, 0.018f, 0.15f); p.drawRgb(m.cube, ms.top, color, 0.8f); ms.pop()
            }
            3 -> {
                ms.push().translate(0f, 0.065f, 0f).scale(0.142f, 0.06f, 0.148f); p.drawRgb(m.cyl, ms.top, color, 0.9f); ms.pop()
                ms.push().translate(0f, 0.065f, 0.15f).scale(0.2f, 0.016f, 0.14f); p.drawRgb(m.cube, ms.top, color, 0.8f); ms.pop()
            }
            else -> Unit
        }
    }
}
