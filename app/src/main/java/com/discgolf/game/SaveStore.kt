package com.discgolf.game

import android.content.Context
import com.discgolf.game.core.PlayerProfile
import com.discgolf.game.core.ProfileCodec

/** One golfer, saved on the device. */
object SaveStore {
    private const val FILE = "disc_golf_tour"
    private const val KEY = "profile"

    fun load(ctx: Context): PlayerProfile? {
        val s = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        return ProfileCodec.decode(s)
    }

    fun save(ctx: Context, p: PlayerProfile) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, ProfileCodec.encode(p)).apply()
    }

    /** Which course and layout the player last picked: (course id, layout name). */
    fun loadCourseChoice(ctx: Context): Pair<String, String> {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return (sp.getString("course", "") ?: "") to (sp.getString("layout", "") ?: "")
    }

    fun saveCourseChoice(ctx: Context, courseId: String, layoutName: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("course", courseId).putString("layout", layoutName).apply()
    }

    fun loadTimeScale(ctx: Context): Float =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat("timeScale", 1f).let { if (it == 0.5f || it == 0.25f) it else 1f }

    fun saveTimeScale(ctx: Context, v: Float) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat("timeScale", v).apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
