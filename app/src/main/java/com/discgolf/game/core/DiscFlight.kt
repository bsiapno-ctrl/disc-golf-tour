package com.discgolf.game.core

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the world around the disc has to say about its flight. Implemented by the course. */
interface FlightEnvironment {
    val wind: Vec3
    /** Rolling deceleration multiplier at this spot (1 = fairway, higher = rough/slow). */
    fun frictionAt(p: Vec3): Float
    /** Let the world poke the disc: trees, chains, water. May change velocity, state, or flags. */
    fun resolve(flight: DiscFlight)
    /** Ground height at this spot. Flat unless the course has terrain. */
    fun groundAt(x: Float, z: Float): Float = 0f
    /** Ground slope as (dh/dx, 0, dh/dz). */
    fun gradientAt(x: Float, z: Float): Vec3 = Vec3.ZERO
}

enum class FlightState { FLYING, ROLLING, STOPPED, HOLED }
enum class FlightEvent { TREE, CHAINS, POLE, SPLASH, LANDED }

/**
 * One disc in the air (and then on the ground) with a real, if simplified, aerodynamic model:
 * gravity, drag and lift that both depend on the angle of attack, plus a roll dynamic that makes the
 * classic S-curve. A disc thrown faster than its ideal speed turns over; slower, it fades back.
 * Hyzer shots bank the lift vector sideways, so they bend the way you'd expect.
 *
 * Units: metres, seconds, radians. Axes: X right, Y up, Z forward.
 */
