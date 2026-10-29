package com.discgolf.game.core

import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class Phase { INTRO, AIMING, CHARGING, ACCURACY, FLIGHT, RESULT, HOLE_DONE, ROUND_DONE }

/** The banner shown after each throw. */
class ThrowReport(
    val distance: Float,
    val toPin: Float,
    val headline: String,
    val sweetSpot: Boolean,
    val perfectPower: Boolean,
    val holed: Boolean,
)

/**
 * The whole game, minus drawing and Android. One round on one course for one golfer.
 *
 * Feed it input ([aim], [pressAction], [releaseAction], ...) and call [update] every frame.
 * Read its public state to draw the scene and HUD. All the RPG progression happens here:
 * whenever the golfer does something, the matching stat's XP bar fills (see [PlayerProfile]).
 */
class GameController(
    val profile: PlayerProfile,
    val course: List<Hole> = Courses.defaultCourse(),
    seed: Long = System.nanoTime(),
    private val autoAccuracy: Boolean = false,
) {
    private val rng = Random(seed)

    var phase = Phase.INTRO
        private set
    var holeIndex = 0
        private set
    val hole: Hole get() = course[holeIndex]

    // ---- Round state
    val card = IntArray(course.size)
    var strokes = 0
        private set
    var lie: Vec3 = Vec3.ZERO
        private set
    var wind: Vec3 = Vec3.ZERO
        private set

    // ---- Shot setup (what the player is choosing)
    var aimHeading = 0f
        private set
    var disc: DiscModel = DiscModel.DART
        private set
    var shape: ShotShape = ShotShape.FLAT
        private set
    val bag: List<DiscModel> get() = DiscModel.unlockedFor(profile.level)

    // ---- Derived info for the HUD / guide
    var isPutt = false
        private set
    var distanceToPin = 0f
        private set
    /** Power (0..1) a perfect release needs to reach the pin; above 1 means out of range. */
    var pinPower = 0f
        private set
    var guide: List<Vec3> = emptyList()
        private set

    // ---- Live throw
    var meter: SwingMeter? = null
        private set
    var flight: DiscFlight? = null
        private set
    var throwOrigin: Vec3 = Vec3.ZERO
        private set
    /** Seconds since release, for the swing animation. -1 when no swing is in progress. */
    var swingClock = -1f
        private set
    var report: ThrowReport? = null
        private set
    var headline: String = ""
        private set

    // ---- Progress popups (HUD drains these)
    val popups = ArrayList<ProgressEvent>()

    var phaseTime = 0f
        private set

    private var lastThrowDisc: DiscModel = DiscModel.DART
    private var accumulator = 0f
    private var dirty = true
    private var dirtyTimer = 0f
    private var env: HoleEnvironment = HoleEnvironment(hole, Vec3.ZERO, rng, 0f)
    private var puttAttempt = false
    private var preThrowPin = 0f
    private var preThrowLie = Vec3.ZERO

    val par get() = hole.par
    val totalPar get() = course.sumOf { it.par }
    val finished get() = phase == Phase.ROUND_DONE

    init { startHole(0) }

    // =====================================================================
    // Input
    // =====================================================================

    /** Rotate the aim. [radians] is signed; positive turns right. */
    fun aim(radians: Float) {
        if (phase != Phase.AIMING) return
        val centre = dirToHeading((aimTarget() - lie).flat().normalized())
        var h = aimHeading + radians
        val lim = 1.35f
        h = centre + clamp(angleDiff(h, centre), -lim, lim)
        aimHeading = h
        dirty = true
    }

    fun nextDisc(dir: Int) {
        if (phase != Phase.AIMING) return
        val b = bag
        val i = b.indexOf(disc).let { if (it < 0) 0 else it }
        disc = b[(i + dir + b.size) % b.size]
        dirty = true
    }

    fun nextShape(dir: Int) {
        if (phase != Phase.AIMING) return
        shape = if (dir > 0) shape.next() else shape.prev()
        dirty = true
    }

    /** The action button went down. Starts the power bar, or taps the accuracy marker, or advances a screen. */
    fun pressAction() {
        when (phase) {
            Phase.INTRO -> if (phaseTime > 0.4f) setPhase(Phase.AIMING)
            Phase.AIMING -> {
                refreshGuide()
                val m = SwingMeter(
                    profile.pct(Stat.ACCURACY), profile.pct(Stat.TOUCH), isPutt, autoAccuracy,
                )
                meter = m
                m.press()
                setPhase(Phase.CHARGING)
            }
            Phase.ACCURACY -> meter?.press()
            Phase.RESULT -> if (phaseTime > 0.6f) afterResult()
            Phase.HOLE_DONE -> if (phaseTime > 0.8f) nextHole()
            else -> Unit
        }
    }

    fun releaseAction() {
        if (phase == Phase.CHARGING) {
            meter?.release()
            syncMeterPhase()
        }
    }

    // =====================================================================
    // Frame update
    // =====================================================================

    fun update(dt: Float) {
        phaseTime += dt
        if (swingClock >= 0f) swingClock += dt

        when (phase) {
            Phase.INTRO -> if (phaseTime > 3.2f) setPhase(Phase.AIMING)
            Phase.AIMING -> {
                dirtyTimer += dt
                if (dirty && dirtyTimer > 0.06f) { dirtyTimer = 0f; refreshGuide() }
            }
            Phase.CHARGING, Phase.ACCURACY -> {
                val m = meter ?: return
                m.update(dt)
                syncMeterPhase()
            }
            Phase.FLIGHT -> stepFlight(dt)
            Phase.RESULT -> if (phaseTime > 2.6f) afterResult()
            else -> Unit
        }
    }

    private fun syncMeterPhase() {
        val m = meter ?: return
        if (phase == Phase.CHARGING && m.phase != SwingMeter.Phase.CHARGING) setPhase(Phase.ACCURACY)
        if (m.isFinished && (phase == Phase.ACCURACY || phase == Phase.CHARGING)) launch(m)
    }

    // =====================================================================
    // Throwing
    // =====================================================================

    private fun spec() = ThrowSpec(lie, aimHeading, disc, shape, isPutt)

    private fun launch(m: SwingMeter) {
        preThrowPin = lie.distanceXZ(hole.basket)
        preThrowLie = lie
        puttAttempt = isPutt
        lastThrowDisc = disc
        throwOrigin = lie
        val noise = rng.nextFloat() * 2f - 1f
        flight = Launcher.launch(profile, spec(), env, m.power, m.accuracyError, m.overcooked, m.perfectPower, noise)
        accumulator = 0f
        swingClock = 0f
        setPhase(Phase.FLIGHT)
    }

    private fun stepFlight(dt: Float) {
        val f = flight ?: return
        accumulator += min(dt, 0.05f)
        var n = 0
        while (accumulator >= Launcher.SIM_DT && n < 12 && !f.isDone) {
            f.step(Launcher.SIM_DT)
            accumulator -= Launcher.SIM_DT
            n++
        }
        if (f.isDone) finishThrow(f)
    }

    private fun finishThrow(f: DiscFlight) {
        val m = meter
        val end = Vec3(f.pos.x, 0f, f.pos.z)
        val surface = hole.surfaceAt(end.x, end.z)
        val splash = FlightEvent.SPLASH in f.events || surface == Surface.WATER
        val oob = surface == Surface.OUT_OF_BOUNDS
        val distance = preThrowLie.distanceXZ(end)
        val pinAfter = end.distanceXZ(hole.basket)

        strokes += 1
        profile.totalThrows += 1
        var title = ""
        val gains = ArrayList<ProgressEvent>()
        gains += profile.addXp(5)

        if (f.holed) {
            title = if (strokes == 1) "HOLE IN ONE!" else "IN THE BASKET!"
        } else if (splash || oob) {
            strokes += 1
            title = if (splash) "WATER! +1 stroke" else "OUT OF BOUNDS! +1 stroke"
            lie = preThrowLie
        } else {
            lie = end
            title = when {
                FlightEvent.TREE in f.events -> "Tree kick"
                FlightEvent.CHAINS in f.events -> "Chains! Just missed"
                FlightEvent.POLE in f.events -> "Pole hit"
                else -> String.format("%.0f m", distance)
            }
        }

        // ---- RPG: use it and it grows
        val sweet = m?.hitSweetSpot == true
        val perfect = m?.perfectPower == true
        val power = m?.power ?: 0f
        if (puttAttempt) {
            var a = 3f
            if (f.holed) a += 9f + min(preThrowPin, 20f) / 2f
            else if (pinAfter < 2f) a += 2f
            gains += profile.addStatUse(Stat.PUTTING, a)
        } else {
            gains += profile.addStatUse(Stat.POWER, distance / 8f * (0.5f + 0.5f * power))
            var spin = 1.5f + (if (perfect) 3f else 0f)
            if (power > 0.55f && lastThrowDisc.idealSpeed * 0.8f < Launcher.launchSpeed(profile, ThrowSpec(preThrowLie, 0f, lastThrowDisc, shape, false), power, false)) spin += 2f
            gains += profile.addStatUse(Stat.SPIN, spin)
            if (preThrowPin <= 45f) {
                var t = 3f
                if (f.holed) t += 12f else if (pinAfter < 5f) t += 9f else if (pinAfter < 10f) t += 5f
                gains += profile.addStatUse(Stat.TOUCH, t)
            }
        }
        var acc = if (sweet) 5f else 1f
        if (!f.holed && !splash && !oob && surface == Surface.FAIRWAY) acc += 2f
        gains += profile.addStatUse(Stat.ACCURACY, acc)
        if (distance > profile.longestThrow && !splash && !oob) profile.longestThrow = distance

        popups += gains
        report = ThrowReport(distance, pinAfter, title, sweet, perfect, f.holed)
        headline = title

        if (!f.holed && !splash && !oob && pinAfter < TAP_IN_RANGE) {
            // Inside a metre: nobody makes you putt it. Tap it in for one more stroke.
            strokes += 1
            headline = "Tap-in"
            completeHole(f)
        } else if (f.holed) {
            completeHole(f)
        } else if (strokes >= hole.par + 5) {
            headline = "Pick up: max strokes"
            completeHole(f)
        } else {
            prepareNextShot()
            setPhase(Phase.RESULT)
        }
    }

    private fun afterResult() {
        if (phase == Phase.RESULT) setPhase(Phase.AIMING)
    }

    // =====================================================================
    // Holes and rounds
    // =====================================================================

    private fun completeHole(f: DiscFlight?) {
        card[holeIndex] = strokes
        val rel = strokes - hole.par
        val gains = ArrayList<ProgressEvent>()
        val ace = strokes == 1
        val xp = when {
            ace -> 200
            rel <= -2 -> 120
            rel == -1 -> 60
            rel == 0 -> 30
            rel == 1 -> 12
            else -> 5
        }
        if (ace) profile.holesInOne++
        if (rel < 0) profile.birdies++
        gains += profile.addXp(xp)
        popups += gains
        headline = scoreName(strokes, hole.par)
        setPhase(Phase.HOLE_DONE)
    }

    private fun nextHole() {
        if (holeIndex >= course.size - 1) {
            finishRound()
        } else {
            startHole(holeIndex + 1)
        }
    }

    private fun finishRound() {
        val rel = card.sum() - totalPar
        profile.roundsPlayed += 1
        if (rel < profile.bestRelPar) profile.bestRelPar = rel
        popups += profile.addXp(50 + max(0, 10 - rel) * 4)
        setPhase(Phase.ROUND_DONE)
    }

    private fun startHole(index: Int) {
        holeIndex = index
        strokes = 0
        lie = hole.tee
        meter = null
        flight = null
        swingClock = -1f
        report = null
        // Gentle breeze most holes, a real gust now and then.
        val speed = rng.nextFloat() * 3f + (if (rng.nextFloat() < 0.25f) rng.nextFloat() * 3f else 0f)
        val ang = rng.nextFloat() * (2f * Math.PI.toFloat())
        wind = Vec3(sin(ang) * speed, 0f, cos(ang) * speed)
        env = HoleEnvironment(hole, wind, rng, profile.pct(Stat.PUTTING))
        prepareNextShot()
        headline = ""
        setPhase(Phase.INTRO)
    }

    /** Pick the aim, disc and shape for a fresh lie, like a caddie would. */
    private fun prepareNextShot() {
        distanceToPin = lie.distanceXZ(hole.basket)
        isPutt = false
        val target = aimTarget()
        aimHeading = dirToHeading((target - lie).flat().normalized())
        shape = ShotShape.FLAT
        val windEnv = WindOnly(wind)
        val b = bag
        if (distanceToPin <= PUTT_RANGE && DiscModel.DART in b) {
            disc = DiscModel.DART
        } else {
            val want = lie.distanceXZ(target)
            var best: DiscModel? = null
            var bestCarry = -1f
            var pick: DiscModel? = null
            for (d in b) {
                if (d == DiscModel.DART) continue
                val c = Launcher.carry(profile, ThrowSpec(lie, aimHeading, d, ShotShape.FLAT, false), windEnv, 1f)
                if (c > bestCarry) { bestCarry = c; best = d }
                // Prefer the slowest disc that still gets there.
                if (c >= want * 1.04f && (pick == null || d.speed < pick.speed)) pick = d
            }
            disc = pick ?: best ?: DiscModel.DART
        }
        dirty = true
        refreshGuide()
    }

    private fun aimTarget(): Vec3 = hole.guideTarget(lie)

    private fun refreshGuide() {
        dirty = false
        distanceToPin = lie.distanceXZ(hole.basket)
        isPutt = disc == DiscModel.DART && distanceToPin <= PUTT_RANGE
        val windEnv = WindOnly(wind)
        // The guide aims for the pin when it is the target; otherwise it shows the line at the power
        // needed to reach the aim spot.
        val toPin = (hole.basket - lie).flat().normalized()
        val aimDir = headingToDir(aimHeading)
        val alongPin = max(0f, toPin.dot(aimDir))
        val targetDist = if (distanceToPin <= 70f || alongPin > 0.9f) distanceToPin * max(alongPin, 0.5f) else lie.distanceXZ(aimTarget())
        val sp = spec()
        pinPower = Launcher.powerForDistance(profile, sp, windEnv, targetDist)
        guide = Launcher.guidePath(profile, sp, windEnv, min(pinPower, 1f))
    }

    // =====================================================================
    // Misc
    // =====================================================================

    private fun setPhase(p: Phase) { phase = p; phaseTime = 0f }

    fun consumePopups(): List<ProgressEvent> {
        if (popups.isEmpty()) return emptyList()
        val out = ArrayList(popups); popups.clear(); return out
    }

    fun relativeToPar(): Int {
        var played = 0; var par = 0
        for (i in course.indices) if (card[i] > 0) { played += card[i]; par += course[i].par }
        return played - par
    }

    private fun angleDiff(a: Float, b: Float): Float {
        var d = a - b
        while (d > Math.PI) d -= (2 * Math.PI).toFloat()
        while (d < -Math.PI) d += (2 * Math.PI).toFloat()
        return d
    }

    companion object {
        const val PUTT_RANGE = 20f
        const val TAP_IN_RANGE = 1.0f

        fun scoreName(strokes: Int, par: Int): String {
            if (strokes == 1) return "HOLE IN ONE!"
            return when (strokes - par) {
                -3 -> "ALBATROSS!"
                -2 -> "EAGLE!"
                -1 -> "BIRDIE"
                0 -> "PAR"
                1 -> "BOGEY"
                2 -> "DOUBLE BOGEY"
                3 -> "TRIPLE BOGEY"
                else -> if (strokes < par) "SUPERB" else "+${strokes - par}"
            }
        }
    }
}
