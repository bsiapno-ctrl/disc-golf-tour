package com.discgolf.game

import android.content.Context
import com.discgolf.game.core.Course
import com.discgolf.game.core.CourseFile
import com.discgolf.game.core.Courses
import com.discgolf.game.core.Layout
import java.io.File

/**
 * Every course the game can play: the built-in one, any course .json bundled in the app (assets/courses), and any course file
 * dropped in the app's own folder on the phone (`Android/data/com.discgolf.game/files/courses/`).
 */
object CourseLibrary {
    class Loaded(val courses: List<Course>, val problems: List<String>)

    private var cache: Loaded? = null

    /** Folder where players can put their own course files. Created on first use. */
    fun userDir(ctx: Context): File? =
        ctx.getExternalFilesDir(null)?.let { File(it, "courses").apply { mkdirs() } }

    fun load(ctx: Context, force: Boolean = false): Loaded {
        cache?.let { if (!force) return it }
        val courses = ArrayList<Course>()
        val problems = ArrayList<String>()
        courses += Courses.builtIn()

        fun add(label: String, text: String) {
            try {
                val c = CourseFile.parse(text)
                if (courses.any { it.id == c.id }) problems += "$label: a course with id \"${c.id}\" is already loaded"
                else courses += c
            } catch (e: CourseFile.FormatException) {
                problems += "$label: ${e.message}"
            } catch (e: Exception) {
                problems += "$label: ${e.javaClass.simpleName} ${e.message ?: ""}"
            }
        }

        try {
            ctx.assets.list("courses")?.filter { it.endsWith(".json") }?.sorted()?.forEach { name ->
                add(name, ctx.assets.open("courses/$name").bufferedReader().use { it.readText() })
            }
        } catch (_: Exception) {
        }
        userDir(ctx)?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name }?.forEach { f ->
            add(f.name, try { f.readText() } catch (e: Exception) { problems += "${f.name}: unreadable"; return@forEach })
        }
        return Loaded(courses, problems).also { cache = it }
    }

    /** The course and layout the player picked, or the built-in championship layout if that is gone. */
    fun selected(ctx: Context): Pair<Course, Layout> {
        val all = load(ctx).courses
        val (cid, lname) = SaveStore.loadCourseChoice(ctx)
        val course = all.firstOrNull { it.id == cid } ?: all.first()
        val layout = course.layouts.firstOrNull { it.name == lname } ?: course.layouts.first()
        return course to layout
    }
}