class DiscFlight(
    val disc: DiscModel,
    val hand: Float,
    val spin01: Float,
    var pos: Vec3,
    var vel: Vec3,
    /** Bank angle. Positive = hyzer (lift tilts toward the thrower's off side), negative = anhyzer. */
    var bank: Float,
    /** Nose angle. Positive = nose up. */
    var nose: Float,
    private val env: FlightEnvironment,
) {
    var state = FlightState.FLYING
    var time = 0f
    var maxHeight = pos.y
    val events = ArrayList<FlightEvent>(4)

    /** The chains only get one vote per throw. */
    var basketChecked = false
    var holed = false

    /** Thrown as a roller: it is meant to land on its edge and roll, so it keeps much more of its speed. */
    var rollerThrow = false

    /** Landing marker: where the disc first touched the ground. */
    var landing: Vec3? = null
    var airDistance = 0f
    private val start = pos

    private var heading = dirToHeading(vel)
    private var rollSpeed = 0f
    private var rollDrift = 0f
    private var rollDecel = 4f

    val isDone get() = state == FlightState.STOPPED || state == FlightState.HOLED

    /** Current disc normal in world space, for rendering and the lift calculation. */
    fun normal(): Vec3 {
        val h = Vec3(vel.x, 0f, vel.z).normalized().let { if (it == Vec3.ZERO) headingToDir(heading) else it }
        val right = Vec3(h.z, 0f, -h.x)
        val eff = bank * Tuning.bankGain
        val cb = cos(eff); val sb = sin(eff)
        val cn = cos(nose); val sn = sin(nose)
        return (Vec3.UP * (cn * cb) + h * (-sn) + right * (-hand * sb * cn)).normalized()
    }

    fun step(dt: Float) {
        if (isDone) return
        time += dt
        if (time > MAX_TIME) { state = FlightState.STOPPED; return }
        when (state) {
            FlightState.FLYING -> fly(dt)
            FlightState.ROLLING -> roll(dt)
            else -> Unit
        }
        if (holed) { state = FlightState.HOLED }
    }

    private fun fly(dt: Float) {
        val vRel = vel - env.wind
        val speed = vRel.length()
        if (speed < 0.5f) { land(); return }
        val vHat = vRel * (1f / speed)

        val n = normal()
        val sinA = (-n.dot(vHat)).coerceIn(-1f, 1f)
        val alpha = asin(sinA)

        // Lift and drag coefficients (shape follows published disc wind-tunnel fits, tuned for gameplay).
        val cl0 = Tuning.cl0Base + Tuning.cl0PerGlide * disc.glide
        var cl = (cl0 + Tuning.clAlpha * alpha) * (1f + 0.10f * (spin01 - 0.5f))
        if (alpha > Tuning.stallAlpha) cl *= max(0f, 1f - (alpha - Tuning.stallAlpha) * 3f)   // stall
        val cd = Tuning.cd0 * disc.dragScale + Tuning.cdQuad * (alpha + 0.05f) * (alpha + 0.05f)

        val q = 0.5f * AIR_DENSITY * speed * speed * AREA
        val liftDir = (n - vHat * n.dot(vHat)).normalized().let { if (it == Vec3.ZERO) n else it }
        val force = liftDir * (cl * q) + vHat * (-cd * q) + Vec3(0f, -MASS * G, 0f)
        val acc = force * (1f / MASS)

        vel = vel + acc * dt

        // Roll dynamics: fast = turns over, slow = fades. Spin steadies it.
        val s = speed / disc.idealSpeed
        val turnMag = -disc.turn
        val high = smoothstep(0.80f, 1.15f, s) + max(0f, s - 1.15f) * 1.4f
        val low = 1f - smoothstep(Tuning.lowLo, Tuning.lowHi, s)
        val rate = (-turnMag * high * Tuning.kTurn + disc.fade * (low + Tuning.fadeBase) * Tuning.kFade) * (1.15f - 0.30f * spin01)
        bank = (bank + rate * dt).coerceIn(Tuning.bankMin, Tuning.bankMax)

        // The nose pitches down with the flight path, so a slowing disc settles into a glide instead of stalling.
        val gamma = atan2(vel.y, max(vel.lengthXZ(), 0.5f))
        val target = gamma + Tuning.noseTrim
        if (target < nose) nose += (target - nose) * Tuning.noseFollow * dt
        pos = pos + vel * dt
        if (vel.lengthXZ() > 0.5f) heading = dirToHeading(vel)
        maxHeight = max(maxHeight, pos.y)

        env.resolve(this)
        if (isDone) return
        if (pos.y <= env.groundAt(pos.x, pos.z) + GROUND) land()
    }

    private fun land() {
        pos = Vec3(pos.x, env.groundAt(pos.x, pos.z) + GROUND, pos.z)
        if (landing == null) landing = pos
        airDistance = pos.distanceXZ(start)
        events += FlightEvent.LANDED

        val h = vel.lengthXZ()
        val descent = atan2(-vel.y, max(h, 0.01f))
        val tilt = bank * hand
        // Anhyzer discs hit the ground on edge and roll; hyzers skid and stop.
        val roller = tilt < -0.18f || rollerThrow
        var keep = if (rollerThrow) 0.88f else if (roller) 0.62f else if (abs(tilt) < 0.18f) 0.30f else 0.16f
        keep *= if (rollerThrow) (1.15f - descent * 0.5f).coerceIn(0.6f, 1f) else (1.1f - descent).coerceIn(0.25f, 1f)
        rollSpeed = h * keep
        rollDecel = if (roller) 2.2f else 4.0f
        rollDrift = -hand * bank * (if (rollerThrow) 0.3f else 0.9f)
        state = FlightState.ROLLING
        vel = headingToDir(heading) * rollSpeed
        env.resolve(this)
    }

    private fun roll(dt: Float) {
        val f = env.frictionAt(pos)
        rollSpeed -= rollDecel * f * dt
        if (rollSpeed > 0.8f) heading += rollDrift * dt
        var v = headingToDir(heading) * max(rollSpeed, 0f)
        // Gravity along the slope. Discs slide and roll rather than drop, so only part of it counts.
        val g = env.gradientAt(pos.x, pos.z)
        v = v - g * (G * SLOPE_PULL * dt)
        val sp = v.lengthXZ()
        val pull = G * SLOPE_PULL * g.lengthXZ()
        // At rest unless the hill is steeper than friction can hold.
        if (sp <= 0.25f && pull < rollDecel * f * 0.8f) { state = FlightState.STOPPED; vel = Vec3.ZERO; return }
        rollSpeed = sp
        if (sp > 0.05f) heading = dirToHeading(v)
        vel = headingToDir(heading) * rollSpeed
        pos = pos + vel * dt
        pos = Vec3(pos.x, env.groundAt(pos.x, pos.z) + GROUND, pos.z)
        env.resolve(this)
    }

    /** Env callback: the disc smacked something and lost most of its energy. */
    fun bounceOff(normalXZ: Vec3, keep: Float) {
        val n = normalXZ.flat().normalized()
        val vh = Vec3(vel.x, 0f, vel.z)
        val refl = vh - n * (2f * vh.dot(n))
        vel = Vec3(refl.x * keep, -1.2f - abs(vel.y) * 0.2f, refl.z * keep)
        bank = 0f
        if (state == FlightState.ROLLING) {
            heading = dirToHeading(vel); rollSpeed = vel.lengthXZ()
        }
    }

    companion object {
        const val AIR_DENSITY = 1.225f
        const val MASS = 0.175f
        const val AREA = 0.0352f
        const val G = 9.81f
        const val GROUND = 0.03f
        /** Share of gravity along a slope that acts on a rolling disc. */
        const val SLOPE_PULL = 0.45f
        const val MAX_TIME = 40f
        const val RELEASE_HEIGHT = 1.30f
    }
}

