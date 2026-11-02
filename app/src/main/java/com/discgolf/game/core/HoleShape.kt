package com.discgolf.game.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Which way a bend turns, as seen from the tee looking down the hole. */
enum class BendDir { LEFT, RIGHT }

/** One bend at a "via" point: [angleDeg] is how far the line turns (0 = straight on). */
class Bend(val index: Int, val dir: BendDir, val angleDeg: Float, val x: Float, val z: Float, val distFromTee: Float)

/**
 * The shape of a hole with bends. [dir] and [angleDeg] describe the sharpest bend; [bends] lists every bend of at
 * least [MIN_BEND_DEG]. [length] is the length along the fairway.
 */
class Dogleg(val dir: BendDir, val angleDeg: Float, val apexDist: Float, val bends: List<Bend>, val length: Float) {
    val sCurve: Boolean get() = bends.map { it.dir }.toSet().size > 1
    val label: String
        get() = if (sCurve) "S-CURVE" else "DOGLEG ${dir.name}"
}

const val MIN_BEND_DEG = 15f

/** Every bend of the hole's centreline (interior points only) that turns at least [MIN_BEND_DEG]. */
fun bendsOf(hole: Hole): List<Bend> {
    val p = hole.path
    val out = ArrayList<Bend>()
    var acc = 0f
    for (i in 1 until p.size - 1) {
        acc += p[i].distanceXZ(p[i - 1])
        val d1x = p[i].x - p[i - 1].x; val d1z = p[i].z - p[i - 1].z
        val d2x = p[i + 1].x - p[i].x; val d2z = p[i + 1].z - p[i].z
        val l1 = hypot(d1x, d1z); val l2 = hypot(d2x, d2z)
        if (l1 < 1e-3f || l2 < 1e-3f) continue
        val cos = ((d1x * d2x + d1z * d2z) / (l1 * l2)).coerceIn(-1f, 1f)
        val ang = Math.toDegrees(acos(cos).toDouble()).toFloat()
        if (ang < MIN_BEND_DEG) continue
        // x grows east and z north, so a positive cross product is a turn to the left.
        val cross = d1x * d2z - d1z * d2x
        out += Bend(i, if (cross > 0f) BendDir.LEFT else BendDir.RIGHT, ang, p[i].x, p[i].z, acc)
    }
    return out
}

/** Pure description of a hole's dogleg, or null for a straight hole (no bend of [MIN_BEND_DEG] or more). */
fun dogleg(hole: Hole): Dogleg? {
    val bends = bendsOf(hole)
    if (bends.isEmpty()) return null
    val main = bends.maxByOrNull { it.angleDeg }!!
    return Dogleg(main.dir, main.angleDeg, main.distFromTee, bends, hole.length)
}

/** One line for the hole preview, e.g. "DOGLEG LEFT 140m" or "STRAIGHT 90m"; the course's own note follows after a dot. */
fun previewLine(hole: Hole, withNotes: Boolean = false): String {
    val d = dogleg(hole)
    val base = (d?.label ?: "STRAIGHT") + " " + hole.length.toInt() + "m"
    val note = hole.obstacleNotes
    return if (withNotes && !note.isNullOrBlank()) "$base · $note" else base
}

/** Lane analysis: is there a clear way from tee to basket along the fairway corridor, and is the direct line blocked? */
object Lanes {
    const val DISC_CLEARANCE = 1.5f

    /** How far a flying disc must stay from a tree's centre: a bit under the canopy radius. */
    fun treeBlockR(t: Tree) = max(t.canopyR * 0.75f, t.trunkR + 0.3f)
    fun bushBlockR(b: Bush) = b.radius * 0.9f

    /** Distance from the point to the nearest edge of anything that stops a disc (negative = inside). */
    fun clearance(h: Hole, x: Float, z: Float, trees: List<Tree> = h.trees): Float {
        var best = Float.MAX_VALUE
        for (t in trees) {
            val dx = t.x - x; val dz = t.z - z
            if (abs(dx) > 30f || abs(dz) > 30f) continue
            best = min(best, sqrt(dx * dx + dz * dz) - treeBlockR(t))
        }
        for (b in h.bushes) best = min(best, hypot(b.x - x, b.z - z) - bushBlockR(b))
        for (p in h.ponds) best = min(best, (sqrt(sq((x - p.cx) / p.rx) + sq((z - p.cz) / p.rz)) - 1f) * min(p.rx, p.rz))
        for (o in h.obZones) best = min(best, (sqrt(sq((x - o.cx) / o.rx) + sq((z - o.cz) / o.rz)) - 1f) * min(o.rx, o.rz))
        return best
    }

    private fun sq(v: Float) = v * v

