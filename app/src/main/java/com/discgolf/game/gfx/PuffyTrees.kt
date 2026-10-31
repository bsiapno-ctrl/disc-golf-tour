package com.discgolf.game.gfx

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One uploaded tree mesh (see [PuffyTreeGen]). */
class TreeMesh(val vao: Int, val vbo: Int, val ebo: Int, val count: Int)

/**
 * Draws the round, puffy trees. Six shapes in three levels of detail are built once and shared by every tree; the
 * colour family (fresh green, yellow-green, blossom) and bark (brown or birch-white) are uniforms.
 */
class PuffyTrees {
    private val prog = linkProgram(VERT, FRAG)
    private val uMvp = GLES30.glGetUniformLocation(prog, "uMVP")
    private val uModel = GLES30.glGetUniformLocation(prog, "uModel")
    private val uLight = GLES30.glGetUniformLocation(prog, "uLightDir")
    private val uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
    private val uFog = GLES30.glGetUniformLocation(prog, "uFogColor")
    private val uTime = GLES30.glGetUniformLocation(prog, "uTime")
    private val uLean = GLES30.glGetUniformLocation(prog, "uLean")
    private val uTint = GLES30.glGetUniformLocation(prog, "uTint")
    private val uLeafDark = GLES30.glGetUniformLocation(prog, "uLeafDark")
    private val uLeafLight = GLES30.glGetUniformLocation(prog, "uLeafLight")
    private val uBarkDark = GLES30.glGetUniformLocation(prog, "uBarkDark")
    private val uBarkLight = GLES30.glGetUniformLocation(prog, "uBarkLight")
    private val meshes = arrayOfNulls<TreeMesh>(PuffyTreeGen.SEEDS * 3)

    /** Foliage families: 0 fresh green, 1 yellow-green, 2 pink blossom. */
    private val leafDark = arrayOf(floatArrayOf(0.12f, 0.44f, 0.15f), floatArrayOf(0.18f, 0.40f, 0.10f), floatArrayOf(0.62f, 0.30f, 0.44f))
    private val leafLight = arrayOf(floatArrayOf(0.66f, 0.93f, 0.28f), floatArrayOf(0.76f, 0.90f, 0.30f), floatArrayOf(1.00f, 0.80f, 0.88f))
    private val barkDark = arrayOf(floatArrayOf(0.30f, 0.20f, 0.13f), floatArrayOf(0.52f, 0.52f, 0.50f))
    private val barkLight = arrayOf(floatArrayOf(0.70f, 0.52f, 0.34f), floatArrayOf(0.97f, 0.96f, 0.92f))

