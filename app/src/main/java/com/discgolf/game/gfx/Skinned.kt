package com.discgolf.game.gfx

import android.content.Context
import android.opengl.GLES30
import android.opengl.Matrix
import com.discgolf.game.core.MiniJson
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One part of a skinned model, drawn in a single colour (the material). */
class SkinPart(val vao: Int, val vbo: Int, val ebo: Int, val count: Int, val material: String, val color: FloatArray)

/** One animation: for each node, the times and values of its translation, rotation and scale. */
class Clip(val name: String, val start: Float, val duration: Float, val channels: List<Channel>) {
    class Channel(val node: Int, val path: Int /* 0 T, 1 R, 2 S */, val times: FloatArray, val values: FloatArray)
}

/**
 * A character from a glTF file: a skeleton, skinned meshes and animation clips. Poses are blended on the CPU and the
 * bone matrices are handed to [SkinShader].
 */
class SkinnedModel(
    val parts: List<SkinPart>,
    private val parent: IntArray,
    private val restT: FloatArray, private val restR: FloatArray, private val restS: FloatArray,
    private val joints: IntArray,
    private val inverseBind: FloatArray,
    val clips: Map<String, Clip>,
    val nodeNames: List<String>,
    private val meshNodes: IntArray,
) {
    val nodeCount = parent.size
    val jointCount = joints.size
    private val order: IntArray = run {   // parents before children
        val seen = BooleanArray(nodeCount); val out = ArrayList<Int>()
        fun visit(n: Int) { if (seen[n]) return; if (parent[n] >= 0) visit(parent[n]); seen[n] = true; out += n }
        for (i in 0 until nodeCount) visit(i)
        out.toIntArray()
    }

    /** A local pose for every node: translation (3), rotation quaternion (4), scale (3). */
    class Pose(n: Int) { val t = FloatArray(n * 3); val r = FloatArray(n * 4); val s = FloatArray(n * 3) }

    fun newPose() = Pose(nodeCount)
    fun rest(p: Pose) {
        System.arraycopy(restT, 0, p.t, 0, restT.size); System.arraycopy(restR, 0, p.r, 0, restR.size); System.arraycopy(restS, 0, p.s, 0, restS.size)
    }

    /** Fills [p] with the rest pose, then the clip sampled [time] seconds from its start (clamped to its length). */
    fun sample(p: Pose, clip: Clip, time: Float) {
        rest(p)
        val t = clip.start + time.coerceIn(0f, clip.duration)
        for (c in clip.channels) {
            val ts = c.times
            var i = 0
            while (i < ts.size - 2 && t > ts[i + 1]) i++
            val u = if (ts.size < 2) 0f else ((t - ts[i]) / (ts[i + 1] - ts[i]).coerceAtLeast(1e-5f)).coerceIn(0f, 1f)
            val j = min(i + 1, ts.size - 1)
            val n = c.node
            when (c.path) {
                0 -> for (k in 0 until 3) p.t[n * 3 + k] = c.values[i * 3 + k] + (c.values[j * 3 + k] - c.values[i * 3 + k]) * u
                2 -> for (k in 0 until 3) p.s[n * 3 + k] = c.values[i * 3 + k] + (c.values[j * 3 + k] - c.values[i * 3 + k]) * u
                else -> slerp(c.values, i * 4, c.values, j * 4, u, p.r, n * 4)
            }
        }
    }

    /** out = a blended toward b by w. */
    fun blend(out: Pose, a: Pose, b: Pose, w: Float) {
        for (k in a.t.indices) out.t[k] = a.t[k] + (b.t[k] - a.t[k]) * w
        for (k in a.s.indices) out.s[k] = a.s[k] + (b.s[k] - a.s[k]) * w
        for (n in 0 until nodeCount) slerp(a.r, n * 4, b.r, n * 4, w, out.r, n * 4)
    }

    private val globals = FloatArray(nodeCount * 16)
    private val tmp = FloatArray(16)

    /** Works out where every node is, in the model's own space. */
    fun solve(p: Pose) {
        for (n in order) {
            local(p, n, tmp)
            if (parent[n] >= 0) Matrix.multiplyMM(globals, n * 16, globals, parent[n] * 16, tmp, 0)
            else System.arraycopy(tmp, 0, globals, n * 16, 16)
        }
    }

    private fun local(p: Pose, n: Int, out: FloatArray) {
        val x = p.r[n * 4]; val y = p.r[n * 4 + 1]; val z = p.r[n * 4 + 2]; val w = p.r[n * 4 + 3]
        val sx = p.s[n * 3]; val sy = p.s[n * 3 + 1]; val sz = p.s[n * 3 + 2]
        out[0] = (1 - 2 * (y * y + z * z)) * sx; out[1] = (2 * (x * y + z * w)) * sx; out[2] = (2 * (x * z - y * w)) * sx; out[3] = 0f
        out[4] = (2 * (x * y - z * w)) * sy; out[5] = (1 - 2 * (x * x + z * z)) * sy; out[6] = (2 * (y * z + x * w)) * sy; out[7] = 0f
        out[8] = (2 * (x * z + y * w)) * sz; out[9] = (2 * (y * z - x * w)) * sz; out[10] = (1 - 2 * (x * x + y * y)) * sz; out[11] = 0f
        out[12] = p.t[n * 3]; out[13] = p.t[n * 3 + 1]; out[14] = p.t[n * 3 + 2]; out[15] = 1f
    }

    fun indexOf(name: String) = nodeNames.indexOf(name)

    /** Position of a node after [solve], in model space. */
    fun positionOf(node: Int, out: FloatArray) { out[0] = globals[node * 16 + 12]; out[1] = globals[node * 16 + 13]; out[2] = globals[node * 16 + 14] }

    private val skinM = FloatArray(16)
    private val invMesh = FloatArray(16)

    /** The bone matrices for the shader as 3 rows of 4 numbers per joint, after [solve]. */
    fun boneRows(out: FloatArray) {
        val meshInv = meshNodes.firstOrNull()?.let { m -> Matrix.invertM(invMesh, 0, globals, m * 16); invMesh }
        for (j in 0 until jointCount) {
            Matrix.multiplyMM(skinM, 0, globals, joints[j] * 16, inverseBind, j * 16)
            if (meshInv != null) { Matrix.multiplyMM(tmp, 0, meshInv, 0, skinM, 0); System.arraycopy(tmp, 0, skinM, 0, 16) }
            for (r in 0 until 3) for (c in 0 until 4) out[j * 12 + r * 4 + c] = skinM[c * 4 + r]
        }
    }

    fun release() {
        for (p in parts) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(p.vao), 0)
            GLES30.glDeleteBuffers(2, intArrayOf(p.vbo, p.ebo), 0)
        }
    }

    private fun slerp(a: FloatArray, ai: Int, b: FloatArray, bi: Int, t: Float, out: FloatArray, oi: Int) {
        var dot = a[ai] * b[bi] + a[ai + 1] * b[bi + 1] + a[ai + 2] * b[bi + 2] + a[ai + 3] * b[bi + 3]
        val s = if (dot < 0f) -1f else 1f
        dot *= s
        val ka: Float; val kb: Float
        if (dot > 0.9995f) { ka = 1f - t; kb = t * s }
        else {
            val th = acos(dot.coerceIn(-1f, 1f)); val sn = sin(th)
            ka = sin((1f - t) * th) / sn; kb = sin(t * th) / sn * s
        }
        var l = 0f
        for (k in 0 until 4) { out[oi + k] = a[ai + k] * ka + b[bi + k] * kb; l += out[oi + k] * out[oi + k] }
        l = 1f / sqrt(max(l, 1e-9f))
        for (k in 0 until 4) out[oi + k] *= l
    }
}

