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
import kotlin.math.sqrt

/**
 * The ground around one hole: a height-field mesh with normals, a ring of skirt that carries the ground out to the
 * horizon, and a small "surface map" texture saying where the fairway, water and out of bounds are.
 * The fragment shader turns that map into grass, mowing stripes, mud and so on.
 */
class TerrainRender(val hole: Hole, sun: FloatArray = floatArrayOf(0.45f, 0.80f, 0.40f)) {
    private val mesh: Mesh
    private var tex = 0
    private var shadeTex = 0
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
            var y = t.heightAt(x, z)
            // Sink pond beds under the water plane so the shore line, foam and ripples show instead of mud flats.
            for (p in hole.ponds) {
                val qx = (x - p.cx) / p.rx; val qz = (z - p.cz) / p.rz
                val q = kotlin.math.sqrt(qx * qx + qz * qz)
                val u = ((q - 0.80f) / 0.50f).coerceIn(0f, 1f)
                val keep = u * u * (3f - 2f * u)          // 0 = full pond bed, 1 = untouched ground
                y = y * keep + min(y, p.level - 0.16f) * (1f - keep)
            }
            vtx(x, y, z, t.normalAt(x, z))
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
            buf.put(if (hole.sandAt(x, z) && s != Surface.WATER) ON else OFF)
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

