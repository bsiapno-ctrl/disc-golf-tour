import com.discgolf.game.core.*
import java.io.File

/** Prints, for every hole of every bundled course, its shape and whether it has a clear lane. Exit code 1 on a failure.
 *  Run: kotlinc the core sources plus LaneCheck.kt -d out; java -cp out:kotlin-stdlib LaneCheckKt app/src/main/assets/courses [courseId] */
fun main(args: Array<String>) {
    val dir = File(args.getOrElse(0) { "app/src/main/assets/courses" })
    val only = args.getOrNull(1)
    var bad = 0
    for (f in dir.listFiles { x -> x.name.endsWith(".json") }!!.sortedBy { it.name }) {
        val c = CourseFile.parse(f.readText())
        if (only != null && c.id != only) continue
        var shaped = 0; var total = 0
        println("== ${c.name} (${c.id})")
        for (l in c.layouts) for (h in l.holes) {
            total++
            val d = dogleg(h)
            val lane = Lanes.laneExists(h)
            val blocked = Lanes.directLineBlocked(h)
            val obstructed = d != null || h.bushes.isNotEmpty() || h.ponds.isNotEmpty() || h.obZones.isNotEmpty()
            if (d != null || h.bushes.isNotEmpty() || h.ponds.isNotEmpty() || h.obZones.isNotEmpty()) shaped++
            var flag = ""
            if (!lane) flag += " NO-LANE"
            if (d != null && !blocked) flag += " DIRECT-OPEN"
            if (flag.isNotEmpty()) bad++
            println("%2d par %d %5.0fm  %-14s %s trees=%3d bushes=%d ponds=%d ob=%d direct=%s%s".format(
                h.number, h.par, h.length, d?.let { "${it.label} ${it.angleDeg.toInt()}" } ?: "straight", if (obstructed) "*" else " ",
                h.trees.size, h.bushes.size, h.ponds.size, h.obZones.size, if (blocked) "blocked" else "open", flag))
        }
        println("   shaped (bend, bush, water, OB or blocked line): $shaped / $total")
    }
    if (bad > 0) { println("FAILURES: $bad"); System.exit(1) }
}
