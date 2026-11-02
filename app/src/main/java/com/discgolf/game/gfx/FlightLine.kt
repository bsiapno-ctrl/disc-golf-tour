package com.discgolf.game.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import com.discgolf.game.core.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * The predicted flight line: a thin camera-facing blue ribbon along the sampled flight path (from the real flight sim,
 * recomputed by the controller only when the aim inputs change) that dashes and fades over its last 30 per cent so the
 * player still has to judge the end. Also the floating basket marker with its distance in metres.
 * Needs a GL context; create it in onSurfaceCreated.
 */
class FlightLine {
    private val prog = linkProgram(VS, FS)
    private val uVp = GLES30.glGetUniformLocation(prog, "uVP")
    private val uMode = GLES30.glGetUniformLocation(prog, "uMode")
    private val uTex = GLES30.glGetUniformLocation(prog, "uTex")
    private val uAlpha = GLES30.glGetUniformLocation(prog, "uAlpha")

    private val vao: Int
    private val vbo: Int
    private var buf = ByteBuffer.allocateDirect(MAX_VERTS * STRIDE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var tex = 0
    private var texMeters = -1

    init {
        val a = IntArray(1)
        GLES30.glGenVertexArrays(1, a, 0); vao = a[0]
        GLES30.glGenBuffers(1, a, 0); vbo = a[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, MAX_VERTS * STRIDE * 4, null, GLES30.GL_DYNAMIC_DRAW)
        GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE * 4, 0)
        GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE * 4, 12)
        GLES30.glBindVertexArray(0)
    }

    /** [vp] is the view-projection matrix; [right] is the on-screen right direction in world space (for the billboard). */
    fun draw(vp: FloatArray, cam: Vec3, right: Vec3, path: List<Vec3>, basket: Vec3, meters: Int) {
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        if (path.size >= 2) drawRibbon(cam, path)
        drawMarker(cam, right, basket, meters)
        GLES30.glBindVertexArray(0)
    }

    private fun drawRibbon(cam: Vec3, path: List<Vec3>) {
        val n = min(path.size, MAX_VERTS / 2)
        var total = 0f
        val len = FloatArray(n)
        for (i in 1 until n) { total += path[i].distanceTo(path[i - 1]); len[i] = total }
        if (total < 1e-3f) return
        buf.clear()
        var count = 0
        for (i in 0 until n) {
            val p = path[i]
            val prev = path[max(i - 1, 0)]; val next = path[min(i + 1, n - 1)]
            val tg = (next - prev).normalized()
            val toCam = (cam - p).normalized()
            var side = tg.cross(toCam)
            if (side.length() < 1e-4f) side = Vec3(1f, 0f, 0f)
            side = side.normalized()
            val d = cam.distanceTo(p)
            // Roughly constant on-screen thickness, a little thinner as the disc nears the end.
            val w = (0.0085f * d).coerceIn(0.07f, 0.9f)
            val f = len[i] / total
            for (s in intArrayOf(-1, 1)) {
                buf.put(p.x + side.x * w * s).put(p.y + side.y * w * s).put(p.z + side.z * w * s)
                buf.put(len[i]).put(s.toFloat()).put(f)
                count++
            }
        }
        buf.flip()
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * STRIDE * 4, buf)
        GLES30.glUniform1i(uMode, 0)
        GLES30.glUniform1f(uAlpha, 0.92f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, count)
    }

    private fun drawMarker(cam: Vec3, right: Vec3, basket: Vec3, meters: Int) {
        if (tex == 0 || meters != texMeters) {
            if (tex == 0) { val a = IntArray(1); GLES30.glGenTextures(1, a, 0); tex = a[0] }
            uploadLabel(meters); texMeters = meters
        }
        val d = cam.distanceTo(basket)
        // Sized by distance (clamped) so it stays readable at range without swamping the basket up close.
        val h = (0.055f * d).coerceIn(0.9f, 7f)
        val w = h * 2f
        val cx = basket.x; val cy = basket.y + 2.1f + h * 0.5f; val cz = basket.z
        val rx = right.x * w * 0.5f; val ry = right.y * w * 0.5f; val rz = right.z * w * 0.5f
        buf.clear()
        fun v(sx: Float, sy: Float, u: Float, t: Float) {
            buf.put(cx + rx * sx).put(cy + h * 0.5f * sy).put(cz + rz * sx).put(u).put(t).put(0f)
        }
        v(-1f, -1f, 0f, 1f); v(1f, -1f, 1f, 1f); v(-1f, 1f, 0f, 0f); v(1f, 1f, 1f, 0f)
        buf.flip()
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, 4 * STRIDE * 4, buf)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform1i(uMode, 1)
        GLES30.glUniform1f(uAlpha, 1f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** Pill with a little basket icon and the distance, plus a pointer down to the basket. 256x128. */
    private fun uploadLabel(meters: Int) {
        val bmp = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = Color.argb(230, 16, 40, 78)
        c.drawRoundRect(RectF(6f, 6f, 250f, 90f), 42f, 42f, p)
        val tri = Path().apply { moveTo(104f, 88f); lineTo(152f, 88f); lineTo(128f, 120f); close() }
        c.drawPath(tri, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.color = Color.argb(255, 90, 170, 255)
        c.drawRoundRect(RectF(6f, 6f, 250f, 90f), 42f, 42f, p)
        // Basket icon: pole, cage and chains' band.
        p.style = Paint.Style.FILL; p.color = Color.WHITE
        c.drawRect(52f, 22f, 58f, 74f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f
        c.drawArc(RectF(30f, 24f, 80f, 62f), 0f, 180f, false, p)
        c.drawLine(30f, 43f, 80f, 43f, p)
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textAlign = Paint.Align.LEFT
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 52f
        c.drawText("$meters m", 96f, 64f, p)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
    }

    companion object {
        private const val STRIDE = 6
        private const val MAX_VERTS = 512

        private const val VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aA;
uniform mat4 uVP;
out vec3 vA;
void main() { vA = aA; gl_Position = uVP * vec4(aPos, 1.0); }
"""
        // Mode 0: ribbon (vA = metres along, side -1..1, fraction along). Mode 1: label texture (vA.xy = uv).
        private const val FS = """#version 300 es
precision mediump float;
in vec3 vA;
uniform int uMode;
uniform sampler2D uTex;
uniform float uAlpha;
out vec4 fragColor;
void main() {
    if (uMode == 1) {
        vec4 t = texture(uTex, vA.xy);
        fragColor = vec4(t.rgb, t.a * uAlpha);
        return;
    }
    float edge = 1.0 - smoothstep(0.55, 1.0, abs(vA.y));
    // Solid for the first 70 per cent, then dashes that thin out and fade towards the landing point.
    float k = smoothstep(0.70, 0.78, vA.z);
    float dash = step(0.5, fract(vA.x / 2.2));
    float body = mix(1.0, dash * (1.0 - smoothstep(0.78, 1.0, vA.z) * 0.75), k);
    vec3 col = mix(vec3(0.20, 0.55, 1.0), vec3(0.75, 0.90, 1.0), 1.0 - smoothstep(0.0, 0.5, abs(vA.y)));
    fragColor = vec4(col, edge * body * uAlpha);
}
"""
    }
}
