package com.discgolf.game.gfx

import android.content.Context
import android.opengl.Matrix
import com.discgolf.game.core.Appearance
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The golfer drawn from a rigged character model with real throw animations. Which character you get follows the hair
 * style chosen in the creator, and the skin, hair, shirt and shorts colours are applied on top.
 */
class SkinnedGolfer(private val context: Context) {
    private val shader = SkinShader()
    private val models = HashMap<String, SkinnedModel?>()
    private val poseA: SkinnedModel.Pose? = null
    private val tmpWorld = FloatArray(16)
    private val mvp = FloatArray(16)
    private val handPos = FloatArray(3)
    private val elbowPos = FloatArray(3)
    private val basis = FloatArray(16)

    private class Roles(val shirt: Set<String>, val shorts: Set<String>, val hair: Set<String>)

    private val characters = listOf("Casual2", "Casual", "Beach", "Adventurer", "Punk", "Worker")   // by hair style
    private val roles = mapOf(
        "Casual2" to Roles(setOf("LightBrown"), setOf("LightBlue"), setOf("Hair")),
        "Casual" to Roles(setOf("Purple"), setOf("LightBlue"), setOf("Hair")),
        "Beach" to Roles(setOf("LightBrown"), setOf("Red_Dark"), setOf("Hair")),
        "Adventurer" to Roles(setOf("Green"), setOf("Brown"), setOf("Hair")),
        "Punk" to Roles(setOf("White"), setOf("LightBlue"), setOf("Red", "Red_Dark")),
        "Worker" to Roles(emptySet(), setOf("Brown"), setOf("Hair")),
    )

    fun nameFor(look: Appearance) = characters[look.hairStyle.coerceIn(0, characters.size - 1)]

    private fun model(name: String) = models.getOrPut(name) { SkinnedLoader.load(context, name) }

    /** True if the character for this look loaded, so the caller knows whether to fall back to the simple golfer. */
    fun available(look: Appearance) = model(nameFor(look)) != null

    private val pa = HashMap<SkinnedModel, SkinnedModel.Pose>()
    private val pb = HashMap<SkinnedModel, SkinnedModel.Pose>()
    private val pc = HashMap<SkinnedModel, SkinnedModel.Pose>()

    private fun rgb(c: Int, k: Float = 1f) = floatArrayOf(((c shr 16) and 0xFF) / 255f * k, ((c shr 8) and 0xFF) / 255f * k, (c and 0xFF) / 255f * k)

    /**
     * Draws the golfer. [swingTime] is seconds since the final tap, or negative before the throw. If [discOut] is given
     * and the disc is still in the hand, it receives the disc's world matrix and the function returns true.
     */
    fun draw(
        vp: FloatArray, sun: FloatArray, camX: Float, camY: Float, camZ: Float,
        look: Appearance, rightHanded: Boolean, x: Float, y: Float, z: Float, headingDeg: Float,
        forehand: Boolean, power: Float, swingTime: Float, holdUntil: Float, tiltDeg: Float, discOut: FloatArray,
    ): Boolean {
        val name = nameFor(look)
        val m = model(name) ?: return false
        val sfx = if (forehand) "FH" else "BH"
        val windup = m.clips["Windup_$sfx"] ?: return false
        val throwClip = m.clips["Throw_$sfx"] ?: return false
        val a = pa.getOrPut(m) { m.newPose() }; val b = pb.getOrPut(m) { m.newPose() }; val out = pc.getOrPut(m) { m.newPose() }

        m.sample(a, windup, power.coerceIn(0f, 1f) * windup.duration)
        if (swingTime >= 0f) {
            m.sample(b, throwClip, swingTime)
            val w = (swingTime / 0.12f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            m.blend(out, a, b, w)
        } else m.blend(out, a, a, 0f)
        m.solve(out)

        // Root: place, face the target, and mirror so the model's right arm is on the golfer's right in this world.
        val bw = when (look.build) { 0 -> 0.94f; 2 -> 1.08f; else -> 1f }
        Matrix.setIdentityM(tmpWorld, 0)
        Matrix.translateM(tmpWorld, 0, x, y, z)
        Matrix.rotateM(tmpWorld, 0, headingDeg, 0f, 1f, 0f)
        Matrix.scaleM(tmpWorld, 0, if (rightHanded) -bw else bw, 1f, bw)
        Matrix.multiplyMM(mvp, 0, vp, 0, tmpWorld, 0)

        val skin = rgb(Appearance.SKIN[look.skinTone.coerceIn(0, Appearance.SKIN_COUNT - 1)])
        val skinDark = rgb(Appearance.SKIN[look.skinTone.coerceIn(0, Appearance.SKIN_COUNT - 1)], 0.85f)
        val hairC = rgb(Appearance.HAIR[look.hairColor.coerceIn(0, Appearance.HAIR_COLOR_COUNT - 1)])
        val brow = rgb(Appearance.HAIR[look.hairColor.coerceIn(0, Appearance.HAIR_COLOR_COUNT - 1)], 0.6f)
        val shirtC = rgb(Appearance.SHIRT[look.shirtColor.coerceIn(0, Appearance.SHIRT_COUNT - 1)])
        val shortsC = rgb(Appearance.SHORTS[look.shortsColor.coerceIn(0, Appearance.SHORTS_COUNT - 1)])
        val r = roles[name]
        shader.begin(sun, camX, camY, camZ)
        shader.draw(m, mvp, tmpWorld) { mat ->
            when {
                mat == "Skin" -> skin
                mat == "Skin_Darker" -> skinDark
                mat == "Eyebrows" -> brow
                r != null && mat in r.hair -> hairC
                r != null && mat in r.shirt -> shirtC
                r != null && mat in r.shorts -> shortsC
                else -> null
            }
        }

        val holding = swingTime < 0f || swingTime < holdUntil
        if (!holding) return false
        val wrist = m.indexOf("Wrist.R"); val fore = m.indexOf("LowerArm.R")
        if (wrist < 0 || fore < 0) return false
        m.positionOf(wrist, handPos); m.positionOf(fore, elbowPos)
        var dx = handPos[0] - elbowPos[0]; var dy = handPos[1] - elbowPos[1]; var dz = handPos[2] - elbowPos[2]
        val dl = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
        dx /= dl; dy /= dl; dz /= dl
        // Disc sits just past the wrist, held level, facing along the forearm, then rolled by the release tilt.
        val cx = handPos[0] + dx * 0.10f; val cy = handPos[1] + dy * 0.10f + 0.02f; val cz = handPos[2] + dz * 0.10f
        var fx = dx; var fz = dz
        val fl = hypot(fx, fz)
        if (fl < 1e-3f) { fx = 0f; fz = 1f } else { fx /= fl; fz /= fl }
        Matrix.setIdentityM(basis, 0)
        basis[0] = fz; basis[1] = 0f; basis[2] = -fx
        basis[4] = 0f; basis[5] = 1f; basis[6] = 0f
        basis[8] = fx; basis[9] = 0f; basis[10] = fz
        basis[12] = cx; basis[13] = cy; basis[14] = cz
        // The model's left is +X, so a hyzer (throwing-side edge up) rolls the other way about the forward axis.
        Matrix.rotateM(basis, 0, -tiltDeg, 0f, 0f, 1f)
        Matrix.multiplyMM(discOut, 0, tmpWorld, 0, basis, 0)
        return true
    }

    fun release() { for (m in models.values) m?.release(); models.clear(); pa.clear(); pb.clear(); pc.clear() }
}
