import com.discgolf.game.core.*
fun main(args: Array<String>) {
    val n = args[0].toInt()
    // Optional second argument: a course JSON (first layout) instead of the built-in course.
    val h = if (args.size > 1) CourseFile.parse(java.io.File(args[1]).readText()).layouts[0].holes[n - 1] else Courses.defaultCourse()[n - 1]
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
    val dec = h.let { Scenery.decorFor(it) }.joinToString(",") { "[\"${it.model}\",${it.x},${h.groundAt(it.x, it.z)},${it.z},${it.scale},${it.rot},${it.tint},${it.lie},${it.kind}]" }
    println("""{"decor":[$dec],"x0":$x0,"z0":$z0,"cell":$cell,"nx":$nx,"nz":$nz,"h":[${hs.dropLast(1)}],"tw":$tw,"th":$th,"surf":"$sm","trees":[$trees],"path":[$path],"tee":[${h.tee.x},${h.tee.y},${h.tee.z}],"basket":[${h.basket.x},${h.basket.y},${h.basket.z}],"ponds":[$ponds]}""")
}
