package com.discgolf.game.gfx

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One static triangle mesh on the GPU. Vertices are interleaved position (3) + normal (3). */
class Mesh(vertices: FloatArray, indices: ShortArray) {
    private val vao: Int
    private val count = indices.size

    init {
        val ids = IntArray(3)
        GLES30.glGenVertexArrays(1, ids, 0)
        GLES30.glGenBuffers(2, ids, 1)
        vao = ids[0]
        GLES30.glBindVertexArray(vao)

        val vb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)

        val ib = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[2])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, ib, GLES30.GL_STATIC_DRAW)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_SHORT, 0)
    }
}

/** Procedural primitives. Everything is unit-sized and gets scaled by the model matrix. */
object Shapes {
    private class Builder {
        val v = ArrayList<Float>()
        val i = ArrayList<Short>()
        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Short {
            v += x; v += y; v += z; v += nx; v += ny; v += nz
            return (v.size / 6 - 1).toShort()
        }
        fun tri(a: Short, b: Short, c: Short) { i += a; i += b; i += c }
        fun quad(a: Short, b: Short, c: Short, d: Short) { tri(a, b, c); tri(a, c, d) }
        fun build() = Mesh(v.toFloatArray(), i.toShortArray())
    }

    /** Cube of side 1 centred on the origin. */
    fun cube(): Mesh {
        val b = Builder()
        val h = 0.5f
        // (normal, u axis, v axis)
        val faces = arrayOf(
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f),
            floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
            floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f),
            floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
        )
        for (f in faces) {
            val nx = f[0]; val ny = f[1]; val nz = f[2]
            val ux = f[3]; val uy = f[4]; val uz = f[5]
            val vx = f[6]; val vy = f[7]; val vz = f[8]
            fun p(s: Float, t: Float) = b.vertex(
                (nx + ux * s + vx * t) * h, (ny + uy * s + vy * t) * h, (nz + uz * s + vz * t) * h, nx, ny, nz,
            )
            b.quad(p(-1f, -1f), p(1f, -1f), p(1f, 1f), p(-1f, 1f))
        }
        return b.build()
    }

    /** Cylinder of radius 1 from y=0 to y=1, with caps. */
    fun cylinder(seg: Int = 20): Mesh {
        val b = Builder()
        for (k in 0..seg) {
            val a = (2.0 * Math.PI * k / seg).toFloat()
            val c = cos(a); val s = sin(a)
            val i0 = b.vertex(c, 0f, s, c, 0f, s)
            val i1 = b.vertex(c, 1f, s, c, 0f, s)
            if (k < seg) {
                val n0 = (i0 + 2).toShort(); val n1 = (i1 + 2).toShort()
                b.quad(i0, n0, n1, i1)
            }
        }
        for ((y, ny) in listOf(0f to -1f, 1f to 1f)) {
            val centre = b.vertex(0f, y, 0f, 0f, ny, 0f)
            val ring = ArrayList<Short>()
            for (k in 0 until seg) {
                val a = (2.0 * Math.PI * k / seg).toFloat()
                ring += b.vertex(cos(a), y, sin(a), 0f, ny, 0f)
            }
            for (k in 0 until seg) {
                val a = ring[k]; val c2 = ring[(k + 1) % seg]
                if (ny > 0) b.tri(centre, c2, a) else b.tri(centre, a, c2)
            }
        }
        return b.build()
    }

    /** Cone: base radius 1 at y=0, tip at y=1. */
    fun cone(seg: Int = 18): Mesh {
        val b = Builder()
        val inv = 1f / sqrt(2f)
        for (k in 0 until seg) {
            val a0 = (2.0 * Math.PI * k / seg).toFloat()
            val a1 = (2.0 * Math.PI * (k + 1) / seg).toFloat()
            val am = (a0 + a1) / 2f
            val t = b.vertex(0f, 1f, 0f, cos(am) * inv, inv, sin(am) * inv)
            val p0 = b.vertex(cos(a0), 0f, sin(a0), cos(a0) * inv, inv, sin(a0) * inv)
            val p1 = b.vertex(cos(a1), 0f, sin(a1), cos(a1) * inv, inv, sin(a1) * inv)
            b.tri(t, p1, p0)
        }
        val centre = b.vertex(0f, 0f, 0f, 0f, -1f, 0f)
        val ring = ArrayList<Short>()
        for (k in 0 until seg) {
            val a = (2.0 * Math.PI * k / seg).toFloat()
            ring += b.vertex(cos(a), 0f, sin(a), 0f, -1f, 0f)
        }
        for (k in 0 until seg) b.tri(centre, ring[k], ring[(k + 1) % seg])
        return b.build()
    }

    /** Sphere of radius 1. */
    fun sphere(lat: Int = 12, lon: Int = 18): Mesh {
        val b = Builder()
        for (i in 0..lat) {
            val th = (Math.PI * i / lat).toFloat()
            for (j in 0..lon) {
                val ph = (2.0 * Math.PI * j / lon).toFloat()
                val x = sin(th) * cos(ph); val y = cos(th); val z = sin(th) * sin(ph)
                b.vertex(x, y, z, x, y, z)
            }
        }
        val row = lon + 1
        for (i in 0 until lat) for (j in 0 until lon) {
            val a = (i * row + j).toShort(); val c = (i * row + j + 1).toShort()
            val d = ((i + 1) * row + j).toShort(); val e = ((i + 1) * row + j + 1).toShort()
            b.tri(a, c, d); b.tri(c, e, d)
        }
        return b.build()
    }

    /** Flat circle of radius 1 in the XZ plane, facing up. */
    fun disc(seg: Int = 40): Mesh {
        val b = Builder()
        val c = b.vertex(0f, 0f, 0f, 0f, 1f, 0f)
        val ring = ArrayList<Short>()
        for (k in 0 until seg) {
            val a = (2.0 * Math.PI * k / seg).toFloat()
            ring += b.vertex(cos(a), 0f, sin(a), 0f, 1f, 0f)
        }
        for (k in 0 until seg) b.tri(c, ring[(k + 1) % seg], ring[k])
        return b.build()
    }

    /** Ring in the XZ plane: major radius 1, tube radius [tube]. */
    fun torus(tube: Float, seg: Int = 28, tubeSeg: Int = 8): Mesh {
        val b = Builder()
        for (i in 0..seg) {
            val a = (2.0 * Math.PI * i / seg).toFloat()
            val ca = cos(a); val sa = sin(a)
            for (j in 0..tubeSeg) {
                val t = (2.0 * Math.PI * j / tubeSeg).toFloat()
                val ct = cos(t); val st = sin(t)
                val r = 1f + tube * ct
                b.vertex(ca * r, tube * st, sa * r, ca * ct, st, sa * ct)
            }
        }
        val row = tubeSeg + 1
        for (i in 0 until seg) for (j in 0 until tubeSeg) {
            val a = (i * row + j).toShort(); val c = ((i + 1) * row + j).toShort()
            val d = ((i + 1) * row + j + 1).toShort(); val e = (i * row + j + 1).toShort()
            b.quad(a, c, d, e)
        }
        return b.build()
    }
}

/** A tiny matrix stack so characters can be built as parent/child transforms. */
class MatStack {
    private val stack = Array(24) { FloatArray(16) }
    private var depth = 0
    val top: FloatArray get() = stack[depth]

    fun identity(): MatStack { depth = 0; Matrix.setIdentityM(top, 0); return this }
    fun translate(x: Float, y: Float, z: Float): MatStack { Matrix.translateM(top, 0, x, y, z); return this }
    fun scale(x: Float, y: Float, z: Float): MatStack { Matrix.scaleM(top, 0, x, y, z); return this }
    fun rotate(deg: Float, x: Float, y: Float, z: Float): MatStack { Matrix.rotateM(top, 0, deg, x, y, z); return this }
    fun push(): MatStack { System.arraycopy(top, 0, stack[depth + 1], 0, 16); depth++; return this }
    fun pop(): MatStack { depth--; return this }
    fun load(m: FloatArray): MatStack { System.arraycopy(m, 0, top, 0, 16); return this }
}