/** Flight-model constants. Kept in one place so they can be tuned against the carry-distance tests. */
object Tuning {
    var cl0Base = 0.08f
    var cl0PerGlide = 0.024f
    var clAlpha = 2.2f
    var cd0 = 0.054f
    var cdQuad = 2.5f
    var kTurn = 0.13f
    var kFade = 0.045f
    var fadeBase = 0.10f
    var lowLo = 0.55f
    var lowHi = 0.95f
    var driveElevDeg = 9f
    var puttElevDeg = 3f
    var noseOverElevDeg = -2.5f
    var bankMin = -1.0f
    var bankMax = 0.85f
    var stallAlpha = 0.35f
    /** How much of the bank angle turns into sideways lift. Real discs shed bank quickly; this keeps curves readable. */
    var bankGain = 0.55f
    /** Pitch relaxation toward the flight path (1/s) and the resting angle of attack offset (rad). */
    var noseFollow = 0.6f
    var noseTrim = 0.10f
}

/** Open field: no wind, no trees. Used by the carry-distance helpers and tests. */
object EmptyField : FlightEnvironment {
    override val wind = Vec3.ZERO
    override fun frictionAt(p: Vec3) = 1f
    override fun resolve(flight: DiscFlight) {}
}

/** Everything needed to throw, minus the swing result. */
class ThrowSpec(
    val origin: Vec3,
    val aimHeading: Float,
    val disc: DiscModel,
    val shape: ShotShape,
    val isPutt: Boolean,
    /** Forehand (sidearm) throw: the disc spins the other way, so every curve is mirrored. */
    val forehand: Boolean = false,
    /** Fine adjustment of the launch angle, in degrees up (+) or down (-). */
    val elevOffsetDeg: Float = 0f,
    /** Fine adjustment of the release tilt, in degrees toward hyzer (+) or anhyzer (-). */
    val bankOffsetDeg: Float = 0f,
)

object Launcher {
    const val PUTT_MAX_SPEED = 17f
    const val PUTT_MIN_SPEED = 3f
    const val DRIVE_MIN_SPEED = 4f
    /** A sidearm throw is a little slower than a backhand for most players. */
    const val FOREHAND_SPEED = 0.94f
    const val FOREHAND_YAW = 1.18f
    const val MAX_ELEV_DEG = 22f
    const val MIN_ELEV_DEG = -4f

    /** Release speed in m/s for a given power 0..1. */
    fun launchSpeed(p: PlayerProfile, spec: ThrowSpec, power: Float, perfect: Boolean): Float {
        val vMax = (if (spec.isPutt) PUTT_MAX_SPEED else min(p.armSpeed(), spec.disc.speedLimit)) * (if (spec.forehand) FOREHAND_SPEED else 1f)
        val lo = if (spec.isPutt) PUTT_MIN_SPEED else DRIVE_MIN_SPEED
        val v = lo + (vMax - lo) * power
        return if (perfect) v * 1.02f else v
    }

