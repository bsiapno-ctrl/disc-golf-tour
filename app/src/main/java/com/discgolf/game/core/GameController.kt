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

    /** Sidearm throw. Stays on until switched off. */
    var forehand = false
    /** Fine tuning of the launch angle and release tilt, in degrees. Reset for each new shot. */
    var elevOffset = 0f
    var bankOffset = 0f
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
    /** 1 for a putt inside circle 1 (10 m), 2 for circle 2 (out to PUTT_RANGE), 0 when not putting. */
    val puttCircle: Int get() = if (!isPutt) 0 else if (distanceToPin <= CIRCLE_1) 1 else 2

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
    /** How far down the fairway the caddie aims. Grows with the golfer's reach. */
    private var lookAhead = 55f

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
        bankOffset = 0f
        dirty = true
    }

    fun toggleForehand() {
        if (phase != Phase.AIMING) return
        forehand = !forehand
        dirty = true
    }

    /** Raise (+1) or lower (-1) the launch angle in 1-degree steps. */
    fun adjustAngle(dir: Int) {
        if (phase != Phase.AIMING) return
        elevOffset = (elevOffset + dir).coerceIn(-6f, 14f)
        dirty = true
    }

    /** Tilt the release toward hyzer (+1) or anhyzer (-1) in 2-degree steps. */
    fun adjustTilt(dir: Int) {
        if (phase != Phase.AIMING) return
        bankOffset = (bankOffset + dir * 2f).coerceIn(-16f, 16f)
        dirty = true
    }

    /** The launch angle that will be used, in degrees above horizontal, for display. */
    val launchAngleDeg: Float get() =
        ((if (isPutt) Tuning.puttElevDeg else Tuning.driveElevDeg) + shape.elevDegrees + elevOffset).coerceIn(Launcher.MIN_ELEV_DEG, Launcher.MAX_ELEV_DEG)

    /** The release tilt that will be used, in degrees (+ hyzer, - anhyzer). */
    val releaseTiltDeg: Float get() = shape.bankDegrees + bankOffset

    /** The action button went down. Starts the power bar, or taps the accuracy marker, or advances a screen. */
    fun pressAction() {
        when (phase) {
            Phase.INTRO -> if (phaseTime > 0.4f) setPhase(Phase.AIMING)
            Phase.AIMING -> {
                refreshGuide()
                val m = SwingMeter(
                    profile.pct(Stat.ACCURACY), profile.pct(Stat.TOUCH), isPutt, autoAccuracy,
                    markPower = pinPower, circle1 = puttCircle == 1,
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

    private fun spec() = ThrowSpec(lie, aimHeading, disc, shape, isPutt, forehand, elevOffset, bankOffset)

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

    /** True once the disc has left the hand. */
    val released: Boolean get() = swingClock < 0f || swingClock >= RELEASE_TIME

    private fun stepFlight(dt: Float) {
        val f = flight ?: return
        // The disc stays in the hand until the arm has swung through to the release point.
        if (swingClock in 0f..RELEASE_TIME) return
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
        val end = hole.onGround(f.pos.x, f.pos.z)
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
            if (power > 0.55f && lastThrowDisc.idealSpeed * 0.8f < Launcher.launchSpeed(profile, ThrowSpec(preThrowLie, 0f, lastThrowDisc, shape, false, forehand), power, false)) spin += 2f
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
        // A golfer with a bigger bag aims further down the fairway, to about 80% of their best flat carry.
        val windEnv = WindOnly(wind, hole.terrain)
        var reach = 0f
        for (d in bag) {
            if (d == DiscModel.DART) continue
            reach = max(reach, Launcher.carry(profile, ThrowSpec(lie, 0f, d, ShotShape.FLAT, false, forehand), windEnv, 1f))
        }
        lookAhead = clamp(reach * 0.8f, 55f, 120f)
        val target = aimTarget()
        aimHeading = dirToHeading((target - lie).flat().normalized())
        shape = ShotShape.FLAT
        elevOffset = 0f
        bankOffset = 0f
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
                val c = Launcher.carry(profile, ThrowSpec(lie, aimHeading, d, ShotShape.FLAT, false, forehand), windEnv, 1f)
                if (c > bestCarry) { bestCarry = c; best = d }
                // Prefer the slowest disc that still gets there.
                if (c >= want * 1.08f && (pick == null || d.speed < pick.speed)) pick = d
            }
            disc = pick ?: best ?: DiscModel.DART
        }
        dirty = true
        refreshGuide()
        if (!isPutt) chooseClearLine(target)
    }

    /**
     * The caddie reads the trees and water: try a few release shapes and aim offsets with the disc already
     * picked, fly each one on the real hole (with a little accuracy error either side), and keep the line
     * that finishes nearest the target without clipping a tree or finding water.
     */
    private fun chooseClearLine(target: Vec3) {
        val baseHeading = aimHeading
        val env = HoleEnvironment(hole, wind, Random(7), 0f)
        val windEnv = WindOnly(wind, hole.terrain)
        val basePower = min(pinPower, 1f)
        val deg = (Math.PI / 180.0).toFloat()
        var bestScore = Float.MAX_VALUE
        var bestShape = ShotShape.FLAT
        var bestOff = 0f
        for (shp in CADDIE_SHAPES) {
          // Aim the power at the target for this shape so shapes are compared fairly.
          val pw = min(Launcher.powerForDistance(profile, ThrowSpec(lie, baseHeading, disc, shp, false, forehand), windEnv, lie.distanceXZ(target)), 1f).let { if (it <= 0f) basePower else it }
          for (off in CADDIE_OFFSETS) {
            val sp = ThrowSpec(lie, baseHeading + off * deg, disc, shp, false, forehand)
            var total = 0f
            for (err in CADDIE_ERRS) {
                val f = Launcher.launch(profile, sp, env, pw, err, false, false)
                var n = 0
                while (!f.isDone && n < 6000) { f.step(Launcher.SIM_DT); n++ }
                val end = hole.onGround(f.pos.x, f.pos.z)
                var pen = end.distanceXZ(target)
                if (FlightEvent.TREE in f.events) pen += 35f
                val surf = hole.surfaceAt(end.x, end.z)
                if (FlightEvent.SPLASH in f.events || surf == Surface.WATER || surf == Surface.OUT_OF_BOUNDS) pen += 120f
                else if (surf == Surface.ROUGH) pen += 6f
                total += if (err == 0f) pen * 1.5f else pen
            }
            // Keep the plain line unless another is clearly better.
            if (shp != ShotShape.FLAT || off != 0f) total += 12f
            if (total < bestScore) { bestScore = total; bestShape = shp; bestOff = off }
          }
        }
        if (bestShape != shape || bestOff != 0f) {
            shape = bestShape
            aimHeading = baseHeading + bestOff * deg
            refreshGuide()
        }
    }

    private fun aimTarget(): Vec3 = hole.guideTarget(lie, lookAhead)

    private fun refreshGuide() {
        dirty = false
        distanceToPin = lie.distanceXZ(hole.basket)
        isPutt = disc == DiscModel.DART && distanceToPin <= PUTT_RANGE
        val windEnv = WindOnly(wind, hole.terrain)
        // The guide aims for the pin when it is the target; otherwise it shows the line at the power
        // needed to reach the aim spot.
        val toPin = (hole.basket - lie).flat().normalized()
        val aimDir = headingToDir(aimHeading)
        val alongPin = max(0f, toPin.dot(aimDir))
        val targetDist = if (distanceToPin <= 70f || alongPin > 0.9f) distanceToPin * max(alongPin, 0.5f) else lie.distanceXZ(aimTarget())
        val sp = spec()
        pinPower = if (isPutt) Launcher.powerForPutt(profile, sp, windEnv, distanceToPin, hole.basket.y)
        else Launcher.powerForDistance(profile, sp, windEnv, targetDist)
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
        /** Inside this is circle 1: the short, high-percentage putt. */
        const val CIRCLE_1 = 10f
        private val CADDIE_SHAPES = listOf(ShotShape.FLAT, ShotShape.HYZER, ShotShape.ANHYZER)
        private val CADDIE_OFFSETS = floatArrayOf(0f, -4f, 4f, -8f, 8f, -13f, 13f)
        private val CADDIE_ERRS = floatArrayOf(-0.5f, 0f, 0.5f)
        /** Seconds from the final tap to the disc leaving the hand, and to the end of the follow-through. */
        const val RELEASE_TIME = 0.26f
        const val SWING_TIME = 0.70f
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
