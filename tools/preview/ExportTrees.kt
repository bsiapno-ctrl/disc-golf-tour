import com.discgolf.game.gfx.PuffyTreeGen
fun main() {
    val sb = StringBuilder("[")
    for (s in 0 until PuffyTreeGen.SEEDS) for (d in 0..2) {
        val m = PuffyTreeGen.build(s, d)
        if (sb.length > 1) sb.append(',')
        sb.append("{\"v\":[").append(m.vertices.joinToString(",") { "%.4f".format(it) }).append("],\"i\":[").append(m.indices.joinToString(",")).append("]}")
    }
    println(sb.append(']'))
}