    /**
     * Build the flight for a throw.
     * [accuracyError] is -1..1 from the meter; [noise] is an extra random release error in -1..1 (pass 0 for none).
     */
    fun launch(
        p: PlayerProfile,
        spec: ThrowSpec,
        env: FlightEnvironment,
        power: Float,
        accuracyError: Float,
        overcooked: Boolean,
        perfect: Boolean,
        noise: Float = 0f,
    ): DiscFlight {
        val acc = p.pct(Stat.ACCURACY)
        val deg = (Math.PI / 180.0).toFloat()
        var maxYaw = lerp(11f, 8f, acc) * deg
        if (spec.isPutt) maxYaw *= 0.8f
        if (overcooked) maxYaw *= 1.6f
        if (spec.forehand) maxYaw *= FOREHAND_YAW
        // A perfect release is clean: the timing miss shrinks and the random wobble halves.
        val accErr = if (perfect) accuracyError * 0.8f else accuracyError
        var jitter = lerp(2.4f, 1.8f, p.pct(Stat.SPIN)) * deg * (0.4f + 0.6f * power) * noise * (if (perfect) 0.5f else 1f)
        if (spec.isPutt) jitter *= 1.1f
        // Muscling it: past a very hard pull the wobble grows, and an overcooked pull flares the disc off to one side.
        if (!spec.isPutt && power > 0.9f && !perfect) jitter *= 1f + (power - 0.9f) * 6f
        var flare = 0f
        if (overcooked) flare = (if (noise >= 0f) 1f else -1f) * (2.5f + 2.5f * (1f - acc)) * deg

        val heading = spec.aimHeading + accErr * maxYaw + jitter + flare
        val bank = ((spec.shape.bankDegrees + spec.bankOffsetDeg) * deg + accErr * 8f * deg - flare * 1.2f).coerceIn(Tuning.bankMin, Tuning.bankMax)
        val baseElev = (if (spec.isPutt) Tuning.puttElevDeg else Tuning.driveElevDeg) + spec.shape.elevDegrees
        val elev = (baseElev + spec.elevOffsetDeg).coerceIn(MIN_ELEV_DEG, MAX_ELEV_DEG) * deg
        val nose = elev + (spec.shape.noseDegrees + Tuning.noseOverElevDeg) * deg

        val speed = launchSpeed(p, spec, power, perfect)
        val dir = Vec3(sin(heading) * cos(elev), sin(elev), cos(heading) * cos(elev))
        val start = Vec3(spec.origin.x, spec.origin.y + DiscFlight.RELEASE_HEIGHT, spec.origin.z)
        val flight = DiscFlight(spec.disc, p.handedness.sign * (if (spec.forehand) -1f else 1f), p.pct(Stat.SPIN), start, dir * speed, bank, nose, env)
        flight.rollerThrow = spec.shape.rolls
        return flight
    }

    /** Flat-ground carry in metres for a perfect release, ignoring trees. Used by the caddie. */
    fun carry(p: PlayerProfile, spec: ThrowSpec, env: FlightEnvironment, power: Float, perfect: Boolean = power >= SwingMeter.PERFECT_POWER): Float {
        val f = launch(p, spec, env, power, 0f, false, perfect)
        var steps = 0
        while (f.state == FlightState.FLYING && steps < 6000) { f.step(SIM_DT); steps++ }
        return f.landing?.distanceXZ(spec.origin) ?: f.pos.distanceXZ(spec.origin)
    }

    /** Sampled flight path of a perfect release, for the aim guide. Includes the roll-out. */
    fun guidePath(p: PlayerProfile, spec: ThrowSpec, env: FlightEnvironment, power: Float): List<Vec3> {
        val f = launch(p, spec, env, power, 0f, false, power >= SwingMeter.PERFECT_POWER)
        val pts = ArrayList<Vec3>(64)
        var steps = 0
        while (f.state == FlightState.FLYING && steps < 6000) {
            f.step(SIM_DT)
            if (steps % 8 == 0) pts += f.pos
            steps++
        }
        pts += f.pos
        return pts
    }

    /** The power (0..1) that lands a perfect release on the flat at [distance] metres. 1.01 means out of range. */
    fun powerForDistance(p: PlayerProfile, spec: ThrowSpec, env: FlightEnvironment, distance: Float): Float {
        if (carry(p, spec, env, 1f) < distance) return 1.01f
        var lo = 0f; var hi = 1f
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (carry(p, spec, env, mid) < distance) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    /** Height above the basket base of a perfect release as it crosses [range] metres from the origin; -1 if it lands short. */
    fun heightAtRange(p: PlayerProfile, spec: ThrowSpec, env: FlightEnvironment, power: Float, range: Float, baseY: Float): Float {
        val f = launch(p, spec, env, power, 0f, false, power >= SwingMeter.PERFECT_POWER)
        var steps = 0
        while (f.state == FlightState.FLYING && steps < 6000) {
            f.step(SIM_DT); steps++
            if (f.pos.distanceXZ(spec.origin) >= range) return f.pos.y - baseY
        }
        return -1f
    }

    /** Height above the base where a putt is aimed: the middle of the chains. */
    const val PUTT_AIM_HEIGHT = 0.95f

    /**
     * The power (0..1) that sends a perfect putt through the chains at [distance] metres. Putts are not
     * aimed at the ground under the basket (that would land on the grass): they arrive at chain height.
     * 1.01 means out of range.
     */
    fun powerForPutt(p: PlayerProfile, spec: ThrowSpec, env: FlightEnvironment, distance: Float, baseY: Float): Float {
        if (heightAtRange(p, spec, env, 1f, distance, baseY) < PUTT_AIM_HEIGHT) return 1.01f
        var lo = 0f; var hi = 1f
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (heightAtRange(p, spec, env, mid, distance, baseY) < PUTT_AIM_HEIGHT) lo = mid else hi = mid
        }
        return (lo + hi) / 2f
    }

    const val SIM_DT = 1f / 120f
}
