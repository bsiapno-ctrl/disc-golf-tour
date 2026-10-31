package com.discgolf.game.core

import java.util.Random

/** Non-colliding set dressing around a hole: bushes, flower patches, grass, rocks and fallen logs. Pure, so tools can reuse it. */
object Scenery {
    /** [model] is a glTF name, or "@rock" for a procedural boulder. [lie] > 0 means a log lying on its side, in degrees of roll. */
    class Item(val model: String, val x: Float, val z: Float, val scale: Float, val rot: Float, val tint: Float, val lie: Float = 0f)

    private val bushes = arrayOf("Bush", "Bush_Large", "Bush_Flowers", "Bush_Large_Flowers")
    private val flowers = arrayOf("Flower_1_Clump", "Flower_2_Clump", "Flower_3_Clump", "Flower_4_Clump", "Flower_5_Clump")
    private val dead = arrayOf("DeadTree_1", "DeadTree_2", "DeadTree_3")

    fun decorFor(h: Hole): List<Item> {
        val rnd = Random(900L + h.number * 77L)
        val out = ArrayList<Item>()
        fun ok(x: Float, z: Float, clearTrees: Float = 3f): Boolean {
            if (h.surfaceAt(x, z) == Surface.WATER || h.sandAt(x, z)) return false
            if (h.distToPath(x, z) < h.fairwayWidth * 0.5f + 0.8f) return false
            if (h.ponds.any { val dx = (x - it.cx) / (it.rx + 1.4f); val dz = (z - it.cz) / (it.rz + 1.4f); dx * dx + dz * dz < 1f }) return false
            return h.trees.none { val dx = it.x - x; val dz = it.z - z; dx * dx + dz * dz < it.trunkR * it.trunkR * clearTrees * clearTrees }
        }
        fun around(s: Float, minOff: Float, maxOff: Float): Pair<Float, Float> {
            val (p, d) = h.pointAt(s)
            val side = if (rnd.nextBoolean()) 1f else -1f
            val off = h.fairwayWidth * 0.5f + minOff + rnd.nextFloat() * (maxOff - minOff)
            return (p.x + d.z * off * side) to (p.z - d.x * off * side)
        }
        // Patches: a flower bed of one kind, or a shrub group, scattered around a centre.
        val patches = (h.length / 11f).toInt().coerceIn(6, 26)
        for (i in 0 until patches) {
            val (cx, cz) = around(rnd.nextFloat() * h.length, 1.2f, 11f)
            val bed = rnd.nextFloat() < 0.55f
            val kind = flowers[rnd.nextInt(flowers.size)]
            val n = 3 + rnd.nextInt(5)
            for (k in 0 until n) {
                val a = rnd.nextFloat() * 6.283f; val r = rnd.nextFloat() * (if (bed) 2.2f else 3f)
                val x = cx + kotlin.math.cos(a) * r; val z = cz + kotlin.math.sin(a) * r
                if (!ok(x, z)) continue
                val model = if (bed) (if (rnd.nextFloat() < 0.8f) kind else flowers[rnd.nextInt(flowers.size)]) else bushes[rnd.nextInt(bushes.size)]
                out += Item(model, x, z, (if (bed) 0.9f else 0.65f) + rnd.nextFloat() * (if (bed) 0.9f else 0.6f), rnd.nextFloat() * 360f, 0.85f + rnd.nextFloat() * 0.3f)
            }
        }
        // Loose grass tufts.
        repeat((h.length / 4f).toInt().coerceIn(20, 90)) {
            val (x, z) = around(rnd.nextFloat() * h.length, 1.2f, 10f)
            if (ok(x, z)) out += Item(if (rnd.nextFloat() < 0.6f) "Grass_Large" else "Grass_Small", x, z, 1f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 360f, 0.9f + rnd.nextFloat() * 0.2f)
        }
        // Dressing round the green: low shrubs and flower beds just beyond the putting circle.
        repeat(9) {
            val a = rnd.nextFloat() * 6.283f; val r = 9f + rnd.nextFloat() * 6f
            val x = h.basket.x + kotlin.math.cos(a) * r; val z = h.basket.z + kotlin.math.sin(a) * r
            if (h.surfaceAt(x, z) == Surface.WATER || h.sandAt(x, z) || h.trees.any { val dx = it.x - x; val dz = it.z - z; dx * dx + dz * dz < 4f }) return@repeat
            val model = if (it < 4) bushes[rnd.nextInt(bushes.size)] else flowers[rnd.nextInt(flowers.size)]
            out += Item(model, x, z, 0.7f + rnd.nextFloat() * 0.7f, rnd.nextFloat() * 360f, 0.9f + rnd.nextFloat() * 0.2f)
        }
        // Boulders and reeds' worth of shrubs around each pond.
        for (pd in h.ponds) repeat(7) {
            val a = rnd.nextFloat() * 6.283f; val k = 1.12f + rnd.nextFloat() * 0.25f
            val x = pd.cx + kotlin.math.cos(a) * pd.rx * k; val z = pd.cz + kotlin.math.sin(a) * pd.rz * k
            if (h.surfaceAt(x, z) == Surface.WATER || h.sandAt(x, z)) return@repeat
            if (h.distToPath(x, z) < h.fairwayWidth * 0.5f - 1f) return@repeat
            if (h.trees.any { val dx = it.x - x; val dz = it.z - z; dx * dx + dz * dz < 4f }) return@repeat
            if (it % 3 == 0) out += Item("@rock", x, z, 0.5f + rnd.nextFloat() * 0.7f, rnd.nextFloat() * 360f, 0.9f)
            else out += Item(if (it % 3 == 1) "Grass_Large" else bushes[rnd.nextInt(bushes.size)], x, z, 0.9f + rnd.nextFloat() * 0.6f, rnd.nextFloat() * 360f, 1f)
        }
        // Rocks, in twos and threes, and the odd fallen log.
        repeat((h.length / 32f).toInt().coerceIn(3, 8)) {
            val (cx, cz) = around(rnd.nextFloat() * h.length, 2.5f, 13f)
            repeat(1 + rnd.nextInt(3)) {
                val x = cx + (rnd.nextFloat() - 0.5f) * 3f; val z = cz + (rnd.nextFloat() - 0.5f) * 3f
                if (ok(x, z, 2f)) out += Item("@rock", x, z, 0.35f + rnd.nextFloat() * rnd.nextFloat() * 1.1f, rnd.nextFloat() * 360f, 0.85f + rnd.nextFloat() * 0.3f)
            }
        }
        repeat((h.length / 70f).toInt().coerceIn(1, 3)) {
            val (x, z) = around(rnd.nextFloat() * h.length, 4f, 14f)
            if (ok(x, z, 4f)) out += Item(dead[rnd.nextInt(dead.size)], x, z, 0.28f + rnd.nextFloat() * 0.12f, rnd.nextFloat() * 360f, 0.9f, lie = 82f + rnd.nextFloat() * 10f)
        }
        return out
    }
}