    fun begin(sun: FloatArray, camX: Float, camY: Float, camZ: Float, time: Float) {
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, sun[0], sun[1], sun[2])
        GLES30.glUniform3f(uCam, camX, camY, camZ)
        GLES30.glUniform3f(uFog, HORIZON_R, HORIZON_G, HORIZON_B)
        GLES30.glUniform1f(uTime, time)
    }

    private fun mesh(seed: Int, detail: Int): TreeMesh = meshes[seed * 3 + detail] ?: run {
        val d = PuffyTreeGen.build(seed, detail)
        val vb = ByteBuffer.allocateDirect(d.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(d.vertices); vb.position(0)
        val ib = ByteBuffer.allocateDirect(d.indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(d.indices); ib.position(0)
        val ids = IntArray(3)
        GLES30.glGenVertexArrays(1, ids, 0); GLES30.glGenBuffers(2, ids, 1)
        GLES30.glBindVertexArray(ids[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, d.vertices.size * 4, vb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[2])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, d.indices.size * 2, ib, GLES30.GL_STATIC_DRAW)
        for (a in 0..2) { GLES30.glEnableVertexAttribArray(a); GLES30.glVertexAttribPointer(a, 3, GLES30.GL_FLOAT, false, 36, a * 12) }
        GLES30.glBindVertexArray(0)
        TreeMesh(ids[0], ids[1], ids[2], d.indices.size).also { meshes[seed * 3 + detail] = it }
    }

    /** Level of detail for a tree [dist] metres from the camera. */
    fun detailFor(dist: Float) = if (dist < 42f) 2 else if (dist < 120f) 1 else 0

    fun draw(seed: Int, detail: Int, mvp: FloatArray, world: FloatArray, tint: Float, lean: Float, leafKind: Int, birch: Boolean) {
        val m = mesh(seed % PuffyTreeGen.SEEDS, detail)
        GLES30.glUniform3fv(uLeafDark, 1, leafDark[leafKind], 0)
        GLES30.glUniform3fv(uLeafLight, 1, leafLight[leafKind], 0)
        GLES30.glUniform3fv(uBarkDark, 1, barkDark[if (birch) 1 else 0], 0)
        GLES30.glUniform3fv(uBarkLight, 1, barkLight[if (birch) 1 else 0], 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, world, 0)
        GLES30.glUniform3f(uTint, tint, tint, tint)
        GLES30.glUniform1f(uLean, lean)
        GLES30.glBindVertexArray(m.vao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.count, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        for (m in meshes) if (m != null) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(m.vao), 0)
            GLES30.glDeleteBuffers(2, intArrayOf(m.vbo, m.ebo), 0)
        }
        meshes.fill(null)
    }

    companion object {
        private const val VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec3 aCol;
uniform mat4 uMVP;
uniform mat4 uModel;
uniform float uTime;
uniform float uLean;
out vec3 vNormal;
out vec3 vWorld;
out vec3 vCol;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    // Wind: the crown sways, the foot does not.
    float h = max(aPos.y, 0.0);
    vec3 p = aPos + vec3(sin(uTime * 1.4 + uModel[3].x * 0.31 + aPos.y * 0.6), 0.0, cos(uTime * 1.1 + uModel[3].z * 0.27)) * h * h * uLean;
    vWorld = w.xyz;
    vNormal = normalize(mat3(uModel) * aNormal);
    vCol = aCol;
    gl_Position = uMVP * vec4(p, 1.0);
}
"""

        private const val FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorld;
in vec3 vCol;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform vec3 uTint;
uniform vec3 uFogColor;
uniform vec3 uLeafDark;
uniform vec3 uLeafLight;
uniform vec3 uBarkDark;
uniform vec3 uBarkLight;
out vec4 fragColor;
float hash(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
void main() {
    vec3 n = normalize(vNormal);
    vec3 l = normalize(uLightDir);
    float g = vCol.x;
    float isBark = vCol.y;
    // Leaf-clump mottling: soft patches of light and shade so the crown reads as foliage and not plastic.
    vec3 q = vWorld * 1.7;
    float sp = (sin(q.x * 2.3 + sin(q.z * 1.7)) * sin(q.y * 2.9 + sin(q.x * 1.3)) * sin(q.z * 2.1 + q.y * 0.9)) * 0.5 + 0.5;
    float k = clamp(g + (sp - 0.5) * 0.34 * (1.0 - isBark), 0.0, 1.0);
    k = smoothstep(0.05, 0.95, k);
    vec3 leaf = mix(uLeafDark, uLeafLight, k);
    vec3 bark = mix(uBarkDark, uBarkLight, k);
    // Birch: dark dashes round the pale trunk.
    float birchy = step(0.9, uBarkLight.g);
    float dash = step(0.80, hash(vec3(floor(vWorld.y * 3.2), floor(vWorld.x * 6.0 + vWorld.z * 5.0), 1.0)));
    bark *= 1.0 - 0.62 * dash * birchy;
    vec3 base = mix(leaf, bark, isBark) * uTint;
    float diff = clamp(dot(n, l) * 0.55 + 0.45, 0.0, 1.0);
    diff = mix(diff, smoothstep(0.25, 0.85, diff), 0.55);
    vec3 sky = vec3(0.70, 0.80, 0.98);
    vec3 gnd = vec3(0.34, 0.38, 0.26);
    vec3 amb = mix(gnd, sky, n.y * 0.5 + 0.5) * 0.74;
    vec3 lit = base * (amb + vec3(1.0, 0.96, 0.88) * diff * 0.80);
    // Warm rim of sun on the lit upper edge of each puff.
    vec3 v = normalize(uCamPos - vWorld);
    float rim = pow(1.0 - max(dot(n, v), 0.0), 3.0) * max(dot(n, l), 0.0);
    lit += uLeafLight * rim * 0.22 * (1.0 - isBark);
    float lm = dot(lit, vec3(0.3, 0.59, 0.11));
    lit = mix(vec3(lm), lit, 1.20);
    float d = length(vWorld - uCamPos);
    float fogAmt = smoothstep($FOG_START, $FOG_END, d);
    fragColor = vec4(mix(lit, uFogColor, fogAmt), 1.0);
}
"""
    }
}