/** Reads a character .gltf from assets/characters. Returns null (and logs) if anything is wrong. */
object SkinnedLoader {
    private fun num(m: Map<*, *>, k: String, d: Double = 0.0) = (m[k] as? Number)?.toDouble() ?: d
    private fun list(m: Map<*, *>, k: String) = m[k] as? List<*> ?: emptyList<Any?>()
    private fun map(o: Any?) = o as Map<*, *>
    private fun srgb(c: Float) = c.coerceIn(0f, 1f).pow(1f / 2.2f)

    /** Averages normals of vertices at the same spot (flat-shaded low-poly becomes soft), keeping a little of the original. */
    private fun smoothNormals(pos: FloatArray, nor: FloatArray): FloatArray {
        val sum = HashMap<Long, FloatArray>()
        fun key(i: Int): Long {
            fun q(v: Float) = (Math.round(v * 400f).toLong() and 0x1FFFFF)
            return q(pos[i * 3]) or (q(pos[i * 3 + 1]) shl 21) or (q(pos[i * 3 + 2]) shl 42)
        }
        val n = pos.size / 3
        for (i in 0 until n) { val a = sum.getOrPut(key(i)) { FloatArray(3) }; a[0] += nor[i * 3]; a[1] += nor[i * 3 + 1]; a[2] += nor[i * 3 + 2] }
        val out = FloatArray(nor.size)
        for (i in 0 until n) {
            val a = sum[key(i)]!!
            val l = 1f / sqrt(max(a[0] * a[0] + a[1] * a[1] + a[2] * a[2], 1e-9f))
            for (k in 0 until 3) out[i * 3 + k] = nor[i * 3 + k] * 0.15f + a[k] * l * 0.85f
        }
        return out
    }

