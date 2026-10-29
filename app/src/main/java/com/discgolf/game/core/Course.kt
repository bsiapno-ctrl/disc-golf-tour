package com.discgolf.game.core

import java.util.Random
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class Tree(
    val x: Float,
    val z: Float,
    val height: Float,
    val trunkR: Float,
    val canopyR: Float,
    /** 0 = pine, 1 = round-leaf. Only affects how it looks. */
    val variant: Int,
)

/** Water hazard (an ellipse). Landing in it is a penalty stroke. */
class Pond(val cx: Float, val cz: Float, val rx: Float, val rz: Float) {
    fun contains(x: Float, z: Float): Boolean {
        val dx = (x - cx) / rx
        val dz = (z - cz) / rz
        return dx * dx + dz * dz < 1f
    }
}

enum class Surface { FAIRWAY, ROUGH, WATER, OUT_OF_BOUNDS }

class Hole(
    val number: Int,
    val name: String,
    val par: Int,
    /** Centreline from tee (first) to basket (last). */
    val path: List<Vec3>,
    val fairwayWidth: Float,
    val trees: List<Tree>,
    val ponds: List<Pond>,
) {
    val tee: Vec3 get() = path.first()
    val basket: Vec3 get() = path.last()

    private val cum = FloatArray(path.size)
    val length: Float

    init {
        var acc = 0f
        for (i in 1 until path.size) {
            acc += path[i].distanceXZ(path[i - 1])
            cum[i] = acc
        }
        length = acc
    }

    /** Distance to the centreline and arc-length position of the nearest point on it. */
    private fun project(x: Float, z: Float): Pair<Float, Float> {
        var best = Float.MAX_VALUE
        var bestS = 0f
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val abx = b.x - a.x; val abz = b.z - a.z
            val len2 = abx * abx + abz * abz
            val t = if (len2 < 1e-6f) 0f else clamp(((x - a.x) * abx + (z - a.z) * abz) / len2, 0f, 1f)
            val px = a.x + abx * t; val pz = a.z + abz * t
            val d = hypot(x - px, z - pz)
            if (d < best) { best = d; bestS = cum[i] + t * (cum[i + 1] - cum[i]) }
        }
        return best to bestS
    }

    fun distToPath(x: Float, z: Float) = project(x, z).first

    fun surfaceAt(x: Float, z: Float): Surface {
        for (p in ponds) if (p.contains(x, z)) return Surface.WATER
        val (d, s) = project(x, z)
        if (d > 62f || (s <= 0.01f && z < tee.z - 18f)) return Surface.OUT_OF_BOUNDS
        return if (d <= fairwayWidth / 2f) Surface.FAIRWAY else Surface.ROUGH
    }

    /** Point and unit direction on the centreline at arc length [s]. */
    fun pointAt(s: Float): Pair<Vec3, Vec3> {
        val ss = clamp(s, 0f, length)
        for (i in 0 until path.size - 1) {
            if (ss <= cum[i + 1] || i == path.size - 2) {
                val a = path[i]; val b = path[i + 1]
                val seg = max(cum[i + 1] - cum[i], 1e-4f)
                val t = clamp((ss - cum[i]) / seg, 0f, 1f)
                val dir = (b - a).flat().normalized()
                return Vec3(a.x + (b.x - a.x) * t, 0f, a.z + (b.z - a.z) * t) to dir
            }
        }
        return path.last() to Vec3.FORWARD
    }

    /** Where a sensible golfer would aim from [p]: a spot down the fairway, or the basket when close. */
    fun guideTarget(p: Vec3, lookAhead: Float = 55f): Vec3 {
        val toBasket = p.distanceXZ(basket)
        if (toBasket <= lookAhead) return basket
        val s = project(p.x, p.z).second
        val (pt, _) = pointAt(s + lookAhead)
        return pt
    }
}

