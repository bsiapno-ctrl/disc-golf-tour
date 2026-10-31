package com.discgolf.game.gfx

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import com.discgolf.game.core.MiniJson
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** One drawable piece of a model: a vertex array, how many indices it has, and its picture (texture). */
class ModelPart(val vao: Int, val vbo: Int, val ebo: Int, val count: Int, val texture: Int, val leaf: Boolean = false)

/** A textured model read from a glTF file. [height] is how tall it is before any scaling, in its own units. */
class Model(val parts: List<ModelPart>, val height: Float, val radius: Float) {
    fun release() {
        for (p in parts) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(p.vao), 0)
            GLES30.glDeleteBuffers(2, intArrayOf(p.vbo, p.ebo), 0)
        }
    }
}

/**
 * Loads the .gltf + .bin models in assets/models. Only what these simple models need is read: positions, normals,
 * texture coordinates, 16/32-bit indices, one node transform, and the base colour picture of each material.
 */
class ModelLibrary(private val context: Context) {
    private val models = HashMap<String, Model?>()
    private val textures = HashMap<String, Int>()

    fun get(name: String): Model? = models.getOrPut(name) {
        try { load(name) } catch (e: Exception) { android.util.Log.w("Models", "could not load $name: $e"); null }
    }

    fun release() {
        for (m in models.values) m?.release()
        models.clear()
        if (textures.isNotEmpty()) GLES30.glDeleteTextures(textures.size, textures.values.toIntArray(), 0)
        textures.clear()
    }

    private fun texture(file: String): Int = textures.getOrPut(file) {
        val bmp = context.assets.open("models/$file").use { BitmapFactory.decodeStream(it) }
        val id = IntArray(1)
        GLES30.glGenTextures(1, id, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
        bmp.recycle()
        id[0]
    }

    private fun num(m: Map<*, *>, k: String, d: Double = 0.0) = (m[k] as? Number)?.toDouble() ?: d
    private fun list(m: Map<*, *>, k: String) = m[k] as? List<*> ?: emptyList<Any?>()
    private fun map(o: Any?) = o as Map<*, *>

    private fun load(name: String): Model {
        val json = map(MiniJson.parse(context.assets.open("models/$name.gltf").bufferedReader().use { it.readText() }))
        val bin = context.assets.open("models/$name.bin").use { it.readBytes() }
        val data = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN)
        val accessors = list(json, "accessors"); val views = list(json, "bufferViews")

        fun floats(acc: Int, comps: Int): FloatArray {
            val a = map(accessors[acc]); val v = map(views[num(a, "bufferView").toInt()])
            val count = num(a, "count").toInt()
            val start = num(v, "byteOffset").toInt() + num(a, "byteOffset").toInt()
            val stride = num(v, "byteStride").toInt().let { if (it == 0) comps * 4 else it }
            val out = FloatArray(count * comps)
            for (i in 0 until count) for (c in 0 until comps) out[i * comps + c] = data.getFloat(start + i * stride + c * 4)
            return out
        }
        fun indices(acc: Int): ShortArray {
            val a = map(accessors[acc]); val v = map(views[num(a, "bufferView").toInt()])
            val count = num(a, "count").toInt()
            val start = num(v, "byteOffset").toInt() + num(a, "byteOffset").toInt()
            val wide = num(a, "componentType").toInt() == 5125
            return ShortArray(count) { if (wide) data.getInt(start + it * 4).toShort() else data.getShort(start + it * 2) }
        }

        // The single node's transform (translation, rotation as a quaternion, scale), baked into the vertices.
        val node = list(json, "nodes").firstOrNull()?.let { map(it) }
        val t = node?.let { list(it, "translation").map { v -> (v as Number).toFloat() } }?.takeIf { it.size == 3 } ?: listOf(0f, 0f, 0f)
        val q = node?.let { list(it, "rotation").map { v -> (v as Number).toFloat() } }?.takeIf { it.size == 4 } ?: listOf(0f, 0f, 0f, 1f)
        val s = node?.let { list(it, "scale").map { v -> (v as Number).toFloat() } }?.takeIf { it.size == 3 } ?: listOf(1f, 1f, 1f)
        fun rot(x: Float, y: Float, z: Float): FloatArray {
            val (qx, qy, qz, qw) = q
            val tx = 2f * (qy * z - qz * y); val ty = 2f * (qz * x - qx * z); val tz = 2f * (qx * y - qy * x)
            return floatArrayOf(x + qw * tx + (qy * tz - qz * ty), y + qw * ty + (qz * tx - qx * tz), z + qw * tz + (qx * ty - qy * tx))
        }

        val parts = ArrayList<ModelPart>()
        var height = 0.01f; var radius = 0.01f
        val materials = list(json, "materials"); val texs = list(json, "textures"); val images = list(json, "images")
        for (mesh in list(json, "meshes")) for (prim in list(map(mesh), "primitives")) {
            val p = map(prim); val attr = map(p["attributes"])
            val pos = floats(num(attr, "POSITION").toInt(), 3)
            val nor = floats(num(attr, "NORMAL").toInt(), 3)
            val uv = if (attr["TEXCOORD_0"] != null) floats(num(attr, "TEXCOORD_0").toInt(), 2) else FloatArray(pos.size / 3 * 2)
            val idx = indices(num(p, "indices").toInt())
            val n = pos.size / 3
            val vb = ByteBuffer.allocateDirect(n * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            for (i in 0 until n) {
                val r = rot(pos[i * 3] * s[0], pos[i * 3 + 1] * s[1], pos[i * 3 + 2] * s[2])
                val rn = rot(nor[i * 3], nor[i * 3 + 1], nor[i * 3 + 2])
                val x = r[0] + t[0]; val y = r[1] + t[1]; val z = r[2] + t[2]
                height = max(height, y); radius = max(radius, kotlin.math.hypot(x, z))
                vb.put(x).put(y).put(z).put(rn[0]).put(rn[1]).put(rn[2]).put(uv[i * 2]).put(uv[i * 2 + 1])
            }
            vb.position(0)
            val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(idx); ib.position(0)

            val mat = map(materials[num(p, "material").toInt()])
            var leaf = false
            val tex = (map(mat["pbrMetallicRoughness"])["baseColorTexture"] as? Map<*, *>)?.let { bt ->
                val src = num(map(texs[num(bt, "index").toInt()]), "source").toInt()
                val uri = map(images[src])["uri"] as String
                leaf = uri.contains("Leaves")
                texture(uri)
            } ?: 0

            val ids = IntArray(3)
            GLES30.glGenVertexArrays(1, ids, 0); GLES30.glGenBuffers(2, ids, 1)
            GLES30.glBindVertexArray(ids[0])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, n * 32, vb, GLES30.GL_STATIC_DRAW)
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[2])
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
            GLES30.glEnableVertexAttribArray(1); GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 32, 12)
            GLES30.glEnableVertexAttribArray(2); GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 32, 24)
            GLES30.glBindVertexArray(0)
            parts += ModelPart(ids[0], ids[1], ids[2], idx.size, tex, leaf)
        }
        return Model(parts, height, radius)
    }
}

