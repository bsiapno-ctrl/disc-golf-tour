package com.discgolf.game.core

/** The five RPG ratings, shown 1-99 like the old EA sports games. */
enum class Stat(val label: String, val blurb: String) {
    POWER("Power", "Arm speed. Every throw goes farther."),
    ACCURACY("Accuracy", "Wider sweet spot and smaller misses on the accuracy tap."),
    PUTTING("Putting", "Sinks more putts and forgives sloppy ones."),
    TOUCH("Touch", "Slows the power meter so partial shots are easier to time."),
    SPIN("Spin", "Cleaner, steadier flights with less random drift.");
}

/** Starting builds. Each one is a different way to play before you start spending points. */
enum class Archetype(val label: String, val blurb: String, val base: IntArray) {
    //                                                                POW ACC PUT TCH SPN
    BOMBER("Bomber", "Huge arm, wild accuracy.", intArrayOf(48, 24, 24, 26, 36)),
    SNIPER("Sniper", "Lives on the fairway.", intArrayOf(28, 48, 34, 38, 30)),
    PUTTER("Putting Pro", "Wins it on the greens.", intArrayOf(26, 34, 50, 40, 28)),
    ALLROUND("All-Rounder", "No weaknesses, no strengths.", intArrayOf(36, 36, 36, 36, 34));
}

/** Everything the creator screen can change about how the golfer looks. */
data class Appearance(
    var skinTone: Int = 1,
    var hairStyle: Int = 1,
    var hairColor: Int = 1,
    var headwear: Int = 1,
    var shirtColor: Int = 2,
    var shortsColor: Int = 1,
    var build: Int = 1,
) {
    companion object {
        val SKIN = intArrayOf(0xF5D0B0, 0xE0AC85, 0xC68A5E, 0x9C6A44, 0x6E4528, 0x4A2E1B)
        val HAIR = intArrayOf(0x1B1B1B, 0x4A3020, 0x8A5A2B, 0xC9A04E, 0xB8341F, 0xC8C8C8, 0x2E5BD1, 0x2FA84F)
        val SHIRT = intArrayOf(0xE8453C, 0x2E86DE, 0xF1C40F, 0x27AE60, 0xECF0F1, 0x2C3E50, 0xE67E22, 0x9B59B6, 0xFF6FA5, 0x16A085)
        val SHORTS = intArrayOf(0x2C3E50, 0x34495E, 0xBDC3C7, 0x7F5A3A, 0x145A32, 0xB03A2E)

        val HAIR_STYLES = listOf("Buzz", "Short", "Spiky", "Long", "Ponytail", "Bald")
        val HEADWEAR = listOf("None", "Cap", "Cap Back", "Visor")
        val BUILDS = listOf("Slim", "Average", "Stocky")

        const val SKIN_COUNT = 6
        const val HAIR_COLOR_COUNT = 8
        const val SHIRT_COUNT = 10
        const val SHORTS_COUNT = 6
    }
}

/** A short message for the HUD: stat went up, level up, disc unlocked. */
data class ProgressEvent(val kind: Kind, val text: String) {
    enum class Kind { STAT_UP, LEVEL_UP, UNLOCK, XP }
}

/**
 * The golfer: identity, look, RPG ratings, progression, and career numbers.
 *
 * Progression works two ways at once, the way old-school RPGs and the EA golf games did:
 *  1. Use it and it grows. Every stat has its own XP bar that fills when you do the thing that stat is about.
 *  2. Level it and spend it. Overall XP raises the player level; each level hands out points to spend by hand,
 *     and unlocks new discs for the bag.
 */
class PlayerProfile(var name: String = "Rookie") {
    var handedness: Handedness = Handedness.RIGHT
    var look: Appearance = Appearance()
    var archetype: Archetype = Archetype.ALLROUND

    val stats = IntArray(Stat.values().size) { 35 }
    val statXp = FloatArray(Stat.values().size)

    var level = 1
    var xp = 0
    var unspentPoints = 0

    // Career
    var roundsPlayed = 0
    var bestRelPar = NO_ROUND
    var holesInOne = 0
    var birdies = 0
    var longestThrow = 0f
    var totalThrows = 0

    fun stat(s: Stat) = stats[s.ordinal]
    /** Stat as 0..1. */
    fun pct(s: Stat) = (stats[s.ordinal] - 1) / 98f

    /** Top release speed in m/s the arm can produce. Ratings 1..99 map to roughly 15..30 m/s. */
    fun armSpeed() = 15f + 15f * pct(Stat.POWER)

    fun xpForNextLevel() = 120 + 80 * (level - 1)
    fun statXpNeeded(value: Int) = 18f + value * 1.1f

    fun applyArchetype(a: Archetype) {
        archetype = a
        for (s in Stat.values()) stats[s.ordinal] = a.base[s.ordinal]
        statXp.fill(0f)
    }