/** Water, out of bounds, rough, trees, and the basket itself, from the disc's point of view. */
class HoleEnvironment(
    private val hole: Hole,
    override var wind: Vec3,
    private val rng: Random,
    private val putting01: Float,
) : FlightEnvironment {

    override fun frictionAt(p: Vec3): Float = when (hole.surfaceAt(p.x, p.z)) {
        Surface.FAIRWAY -> 1f
        Surface.ROUGH -> 1.9f
        else -> 3f
    }

    override fun resolve(flight: DiscFlight) {
        val p = flight.pos
        val onGround = flight.state == FlightState.ROLLING

        // Trees
        for (t in hole.trees) {
            val dx = p.x - t.x
            val dz = p.z - t.z
            if (abs(dx) > t.canopyR + 1f || abs(dz) > t.canopyR + 1f) continue
            if (p.y > t.height) continue
            val frac = p.y / t.height
            val r = if (frac < 0.42f) t.trunkR + 0.12f
            else t.canopyR * 0.8f * (1f - 0.6f * (frac - 0.42f) / 0.58f)
            val d = hypot(dx, dz)
            if (d < r && !(onGround && frac >= 0.42f)) {
                val n = if (d > 1e-3f) Vec3(dx / d, 0f, dz / d) else Vec3(1f, 0f, 0f)
                flight.pos = Vec3(t.x + n.x * r * 1.03f, p.y, t.z + n.z * r * 1.03f)
                flight.bounceOff(n, if (frac < 0.42f) 0.28f else 0.40f)
                flight.events += FlightEvent.TREE
                return
            }
        }

        // Water
        if (p.y <= 0.15f) {
            for (pond in hole.ponds) {
                if (pond.contains(p.x, p.z)) {
                    flight.state = FlightState.STOPPED
                    flight.vel = Vec3.ZERO
                    flight.events += FlightEvent.SPLASH
                    return
                }
            }
        }

        // Basket: pole, then chains. The chains get exactly one say per throw.
        val b = hole.basket
        val dx = p.x - b.x
        val dz = p.z - b.z
        val d = hypot(dx, dz)
        if (flight.state == FlightState.FLYING && d < RING_R && p.y > CHAIN_LOW && p.y < CHAIN_HIGH && !flight.basketChecked) {
            flight.basketChecked = true
            if (d < POLE_R) {
                flight.events += FlightEvent.POLE
                flight.bounceOff(Vec3(if (dx >= 0) 1f else -1f, 0f, 0.3f), 0.2f)
                return
            }
            val speed = flight.vel.length()
            // Slow discs drop into the chains; hard ones blow through or kick out. Putting skill helps.
            var chance = 0.97f - 0.035f * max(0f, speed - 3.5f)
            if (p.y > 1.2f) chance *= 0.9f
            chance += 0.20f * putting01
            chance = clamp(chance, 0.03f, 0.97f)
            if (rng.nextFloat() < chance) {
                flight.holed = true
                flight.pos = Vec3(b.x, 0.12f, b.z)
                flight.vel = Vec3.ZERO
            } else {
                flight.events += FlightEvent.CHAINS
                val n = if (d > 1e-3f) Vec3(dx / d, 0f, dz / d) else Vec3(0f, 0f, -1f)
                flight.pos = Vec3(b.x + n.x * (RING_R + 0.1f), p.y, b.z + n.z * (RING_R + 0.1f))
                flight.bounceOff(n, 0.18f)
            }
        }
    }

    companion object {
        const val POLE_R = 0.07f
        const val RING_R = 0.34f
        const val CHAIN_LOW = 0.50f
        const val CHAIN_HIGH = 1.42f
    }
}

/** Wind only. Used for caddie maths and the aim guide, where trees should not interfere. */
class WindOnly(override var wind: Vec3) : FlightEnvironment {
    override fun frictionAt(p: Vec3) = 1f
    override fun resolve(flight: DiscFlight) {}
}

object Courses {

