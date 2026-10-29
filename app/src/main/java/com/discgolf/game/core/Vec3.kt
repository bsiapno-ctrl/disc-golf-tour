package com.discgolf.game.core

import kotlin.math.sqrt

/**
 * Minimal immutable 3D vector. World axes: X = right, Y = up, Z = forward (toward the first basket).
 * Immutable on purpose: the physics runs at 120 Hz on a handful of objects, so allocation cost is a non-issue
 * and the code stays free of aliasing bugs.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthXZ() = sqrt(x * x + z * z)

    fun normalized(): Vec3 {
        val l = length()
        return if (l > 1e-6f) Vec3(x / l, y / l, z / l) else ZERO
    }

    /** Same vector with Y forced to zero (used for ground-plane distances). */
    fun flat() = Vec3(x, 0f, z)

    fun distanceTo(o: Vec3) = (this - o).length()
    fun distanceXZ(o: Vec3) = (this - o).lengthXZ()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
        val FORWARD = Vec3(0f, 0f, 1f)
    }
}

fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun clamp(v: Float, lo: Float, hi: Float) = if (v < lo) lo else if (v > hi) hi else v
fun clamp(v: Int, lo: Int, hi: Int) = if (v < lo) lo else if (v > hi) hi else v
fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = clamp((x - e0) / (e1 - e0), 0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Heading in radians: 0 = +Z, positive turns toward +X (to the player's right). */
fun headingToDir(heading: Float) = Vec3(kotlin.math.sin(heading), 0f, kotlin.math.cos(heading))

fun dirToHeading(d: Vec3) = kotlin.math.atan2(d.x, d.z)
