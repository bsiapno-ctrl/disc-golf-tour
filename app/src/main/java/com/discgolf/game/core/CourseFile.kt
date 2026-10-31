package com.discgolf.game.core

/**
 * Reads a course from a JSON text. See docs/COURSE_FORMAT.md for the format.
 *
 * Coordinates are metres on a local plane: x grows to the east, z to the north, and heights are metres above any
 * zero (usually sea level). One file can hold several layouts that share the same land.
 */
object CourseFile {

    class FormatException(message: String) : Exception(message)

    fun parse(text: String): Course {
        val root = MiniJson.parse(text) as? Map<*, *> ?: throw FormatException("course file must be a JSON object")
        val name = root.str("name") ?: throw FormatException("missing \"name\"")
        val id = root.str("id") ?: name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val location = root.str("location") ?: ""

        val terrain: Terrain = (root["terrain"] as? Map<*, *>)?.let { parseTerrain(it) } ?: FlatTerrain

        val defs = LinkedHashMap<Int, HoleDef>()
        val holesJson = root["holes"] as? List<*> ?: throw FormatException("missing \"holes\"")
        for (h in holesJson) {
            val m = h as? Map<*, *> ?: throw FormatException("each hole must be an object")
            val def = parseHole(m, terrain)
            defs[def.number] = def
        }
        if (defs.isEmpty()) throw FormatException("course has no holes")

        val layouts = ArrayList<Layout>()
        val layoutsJson = root["layouts"] as? List<*>
        if (layoutsJson == null || layoutsJson.isEmpty()) {
            val holes = defs.values.sortedBy { it.number }.mapIndexed { i, d ->
                d.build(i + 1, d.tees.keys.first(), d.baskets.keys.first(), null, terrain)
            }
            layouts += Layout("Default", "", holes)
        } else {
            for (l in layoutsJson) {
                val lm = l as? Map<*, *> ?: throw FormatException("each layout must be an object")
                val lname = lm.str("name") ?: throw FormatException("layout without a name")
                val entries = lm["holes"] as? List<*> ?: throw FormatException("layout \"$lname\" has no holes")
                val holes = entries.mapIndexed { i, e ->
                    val em = e as? Map<*, *> ?: throw FormatException("layout \"$lname\": bad hole entry")
                    val n = em.num("hole")?.toInt() ?: throw FormatException("layout \"$lname\": entry without \"hole\"")
                    val d = defs[n] ?: throw FormatException("layout \"$lname\" uses hole $n, which is not defined")
                    val tee = em.str("tee") ?: d.tees.keys.first()
                    val basket = em.str("basket") ?: d.baskets.keys.first()
                    if (tee !in d.tees) throw FormatException("hole $n has no tee \"$tee\"")
                    if (basket !in d.baskets) throw FormatException("hole $n has no basket \"$basket\"")
                    d.build(i + 1, tee, basket, em.num("par")?.toInt(), terrain)
                }
                if (holes.isEmpty()) throw FormatException("layout \"$lname\" is empty")
                layouts += Layout(lname, lm.str("description") ?: "", holes)
            }
        }
        return Course(id, name, location, layouts)
    }

    // ---------------------------------------------------------------------

    private class HoleDef(
        val number: Int,
        val name: String,
        val width: Float,
        val tees: Map<String, Pair<Float, Float>>,
        val baskets: Map<String, Pair<Float, Float>>,
        val via: List<Pair<Float, Float>>,
        val trees: List<Tree>?,
        val ponds: List<Pond>,
        val density: Float,
        val guardian: Float,
    ) {
        fun build(order: Int, tee: String, basket: String, par: Int?, terrain: Terrain): Hole {
            val t = tees.getValue(tee); val b = baskets.getValue(basket)
            val path = ArrayList<Vec3>()
            path += Vec3(t.first, 0f, t.second)
            for (v in via) path += Vec3(v.first, 0f, v.second)
            path += Vec3(b.first, 0f, b.second)
            val shell = Hole(order, name, 3, path, width, emptyList(), ponds, terrain)
            val treeList = trees ?: Courses.scatterTrees(shell, width, density, guardian, 5000L + number * 131L)
            return Hole(order, name, par ?: Courses.parFor(shell.length), path, width, treeList, ponds, terrain)
        }
    }

