package com.discgolf.game.core

/**
 * A disc in the bag. The four flight numbers follow the usual disc golf convention:
 *  - speed: how fast it wants to be thrown (1-14)
 *  - glide: how well it stays in the air (1-7)
 *  - turn:  high-speed tendency, 0 to -5. More negative = turns over (to the right for a RHBH) sooner
 *  - fade:  low-speed finish, 0 to 5. Bigger = harder hook back to the left for a RHBH
 *
 * [unlockLevel] gates the disc behind the player's level, so the bag grows as the golfer does.
 */
enum class DiscModel(
    val displayName: String,
    val kind: String,
    val speed: Int,
    val glide: Int,
    val turn: Float,
    val fade: Float,
    val unlockLevel: Int,
    val colorRgb: Int,
    /** True for discs whose numbers are the published Discraft flight numbers (docs/discraft_flight_data.json). */
    val discraft: Boolean = false,
) {
    DART("Dart", "Putter", 2, 3, 0f, 1f, 1, 0xE8453C),
    ANVIL("Anvil", "Midrange", 5, 5, -1f, 1f, 1, 0x2E86DE),
    DRIFTER("Drifter", "Midrange", 4, 5, -2f, 1f, 3, 0x27AE60),
    FALCON("Falcon", "Fairway", 9, 5, -1f, 2f, 2, 0xF1C40F),
    ARROW("Arrow", "Fairway", 7, 5, -2f, 1f, 4, 0x9B59B6),
    HAMMER("Hammer", "Distance", 12, 5, -1f, 3f, 5, 0xE67E22),
    COMET("Comet", "Distance", 11, 6, -3f, 2f, 7, 0x1ABC9C),
    WRAITH("Wraith", "Distance", 13, 6, -2f, 3f, 9, 0x34495E),

    // ---- Discraft molds (name, type and Speed/Glide/Turn/Fade as listed on discraft.com, retrieved 2026-11-02)
    ANAX("Anax", "Distance", 10, 6, 0.0f, 3.0f, 8, 0xD35400, discraft = true),
    AVENGER_SS("Avenger SS", "Distance", 10, 5, -3.0f, 1.0f, 8, 0xC0392B, discraft = true),
    THRASHER("Thrasher", "Distance", 12, 5, -3.0f, 2.0f, 11, 0xF39C12, discraft = true),
    CRANK("Crank", "Distance", 13, 5, -2.0f, 2.0f, 13, 0xE74C3C, discraft = true),
    DRIVE("Drive", "Distance", 11, 5, -1.0f, 2.0f, 9, 0xE67E22, discraft = true),
    FLICK("Flick", "Distance", 12, 3, 1.0f, 5.0f, 12, 0xD35400, discraft = true),
    FORCE("Force", "Distance", 12, 5, 0.0f, 3.0f, 11, 0xC0392B, discraft = true),
    HADES("Hades", "Distance", 12, 6, -3.0f, 2.0f, 11, 0xF39C12, discraft = true),
    HEAT("Heat", "Distance", 9, 6, -3.0f, 1.0f, 1, 0xE74C3C, discraft = true),
    NUKE("Nuke", "Distance", 13, 5, -1.0f, 3.0f, 13, 0xE67E22, discraft = true),
    NUKE_OS("Nuke OS", "Distance", 13, 4, 0.0f, 4.0f, 14, 0xD35400, discraft = true),
    NUKE_SS("Nuke SS", "Distance", 13, 5, -3.0f, 3.0f, 13, 0xC0392B, discraft = true),
    SCORCH("Scorch", "Distance", 11, 6, -2.0f, 2.0f, 9, 0xF39C12, discraft = true),
    VENOM("Venom", "Distance", 13, 5, 0.0f, 3.0f, 13, 0xE74C3C, discraft = true),
    ZEUS("Zeus", "Distance", 12, 5, -2.0f, 3.0f, 11, 0xE67E22, discraft = true),
    ATHENA("Athena", "Fairway", 7, 5, 0.0f, 2.0f, 4, 0x9B59B6, discraft = true),
    CICADA("Cicada", "Fairway", 7, 6, -1.0f, 1.0f, 1, 0x8E44AD, discraft = true),
    MANTIS("Mantis", "Fairway", 8, 4, -2.0f, 2.0f, 5, 0xF4D03F, discraft = true),
    PASSION("Passion", "Fairway", 8, 5, -1.0f, 1.0f, 5, 0xAF7AC5, discraft = true),
    PREDATOR("Predator", "Fairway", 9, 4, 1.0f, 4.0f, 7, 0xF1C40F, discraft = true),
    RAPTOR("Raptor", "Fairway", 9, 4, 0.0f, 3.0f, 6, 0x9B59B6, discraft = true),
    STALKER("Stalker", "Fairway", 7, 5, -1.0f, 2.0f, 4, 0x8E44AD, discraft = true),
    STING("Sting", "Fairway", 7, 5, -2.0f, 1.0f, 4, 0xF4D03F, discraft = true),
    STRATUS("Stratus", "Fairway", 5, 4, -3.0f, 1.0f, 2, 0xAF7AC5, discraft = true),
    UNDERTAKER("Undertaker", "Fairway", 9, 5, -1.0f, 2.0f, 6, 0xF1C40F, discraft = true),
    VULTURE("Vulture", "Fairway", 10, 5, 0.0f, 2.0f, 8, 0x9B59B6, discraft = true),
    BUZZZ("Buzzz", "Midrange", 5, 4, -1.0f, 1.0f, 1, 0x27AE60, discraft = true),
    BUZZZ_SS("Buzzz SS", "Midrange", 5, 4, -2.0f, 1.0f, 3, 0x16A085, discraft = true),
    MALTA("Malta", "Midrange", 5, 4, 1.0f, 3.0f, 3, 0x5DADE2, discraft = true),
    METEOR("Meteor", "Midrange", 5, 4, -3.0f, 1.0f, 3, 0x3498DB, discraft = true),
    SWARM("Swarm", "Midrange", 5, 3, 0.0f, 4.0f, 3, 0x2E86DE, discraft = true),
    BANGER_GT("Banger GT", "Putter", 2, 3, 0.0f, 1.0f, 1, 0xEC7063, discraft = true),
    CHALLENGER("Challenger", "Putter", 2, 3, 0.0f, 2.0f, 1, 0xFF7F50, discraft = true),
    CHALLENGER_OS("Challenger OS", "Putter", 2, 3, 0.0f, 3.0f, 1, 0xFA8072, discraft = true),
    CHALLENGER_SS("Challenger SS", "Putter", 2, 3, -1.0f, 2.0f, 1, 0xCD6155, discraft = true),
    FIERCE("Fierce", "Putter", 3, 4, -2.0f, 0.0f, 2, 0xE8453C, discraft = true),
    FOCUS("Focus", "Putter", 2, 2, -1.0f, 2.0f, 1, 0xEC7063, discraft = true),
    KRATOS("Kratos", "Putter", 3, 3, 0.0f, 3.0f, 2, 0xFF7F50, discraft = true),
    LUNA("Luna", "Putter", 3, 4, 0.0f, 2.0f, 1, 0xFA8072, discraft = true),
    MAGNET("Magnet", "Putter", 2, 3, -1.0f, 1.0f, 1, 0xCD6155, discraft = true),
    RINGER("Ringer", "Putter", 4, 4, 0.0f, 2.0f, 3, 0xE8453C, discraft = true),
    ROACH("Roach", "Putter", 2, 4, 0.0f, 1.0f, 1, 0xEC7063, discraft = true),
    ZONE("Zone", "Putter", 4, 3, 0.0f, 3.0f, 3, 0xFF7F50, discraft = true),
    ZONE_OS("Zone OS", "Putter", 4, 2, 1.0f, 5.0f, 4, 0xFA8072, discraft = true);

    /** Ideal release speed in m/s. Throw slower than this and the disc fades early; throw faster and it turns over. */
    val idealSpeed: Float get() = 10f + speed * 1.7f

    /** The hardest this disc can be thrown at all (arm speed cap). Flattens above speed 5 so a 12-speed pro drive carries about 150 m. */
    val speedLimit: Float get() = 20f + 0.95f * minOf(speed, 5) + 0.5f * maxOf(0, speed - 5)

    /** Drag scale: fast discs have sharp edges and cut through the air, putters are blunt. */
    val dragScale: Float get() = 1.55f - speed * 0.065f + (if (speed <= 6) 0.18f else 0f)

    val isPutter: Boolean get() = kind == "Putter"

    /** Simple stability read-out from turn + fade, like the labels on a flight chart. */
    val stability: String get() = stabilityOf(turn, fade)

    /**
     * Top-down sketch of the flight for the picker: [steps]+1 points of (lateral, forward), both 0..1-ish,
     * lateral positive = right for a right-hand backhand. Turn pushes right early, fade hooks left at the end.
     */
    fun flightSketch(steps: Int = 12): List<Pair<Float, Float>> = (0..steps).map { i ->
        val u = i / steps.toFloat()
        val turnPart = -turn * 0.30f * kotlin.math.sin(Math.PI.toFloat() * kotlin.math.min(1f, u * 1.35f)).coerceAtLeast(0f) * (1f - 0.35f * u)
        val fu = ((u - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val fadePart = -fade * 0.30f * fu * fu
        Pair(turnPart + fadePart, u)
    }

    companion object {
        /** s = turn + fade: at most -2 reads understable, 2 or more overstable. */
        fun stabilityOf(turn: Float, fade: Float): String {
            val s = turn + fade
            return if (s <= -2f) "UNDERSTABLE" else if (s >= 2f) "OVERSTABLE" else "STABLE"
        }

        val kindOrder = listOf("Putter", "Midrange", "Fairway", "Distance")

        /** Discs of one type, slowest first (for the picker's type filter). */
        fun ofKind(kind: String, level: Int): List<DiscModel> =
            unlockedFor(level).filter { it.kind == kind }.sortedWith(compareBy({ it.speed }, { it.unlockLevel }, { it.ordinal }))

        fun unlockedFor(level: Int): List<DiscModel> = values().filter { it.unlockLevel <= level }
        val putter = DART
    }
}

/** How the player releases the disc. Chosen with up/down on the d-pad before the throw. */
enum class ShotShape(val label: String, val bankDegrees: Float, val noseDegrees: Float, val elevDegrees: Float = 0f, val rolls: Boolean = false) {
    HYZER("Hyzer", 20f, 0.5f),
    HYZER_FLIP("Hyzer flip", 32f, 0.5f, 1f),
    FLAT("Flat", 0f, 1f),
    ANHYZER("Anhyzer", -16f, 1f),
    ROLLER("Roller", -14f, -1.5f, -3f, rolls = true);

    fun next(): ShotShape = values()[(ordinal + 1) % values().size]
    fun prev(): ShotShape = values()[(ordinal + values().size - 1) % values().size]
}

/** Throwing hand. Left-handed players mirror every curve. */
enum class Handedness(val sign: Float, val label: String) {
    RIGHT(1f, "Right-handed"),
    LEFT(-1f, "Left-handed")
}
