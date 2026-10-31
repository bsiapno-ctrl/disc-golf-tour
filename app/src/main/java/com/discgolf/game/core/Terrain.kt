package com.discgolf.game.core

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The shape of the ground. Heights are metres above the course's zero level; X right, Z forward. */
interface Terrain {
    fun heightAt(x: Float, z: Float): Float

    /** Slope as (dh/dx, 0, dh/dz). Uphill in +x gives a positive x component. */
    fun gradientAt(x: Float, z: Float): Vec3

    /** Unit surface normal. */
    fun normalAt(x: Float, z: Float): Vec3 {
        val g = gradientAt(x, z)
        return Vec3(-g.x, 1f, -g.z).normalized()
    }
}

object FlatTerrain : Terrain {
    override fun heightAt(x: Float, z: Float) = 0f
    override fun gradientAt(x: Float, z: Float) = Vec3.ZERO
}

/**
 * Heights on a regular grid, interpolated bilinearly. Outside the grid the nearest edge height is used.
 * [heights] is row-major: index = iz * nx + ix, where x = x0 + ix * cell and z = z0 + iz * cell.
 */
class GridTerrain(
    val x0: Float,
    val z0: Float,
    val cell: Float,
    val nx: Int,
    val nz: Int,
    val heights: FloatArray,
) : Terrain {
    init {
        require(nx >= 2 && nz >= 2) { "terrain needs at least 2x2 samples" }
        require(heights.size == nx * nz) { "terrain has ${heights.size} samples, expected ${nx * nz}" }
    }

    val x1 get() = x0 + (nx - 1) * cell
    val z1 get() = z0 + (nz - 1) * cell

    private fun h(ix: Int, iz: Int) = heights[iz.coerceIn(0, nz - 1) * nx + ix.coerceIn(0, nx - 1)]

    override fun heightAt(x: Float, z: Float): Float {
        val fx = ((x - x0) / cell).coerceIn(0f, (nx - 1).toFloat())
        val fz = ((z - z0) / cell).coerceIn(0f, (nz - 1).toFloat())
        val ix = min(floor(fx).toInt(), nx - 2)
        val iz = min(floor(fz).toInt(), nz - 2)
        val tx = fx - ix
        val tz = fz - iz
        val a = h(ix, iz); val b = h(ix + 1, iz); val c = h(ix, iz + 1); val d = h(ix + 1, iz + 1)
        return (a * (1f - tx) + b * tx) * (1f - tz) + (c * (1f - tx) + d * tx) * tz
    }

    override fun gradientAt(x: Float, z: Float): Vec3 {
        val fx = ((x - x0) / cell).coerceIn(0f, (nx - 1).toFloat())
        val fz = ((z - z0) / cell).coerceIn(0f, (nz - 1).toFloat())
        val ix = min(floor(fx).toInt(), nx - 2)
        val iz = min(floor(fz).toInt(), nz - 2)
        val tx = fx - ix
        val tz = fz - iz
        val a = h(ix, iz); val b = h(ix + 1, iz); val c = h(ix, iz + 1); val d = h(ix + 1, iz + 1)
        val dx = ((b - a) * (1f - tz) + (d - c) * tz) / cell
        val dz = ((c - a) * (1f - tx) + (d - b) * tx) / cell
        return Vec3(dx, 0f, dz)
    }

    companion object {
        /** Sample any terrain onto a grid (used to save procedural terrain, or to resample a big one). */
        fun sample(src: Terrain, x0: Float, z0: Float, cell: Float, nx: Int, nz: Int): GridTerrain {
            val hs = FloatArray(nx * nz)
            for (iz in 0 until nz) for (ix in 0 until nx) hs[iz * nx + ix] = src.heightAt(x0 + ix * cell, z0 + iz * cell)
            return GridTerrain(x0, z0, cell, nx, nz, hs)
        }
    }
}

/** Smooth, repeatable value noise in [-1, 1]. No Android types, so it is the same on every device. */
class Noise2(private val seed: Int) {
    private fun hash(ix: Int, iz: Int): Float {
        var h = ix * 374761393 + iz * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFF) / 32767.5f - 1f
    }

    fun at(x: Float, z: Float): Float {
        val ix = floor(x).toInt(); val iz = floor(z).toInt()
        val fx = x - ix; val fz = z - iz
        val sx = fx * fx * (3f - 2f * fx); val sz = fz * fz * (3f - 2f * fz)
        val a = hash(ix, iz); val b = hash(ix + 1, iz); val c = hash(ix, iz + 1); val d = hash(ix + 1, iz + 1)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sz
    }
}

/** What a hole's elevation looks like. Used by the built-in course. */
class Relief(
    /** Height of the basket above the tee, in metres. Negative = downhill. */
    val climb: Float,
    /** Height of the rolling hills, in metres. */
    val hills: Float,
    /** Extra rise per metre away from the fairway edge, so the fairway sits in a shallow valley. */
    val bank: Float = 0.09f,
    /** Optional (fraction along the hole, extra height) bumps, e.g. a crest in the middle. */
    val bumps: List<Pair<Float, Float>> = emptyList(),
)