/** Draws [Model]s with their pictures, leaf cut-outs, a little wind sway, and the same light and fog as everything else. */
class ModelShader {
    private val prog = linkProgram(VERT, FRAG)
    private val uMvp = GLES30.glGetUniformLocation(prog, "uMVP")
    private val uModel = GLES30.glGetUniformLocation(prog, "uModel")
    private val uTex = GLES30.glGetUniformLocation(prog, "uTex")
    private val uLight = GLES30.glGetUniformLocation(prog, "uLightDir")
    private val uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
    private val uTint = GLES30.glGetUniformLocation(prog, "uTint")
    private val uTime = GLES30.glGetUniformLocation(prog, "uTime")
    private val uSway = GLES30.glGetUniformLocation(prog, "uSway")
    private val uLean = GLES30.glGetUniformLocation(prog, "uLean")
    private val uFog = GLES30.glGetUniformLocation(prog, "uFogColor")
    private val uLeaf = GLES30.glGetUniformLocation(prog, "uLeaf")
    private val uLeafDark = GLES30.glGetUniformLocation(prog, "uLeafDark")
    private val uLeafLight = GLES30.glGetUniformLocation(prog, "uLeafLight")

    fun begin(sun: FloatArray, camX: Float, camY: Float, camZ: Float, time: Float) {
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, sun[0], sun[1], sun[2])
        GLES30.glUniform3f(uCam, camX, camY, camZ)
        GLES30.glUniform3f(uFog, HORIZON_R, HORIZON_G, HORIZON_B)
        GLES30.glUniform1f(uTime, time)
        GLES30.glUniform1i(uTex, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /** Foliage colour families: 0 fresh green, 1 yellow-green, 2 pink blossom. */
    private val leafDark = arrayOf(floatArrayOf(0.10f, 0.36f, 0.09f), floatArrayOf(0.20f, 0.42f, 0.08f), floatArrayOf(0.66f, 0.32f, 0.46f))
    private val leafLight = arrayOf(floatArrayOf(0.52f, 0.80f, 0.22f), floatArrayOf(0.70f, 0.86f, 0.28f), floatArrayOf(1.00f, 0.78f, 0.85f))

    fun draw(model: Model, mvp: FloatArray, world: FloatArray, tr: Float, tg: Float, tb: Float, sway: Float, leafKind: Int = 0) {
        GLES30.glUniform3fv(uLeafDark, 1, leafDark[leafKind], 0)
        GLES30.glUniform3fv(uLeafLight, 1, leafLight[leafKind], 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, world, 0)
        GLES30.glUniform3f(uTint, tr, tg, tb)
        GLES30.glUniform1f(uSway, 1f / model.height)
        GLES30.glUniform1f(uLean, sway)
        for (p in model.parts) {
            GLES30.glUniform1f(uLeaf, if (p.leaf) 1f else 0f)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, p.texture)
            GLES30.glBindVertexArray(p.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, p.count, GLES30.GL_UNSIGNED_SHORT, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    companion object {
        private const val VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uMVP;
uniform mat4 uModel;
uniform float uTime;
uniform float uSway;
uniform float uLean;
out vec3 vNormal;
out vec3 vWorld;
out vec2 vUv;
out float vH;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    // Wind: the higher up a vertex is, the further it leans. Phase depends on where the model stands.
    float h = max(aPos.y, 0.0) * uSway;
    vec3 p = aPos + vec3(sin(uTime * 1.4 + uModel[3].x * 0.31 + aPos.y * 0.6), 0.0, cos(uTime * 1.1 + uModel[3].z * 0.27)) * h * h * uLean;
    vWorld = w.xyz;
    vNormal = normalize(mat3(uModel) * aNormal);
    vUv = aUv;
    vH = aPos.y * uSway;
    gl_Position = uMVP * vec4(p, 1.0);
}
"""

        private const val FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
in vec2 vUv;
in float vH;
uniform sampler2D uTex;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform vec3 uTint;
uniform vec3 uFogColor;
uniform float uLeaf;
uniform vec3 uLeafDark;
uniform vec3 uLeafLight;
out vec4 fragColor;
void main() {
    vec4 t = texture(uTex, vUv);
    if (t.a < 0.45) discard;
    if (uLeaf > 0.5) {
        // Foliage is re-coloured to fresh, saturated greens whatever the picture says (maples ship in autumn colours).
        float lum = dot(t.rgb, vec3(0.30, 0.59, 0.11));
        float k = clamp(lum * 1.7, 0.0, 1.0);
        t.rgb = mix(uLeafDark, uLeafLight, k * k);
        // Round, sunlit tops: lighter and warmer toward the crown, deeper and cooler underneath.
        float top = smoothstep(0.35, 1.0, vH);
        t.rgb *= mix(vec3(0.74, 0.86, 0.80), vec3(1.14, 1.10, 0.88), top);
        t.rgb = mix(t.rgb, uLeafLight * 1.15, top * 0.25);
    }
    vec3 n = normalize(vNormal);
    if (!gl_FrontFacing) n = -n;
    vec3 l = normalize(uLightDir);
    // Wrapped light, so leaves facing away from the sun are still bright, as light passes through them.
    float diff = clamp(dot(n, l) * 0.6 + 0.4, 0.0, 1.0);
    diff = mix(diff, smoothstep(0.25, 0.80, diff), 0.55);      // toon-ish: clumps of light and shade
    vec3 sky = vec3(0.70, 0.80, 0.98);
    vec3 gnd = vec3(0.34, 0.38, 0.26);
    vec3 amb = mix(gnd, sky, n.y * 0.5 + 0.5) * 0.70;
    vec3 base = t.rgb * uTint;
    vec3 lit = base * (amb + vec3(1.0, 0.96, 0.88) * diff * 0.70);
    float lm = dot(lit, vec3(0.3, 0.59, 0.11));
    lit = mix(vec3(lm), lit, 1.22);                               // richer colour
    float d = length(vWorld - uCamPos);
    float fogAmt = smoothstep($FOG_START, $FOG_END, d);
    fragColor = vec4(mix(lit, uFogColor, fogAmt), 1.0);
}
"""
    }
}