    /** Fill a stat's own XP bar. Returns events if it ticked up. */
    fun addStatUse(stat: Stat, amount: Float): List<ProgressEvent> {
        val out = ArrayList<ProgressEvent>(1)
        val i = stat.ordinal
        if (stats[i] >= MAX_STAT) return out
        statXp[i] += amount
        while (stats[i] < MAX_STAT && statXp[i] >= statXpNeeded(stats[i])) {
            statXp[i] -= statXpNeeded(stats[i])
            stats[i]++
            out += ProgressEvent(ProgressEvent.Kind.STAT_UP, "${stat.label.uppercase()} +1  (${stats[i]})")
        }
        return out
    }

    /** Add player XP. Levels grant 3 points each and may unlock discs. */
    fun addXp(amount: Int): List<ProgressEvent> {
        val out = ArrayList<ProgressEvent>(2)
        if (level >= MAX_LEVEL) return out
        xp += amount
        while (level < MAX_LEVEL && xp >= xpForNextLevel()) {
            xp -= xpForNextLevel()
            level++
            unspentPoints += POINTS_PER_LEVEL
            out += ProgressEvent(ProgressEvent.Kind.LEVEL_UP, "LEVEL $level!  +$POINTS_PER_LEVEL points")
            DiscModel.values().filter { it.unlockLevel == level }.forEach {
                out += ProgressEvent(ProgressEvent.Kind.UNLOCK, "New disc: ${it.displayName} (${it.kind})")
            }
        }
        return out
    }

    fun spendPoint(stat: Stat): Boolean {
        if (unspentPoints <= 0 || stats[stat.ordinal] >= MAX_STAT) return false
        unspentPoints--
        stats[stat.ordinal]++
        return true
    }

    fun overall(): Int = (stats.sum() / stats.size.toFloat()).toInt()

    companion object {
        const val MAX_STAT = 99
        const val MAX_LEVEL = 20
        const val POINTS_PER_LEVEL = 3
        const val CREATE_POINTS = 10
        const val CREATE_STAT_CAP = 60
        const val NO_ROUND = Int.MAX_VALUE
    }
}

/**
 * Tiny key=value text format, so the profile can live in SharedPreferences without an extra library
 * (and so it can be unit-tested on a plain JVM).
 */
object ProfileCodec {
    fun encode(p: PlayerProfile): String = buildString {
        fun put(k: String, v: Any) { append(k).append('=').append(v).append('\n') }
        put("name", p.name.replace('\n', ' '))
        put("hand", p.handedness.name)
        put("arch", p.archetype.name)
        val l = p.look
        put("look", listOf(l.skinTone, l.hairStyle, l.hairColor, l.headwear, l.shirtColor, l.shortsColor, l.build).joinToString(","))
        put("stats", p.stats.joinToString(","))
        put("statxp", p.statXp.joinToString(","))
        put("level", p.level)
        put("xp", p.xp)
        put("points", p.unspentPoints)
        put("rounds", p.roundsPlayed)
        put("best", p.bestRelPar)
        put("aces", p.holesInOne)
        put("birdies", p.birdies)
        put("longest", p.longestThrow)
        put("throws", p.totalThrows)
    }

    fun decode(text: String): PlayerProfile? {
        return try {
            val map = HashMap<String, String>()
            text.lineSequence().forEach { line ->
                val i = line.indexOf('=')
                if (i > 0) map[line.substring(0, i)] = line.substring(i + 1)
            }
            val p = PlayerProfile(map["name"] ?: return null)
            p.handedness = Handedness.valueOf(map["hand"] ?: "RIGHT")
            p.archetype = Archetype.valueOf(map["arch"] ?: "ALLROUND")
            map["look"]?.split(",")?.map { it.toInt() }?.let { v ->
                if (v.size >= 7) p.look = Appearance(v[0], v[1], v[2], v[3], v[4], v[5], v[6])
            }
            map["stats"]?.split(",")?.forEachIndexed { i, s -> if (i < p.stats.size) p.stats[i] = s.toInt().coerceIn(1, 99) }
            map["statxp"]?.split(",")?.forEachIndexed { i, s -> if (i < p.statXp.size) p.statXp[i] = s.toFloat() }
            p.level = map["level"]?.toInt() ?: 1
            p.xp = map["xp"]?.toInt() ?: 0
            p.unspentPoints = map["points"]?.toInt() ?: 0
            p.roundsPlayed = map["rounds"]?.toInt() ?: 0
            p.bestRelPar = map["best"]?.toInt() ?: PlayerProfile.NO_ROUND
            p.holesInOne = map["aces"]?.toInt() ?: 0
            p.birdies = map["birdies"]?.toInt() ?: 0
            p.longestThrow = map["longest"]?.toFloat() ?: 0f
            p.totalThrows = map["throws"]?.toInt() ?: 0
            p
        } catch (e: Exception) {
            null
        }
    }
}
