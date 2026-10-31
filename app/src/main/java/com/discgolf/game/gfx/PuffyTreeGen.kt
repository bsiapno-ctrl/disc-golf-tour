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
    const val SEEDS = 6

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
     * One lumpy sphere. [detail] 2 = mid/large puffs get 320 triangles, 1 = everything 80, 0 = everything 20 (far away).
     * Normals are blended toward the direction away from the canopy centre.
     */
    private fun blob(b: Builder, cx: Float, cy: Float, cz: Float, rad: Float, squash: Float, canopy: FloatArray, detail: Int, rnd: Rng, shadeBias: Float) {
        val sub = when (detail) { 2 -> if (rad > 0.15f) 2 else 1; 1 -> 1; else -> 0 }
        val m = ico(sub)
        val p1 = rnd.range(0f, 6.28f); val p2 = rnd.range(0f, 6.28f); val p3 = rnd.range(0f, 6.28f)
        val id = rnd.next()
        var ox = cx - canopy[0]; var oy = cy - canopy[1]; var oz = cz - canopy[2]
        val ol = max(sqrt(ox * ox + oy * oy + oz * oz), 1e-4f); ox /= ol; oy /= ol; oz /= ol
        val base = b.v.size / 9
        val n = m.pos.size / 3
        for (k in 0 until n) {
            val dx = m.pos[k * 3]; val dy = m.pos[k * 3 + 1]; val dz = m.pos[k * 3 + 2]
            // Lumpy surface: a couple of low-frequency waves push the radius in and out by about 10 percent.
            val bump = 0.5f * sin(dx * 3.3f + p1) * sin(dy * 2.9f + p2) + 0.5f * sin(dz * 3.7f + p3) * sin(dx * 2.3f - p1)
            val r = rad * (1f + 0.11f * bump)
            val x = cx + dx * r; val y = cy + dy * r * squash; val z = cz + dz * r
            // Canopy-shaped normals: mostly the blob's own, leaning to the crown's global bulge.
            val nx = dx * 0.62f + (x - canopy[0]) * 0.9f; val ny = dy * 0.62f + (y - canopy[1]) * 1.2f; val nz = dz * 0.62f + (z - canopy[2]) * 0.9f
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            // Light amount: up-facing and high-up is bright; the belly and the inside faces are dark.
            val up = dy
            val inward = -(dx * ox + dy * oy + dz * oz)         // 1 when facing the crown centre (hidden)
            var g = 0.66f + 0.46f * up + 0.55f * (cy - canopy[1]) - 0.30f * max(inward, 0f) + shadeBias + (id - 0.5f) * 0.10f
            g = g.coerceIn(0f, 1f)
            b.vertex(x, y, z, nx / nl, ny / nl, nz / nl, g, 0f, id)
        }
        var i = 0
        while (i < m.idx.size) { b.tri(base + m.idx[i], base + m.idx[i + 1], base + m.idx[i + 2]); i += 3 }
    }

    fun build(seed: Int, detail: Int): TreeMeshData {
        val rnd = Rng(seed * 7919 + 17)
        val b = Builder()
        val cc = floatArrayOf(0f, 0.66f, 0f)                          // crown centre
        val lean = rnd.range(-0.03f, 0.03f)

        // Blob layout first so limbs can reach for them.
        class Blob(val x: Float, val y: Float, val z: Float, val r: Float, val sq: Float, val bias: Float)
        val blobs = ArrayList<Blob>()
        val widen = rnd.range(0.92f, 1.14f)
        blobs += Blob(lean, 0.69f, lean * 0.5f, rnd.range(0.255f, 0.29f), 0.86f, 0.02f)        // big top-centre puff
        val ring = 5 + (seed % 2)
        val a0 = rnd.range(0f, 6.28f)
        for (k in 0 until ring) {
            val a = a0 + k * 2f * PI.toFloat() / ring + rnd.range(-0.25f, 0.25f)
            val d = rnd.range(0.19f, 0.25f) * widen
            blobs += Blob(cos(a) * d, rnd.range(0.57f, 0.66f), sin(a) * d, rnd.range(0.17f, 0.215f), 0.84f, -0.02f)
        }
        for (k in 0 until 3) {
            val a = a0 + 0.9f + k * 2.1f + rnd.range(-0.3f, 0.3f)
            val d = rnd.range(0.22f, 0.30f) * widen
            blobs += Blob(cos(a) * d, rnd.range(0.47f, 0.53f), sin(a) * d, rnd.range(0.12f, 0.16f), 0.8f, -0.10f)   // low skirt
        }
        for (k in 0 until 2) {
            val a = a0 + k * 3.4f + 1.3f
            val d = rnd.range(0.05f, 0.13f)
            blobs += Blob(cos(a) * d, rnd.range(0.83f, 0.88f), sin(a) * d, rnd.range(0.12f, 0.16f), 0.86f, 0.10f)   // top tufts
        }

        // Trunk: thick, flared at the foot, tapering into the crown; and limbs toward the bigger puffs.
        val sides = if (detail >= 2) 10 else if (detail == 1) 7 else 5
        tube(b, 0f, -0.02f, 0f, lean * 0.4f, 0.10f, 0f, 0.056f, 0.040f, sides, 0.10f, 0.30f, 0.1f)
        tube(b, lean * 0.4f, 0.10f, 0f, lean, 0.42f, lean * 0.5f, 0.040f, 0.028f, sides, 0.30f, 0.55f, 0.2f)
        tube(b, lean, 0.42f, lean * 0.5f, lean, 0.66f, lean * 0.5f, 0.028f, 0.020f, sides, 0.55f, 0.65f, 0.3f)
        if (detail >= 1) {
            for (k in 1..min(3, blobs.size - 1)) {
                val bl = blobs[k * 2 % blobs.size]
                tube(b, lean, 0.40f + 0.04f * k, lean * 0.5f, bl.x * 0.75f, bl.y - 0.02f, bl.z * 0.75f, 0.020f, 0.012f, 6, 0.45f, 0.65f, 0.4f)
            }
        }
        for (bl in blobs) blob(b, bl.x, bl.y, bl.z, bl.r, bl.sq, cc, detail, rnd, bl.bias)
        return b.build()
    }
}
