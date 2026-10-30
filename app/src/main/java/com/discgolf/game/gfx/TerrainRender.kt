package com.discgolf.game.gfx

import android.opengl.GLES30
import com.discgolf.game.core.Hole
import com.discgolf.game.core.Surface
import com.discgolf.game.core.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The ground around one hole: a height-field mesh with normals, a ring of skirt that carries the ground out to the
 * horizon, and a small "surface map" texture saying where the fairway, water and out of bounds are.
 * The fragment shader turns that map into grass, mowing stripes, mud and so on.
 */
class TerrainRender(val hole: Hole) {
    private val mesh: Mesh
    private var tex = 0
    private val texX0: Float
    private val texZ0: Float
    private val texW: Float
    private val texH: Float

    init {
        // Window: the hole's bounding box plus room for woods and out of bounds.
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (p in hole.path) { minX = min(minX, p.x); maxX = max(maxX, p.x); minZ = min(minZ, p.z); maxZ = max(maxZ, p.z) }
        val pad = 85f
        val wx0 = minX - pad; val wz0 = minZ - pad
        val sizeX = maxX - minX + 2 * pad; val sizeZ = maxZ - minZ + 2 * pad

        // Mesh resolution: 2 m, coarser only if a very long hole would need more than ~55k vertices.
        var cell = 2f
        while ((sizeX / cell + 1) * (sizeZ / cell + 1) > 55000f) cell *= 1.25f
        val nx = ceil(sizeX / cell).toInt() + 1
        val nz = ceil(sizeZ / cell).toInt() + 1
        val t = hole.terrain

        val verts = ArrayList<Float>((nx * nz + 2 * (nx + nz)) * 6)
        fun vtx(x: Float, y: Float, z: Float, n: Vec3) { verts += x; verts += y; verts += z; verts += n.x; verts += n.y; verts += n.z }
        val up = Vec3(0f, 1f, 0f)
        for (iz in 0 until nz) for (ix in 0 until nx) {
            val x = wx0 + ix * cell; val z = wz0 + iz * cell
            vtx(x, t.heightAt(x, z), z, t.normalAt(x, z))
        }
        val idx = ArrayList<Short>((nx * nz + 2 * (nx + nz)) * 6)
        for (iz in 0 until nz - 1) for (ix in 0 until nx - 1) {
            val a = (iz * nx + ix); val b = a + 1; val c = a + nx; val d = c + 1
            idx += a.toShort(); idx += c.toShort(); idx += b.toShort()
            idx += b.toShort(); idx += c.toShort(); idx += d.toShort()
        }

        // Skirt: walk round the edge of the window and send each edge vertex far out at the edge's average height,
        // so there is ground all the way to the horizon and no visible rim.
        val border = ArrayList<Int>()
        for (ix in 0 until nx) border += ix                                   // z = low edge, left to right
        for (iz in 1 until nz) border += iz * nx + (nx - 1)                   // right edge, going up
        for (ix in nx - 2 downTo 0) border += (nz - 1) * nx + ix             // z = high edge, right to left
        for (iz in nz - 2 downTo 1) border += iz * nx                         // left edge, going down
        var avg = 0f
        for (i in border) avg += verts[i * 6 + 1]
        avg /= border.size
        val far = 1400f
        val firstOuter = verts.size / 6
        for (i in border) {
            val ix = i % nx; val iz = i / nx
            val ox = if (ix == 0) -1f else if (ix == nx - 1) 1f else 0f
            val oz = if (iz == 0) -1f else if (iz == nz - 1) 1f else 0f
            vtx(verts[i * 6] + ox * far, avg, verts[i * 6 + 2] + oz * far, up)
        }
        for (k in border.indices) {
            val k2 = (k + 1) % border.size
            val a = border[k]; val b = border[k2]
            val oa = firstOuter + k; val ob = firstOuter + k2
            idx += a.toShort(); idx += oa.toShort(); idx += b.toShort()
            idx += b.toShort(); idx += oa.toShort(); idx += ob.toShort()
        }
        check(verts.size / 6 <= 65535) { "terrain mesh too large" }
        mesh = Mesh(verts.toFloatArray(), idx.toShortArray())

        // Surface map: one texel per metre (coarser for huge holes), RGBA = fairway, water, out of bounds, unused.
        var texel = 1f
        while ((sizeX / texel) * (sizeZ / texel) > 300_000f) texel *= 1.3f
        val tw = ceil(sizeX / texel).toInt(); val th = ceil(sizeZ / texel).toInt()
        val buf = ByteBuffer.allocateDirect(tw * th * 4).order(ByteOrder.nativeOrder())
        for (j in 0 until th) for (i in 0 until tw) {
            val x = wx0 + (i + 0.5f) * texel; val z = wz0 + (j + 0.5f) * texel
            val s = hole.surfaceAt(x, z)
            buf.put(if (s == Surface.FAIRWAY) ON else OFF)
            buf.put(if (s == Surface.WATER) ON else OFF)
            buf.put(if (s == Surface.OUT_OF_BOUNDS) ON else OFF)
            buf.put(ON)
        }
        buf.position(0)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, tw, th, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        texX0 = wx0; texZ0 = wz0; texW = tw * texel; texH = th * texel
    }