    private fun parseHole(m: Map<*, *>, terrain: Terrain): HoleDef {
        val number = m.num("number")?.toInt() ?: throw FormatException("hole without a \"number\"")
        val tees = pointMap(m["tees"], "hole $number tees").ifEmpty { throw FormatException("hole $number has no tees") }
        val baskets = pointMap(m["baskets"], "hole $number baskets").ifEmpty { throw FormatException("hole $number has no baskets") }
        val via = (m["via"] as? List<*>)?.map { pt(it, "hole $number via") } ?: emptyList()
        val trees = (m["trees"] as? List<*>)?.map { e ->
            val a = numbers(e, "hole $number tree")
            if (a.size < 4) throw FormatException("hole $number: a tree needs [x, z, height, canopy]")
            Tree(a[0], a[1], a[2], (a[2] * 0.03f).coerceIn(0.15f, 0.45f), a[3], if (a.size > 4) a[4].toInt() else 0)
        }
        val ponds = (m["ponds"] as? List<*>)?.map { e ->
            val a = numbers(e, "hole $number pond")
            if (a.size < 4) throw FormatException("hole $number: a pond needs [cx, cz, rx, rz]")
            Pond(a[0], a[1], a[2], a[3]).also { it.level = terrain.heightAt(a[0], a[1]) - 0.05f }
        } ?: emptyList()
        return HoleDef(
            number, m.str("name") ?: "Hole $number", m.num("fairwayWidth") ?: 14f, tees, baskets, via, trees, ponds,
            m.num("treeDensity") ?: 0.5f, m.num("guardianChance") ?: 0.2f,
        )
    }

    private fun parseTerrain(m: Map<*, *>): Terrain {
        val nx = m.num("nx")?.toInt() ?: throw FormatException("terrain needs \"nx\"")
        val nz = m.num("nz")?.toInt() ?: throw FormatException("terrain needs \"nz\"")
        val list = m["heights"] as? List<*> ?: throw FormatException("terrain needs \"heights\"")
        if (list.size != nx * nz) throw FormatException("terrain has ${list.size} heights, expected ${nx * nz}")
        val hs = FloatArray(list.size) { (list[it] as? Number)?.toFloat() ?: throw FormatException("terrain height $it is not a number") }
        return GridTerrain(m.num("x0") ?: 0f, m.num("z0") ?: 0f, m.num("cell") ?: 2f, nx, nz, hs)
    }

    private fun pointMap(o: Any?, what: String): Map<String, Pair<Float, Float>> {
        val m = o as? Map<*, *> ?: return emptyMap()
        val out = LinkedHashMap<String, Pair<Float, Float>>()
        for ((k, v) in m) out[k.toString()] = pt(v, what)
        return out
    }

    private fun pt(o: Any?, what: String): Pair<Float, Float> {
        val a = numbers(o, what)
        if (a.size < 2) throw FormatException("$what: expected [x, z]")
        return a[0] to a[1]
    }

    private fun numbers(o: Any?, what: String): List<Float> {
        val l = o as? List<*> ?: throw FormatException("$what: expected a list of numbers")
        return l.map { (it as? Number)?.toFloat() ?: throw FormatException("$what: not a number") }
    }

    private fun Map<*, *>.str(k: String) = this[k] as? String
    private fun Map<*, *>.num(k: String) = (this[k] as? Number)?.toFloat()
}

/** A small JSON reader (objects, arrays, strings, numbers, booleans, null). Kept in core so tests run on the plain JVM. */
object MiniJson {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skip()
        val v = p.value()
        p.skip()
        if (p.i < text.length) throw CourseFile.FormatException("unexpected text after the JSON value at ${p.i}")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(msg: String): Nothing = throw CourseFile.FormatException("JSON error at $i: $msg")

        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(): Any? {
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else fail("unexpected '$c'")
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) fail("expected $word")
            i += word.length
            return v
        }

        fun num(): Double {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(st, i).toDoubleOrNull() ?: fail("bad number")
        }

        fun str(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (i < s.length && s[i] != '"') {
                var c = s[i++]
                if (c == '\\') {
                    if (i >= s.length) fail("bad escape")
                    c = s[i++]
                    when (c) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> {
                            if (i + 4 > s.length) fail("bad \\u escape")
                            sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                        }
                        else -> sb.append(c)
                    }
                } else sb.append(c)
            }
            if (i >= s.length) fail("unterminated string")
            i++ // closing quote
            return sb.toString()
        }

        fun arr(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skip()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                skip(); out += value(); skip()
                if (i >= s.length) fail("unterminated array")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return out }
                fail("expected , or ]")
            }
        }

        fun obj(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skip()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                skip()
                if (i >= s.length || s[i] != '"') fail("expected a key")
                val k = str(); skip()
                if (i >= s.length || s[i] != ':') fail("expected :")
                i++; skip()
                out[k] = value(); skip()
                if (i >= s.length) fail("unterminated object")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return out }
                fail("expected , or }")
            }
        }
    }
}