    fun load(context: Context, name: String): SkinnedModel? = try { read(context, name) } catch (e: Exception) {
        android.util.Log.w("Skinned", "could not load $name: $e"); null
    }

    private fun read(context: Context, name: String): SkinnedModel {
        val json = map(MiniJson.parse(context.assets.open("characters/$name.gltf").bufferedReader().use { it.readText() }))
        val bin = ByteBuffer.wrap(context.assets.open("characters/$name.bin").use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN)
        val accessors = list(json, "accessors"); val views = list(json, "bufferViews")

        fun comps(type: String) = when (type) { "SCALAR" -> 1; "VEC2" -> 2; "VEC3" -> 3; "VEC4" -> 4; "MAT4" -> 16; else -> error("type $type") }
        /** Any accessor as floats (normalised integers are scaled to 0..1). */
        fun read(acc: Int): FloatArray {
            val a = map(accessors[acc]); val v = map(views[num(a, "bufferView").toInt()])
            val n = comps(a["type"] as String); val count = num(a, "count").toInt()
            val ct = num(a, "componentType").toInt()
            val size = when (ct) { 5126, 5125 -> 4; 5123, 5122 -> 2; else -> 1 }
            val start = num(v, "byteOffset").toInt() + num(a, "byteOffset").toInt()
            val stride = num(v, "byteStride").toInt().let { if (it == 0) n * size else it }
            val norm = a["normalized"] == true
            val out = FloatArray(count * n)
            for (i in 0 until count) for (c in 0 until n) {
                val o = start + i * stride + c * size
                out[i * n + c] = when (ct) {
                    5126 -> bin.getFloat(o)
                    5125 -> bin.getInt(o).toFloat()
                    5123 -> (bin.getShort(o).toInt() and 0xFFFF).let { if (norm) it / 65535f else it.toFloat() }
                    5121 -> (bin.get(o).toInt() and 0xFF).let { if (norm) it / 255f else it.toFloat() }
                    else -> error("component type $ct")
                }
            }
            return out
        }

        // ---- nodes
        val nodes = list(json, "nodes").map { map(it) }
        val n = nodes.size
        val parent = IntArray(n) { -1 }
        for ((i, nd) in nodes.withIndex()) for (c in list(nd, "children")) parent[(c as Number).toInt()] = i
        val rt = FloatArray(n * 3); val rr = FloatArray(n * 4); val rs = FloatArray(n * 3)
        for ((i, nd) in nodes.withIndex()) {
            val t = list(nd, "translation").map { (it as Number).toFloat() }
            val r = list(nd, "rotation").map { (it as Number).toFloat() }
            val s = list(nd, "scale").map { (it as Number).toFloat() }
            for (k in 0 until 3) { rt[i * 3 + k] = t.getOrElse(k) { 0f }; rs[i * 3 + k] = s.getOrElse(k) { 1f } }
            for (k in 0 until 4) rr[i * 4 + k] = r.getOrElse(k) { if (k == 3) 1f else 0f }
        }
        val names = nodes.map { (it["name"] as? String) ?: "" }

        // ---- skin
        val skin = map(list(json, "skins")[0])
        val joints = list(skin, "joints").map { (it as Number).toInt() }.toIntArray()
        val ibm = read(num(skin, "inverseBindMatrices").toInt())

        // ---- meshes
        val materials = list(json, "materials").map { map(it) }
        val parts = ArrayList<SkinPart>()
        val meshNodes = ArrayList<Int>()
        for ((ni, nd) in nodes.withIndex()) {
            val mi = nd["mesh"] ?: continue
            meshNodes += ni
            for (prim in list(map(list(json, "meshes")[(mi as Number).toInt()]), "primitives")) {
                val p = map(prim); val at = map(p["attributes"])
                val pos = read(num(at, "POSITION").toInt()); val nor = smoothNormals(pos, read(num(at, "NORMAL").toInt()))
                val jn = read(num(at, "JOINTS_0").toInt()); val wt = read(num(at, "WEIGHTS_0").toInt())
                val idx = read(num(p, "indices").toInt())
                val cnt = pos.size / 3
                val vb = ByteBuffer.allocateDirect(cnt * 14 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                for (i in 0 until cnt) {
                    vb.put(pos[i * 3]).put(pos[i * 3 + 1]).put(pos[i * 3 + 2]).put(nor[i * 3]).put(nor[i * 3 + 1]).put(nor[i * 3 + 2])
                    for (k in 0 until 4) vb.put(jn[i * 4 + k])
                    for (k in 0 until 4) vb.put(wt[i * 4 + k])
                }
                vb.position(0)
                val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
                for (v in idx) ib.put(v.toInt().toShort())
                ib.position(0)
                val mat = map(materials[num(p, "material").toInt()])
                val bc = list(map(mat["pbrMetallicRoughness"] ?: emptyMap<String, Any>()), "baseColorFactor").map { (it as Number).toFloat() }
                val color = floatArrayOf(srgb(bc.getOrElse(0) { 0.8f }), srgb(bc.getOrElse(1) { 0.8f }), srgb(bc.getOrElse(2) { 0.8f }))

                val ids = IntArray(3)
                GLES30.glGenVertexArrays(1, ids, 0); GLES30.glGenBuffers(2, ids, 1)
                GLES30.glBindVertexArray(ids[0])
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, cnt * 56, vb, GLES30.GL_STATIC_DRAW)
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[2])
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GLES30.GL_STATIC_DRAW)
                GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 56, 0)
                GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 56, 12)
                GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, 56, 24)
                GLES30.glEnableVertexAttribArray(3); GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, 56, 40)
                GLES30.glBindVertexArray(0)
                parts += SkinPart(ids[0], ids[1], ids[2], idx.size, (mat["name"] as? String) ?: "", color)
            }
        }

        // ---- animations
        val clips = LinkedHashMap<String, Clip>()
        for (an in list(json, "animations")) {
            val a = map(an); val samplers = list(a, "samplers").map { map(it) }
            val chans = ArrayList<Clip.Channel>(); var t0 = Float.MAX_VALUE; var t1 = -Float.MAX_VALUE
            for (ch in list(a, "channels")) {
                val c = map(ch); val tg = map(c["target"])
                val path = when (tg["path"]) { "translation" -> 0; "rotation" -> 1; "scale" -> 2; else -> continue }
                val sm = samplers[num(c, "sampler").toInt()]
                val times = read(num(sm, "input").toInt()); val values = read(num(sm, "output").toInt())
                t0 = min(t0, times.first()); t1 = max(t1, times.last())
                chans += Clip.Channel(num(tg, "node").toInt(), path, times, values)
            }
            val nm = (a["name"] as? String) ?: "clip${clips.size}"
            clips[nm] = Clip(nm, t0, max(t1 - t0, 1e-3f), chans)
        }
        return SkinnedModel(parts, parent, rt, rr, rs, joints, ibm, clips, names, meshNodes.toIntArray())
    }
}