    fun bindAndDraw(uRect: Int, uTex: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glUniform4f(uRect, texX0, texZ0, 1f / texW, 1f / texH)
        mesh.draw()
    }

    private companion object {
        const val ON: Byte = -1   // 255 when read as an unsigned byte
        const val OFF: Byte = 0
    }

    fun release() {
        mesh.release()
        val ids = intArrayOf(tex)
        GLES30.glDeleteTextures(1, ids, 0)
    }
}

/** The ground shader. Grass, rough, fairway stripes, the putting green, banks, mud and fog, all computed per pixel. */
class TerrainShader {
    private val prog = linkProgram(VERT, FRAG)
    private val uVp = GLES30.glGetUniformLocation(prog, "uVP")
    private val uRect = GLES30.glGetUniformLocation(prog, "uSurfRect")
    private val uTex = GLES30.glGetUniformLocation(prog, "uSurf")
    private val uLight = GLES30.glGetUniformLocation(prog, "uLightDir")
    private val uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
    private val uBasket = GLES30.glGetUniformLocation(prog, "uBasket")

    fun draw(t: TerrainRender, vp: FloatArray, cam: Vec3, light: FloatArray) {
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        GLES30.glUniform3f(uLight, light[0], light[1], light[2])
        GLES30.glUniform3f(uCam, cam.x, cam.y, cam.z)
        val b = t.hole.basket
        GLES30.glUniform3f(uBasket, b.x, b.y, b.z)
        t.bindAndDraw(uRect, uTex)
    }

    companion object {
        private const val VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
uniform mat4 uVP;
out vec3 vNormal;
out vec3 vWorld;
void main() {
    vWorld = aPos;
    vNormal = aNormal;
    gl_Position = uVP * vec4(aPos, 1.0);
}
"""

        private const val FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
uniform sampler2D uSurf;
uniform vec4 uSurfRect;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform vec3 uBasket;
out vec4 fragColor;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * vnoise(p); p = p * 2.03 + 17.1; a *= 0.5; }
    return v;
}

void main() {
    vec3 n = normalize(vNormal);
    vec2 uv = (vWorld.xz - uSurfRect.xy) * uSurfRect.zw;
    vec3 map = texture(uSurf, uv).rgb;
    float fair = smoothstep(0.35, 0.65, map.r);
    float water = smoothstep(0.30, 0.70, map.g);
    float oob = map.b;

    float dist = length(vWorld - uCamPos);
    float broad = fbm(vWorld.xz * 0.045);
    float mid = fbm(vWorld.xz * 0.30);
    // Fine grain fades with distance so far ground does not shimmer.
    float fine = mix(vnoise(vWorld.xz * 3.5), 0.5, smoothstep(25.0, 90.0, dist));

    vec3 rough = mix(vec3(0.16, 0.31, 0.11), vec3(0.27, 0.42, 0.15), broad);
    rough = mix(rough, vec3(0.38, 0.40, 0.17), smoothstep(0.58, 0.82, mid) * 0.55);   // dry, yellowed patches
    rough *= 0.86 + 0.28 * fine;

    vec3 fairway = mix(vec3(0.29, 0.54, 0.19), vec3(0.37, 0.61, 0.24), broad);
    // Mowing stripes, fading out in the distance where they would alias.
    float stripe = step(0.5, fract(vWorld.z / 6.0));
    fairway *= 1.0 - 0.075 * stripe * (1.0 - smoothstep(60.0, 160.0, dist));
    fairway *= 0.94 + 0.12 * fine;

    // Putting green around the basket: short, bright, mown in rings.
    float dB = length(vWorld.xz - uBasket.xz);
    float green = 1.0 - smoothstep(6.2, 7.2, dB);
    vec3 greenCol = vec3(0.40, 0.69, 0.30) * (1.0 - 0.03 * step(0.5, fract(dB / 1.4)));

    vec3 col = mix(rough, fairway, fair);
    col = mix(col, greenCol, green);
    col = mix(col, col * vec3(0.84, 0.84, 0.70), oob * 0.55);                // out of bounds is scruffier

    // Steep banks show earth through the grass; ponds are muddy.
    float slope = 1.0 - n.y;
    col = mix(col, vec3(0.42, 0.36, 0.27), smoothstep(0.16, 0.34, slope) * 0.65);
    col = mix(col, vec3(0.20, 0.19, 0.14), water);

    // Light: sun plus sky-and-ground ambient.
    float diff = max(dot(n, normalize(uLightDir)), 0.0);
    vec3 amb = mix(vec3(0.34, 0.38, 0.26), vec3(0.70, 0.80, 0.98), n.y * 0.5 + 0.5) * 0.62;
    vec3 lit = col * (amb + vec3(1.0, 0.96, 0.88) * diff * 0.80);

    float fog = smoothstep($FOG_START, $FOG_END, dist);
    fragColor = vec4(mix(lit, vec3($HORIZON_R, $HORIZON_G, $HORIZON_B), fog), 1.0);
}
"""
    }
}