    /** True when no straight tee-to-basket flight path has [clear] metres of room (the direct line is blocked). */
    fun directLineBlocked(h: Hole, clear: Float = DISC_CLEARANCE, trees: List<Tree> = h.trees): Boolean {
        val a = h.tee; val b = h.basket
        val len = a.distanceXZ(b)
        val n = (len / 0.5f).toInt().coerceAtLeast(2)
        // Skip the tee pad and the putting circle, where scenery is deliberately cleared.
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = a.x + (b.x - a.x) * t; val z = a.z + (b.z - a.z) * t
            if (hypot(x - a.x, z - a.z) < 4f || hypot(x - b.x, z - b.z) < 4f) continue
            if (clearance(h, x, z, trees) < clear) return true
        }
        return false
    }

    /**
     * True when a path of width 2 x [clear] exists from the tee to the basket that stays inside the fairway corridor
     * (the mown width plus [margin] each side) and keeps [clear] metres from every tree, bush and hazard.
     */
    fun laneExists(h: Hole, clear: Float = DISC_CLEARANCE, margin: Float = 2f): Boolean {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var z0 = Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (p in h.path) { x0 = min(x0, p.x); x1 = max(x1, p.x); z0 = min(z0, p.z); z1 = max(z1, p.z) }
        val pad = h.fairwayWidth / 2f + margin + 2f
        x0 -= pad; z0 -= pad
        val step = 1f
        val nx = ((x1 + pad - x0) / step).toInt() + 1
        val nz = ((z1 + pad - z0) / step).toInt() + 1
        val ok = BooleanArray(nx * nz)
        val reach = h.fairwayWidth / 2f + margin
        for (j in 0 until nz) for (i in 0 until nx) {
            val x = x0 + i * step; val z = z0 + j * step
            if (h.distToPath(x, z) > reach) continue
            ok[j * nx + i] = clearance(h, x, z) >= clear
        }
        fun cell(x: Float, z: Float) = ((z - z0) / step).toInt().coerceIn(0, nz - 1) * nx + ((x - x0) / step).toInt().coerceIn(0, nx - 1)
        val seen = BooleanArray(nx * nz)
        val queue = IntArray(nx * nz)
        var qh = 0; var qt = 0
        // Starts anywhere within 3 m of the tee; the goal is anywhere within 3 m of the basket.
        val t = h.tee; val bk = h.basket
        for (dj in -3..3) for (di in -3..3) {
            val x = t.x + di; val z = t.z + dj
            if (hypot(di.toFloat(), dj.toFloat()) > 3f) continue
            val c = cell(x, z)
            if (ok[c] && !seen[c]) { seen[c] = true; queue[qt++] = c }
        }
        while (qh < qt) {
            val c = queue[qh++]
            val ci = c % nx; val cj = c / nx
            if (hypot(x0 + ci * step - bk.x, z0 + cj * step - bk.z) <= 3f) return true
            for (dj in -1..1) for (di in -1..1) {
                if (di == 0 && dj == 0) continue
                val ni = ci + di; val nj = cj + dj
                if (ni < 0 || nj < 0 || ni >= nx || nj >= nz) continue
                val nc = nj * nx + ni
                if (ok[nc] && !seen[nc]) { seen[nc] = true; queue[qt++] = nc }
            }
        }
        return false
    }

    /**
     * Spots where trees should fill the inside corner of each bend: a loose lattice over the wedge between the two legs
     * (out to 70% of the longer leg, at most 42 m), kept outside the mown corridor. Pure geometry; the
     * caller validates each spot. Returns (x, z, tall) where tall > 0 near the apex.
     */
    fun cornerSpots(shell: Hole, rnd: java.util.Random): List<Triple<Float, Float, Float>> {
        val out = ArrayList<Triple<Float, Float, Float>>()
        val p = shell.path
        for (b in bendsOf(shell)) {
            val v = p[b.index]; val a0 = p[b.index - 1]; val c0 = p[b.index + 1]
            val l1 = v.distanceXZ(a0); val l2 = v.distanceXZ(c0)
            val u1x = (a0.x - v.x) / l1; val u1z = (a0.z - v.z) / l1
            val u2x = (c0.x - v.x) / l2; val u2z = (c0.z - v.z) / l2
            var bx = u1x + u2x; var bz = u1z + u2z
            val bl = hypot(bx, bz)
            if (bl < 1e-3f) continue
            bx /= bl; bz /= bl
            // Half of the interior angle: points closer to the bisector than this lie between the legs.
            val cosHalf = ((u1x * bx + u1z * bz)).coerceIn(-1f, 1f)
            val reach = min(max(l1, l2) * 0.7f, 42f)
            val spacing = 3.6f
            val n = (reach / spacing).toInt() + 1
            for (i in -n..n) for (j in -n..n) {
                val dx = i * spacing + (rnd.nextFloat() - 0.5f) * 1.5f
                val dz = j * spacing + (rnd.nextFloat() - 0.5f) * 1.5f
                val r = hypot(dx, dz)
                if (r > reach || r < 2f) continue
                if ((dx * bx + dz * bz) / r < cosHalf + 0.02f) continue
                val x = v.x + dx; val z = v.z + dz
                if (shell.distToPath(x, z) < shell.fairwayWidth / 2f + 1.2f) continue
                if (rnd.nextFloat() < 0.12f) continue
                out += Triple(x, z, if (r < 12f) 2.5f else 0f)
            }
        }
        return out
    }
}
