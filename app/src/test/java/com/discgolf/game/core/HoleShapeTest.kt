package com.discgolf.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Doglegs, bushes, lanes: every bundled hole must be playable, and a dogleg must really shut the straight line. */
class HoleShapeTest {
    private fun dir(): File = listOf("src/main/assets/courses", "app/src/main/assets/courses")
        .map { File(it) }.first { it.isDirectory }

    private fun line(vararg p: Pair<Float, Float>) = p.map { Vec3(it.first, 0f, it.second) }

    @Test fun doglegDirectionAndAngle() {
        // Heading north then turning west (x decreases) is a left turn.
        val left = Hole(1, "l", 4, line(0f to 0f, 0f to 60f, -50f to 100f), 12f, emptyList(), emptyList())
        val d = dogleg(left)!!
        assertEquals(BendDir.LEFT, d.dir)
        assertEquals(51f, d.angleDeg, 1.5f)
        assertEquals(60f, d.apexDist, 0.1f)
        val right = Hole(1, "r", 4, line(0f to 0f, 0f to 60f, 50f to 100f), 12f, emptyList(), emptyList())
        assertEquals(BendDir.RIGHT, dogleg(right)!!.dir)
        assertEquals("DOGLEG RIGHT 124m", previewLine(right))
        assertNull(dogleg(Hole(1, "s", 3, line(0f to 0f, 0f to 80f), 12f, emptyList(), emptyList())))
        assertNull(dogleg(Hole(1, "s", 3, line(0f to 0f, 1f to 40f, 0f to 80f), 12f, emptyList(), emptyList())))
    }

    @Test fun bushStopsLowDiscsButNotHighOnes() {
        val bush = Bush(0f, 20f, 1.6f)
        val hole = Hole(1, "b", 3, line(0f to 0f, 0f to 60f), 20f, emptyList(), emptyList(), FlatTerrain, bushes = listOf(bush))
        val env = HoleEnvironment(hole, Vec3.ZERO, java.util.Random(1), 0f)
        val low = DiscFlight(DiscModel.ANVIL, 1f, 0.5f, Vec3(0f, 0.8f, 19.5f), Vec3(0f, 0f, 20f), 0f, 0f, env)
        env.resolve(low)
        assertTrue(FlightEvent.TREE in low.events)
        val high = DiscFlight(DiscModel.ANVIL, 1f, 0.5f, Vec3(0f, 4f, 19.5f), Vec3(0f, 0f, 20f), 0f, 0f, env)
        env.resolve(high)
        assertTrue(FlightEvent.TREE !in high.events)
    }

    @Test fun autoGuardiansShutTheInsideOfADogleg() {
        val path = line(0f to 0f, 0f to 70f, -45f to 120f)
        val shell = Hole(1, "dl", 4, path, 12f, emptyList(), emptyList())
        val trees = Courses.scatterTrees(shell, 12f, 0.4f, 0.2f, 42L)
        val hole = Hole(1, "dl", 4, path, 12f, trees, emptyList())
        assertTrue(Lanes.directLineBlocked(hole))
        assertTrue(Lanes.laneExists(hole))
    }

    @Test fun everyBundledHoleHasAClearLaneAndDoglegsBlockTheDirectLine() {
        val files = dir().listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }
        var doglegs = 0
        for (f in files) {
            val c = CourseFile.parse(f.readText())
            for (l in c.layouts) for (h in l.holes) {
                val w = "${c.id}/${l.name}/${h.number}"
                assertTrue("$w has no clear lane (1.5 m) from tee to basket", Lanes.laneExists(h))
                if (dogleg(h) != null && c.id != "picnic-island") {   // Picnic Island trees are traced from satellite imagery, not invented to block the line
                    doglegs++
                    assertTrue("$w has a dogleg but the direct tee-basket line is open", Lanes.directLineBlocked(h))
                }
            }
        }
        assertTrue(doglegs > 20)
    }

    @Test fun courseFileReadsBushesObAndNotes() {
        val c = CourseFile.parse("""{"name":"X","holes":[{"number":1,"tees":{"T":[0,0]},"baskets":{"B":[0,80]},
            "bushes":[[3,30,1.5],[-3,32,1.2]],"ob":[[20,40,5,30]],"obstacleNotes":"Creek right.","treeDensity":0.1}]}""")
        val h = c.layouts[0].holes[0]
        assertEquals(2, h.bushes.size)
        assertEquals(Surface.OUT_OF_BOUNDS, h.surfaceAt(20f, 40f))
        assertEquals("Creek right.", h.obstacleNotes)
        assertEquals("STRAIGHT 80m · Creek right.", previewLine(h, withNotes = true))
    }
}
