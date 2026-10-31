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
) {
    DART("Dart", "Putter", 2, 3, 0f, 1f, 1, 0xE8453C),
    ANVIL("Anvil", "Midrange", 5, 5, -1f, 1f, 1, 0x2E86DE),
    DRIFTER("Drifter", "Midrange", 4, 5, -3f, 0f, 3, 0x27AE60),
    FALCON("Falcon", "Fairway", 9, 5, -1f, 2f, 2, 0xF1C40F),
    ARROW("Arrow", "Fairway", 7, 5, -2f, 1f, 4, 0x9B59B6),
    HAMMER("Hammer", "Distance", 12, 5, -1f, 3f, 5, 0xE67E22),
    COMET("Comet", "Distance", 11, 6, -3f, 2f, 7, 0x1ABC9C),
    WRAITH("Wraith", "Distance", 13, 6, -2f, 3f, 9, 0x34495E);

    /** Ideal release speed in m/s. Throw slower than this and the disc fades early; throw faster and it turns over. */
    val idealSpeed: Float get() = 9f + speed * 1.55f

    /** The hardest this disc can be thrown at all (arm speed cap). */
    val speedLimit: Float get() = 20f + speed * 0.95f

    /** Drag scale: fast discs have sharp edges and cut through the air, putters are blunt. */
    val dragScale: Float get() = 1.55f - speed * 0.065f + (if (speed <= 6) 0.18f else 0f)

    companion object {
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
