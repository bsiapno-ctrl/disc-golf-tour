package com.discgolf.game.gfx

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interleaved tree mesh: position (3), normal (3), then a colour code (3): x = light amount 0 (shaded) to 1 (sunlit),
 * y = 0 leaf / 1 bark, z = a small per-blob random number the shader can use for variety.
 */
class TreeMeshData(val vertices: FloatArray, val indices: ShortArray)

/**
 * Builds round, puffy cartoon trees: a thick tapering trunk, a few limbs, and a canopy that is a cluster of lumpy
 * spheres. Normals lean toward the canopy centre so the whole crown shades like one ball with softly bulging puffs,
 * and every vertex carries a light-to-dark gradient. Pure Kotlin (no Android), so the desktop preview can run it.
 *
 * The tree is 1 unit tall, standing at the origin; the caller scales it by the tree's height.
 */
object PuffyTreeGen {
    /** Species: 0-3 round oaks, 4 tall oval (birch), 5 pine, 6 palm, 7 round shrub, 8 tall slim spruce. */
    const val SEEDS = 9

    private class Rng(var s: Int) {
        fun next(): Float { s = s * 1664525 + 1013904223; return ((s ushr 8) and 0xFFFFFF) / 16777216f }
        fun range(a: Float, b: Float) = a + (b - a) * next()
    }

    private class Ico(val pos: FloatArray, val idx: IntArray)
    private val icos = HashMap<Int, Ico>()

    private fun ico(sub: Int): Ico = icos.getOrPut(sub) {
        val t = ((1.0 + sqrt(5.0)) / 2.0).toFloat()
        val p = ArrayList<FloatArray>()
        fun add(x: Float, y: Float, z: Float): Int { val l = sqrt(x * x + y * y + z * z); p += floatArrayOf(x / l, y / l, z / l); return p.size - 1 }
        add(-1f, t, 0f); add(1f, t, 0f); add(-1f, -t, 0f); add(1f, -t, 0f)
        add(0f, -1f, t); add(0f, 1f, t); add(0f, -1f, -t); add(0f, 1f, -t)
        add(t, 0f, -1f); add(t, 0f, 1f); add(-t, 0f, -1f); add(-t, 0f, 1f)
        var f = intArrayOf(0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1)
        repeat(sub) {
            val cache = HashMap<Long, Int>()
            fun mid(a: Int, b: Int): Int {
                val key = min(a, b).toLong() * 100000L + max(a, b)
                return cache.getOrPut(key) { add(p[a][0] + p[b][0], p[a][1] + p[b][1], p[a][2] + p[b][2]) }
            }
            val nf = IntArray(f.size * 4)
            var o = 0
            var i = 0
            while (i < f.size) {
                val a = f[i]; val b = f[i + 1]; val c = f[i + 2]
                val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
                for (v in intArrayOf(a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca)) nf[o++] = v
                i += 3
            }
            f = nf
        }
        val out = FloatArray(p.size * 3)
        for (i in p.indices) { out[i * 3] = p[i][0]; out[i * 3 + 1] = p[i][1]; out[i * 3 + 2] = p[i][2] }
        Ico(out, f)
    }