    fun defaultCourse(): List<Hole> = listOf(
        build(1, "Opening Day", 3, listOf(0f to 0f, 0f to 80f), 16f, 0.55f, 0.15f),
        build(2, "Dogleg Right", 4, listOf(0f to 0f, 0f to 58f, 26f to 104f, 32f to 138f), 15f, 0.6f, 0.2f),
        build(3, "Water Carry", 3, listOf(0f to 0f, 0f to 92f), 18f, 0.5f, 0.15f,
            ponds = listOf(Pond(-10f, 56f, 9f, 14f))),
        build(4, "The Alley", 4, listOf(0f to 0f, 0f to 125f), 10f, 0.9f, 0.4f),
        build(5, "Left Hook", 4, listOf(0f to 0f, 0f to 54f, -30f to 98f, -36f to 132f), 14f, 0.65f, 0.25f),
        build(6, "Short & Sweet", 3, listOf(0f to 0f, 0f to 64f), 12f, 0.5f, 0.2f,
            extra = listOf(
                Tree(-2.6f, 44f, 10f, 0.3f, 2.4f, 0), Tree(2.6f, 44f, 11f, 0.3f, 2.5f, 0),
                Tree(-6f, 53f, 9f, 0.3f, 2.2f, 1), Tree(6.5f, 53f, 10f, 0.3f, 2.3f, 1))),
        build(7, "Island Green", 3, listOf(0f to 0f, 0f to 86f), 20f, 0.4f, 0.1f,
            ponds = listOf(Pond(-14f, 82f, 12f, 9f), Pond(14f, 82f, 12f, 9f))),
        build(8, "Big Bend", 5, listOf(0f to 0f, 0f to 70f, 34f to 118f, 38f to 172f, 18f to 200f), 16f, 0.7f, 0.25f),
        build(9, "Homestretch", 5, listOf(0f to 0f, 0f to 80f, -24f to 140f, -20f to 190f, 0f to 206f), 16f, 0.7f, 0.25f,
            ponds = listOf(Pond(-42f, 100f, 10f, 14f))),
    )

    private fun build(
        number: Int,
        name: String,
        par: Int,
        pts: List<Pair<Float, Float>>,
        width: Float,
        density: Float,
        guardianChance: Float,
        ponds: List<Pond> = emptyList(),
        extra: List<Tree> = emptyList(),
    ): Hole {
        val path = pts.map { Vec3(it.first, 0f, it.second) }
        val rnd = Random(1000L + number * 131L)
        val shell = Hole(number, name, par, path, width, emptyList(), ponds)
        val trees = ArrayList<Tree>(extra)

        fun tryAdd(x: Float, z: Float) {
            if (ponds.any { it.contains(x, z) }) return
            if (hypot(x - shell.basket.x, z - shell.basket.z) < 5f) return
            if (hypot(x - shell.tee.x, z - shell.tee.z) < 6f) return
            if (shell.distToPath(x, z) < width / 2f + 0.15f) return
            val variant = if (rnd.nextFloat() < 0.65f) 0 else 1
            trees += Tree(x, z, 7f + rnd.nextFloat() * 6.5f, 0.2f + rnd.nextFloat() * 0.14f, 1.7f + rnd.nextFloat() * 1.4f, variant)
        }

        var s = 7f
        while (s < shell.length - 3f) {
            val (pt, dir) = shell.pointAt(s)
            val right = Vec3(dir.z, 0f, -dir.x)
            for (side in intArrayOf(-1, 1)) {
                if (rnd.nextFloat() < density) {
                    val off = if (rnd.nextFloat() < guardianChance) width / 2f + 0.3f + rnd.nextFloat() * 1.2f
                    else width / 2f + 1.5f + rnd.nextFloat() * 6f
                    tryAdd(pt.x + right.x * side * off, pt.z + right.z * side * off)
                }
            }
            s += 3.2f + rnd.nextFloat() * 3f
        }
        // Deep woods beyond the fairway edge.
        repeat((shell.length * 0.7f).toInt()) {
            val (pt, dir) = shell.pointAt(rnd.nextFloat() * shell.length)
            val right = Vec3(dir.z, 0f, -dir.x)
            val side = if (rnd.nextBoolean()) 1 else -1
            val off = width / 2f + 9f + rnd.nextFloat() * 38f
            tryAdd(pt.x + right.x * side * off, pt.z + right.z * side * off)
        }
        return Hole(number, name, par, path, width, trees, ponds)
    }
}
