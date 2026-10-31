import com.discgolf.game.core.*
fun main(args: Array<String>) {
    val n = args[0].toInt()
    val h = Courses.defaultCourse()[n - 1]
    var minX = 1e9f; var maxX = -1e9f; var minZ = 1e9f; var maxZ = -1e9f
    for (p in h.path) { minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x); minZ = minOf(minZ, p.z); maxZ = maxOf(maxZ, p.z) }
    val pad = 85f; val x0 = minX - pad; val z0 = minZ - pad
    val cell = 2f
    val nx = Math.ceil(((maxX - minX + 2 * pad) / cell).toDouble()).toInt() + 1
    val nz = Math.ceil(((maxZ - minZ + 2 * pad) / cell).toDouble()).toInt() + 1
    val hs = StringBuilder()
    for (iz in 0 until nz) for (ix in 0 until nx) { hs.append("%.3f".format(h.groundAt(x0 + ix * cell, z0 + iz * cell))).append(',') }
    val tw = Math.ceil((maxX - minX + 2 * pad).toDouble()).toInt(); val th = Math.ceil((maxZ - minZ + 2 * pad).toDouble()).toInt()
    val sm = StringBuilder()
    for (j in 0 until th) for (i in 0 until tw) {
        val s = h.surfaceAt(x0 + i + 0.5f, z0 + j + 0.5f)
        sm.append(if (s == Surface.FAIRWAY) 1 else if (s == Surface.WATER) 2 else if (s == Surface.OUT_OF_BOUNDS) 3 else 0)
    }
    val trees = h.trees.joinToString(",") { "[${it.x},${h.groundAt(it.x, it.z)},${it.z},${it.height},${it.canopyR},${it.variant}]" }
    val path = h.path.joinToString(",") { "[${it.x},${it.z}]" }
    val ponds = h.ponds.joinToString(",") { "[${it.cx},${it.cz},${it.rx},${it.rz},${it.level}]" }
    val rnd = java.util.Random(900L + h.number * 77L)
    val bushes = arrayOf("Bush", "Bush_Large", "Bush_Flowers", "Bush_Large_Flowers")
    val flowers = arrayOf("Flower_1_Clump", "Flower_2_Clump", "Flower_3_Clump", "Flower_4_Clump", "Flower_5_Clump")
    val nDec = (h.length / 3.2f).toInt().coerceIn(40, 260)
    val dec = ArrayList<String>()
    for (i in 0 until nDec) {
        val (p, d) = h.pointAt(rnd.nextFloat() * h.length)
        val side = if (rnd.nextBoolean()) 1f else -1f
        val off = h.fairwayWidth * 0.5f + 1.5f + rnd.nextFloat() * 9f
        val x = p.x + d.z * off * side; val z = p.z - d.x * off * side
        if (h.surfaceAt(x, z) == Surface.WATER) continue
        if (h.trees.any { val dx = it.x - x; val dz = it.z - z; dx * dx + dz * dz < it.trunkR * it.trunkR * 9f }) continue
        val r = rnd.nextFloat()
        val model = if (r < 0.45f) bushes[rnd.nextInt(bushes.size)] else if (r < 0.80f) flowers[rnd.nextInt(flowers.size)] else if (r < 0.9f) "Grass_Large" else "Grass_Small"
        dec += "[\"$model\",$x,${h.groundAt(x, z)},$z,${0.9f + rnd.nextFloat() * 0.9f},${rnd.nextFloat() * 360f},${0.85f + rnd.nextFloat() * 0.3f}]"
    }
    println("""{"decor":[${dec.joinToString(",")}],"x0":$x0,"z0":$z0,"cell":$cell,"nx":$nx,"nz":$nz,"h":[${hs.dropLast(1)}],"tw":$tw,"th":$th,"surf":"$sm","trees":[$trees],"path":[$path],"tee":[${h.tee.x},${h.tee.y},${h.tee.z}],"basket":[${h.basket.x},${h.basket.y},${h.basket.z}],"ponds":[$ponds]}""")
}