/** Draws a [SkinnedModel] with bone matrices, simple sun and sky light, and the shared fog. */
class SkinShader {
    private val prog = linkProgram(VERT, FRAG)
    private val uMvp = GLES30.glGetUniformLocation(prog, "uMVP")
    private val uModel = GLES30.glGetUniformLocation(prog, "uModel")
    private val uBones = GLES30.glGetUniformLocation(prog, "uBones")
    private val uColor = GLES30.glGetUniformLocation(prog, "uColor")
    private val uLight = GLES30.glGetUniformLocation(prog, "uLightDir")
    private val uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
    private val uFog = GLES30.glGetUniformLocation(prog, "uFogColor")
    private val uOutline = GLES30.glGetUniformLocation(prog, "uOutline")
    private val uKind = GLES30.glGetUniformLocation(prog, "uKind")
    private val rows = FloatArray(MAX_BONES * 12)

    fun begin(sun: FloatArray, cx: Float, cy: Float, cz: Float) {
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, sun[0], sun[1], sun[2])
        GLES30.glUniform3f(uCam, cx, cy, cz)
        GLES30.glUniform3f(uFog, HORIZON_R, HORIZON_G, HORIZON_B)
    }

    /** [tint] maps a material name to a colour override (r, g, b), or null to keep the model's own. */
    fun draw(model: SkinnedModel, mvp: FloatArray, world: FloatArray, tint: (String) -> FloatArray?) {
        model.boneRows(rows)
        GLES30.glUniform4fv(uBones, min(model.jointCount, MAX_BONES) * 3, rows, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, world, 0)
        // Cartoon outline: the model pushed out along its normals, back faces only, in near-black.
        GLES30.glEnable(GLES30.GL_CULL_FACE); GLES30.glCullFace(GLES30.GL_FRONT)
        GLES30.glUniform1f(uOutline, 0.020f)
        for (p in model.parts) { GLES30.glBindVertexArray(p.vao); GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.count, GLES30.GL_UNSIGNED_SHORT, 0) }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glUniform1f(uOutline, 0f)
        for (p in model.parts) {
            val over = tint(p.material)
            val c = over ?: p.color
            // What the surface is, so the shader can shade skin, hair, cloth and shoes differently.
            val kind = when {
                p.material.startsWith("Skin") -> 2f
                p.material == "Hair" || p.material == "Eyebrows" -> 3f
                p.material == "Eye" -> 4f
                over != null -> 1f
                else -> 0f
            }
            GLES30.glUniform1f(uKind, kind)
            GLES30.glUniform3f(uColor, c[0], c[1], c[2])
            GLES30.glBindVertexArray(p.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.count, GLES30.GL_UNSIGNED_SHORT, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    companion object {
        const val MAX_BONES = 64
        private const val VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec4 aJoint;
layout(location = 3) in vec4 aWeight;
uniform mat4 uMVP;
uniform mat4 uModel;
uniform vec4 uBones[192];
uniform float uOutline;
uniform vec3 uCamPos;
out vec3 vNormal;
out vec3 vWorld;
out float vLocalY;
vec3 skinPos(vec3 p, vec3 n, out vec3 sn) {
    vec4 p4 = vec4(p, 1.0);
    vec3 pos = vec3(0.0); sn = vec3(0.0);
    for (int k = 0; k < 4; k++) {
        float w = aWeight[k];
        if (w <= 0.0) continue;
        int j = int(aJoint[k] + 0.5) * 3;
        vec3 q = vec3(dot(uBones[j], p4), dot(uBones[j + 1], p4), dot(uBones[j + 2], p4));
        vec3 m = vec3(dot(uBones[j].xyz, n), dot(uBones[j + 1].xyz, n), dot(uBones[j + 2].xyz, n));
        pos += q * w; sn += m * w;
    }
    return pos;
}
void main() {
    vec3 sn;
    vec3 p = skinPos(aPos, aNormal, sn);
    float cd = length((uModel * vec4(p, 1.0)).xyz - uCamPos);
    p += normalize(sn) * uOutline * clamp(0.55 + 0.09 * cd, 0.7, 1.8);
    vec4 w = uModel * vec4(p, 1.0);
    vWorld = w.xyz;
    vLocalY = w.y - uModel[3].y;            // height above the golfer's feet
    vNormal = normalize(mat3(uModel) * sn);
    gl_Position = uMVP * vec4(p, 1.0);
}
"""
        private const val FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
in float vLocalY;
uniform float uKind;
uniform vec3 uColor;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform vec3 uFogColor;
uniform float uOutline;
out vec4 fragColor;
void main() {
    if (uOutline > 0.0) { fragColor = vec4(0.11, 0.06, 0.14, 1.0); return; }
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    // Brighter, more saturated than the model's own colours.
    float lum0 = dot(uColor, vec3(0.30, 0.59, 0.11));
    vec3 base = min(mix(vec3(lum0), uColor, 1.40) * 1.14, vec3(1.0));
    float wrap = dot(n, normalize(uLightDir)) * 0.5 + 0.5;
    // Toon ramp: deep cool shade, soft mid band, bright lit band.
    bool skin = uKind > 1.5 && uKind < 2.5;
    bool hair = uKind > 2.5 && uKind < 3.5;
    bool cloth = uKind > 0.5 && uKind < 1.5;
    // Skin shades warm and rosy; everything else shades cool.
    vec3 c0 = base * (skin ? vec3(0.80, 0.60, 0.64) : vec3(0.62, 0.58, 0.88));
    vec3 c1 = base * (skin ? vec3(0.94, 0.84, 0.84) : vec3(0.80, 0.80, 0.96));
    vec3 c2 = base * vec3(1.14, 1.08, 0.96);
    vec3 lit = mix(c0, c1, smoothstep(0.26, 0.34, wrap));
    lit = mix(lit, c2, smoothstep(0.58, 0.68, wrap));
    lit *= mix(vec3(0.86, 0.86, 0.80), vec3(0.98, 1.02, 1.10), n.y * 0.5 + 0.5);   // sky / ground hemisphere
    // Cloth: fine weave bands, a softly darker hem and underarm, brighter shoulders.
    if (cloth) {
        lit *= 1.0 - 0.045 * smoothstep(0.35, 0.65, sin(vLocalY * 120.0) * 0.5 + 0.5);
        lit *= mix(0.90, 1.06, smoothstep(0.60, 1.50, vLocalY));
        lit *= 1.0 - 0.10 * (1.0 - smoothstep(0.0, 0.05, abs(vLocalY - 0.78)));     // waistband line
    }
    // Hair: a glossy band of light across the crown.
    if (hair) {
        vec3 hv = normalize(normalize(uLightDir) + normalize(uCamPos - vWorld));
        lit += vec3(1.0, 0.95, 0.85) * smoothstep(0.84, 0.90, dot(n, hv)) * 0.30;
    }
    // Shoes and feet: darker soles with a pale band above, and a little ground bounce shade on the lower legs.
    if (!cloth && !hair && !skin) {
        lit *= mix(0.55, 1.0, smoothstep(0.015, 0.045, vLocalY));
        lit += vec3(0.10) * (1.0 - smoothstep(0.045, 0.065, abs(vLocalY - 0.055)));
    }
    lit *= mix(0.88, 1.0, smoothstep(0.0, 0.45, vLocalY));
    vec3 vdir = normalize(uCamPos - vWorld);
    float f = 1.0 - max(dot(n, vdir), 0.0);
    float rim = smoothstep(0.52, 0.80, f);
    lit += mix(vec3(0.55, 0.75, 1.0), vec3(1.0, 0.92, 0.70), smoothstep(0.3, 0.7, wrap)) * rim * 0.42;
    float d = length(vWorld - uCamPos);
    fragColor = vec4(mix(lit, uFogColor, smoothstep($FOG_START, $FOG_END, d)), 1.0);
}
"""
    }
}