        // Shade map, same grid: R = soft tshade shadows of the trees on the ground (long blobs falling away from the sun),
        // G = ambient occlusion pooled round each trunk. Baked once per hole; the ground shader reads it bilinearly.
        val shadow = FloatArray(tw * th)
        val occl = FloatArray(tw * th)
        val sxz = sqrt(sun[0] * sun[0] + sun[2] * sun[2]).coerceAtLeast(1e-3f)
        val dirX = -sun[0] / sxz; val dirZ = -sun[2] / sxz
        val reach = sxz / sun[1].coerceAtLeast(0.2f)           // metres of shadow per metre of height
        for (tr in hole.trees) {
            val cr = tr.canopyR * 1.1f + 0.6f
            val len = min(tr.height * 0.62f * reach, 14f)
            val ex = tr.x + dirX * len; val ez = tr.z + dirZ * len
            val aoR = 1.6f + tr.canopyR * 0.5f
            val reachR = max(cr + 1f, aoR) + len
            val i0 = max(0, ((tr.x - reachR - wx0) / texel).toInt()); val i1 = min(tw - 1, ((tr.x + reachR - wx0) / texel).toInt())
            val j0 = max(0, ((tr.z - reachR - wz0) / texel).toInt()); val j1 = min(th - 1, ((tr.z + reachR - wz0) / texel).toInt())
            val l2 = max(len * len, 1e-4f)
            for (j in j0..j1) for (i in i0..i1) {
                val px = wx0 + (i + 0.5f) * texel; val pz = wz0 + (j + 0.5f) * texel
                val u = (((px - tr.x) * (ex - tr.x) + (pz - tr.z) * (ez - tr.z)) / l2).coerceIn(0f, 1f)
                val qx = px - (tr.x + (ex - tr.x) * u); val qz = pz - (tr.z + (ez - tr.z) * u)
                val d = sqrt(qx * qx + qz * qz)
                val su = ((u - 0.25f) / 0.75f).coerceIn(0f, 1f)
                val rad = (0.35f + tr.trunkR) + (cr - 0.35f - tr.trunkR) * su * su * (3f - 2f * su)
                val k = ((d - rad * 0.45f) / (rad * 0.75f)).coerceIn(0f, 1f)
                val sh = (1f - k * k * (3f - 2f * k)) * 0.9f
                val o = j * tw + i
                if (sh > shadow[o]) shadow[o] = sh
                val dx = px - tr.x; val dz = pz - tr.z
                val a = (1f - sqrt(dx * dx + dz * dz) / aoR).coerceIn(0f, 1f)
                val av = a * a * (3f - 2f * a)
                if (av > occl[o]) occl[o] = av
            }
        }
        val sbuf = ByteBuffer.allocateDirect(tw * th * 4).order(ByteOrder.nativeOrder())
        for (o in 0 until tw * th) {
            sbuf.put((shadow[o] * 255f).toInt().toByte()); sbuf.put((occl[o] * 255f).toInt().toByte()); sbuf.put(0.toByte()); sbuf.put((-1).toByte())
        }
        sbuf.position(0)
        GLES30.glGenTextures(1, ids, 0)
        shadeTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadeTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, tw, th, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, sbuf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        texX0 = wx0; texZ0 = wz0; texW = tw * texel; texH = th * texel
    }

    fun bindAndDraw(uRect: Int, uTex: Int, uShade: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadeTex)
        GLES30.glUniform1i(uShade, 1)
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
        val ids = intArrayOf(tex, shadeTex)
        GLES30.glDeleteTextures(2, ids, 0)
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
    private val uTee = GLES30.glGetUniformLocation(prog, "uTee")
    private val uTeeDir = GLES30.glGetUniformLocation(prog, "uTeeDir")
    private val uShade = GLES30.glGetUniformLocation(prog, "uShade")

    fun draw(t: TerrainRender, vp: FloatArray, cam: Vec3, light: FloatArray) {
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        GLES30.glUniform3f(uLight, light[0], light[1], light[2])
        GLES30.glUniform3f(uCam, cam.x, cam.y, cam.z)
        val b = t.hole.basket
        GLES30.glUniform3f(uBasket, b.x, b.y, b.z)
        val tee = t.hole.tee
        GLES30.glUniform3f(uTee, tee.x, tee.y, tee.z)
        // Which way the tee pad points: along the first leg of the hole.
        val p = t.hole.path
        val tx = if (p.size > 1) p[1].x - p[0].x else 0f
        val tz = if (p.size > 1) p[1].z - p[0].z else 1f
        val tl = sqrt(tx * tx + tz * tz).coerceAtLeast(1e-3f)
        GLES30.glUniform2f(uTeeDir, tx / tl, tz / tl)
        t.bindAndDraw(uRect, uTex, uShade)
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
uniform vec3 uTee;
uniform vec2 uTeeDir;
uniform sampler2D uShade;
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

$GRADE_GLSL
void main() {
    vec3 n = normalize(vNormal);
    // Wobble the lookup so the edges of fairway, sand and water are organic curves, not the map's one-metre grid.
    vec2 wob = vec2(vnoise(vWorld.xz * 0.9), vnoise(vWorld.xz * 0.9 + 31.7)) - 0.5;
    wob += (vec2(vnoise(vWorld.xz * 3.1), vnoise(vWorld.xz * 3.1 + 9.3)) - 0.5) * 0.45;
    vec2 uv = (vWorld.xz + wob * 1.3 - uSurfRect.xy) * uSurfRect.zw;
    vec4 map = texture(uSurf, uv);
    float fair = smoothstep(0.22, 0.78, map.r);     // soft, hand-mown fairway edge
    vec4 shd = texture(uShade, (vWorld.xz - uSurfRect.xy) * uSurfRect.zw);
    float water = smoothstep(0.30, 0.70, map.g);
    float oob = map.b;

    float dist = length(vWorld - uCamPos);
    float broad = fbm(vWorld.xz * 0.045);
    float mid = fbm(vWorld.xz * 0.30);
    // Fine grain fades with distance so far ground does not shimmer.
    float fine = mix(vnoise(vWorld.xz * 3.5), 0.5, smoothstep(25.0, 90.0, dist));

    vec3 rough = mix(vec3(0.07, 0.29, 0.09), vec3(0.18, 0.45, 0.12), broad);
    rough = mix(rough, vec3(0.33, 0.37, 0.14), smoothstep(0.58, 0.82, mid) * 0.5);   // dry, yellowed patches
    // Close up: individual blades (stretched noise at two scales) and speckle, fading before they would shimmer.
    float near = 1.0 - smoothstep(6.0, 55.0, dist);
    float blades = vnoise(vec2(vWorld.x * 38.0, vWorld.z * 9.0)) * 0.5 + vnoise(vec2(vWorld.x * 11.0 + 7.0, vWorld.z * 26.0)) * 0.5;
    float speck = vnoise(vWorld.xz * 95.0);
    float detail = ((blades - 0.5) * 0.30 + (speck - 0.5) * 0.14) * near;
    rough *= 0.86 + 0.28 * fine;
    rough *= 1.0 + detail;
    rough = mix(rough, rough * vec3(1.15, 1.2, 0.7), smoothstep(0.7, 0.95, speck) * near * 0.5);   // pale blade tips

    // Hand-painted grass: long brush streaks running along the mowing stripes at two scales, plus pale blade flicks up close.
    float nfade = 1.0 - smoothstep(14.0, 100.0, dist);
    float st1 = vnoise(vec2(vWorld.x * 0.50, vWorld.z * 4.8));
    float st2 = vnoise(vec2(vWorld.x * 2.4 + 4.0, vWorld.z * 22.0));
    float paint = ((st1 - 0.5) * 0.22 + (st2 - 0.5) * 0.17) * nfade;
    float flick = smoothstep(0.74, 0.96, vnoise(vec2(vWorld.x * 6.0 + 2.0, vWorld.z * 55.0))) * near;
    rough *= 1.0 + paint * 1.2;
    vec3 fairway = mix(vec3(0.27, 0.58, 0.19), vec3(0.38, 0.67, 0.22), broad);
    // Mowing stripes, fading out in the distance where they would alias.
    float stripe = smoothstep(0.38, 0.62, fract(vWorld.z / 6.5 + (vnoise(vec2(vWorld.x * 0.07, 3.0)) - 0.5) * 0.04));
    fairway *= 1.0 - 0.26 * stripe * (1.0 - smoothstep(60.0, 160.0, dist));
    fairway *= 1.0 + paint;
    fairway = mix(fairway, fairway * vec3(1.22, 1.20, 0.80), flick * 0.55);
    fairway *= 0.94 + 0.12 * fine;
    fairway *= 0.92 + 0.16 * smoothstep(0.30, 0.75, mid);                  // worn and lush patches
    fairway = mix(fairway, fairway * vec3(0.90, 0.94, 0.82), smoothstep(0.55, 0.9, fbm(vWorld.xz * 0.12 + 5.0)) * 0.6);
    fairway *= 1.0 + detail * 0.8;

    // Putting green around the basket: short, bright, mown in rings.
    float dB = length(vWorld.xz - uBasket.xz);
    float green = 1.0 - smoothstep(6.2, 7.2, dB);
    vec3 greenCol = vec3(0.36, 0.62, 0.27) * (1.0 - 0.03 * step(0.5, fract(dB / 1.4)));

    float sand = smoothstep(0.35, 0.65, map.a);
    float lip = smoothstep(0.08, 0.35, map.a) * (1.0 - smoothstep(0.40, 0.75, map.a));
    vec3 col = mix(rough, fairway, fair);
    col = mix(col, greenCol, green);
    // Lighter fringe where the fairway meets the rough, like a mown collar.
    float edge = smoothstep(0.14, 0.42, map.r) * (1.0 - smoothstep(0.42, 0.80, map.r));
    col = mix(col, col * vec3(1.10, 1.08, 0.90), edge * 0.8);
    col = mix(col, col * vec3(0.84, 0.84, 0.70), oob * 0.55);                // out of bounds is scruffier

    // Steep banks show earth through the grass; ponds are muddy.
    float slope = 1.0 - n.y;
    col = mix(col, vec3(0.42, 0.36, 0.27), smoothstep(0.16, 0.34, slope) * 0.65);
    col = mix(col, vec3(0.30, 0.27, 0.19), water * 0.85);
    // Wet earth just outside the water, a bright lip round each bunker, then the sand itself with ripple lines and grit.
    col = mix(col, vec3(0.40, 0.34, 0.22), smoothstep(0.04, 0.35, map.g) * (1.0 - water) * 0.7);
    col = mix(col, col * vec3(1.12, 1.05, 0.80), lip * 0.85);
    float rip = 0.5 + 0.5 * sin(vWorld.x * 2.2 + vWorld.z * 1.3 + vnoise(vWorld.xz * 0.8) * 5.0);
    vec3 sandCol = mix(vec3(0.89, 0.80, 0.55), vec3(0.80, 0.70, 0.45), rip * 0.35 + (vnoise(vWorld.xz * 30.0) - 0.5) * 0.25 * (1.0 - smoothstep(10.0, 50.0, dist)));
    col = mix(col, sandCol, sand);

    // Grass tufts: soft dark-cored clumps with lighter blade tips, scattered on a jittered grid; only in the rough, and only near.
    float tuftFade = 1.0 - smoothstep(45.0, 120.0, dist);
    float inRough = (1.0 - fair) * (1.0 - green) * (1.0 - sand) * (1.0 - water);
    if (tuftFade > 0.0 && inRough > 0.0) {
        vec2 tp = vWorld.xz * 0.95;
        vec2 tc = floor(tp), tf = fract(tp) - 0.5;
        vec2 jit = vec2(hash(tc + 2.0), hash(tc + 9.0)) - 0.5;
        vec2 td = tf - jit * 0.5;
        float tr = length(td);
        float tang = atan(td.y, td.x);
        float shape = 0.5 + 0.5 * sin(tang * 7.0 + hash(tc) * 6.28);
        float tuft = (1.0 - smoothstep(0.12, 0.36 + 0.08 * shape, tr)) * step(0.22, hash(tc + 5.0));
        float core = 1.0 - smoothstep(0.0, 0.16, tr);
        vec3 tcol = mix(col * 1.22, col * 0.66, core);
        tcol = mix(tcol, col * vec3(1.35, 1.38, 0.85), smoothstep(0.14, 0.30, tr) * shape * 0.8);
        col = mix(col, tcol, tuft * tuftFade * inRough);
        // Tiny flowers: white, yellow, pink and pale blue dots.
        vec2 fp = vWorld.xz * 2.1;
        vec2 fc = floor(fp), ff = fract(fp) - 0.5;
        float fh = hash(fc + 31.0);
        vec2 fj = (vec2(hash(fc + 3.0), hash(fc + 6.0)) - 0.5) * 0.6;
        float fr = length(ff - fj);
        float fl = step(0.93, fh) * (1.0 - smoothstep(0.06, 0.11, fr)) * (1.0 - smoothstep(25.0, 55.0, dist));
        float kh = hash(fc + 77.0);
        vec3 fcol = kh < 0.3 ? vec3(1.0, 0.97, 0.90) : kh < 0.55 ? vec3(1.0, 0.88, 0.25) : kh < 0.8 ? vec3(1.0, 0.55, 0.72) : vec3(0.60, 0.78, 1.0);
        fcol = mix(fcol, vec3(1.0, 0.85, 0.2), 1.0 - smoothstep(0.0, 0.05, fr));
        col = mix(col, fcol, fl * inRough);
    }
    // Worn dirt apron round the tee pad, with a soft grassy edge.
    vec2 tdv = (vWorld.xz - uTee.xz) * vec2(1.0, 0.62);
    float dT = length(tdv) + (vnoise(vWorld.xz * 1.7) - 0.5) * 0.9;
    float dirt = 1.0 - smoothstep(1.1, 1.9, dT);
    vec3 dirtCol = mix(vec3(0.74, 0.62, 0.40), vec3(0.66, 0.54, 0.34), vnoise(vWorld.xz * 6.0)) * (0.92 + 0.16 * vnoise(vWorld.xz * 40.0));
    col = mix(col, dirtCol, dirt * 0.75);
    // The tee pad itself: a flat concrete-grey slab with a darker border line, aligned with the hole.
    vec2 rel = vWorld.xz - uTee.xz;
    vec2 pq = abs(vec2(dot(rel, vec2(-uTeeDir.y, uTeeDir.x)), dot(rel, uTeeDir) - 0.35)) - vec2(0.95, 1.9);
    float pd = max(pq.x, pq.y) + (vnoise(vWorld.xz * 9.0) - 0.5) * 0.04;
    float pad = 1.0 - smoothstep(-0.04, 0.05, pd);
    vec3 padCol = mix(vec3(0.70, 0.67, 0.60), vec3(0.62, 0.60, 0.54), vnoise(vWorld.xz * 14.0));
    padCol *= 0.94 + 0.10 * vnoise(vWorld.xz * 60.0);
    padCol = mix(padCol, vec3(0.42, 0.41, 0.37), (1.0 - smoothstep(0.0, 0.07, abs(pd + 0.12))) * 0.8);
    // Aggregate speckle, expansion joints every ~1.27 m with a centre seam, a lit bevel at the lip.
    vec2 plq = vec2(dot(rel, vec2(-uTeeDir.y, uTeeDir.x)), dot(rel, uTeeDir) - 0.35);
    float spk = hash(floor(vWorld.xz * 42.0));
    padCol *= 1.0 + (step(0.93, spk) - step(spk, 0.07)) * 0.10 * (1.0 - smoothstep(10.0, 40.0, dist));
    float jt = min(abs(fract(plq.y / 1.27 + 0.5) - 0.5) * 1.27, abs(plq.x));
    padCol *= 1.0 - 0.26 * (1.0 - smoothstep(0.010, 0.026, jt)) * step(pd, -0.16);
    padCol += vec3(0.07, 0.07, 0.06) * (1.0 - smoothstep(0.0, 0.05, abs(pd + 0.045)));
    // Slab thickness: a thin dark shadow just outside the edge, and loose pebbles scattered round it in the dirt.
    col *= 1.0 - 0.30 * smoothstep(0.0, 0.03, pd) * (1.0 - smoothstep(0.03, 0.16, pd));
    float peb = hash(floor(vWorld.xz * 11.0) + 5.0);
    vec2 pf = fract(vWorld.xz * 11.0) - 0.5;
    float pebble = step(0.86, peb) * (1.0 - smoothstep(0.10, 0.20, length(pf))) * dirt * (1.0 - pad) * (1.0 - smoothstep(12.0, 30.0, dist));
    col = mix(col, vec3(0.60 + 0.2 * peb, 0.56 + 0.18 * peb, 0.48 + 0.15 * peb), pebble * 0.8);
    col = mix(col, padCol, pad);
    // Soft ambient occlusion pooled round tree bases (darker, deeper green, a little earth).
    col = mix(col, col * vec3(0.52, 0.58, 0.42), shd.g * 0.75);

    // Light: sun plus sky-and-ground ambient.
    // Contour: exaggerate the slope for lighting so rolls and dips read, and darken creases (curvature from normal derivatives).
    vec3 ln = normalize(vec3(n.x * 2.6, n.y, n.z * 2.6));
    float diff = max(dot(ln, normalize(uLightDir)), 0.0);
    float crease = clamp((length(dFdx(n)) + length(dFdy(n))) * 14.0 / (1.0 + dist * 0.02), 0.0, 1.0);
    float hgt = clamp((vWorld.y - uTee.y) * 0.08 + 0.5, 0.0, 1.0);
    col *= mix(vec3(0.86, 0.94, 0.98), vec3(1.07, 1.04, 0.92), hgt);          // low ground cooler, high ground sun-warmed
    col *= 1.0 - 0.22 * crease * (1.0 - sand);
    // Big soft cloud-shadow-like patches of light and dark for depth.
    col *= 0.90 + 0.20 * fbm(vWorld.xz * 0.018 + 40.0);
    vec3 amb = mix(vec3(0.40, 0.46, 0.34), vec3(0.78, 0.88, 1.0), ln.y * 0.5 + 0.5) * 0.70;
    // Tree shadows: noisy, soft, cool; they take the sun away but keep the sky fill.
    float tshade = smoothstep(0.10, 0.90, shd.r + (vnoise(vWorld.xz * 1.4) - 0.5) * 0.30) * (1.0 - pad * 0.15);
    vec3 lit = col * (amb * (1.0 - 0.10 * tshade) + vec3(1.0, 0.93, 0.78) * diff * 0.58 * (1.0 - 0.88 * tshade));
    lit = mix(lit, lit * vec3(0.84, 0.92, 1.08), tshade * 0.6);

    float lum = dot(lit, vec3(0.3, 0.59, 0.11));
    lit = mix(vec3(lum), lit, 1.0);
    float fog = smoothstep($FOG_START, $FOG_END, dist);
    fragColor = vec4(grade(mix(lit, vec3($HORIZON_R, $HORIZON_G, $HORIZON_B), fog)), 1.0);
}
"""
    }
}