    private class Builder {
        val v = ArrayList<Float>()
        val i = ArrayList<Short>()
        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, g: Float, kind: Float, r: Float): Int {
            val l = max(sqrt(nx * nx + ny * ny + nz * nz), 1e-5f)
            v += x; v += y; v += z; v += nx / l; v += ny / l; v += nz / l; v += g; v += kind; v += r
            return v.size / 9 - 1
        }
        fun tri(a: Int, b: Int, c: Int) { i += a.toShort(); i += b.toShort(); i += c.toShort() }
        fun build() = TreeMeshData(v.toFloatArray(), i.toShortArray())
    }

    /** A tapered tube from [a] to [b] with radii [r0] to [r1]; ends are open (hidden in the ground or the canopy). */
    private fun tube(b: Builder, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, r0: Float, r1: Float, sides: Int, g0: Float, g1: Float, rnd: Float) {
        var dx = bx - ax; var dy = by - ay; var dz = bz - az
        val dl = sqrt(dx * dx + dy * dy + dz * dz); dx /= dl; dy /= dl; dz /= dl
        // Any vector not parallel to the axis gives two perpendiculars.
        var ux = 0f; var uy = 0f; var uz = 1f
        if (abs(dz) > 0.9f) { ux = 1f; uz = 0f }
        var sx = dy * uz - dz * uy; var sy = dz * ux - dx * uz; var sz = dx * uy - dy * ux
        val sl = sqrt(sx * sx + sy * sy + sz * sz); sx /= sl; sy /= sl; sz /= sl
        val tx = dy * sz - dz * sy; val ty = dz * sx - dx * sz; val tz = dx * sy - dy * sx
        val base = b.v.size / 9
        for (ring in 0..1) {
            val r = if (ring == 0) r0 else r1
            val cx = if (ring == 0) ax else bx; val cy = if (ring == 0) ay else by; val cz = if (ring == 0) az else bz
            for (k in 0..sides) {
                val a = k.toFloat() / sides * 2f * PI.toFloat()
                val c = cos(a); val s = sin(a)
                val nx = sx * c + tx * s; val ny = sy * c + ty * s; val nz = sz * c + tz * s
                // Bark: lighter on the side facing the sun (+x,+y), darker low down.
                val lit = 0.5f + 0.35f * (nx * 0.6f + nz * 0.3f)
                val g = (g0 + (g1 - g0) * ring) * 0.55f + lit * 0.45f
                b.vertex(cx + nx * r, cy + ny * r, cz + nz * r, nx, ny + 0.15f * (1f - ring), nz, g, 1f, rnd)
            }
        }
        for (k in 0 until sides) {
            val a = base + k; val c = base + sides + 1 + k
            b.tri(a, c, a + 1); b.tri(a + 1, c, c + 1)
        }
    }

    /**
     * One lumpy ellipsoid [rx] x [ry] x [rz], pitched about z then yawed about y. [sub] picks the icosphere detail
     * (0 = 20 triangles, 1 = 80, 2 = 320). [own] is how much of the blob's own normal is kept (the rest leans away from
     * the crown centre), so a low value shades the crown as one ball and a high one shows every leaf cluster.
     */
    private fun blob(b: Builder, cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float, yaw: Float, pitch: Float,
                     canopy: FloatArray, sub: Int, rnd: Rng, shadeBias: Float, own: Float, lumps: Float) {
        val m = ico(sub)
        val p1 = rnd.range(0f, 6.28f); val p2 = rnd.range(0f, 6.28f); val p3 = rnd.range(0f, 6.28f)
        val id = rnd.next()
        val cp = cos(pitch); val sp = sin(pitch); val cw = cos(yaw); val sw = sin(yaw)
        var ox = cx - canopy[0]; var oy = cy - canopy[1]; var oz = cz - canopy[2]
        val ol = max(sqrt(ox * ox + oy * oy + oz * oz), 1e-4f); ox /= ol; oy /= ol; oz /= ol
        val base = b.v.size / 9
        val n = m.pos.size / 3
        for (k in 0 until n) {
            val dx = m.pos[k * 3]; val dy = m.pos[k * 3 + 1]; val dz = m.pos[k * 3 + 2]
            val bump = 0.5f * sin(dx * 5.1f + p1) * sin(dy * 4.7f + p2) + 0.5f * sin(dz * 5.9f + p3) * sin(dx * 3.9f - p1)
            val r = 1f + lumps * bump
            var lx = dx * rx * r; var ly = dy * ry * r; var lz = dz * rz * r
            var tx = lx * cp - ly * sp; var ty = lx * sp + ly * cp
            val x = cx + tx * cw + lz * sw; val y = cy + ty; val z = cz - tx * sw + lz * cw
            // Ellipsoid normal, rotated the same way.
            lx = dx / rx; ly = dy / ry; lz = dz / rz
            tx = lx * cp - ly * sp; ty = lx * sp + ly * cp
            var ax = tx * cw + lz * sw; var ay = ty; var az = -tx * sw + lz * cw
            val al = max(sqrt(ax * ax + ay * ay + az * az), 1e-5f); ax /= al; ay /= al; az /= al
            var bx = x - canopy[0]; var by = y - canopy[1]; var bz = z - canopy[2]
            val bl = max(sqrt(bx * bx + by * by + bz * bz), 1e-5f); bx /= bl; by /= bl; bz /= bl
            val nx = ax * own + bx * (1f - own); val ny = ay * own + by * (1f - own); val nz = az * own + bz * (1f - own)
            val inward = -(ax * ox + ay * oy + az * oz)
            var g = 0.60f + 0.40f * ay + 0.55f * (y - canopy[1]) - 0.30f * max(inward, 0f) + shadeBias + (id - 0.5f) * 0.12f
            g = g.coerceIn(0f, 1f)
            b.vertex(x, y, z, nx, ny, nz, g, 0f, id)
        }
        var i = 0
        while (i < m.idx.size) { b.tri(base + m.idx[i], base + m.idx[i + 1], base + m.idx[i + 2]); i += 3 }
    }

    /** Small leaf-cluster blobs scattered over an ellipsoidal crown, so its outline and shading are layered, not smooth. */
    private fun clusters(b: Builder, cc: FloatArray, rx: Float, ry: Float, count: Int, r0: Float, r1: Float, sub: Int, rnd: Rng, minY: Float) {
        for (k in 0 until count) {
            val a = rnd.range(0f, 6.283f)
            val e = rnd.range(minY, 1f)                       // height on the unit sphere, -1..1
            val rr = sqrt(max(1f - e * e, 0f))
            val x = cc[0] + cos(a) * rr * rx * 0.97f; val y = cc[1] + e * ry * 0.97f; val z = cc[2] + sin(a) * rr * rx * 0.97f
            val r = rnd.range(r0, r1)
            blob(b, x, y, z, r, r * 0.85f, r, 0f, 0f, cc, sub, rnd, if (e > 0.3f) 0.04f else -0.04f, 0.78f, 0.14f)
        }
    }

    private fun trunk(b: Builder, lean: Float, top: Float, r0: Float, sides: Int) {
        tube(b, 0f, -0.02f, 0f, lean * 0.4f, 0.10f, 0f, r0, r0 * 0.72f, sides, 0.10f, 0.30f, 0.1f)
        tube(b, lean * 0.4f, 0.10f, 0f, lean, top * 0.62f, lean * 0.5f, r0 * 0.72f, r0 * 0.50f, sides, 0.30f, 0.55f, 0.2f)
        tube(b, lean, top * 0.62f, lean * 0.5f, lean, top, lean * 0.5f, r0 * 0.50f, r0 * 0.36f, sides, 0.55f, 0.65f, 0.3f)
    }

    fun build(seed: Int, detail: Int): TreeMeshData {
        val rnd = Rng(seed * 7919 + 17)
        val b = Builder()
        val sides = if (detail >= 2) 10 else if (detail == 1) 7 else 5
        val bigSub = when (detail) { 2 -> 2; 1 -> 1; else -> 0 }
        val smallSub = if (detail >= 1) 1 else 0
        val lean = rnd.range(-0.03f, 0.03f)
        when (seed) {
            5 -> pine(b, rnd, detail, sides, false)
            8 -> pine(b, rnd, detail, sides, true)
            6 -> palm(b, rnd, detail, sides)
            7 -> shrub(b, rnd, detail, sides)
            4 -> {
                val cc = floatArrayOf(lean, 0.66f, lean * 0.5f)
                trunk(b, lean, 0.62f, 0.040f, sides)
                if (detail >= 1) for (k in 0 until 3) {
                    val a = k * 2.1f + rnd.range(0f, 1f)
                    tube(b, lean, 0.40f + 0.06f * k, lean * 0.5f, lean + cos(a) * 0.10f, 0.56f + 0.1f * k, lean * 0.5f + sin(a) * 0.10f, 0.014f, 0.008f, 6, 0.45f, 0.65f, 0.4f)
                }
                // Tall oval crown: three stacked rings of lumps and a cap.
                for (layer in 0 until 3) {
                    val y = 0.50f + layer * 0.17f
                    val rad = if (layer == 1) 0.20f else 0.16f
                    val ring = 4
                    val a0 = rnd.range(0f, 6.28f)
                    for (k in 0 until ring) {
                        val a = a0 + k * 6.283f / ring + layer * 0.7f
                        val d = rnd.range(0.10f, 0.15f) * (if (layer == 1) 1.15f else 0.9f)
                        blob(b, lean + cos(a) * d, y + rnd.range(-0.02f, 0.03f), lean * 0.5f + sin(a) * d, rad, rad * 0.9f, rad, 0f, 0f, cc, bigSub, rnd, -0.03f + layer * 0.05f, 0.70f, 0.10f)
                    }
                }
                blob(b, lean, 0.93f, lean * 0.5f, 0.15f, 0.15f, 0.15f, 0f, 0f, cc, bigSub, rnd, 0.10f, 0.70f, 0.10f)
                if (detail >= 1) clusters(b, cc, 0.27f, 0.42f, if (detail >= 2) 22 else 12, 0.055f, 0.09f, smallSub, rnd, -0.3f)
            }
            else -> {
                val cc = floatArrayOf(lean, 0.66f, lean * 0.5f)
                val widen = rnd.range(0.92f, 1.14f) + (seed % 2) * 0.04f
                trunk(b, lean, 0.66f, 0.056f, sides)
                if (detail >= 1) for (k in 0 until 4) {
                    val a = k * 1.6f + rnd.range(0f, 0.8f)
                    val d = 0.20f * widen
                    tube(b, lean, 0.38f + 0.045f * k, lean * 0.5f, lean + cos(a) * d, 0.55f + 0.03f * k, lean * 0.5f + sin(a) * d, 0.020f, 0.010f, 6, 0.45f, 0.65f, 0.4f)
                }
                // Main puffs (bigger lumps, a lower skirt, top tufts), then a layer of small leaf clusters over them.
                blob(b, lean, 0.70f, lean * 0.5f, rnd.range(0.23f, 0.26f), 0.22f, rnd.range(0.23f, 0.26f), 0f, 0f, cc, bigSub, rnd, 0.02f, 0.62f, 0.08f)
                val ring = 5 + (seed % 2)
                val a0 = rnd.range(0f, 6.28f)
                for (k in 0 until ring) {
                    val a = a0 + k * 2f * PI.toFloat() / ring + rnd.range(-0.25f, 0.25f)
                    val d = rnd.range(0.19f, 0.25f) * widen; val r = rnd.range(0.15f, 0.19f)
                    blob(b, cos(a) * d, rnd.range(0.57f, 0.66f), sin(a) * d, r, r * 0.84f, r, 0f, 0f, cc, bigSub, rnd, -0.02f, 0.62f, 0.08f)
                }
                for (k in 0 until 3) {
                    val a = a0 + 0.9f + k * 2.1f + rnd.range(-0.3f, 0.3f)
                    val d = rnd.range(0.22f, 0.30f) * widen; val r = rnd.range(0.11f, 0.14f)
                    blob(b, cos(a) * d, rnd.range(0.47f, 0.53f), sin(a) * d, r, r * 0.8f, r, 0f, 0f, cc, bigSub, rnd, -0.10f, 0.62f, 0.08f)
                }
                if (detail >= 1) clusters(b, cc, 0.31f * widen, 0.28f, if (detail >= 2) 30 else 14, 0.06f, 0.10f, smallSub, rnd, -0.35f)
            }
        }
        return b.build()
    }

    /** Conifer: a tapering trunk and stacked tiers of drooping, flattened needle-clumps. */
    private fun pine(b: Builder, rnd: Rng, detail: Int, sides: Int, slim: Boolean) {
        val wide = if (slim) 0.68f else 1f
        val cc = floatArrayOf(0f, 0.55f, 0f)
        tube(b, 0f, -0.02f, 0f, 0f, 0.30f, 0f, 0.045f, 0.030f, sides, 0.10f, 0.30f, 0.1f)
        tube(b, 0f, 0.30f, 0f, 0f, 0.92f, 0f, 0.030f, 0.008f, sides, 0.30f, 0.60f, 0.2f)
        val sub = if (detail >= 1) 1 else 0
        val tiers = (if (detail >= 2) 8 else 6) + (if (slim) 2 else 0)
        for (t in 0 until tiers) {
            val f = t.toFloat() / (tiers - 1)
            val y = 0.17f + 0.70f * f
            val reach = (0.30f * (1f - 0.80f * f) + 0.04f) * wide
            val n = if (f < 0.4f) 7 else if (f < 0.75f) 5 else 4
            val a0 = rnd.range(0f, 6.28f)
            for (k in 0 until n) {
                val a = a0 + k * 6.283f / n + t * 0.55f
                val len = reach * rnd.range(0.55f, 0.65f)
                val bx = cos(a) * len; val bz = sin(a) * len
                blob(b, bx, y + rnd.range(-0.01f, 0.015f) - 0.02f * (1f - f), bz, len * 1.02f, 0.055f + 0.02f * (1f - f), 0.075f + 0.04f * (1f - f), -a, -0.30f, cc, sub, rnd, -0.04f + 0.10f * f, 0.85f, 0.12f)
            }
            blob(b, 0f, y + 0.02f, 0f, reach * 0.42f, 0.06f, reach * 0.42f, 0f, 0f, cc, sub, rnd, 0.02f + 0.10f * f, 0.8f, 0.10f)
        }
        blob(b, 0f, 0.97f, 0f, 0.035f, 0.075f, 0.035f, 0f, 0f, cc, sub, rnd, 0.15f, 0.9f, 0.05f)
    }

    /** Shrub: a low, wide mound of leaf puffs with a hint of stem, about 0.8 tall; the caller scales it to a bush. */
    private fun shrub(b: Builder, rnd: Rng, detail: Int, sides: Int) {
        val cc = floatArrayOf(0f, 0.30f, 0f)
        val bigSub = when (detail) { 2 -> 2; 1 -> 1; else -> 0 }
        tube(b, 0f, -0.02f, 0f, 0f, 0.12f, 0f, 0.030f, 0.020f, if (sides > 5) 6 else 4, 0.10f, 0.30f, 0.1f)
        blob(b, 0f, 0.28f, 0f, 0.36f, 0.26f, 0.36f, 0f, 0f, cc, bigSub, rnd, 0.0f, 0.62f, 0.08f)
        val a0 = rnd.range(0f, 6.28f)
        for (k in 0 until 5) {
            val a = a0 + k * 6.283f / 5f + rnd.range(-0.2f, 0.2f)
            val d = rnd.range(0.24f, 0.32f); val r = rnd.range(0.17f, 0.23f)
            blob(b, cos(a) * d, rnd.range(0.16f, 0.26f), sin(a) * d, r, r * 0.82f, r, 0f, 0f, cc, bigSub, rnd, -0.04f, 0.62f, 0.08f)
        }
        for (k in 0 until 2) {
            val a = a0 + 1.3f + k * 3.1f
            blob(b, cos(a) * 0.10f, rnd.range(0.38f, 0.44f), sin(a) * 0.10f, 0.17f, 0.14f, 0.17f, 0f, 0f, cc, bigSub, rnd, 0.08f, 0.62f, 0.08f)
        }
        if (detail >= 1) clusters(b, cc, 0.42f, 0.30f, if (detail >= 2) 12 else 6, 0.07f, 0.11f, 1, rnd, -0.1f)
    }

    /** Palm: a curved, ringed trunk topped by a rosette of long drooping fronds. */
    private fun palm(b: Builder, rnd: Rng, detail: Int, sides: Int) {
        val cc = floatArrayOf(0.14f, 0.80f, 0f)
        val bend = 0.14f
        var px = 0f; var py = -0.02f
        for (k in 1..5) {
            val f = k / 5f
            val x = bend * f * f; val y = 0.80f * f
            tube(b, px, py, 0f, x, y, 0f, 0.040f - 0.014f * (k - 1) / 5f, 0.040f - 0.014f * k / 5f, sides, 0.15f + 0.10f * (k - 1), 0.25f + 0.10f * k, 0.15f + 0.1f * (k % 2))
            px = x; py = y
        }
        val fronds = if (detail >= 2) 10 else 7
        val sub = if (detail >= 1) 1 else 0
        for (k in 0 until fronds) {
            val a = k * 6.283f / fronds + rnd.range(-0.2f, 0.2f)
            val len = rnd.range(0.26f, 0.32f)
            val droop = rnd.range(-0.55f, -0.15f)
            blob(b, cc[0] + cos(a) * len * 0.85f, cc[1] + 0.02f + droop * len * 0.4f, sin(a) * len * 0.85f, len, 0.014f, 0.065f, -a, droop, cc, sub, rnd, 0.06f, 0.95f, 0.10f)
        }
        blob(b, cc[0], cc[1], 0f, 0.05f, 0.04f, 0.05f, 0f, 0f, cc, 1, rnd, 0.0f, 0.9f, 0.05f)
    }
}
