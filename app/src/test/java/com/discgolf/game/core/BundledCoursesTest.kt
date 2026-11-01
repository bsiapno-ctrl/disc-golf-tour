package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Parses every bundled course asset and sanity-checks its layouts. */
class BundledCoursesTest {
    private fun dir(): File = listOf("src/main/assets/courses", "app/src/main/assets/courses")
        .map { File(it) }.first { it.isDirectory }

    @Test fun everyBundledCourseParsesAndIsPlayable() {
        val files = dir().listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        assertTrue(files.isNotEmpty())
        val ids = HashSet<String>()
        for (f in files) {
            assertTrue("${f.name} too big", f.length() < 200_000)
            val c = CourseFile.parse(f.readText())
            assertTrue("duplicate id ${c.id}", ids.add(c.id))
            assertTrue(c.layouts.isNotEmpty())
            for (l in c.layouts) for ((i, h) in l.holes.withIndex()) {
                val w = "${c.name}/${l.name}/${i + 1}"
                assertEquals(w, i + 1, h.number)
                assertTrue("$w par ${h.par}", h.par in 3..5)
                assertTrue("$w length ${h.length}", h.length >= 30f)
                assertTrue("$w tee==basket", h.tee.x != h.basket.x || h.tee.z != h.basket.z)
                val expected = when { h.length < 100f -> 3; h.length < 165f -> 4; else -> 5 }
                assertTrue("$w par ${h.par} vs length ${h.length}", Math.abs(h.par - expected) <= 2)
                assertTrue("$w basket unreachable (${h.length} m)", h.length <= 100f * h.par)
            }
        }
    }
}
