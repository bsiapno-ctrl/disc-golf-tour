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
    val cone = Shapes.cone()
    val sphere = Shapes.sphere()
    val disc = Shapes.disc()
    val ringThin = Shapes.torus(0.03f)
    val ringThick = Shapes.torus(0.12f)
}

/** How the golfer's body is posed this frame. */
class Pose(
    /** Throwing arm yaw in degrees: 0 = pointing forward, +110 = wound back to the throwing side, -40 = across the body. */
    var armYaw: Float = 20f,
    var bodyYaw: Float = 0f,
    /** 0..1 knee bend. */
    var crouch: Float = 0f,
)

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

        // Legs
        for (side in floatArrayOf(-1f, 1f)) {
            ms.push().translate(side * 0.11f * w, 0f, 0f)
            ms.push().translate(0f, 0.24f, 0f).scale(0.13f * w, 0.48f, 0.14f)
            p.drawRgb(m.cube, ms.top, skin, 0.95f); ms.pop()
            ms.push().translate(0f, 0.70f, 0f).scale(0.17f * w, 0.44f, 0.19f)
            p.drawRgb(m.cube, ms.top, shorts); ms.pop()
            ms.push().translate(0f, 0.04f, 0.05f).scale(0.15f * w, 0.09f, 0.30f)
            p.drawRgb(m.cube, ms.top, 0xF2F2F2); ms.pop()
            ms.pop()
        }

        // Torso group
        ms.push().translate(0f, 0.95f, 0f).rotate(pose.bodyYaw, 0f, 1f, 0f)
        ms.push().translate(0f, 0.29f, 0f).scale(0.44f * w, 0.58f, 0.25f)
        p.drawRgb(m.cube, ms.top, shirt); ms.pop()
        // belt-line shading
        ms.push().translate(0f, 0.04f, 0f).scale(0.45f * w, 0.07f, 0.26f)
        p.drawRgb(m.cube, ms.top, shorts, 1.1f); ms.pop()

        // Head
        ms.push().translate(0f, 0.76f, 0.01f)
        ms.push().scale(0.125f, 0.145f, 0.13f); p.drawRgb(m.sphere, ms.top, skin); ms.pop()
        drawHair(p, look.hairStyle, hair)
        drawHeadwear(p, look.headwear, shirt)
        ms.pop()

        // Off arm (hangs)
        ms.push().translate(-0.27f * w, 0.52f, 0f).rotate(5f, 0f, 0f, 1f)
        ms.push().translate(0f, -0.23f, 0f).scale(0.095f, 0.46f, 0.095f); p.drawRgb(m.cube, ms.top, skin, 0.95f); ms.pop()
        ms.push().translate(0f, -0.05f, 0f).scale(0.11f, 0.16f, 0.11f); p.drawRgb(m.cube, ms.top, shirt); ms.pop()
        ms.pop()

        // Throwing arm: swings in a horizontal plane around the shoulder
        ms.push().translate(0.27f * w, 0.52f, 0f)
        ms.rotate(pose.armYaw, 0f, 1f, 0f).rotate(28f, 1f, 0f, 0f)
        ms.push().translate(0f, 0f, 0.27f).scale(0.095f, 0.095f, 0.54f); p.drawRgb(m.cube, ms.top, skin, 0.95f); ms.pop()
        ms.push().translate(0f, 0f, 0.07f).scale(0.115f, 0.115f, 0.16f); p.drawRgb(m.cube, ms.top, shirt); ms.pop()
        if (holding != null) {
            ms.push().translate(0f, 0.03f, 0.60f).scale(0.17f, 0.03f, 0.17f)
            p.drawRgb(m.cyl, ms.top, holding.colorRgb); ms.pop()
        }
        ms.pop()

        ms.pop() // torso group
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