/** Builds the terrain for a hole from its centreline and a [Relief]. */
object TerrainGen {
    const val CELL = 2f
    const val MARGIN = 85f

    fun forHole(path: List<Vec3>, fairwayWidth: Float, ponds: List<Pond>, relief: Relief, seed: Int): GridTerrain {
        // Arc length along the path, for projecting grid points on to it.
        val cum = FloatArray(path.size)
        for (i in 1 until path.size) cum[i] = cum[i - 1] + path[i].distanceXZ(path[i - 1])
        val total = max(cum.last(), 1f)

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (p in path) { minX = min(minX, p.x); maxX = max(maxX, p.x); minZ = min(minZ, p.z); maxZ = max(maxZ, p.z) }
        val x0 = floor((minX - MARGIN) / CELL) * CELL
        val z0 = floor((minZ - MARGIN) / CELL) * CELL
        val nx = ((maxX + MARGIN - x0) / CELL).toInt() + 2
        val nz = ((maxZ + MARGIN - z0) / CELL).toInt() + 2

        val big = Noise2(seed)
        val small = Noise2(seed * 31 + 7)
        val tee = path.first(); val basket = path.last()

        fun profile(s: Float): Float {
            val f = (s / total).coerceIn(0f, 1f)
            var h = relief.climb * (f * f * (3f - 2f * f)) // eases in and out, so tee and basket sit on flatter ground
            for ((at, amount) in relief.bumps) {
                val d = (f - at) / 0.22f
                h += amount * max(0f, 1f - d * d)
            }
            return h
        }

        fun projectS(x: Float, z: Float): Pair<Float, Float> {
            var best = Float.MAX_VALUE; var bestS = 0f
            for (i in 0 until path.size - 1) {
                val a = path[i]; val b = path[i + 1]
                val abx = b.x - a.x; val abz = b.z - a.z
                val len2 = abx * abx + abz * abz
                val t = if (len2 < 1e-6f) 0f else clamp(((x - a.x) * abx + (z - a.z) * abz) / len2, 0f, 1f)
                val d = hypot(x - (a.x + abx * t), z - (a.z + abz * t))
                if (d < best) { best = d; bestS = cum[i] + t * (cum[i + 1] - cum[i]) }
            }
            return best to bestS
        }

        // Pond water levels sit a little under the profile height at the pond's centre.
        for (p in ponds) p.level = profile(projectS(p.cx, p.cz).second) - 0.35f

        val hs = FloatArray(nx * nz)
        for (iz in 0 until nz) for (ix in 0 until nx) {
            val x = x0 + ix * CELL; val z = z0 + iz * CELL
            val (d, s) = projectS(x, z)
            var h = profile(s)
            val edge = max(0f, d - fairwayWidth * 0.6f)
            h += relief.bank * min(edge, 40f)
            // Gentle on the fairway, livelier in the rough. Flat pads at the tee and the basket.
            val calm = lerp(0.45f, 1f, smoothstep(fairwayWidth * 0.5f, fairwayWidth * 1.6f, d))
            val padTee = smoothstep(5f, 16f, hypot(x - tee.x, z - tee.z))
            val padPin = smoothstep(7f, 20f, hypot(x - basket.x, z - basket.z))
            val n = big.at(x / 42f, z / 42f) * relief.hills + small.at(x / 15f, z / 15f) * relief.hills * 0.32f
            h += n * calm * padTee * padPin

            for (p in ponds) {
                val q = sqrt(((x - p.cx) / p.rx).let { it * it } + ((z - p.cz) / p.rz).let { it * it })
                if (q < 1.8f) h = max(h, p.level + 0.12f + 0.5f * smoothstep(1f, 1.8f, q)) // banks stay above the water
                if (q < 1f) h = lerp(h, p.level - 0.7f, smoothstep(1f, 0.6f, q))
            }
            hs[iz * nx + ix] = h
        }
        // Out at the edge of the map the ground levels off to one height, so the endless plane beyond joins it without a seam.
        val edgeLevel = (profile(0f) + profile(total)) / 2f
        val x1 = x0 + (nx - 1) * CELL; val z1 = z0 + (nz - 1) * CELL
        for (iz in 0 until nz) for (ix in 0 until nx) {
            val x = x0 + ix * CELL; val z = z0 + iz * CELL
            val dEdge = min(min(x - x0, x1 - x), min(z - z0, z1 - z))
            val fade = 1f - smoothstep(0f, 36f, dEdge)
            val i = iz * nx + ix
            hs[i] = lerp(hs[i], edgeLevel, fade)
        }
        return GridTerrain(x0, z0, CELL, nx, nz, hs)
    }
}
