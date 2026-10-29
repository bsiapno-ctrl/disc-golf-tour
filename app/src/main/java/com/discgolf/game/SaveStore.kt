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

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
