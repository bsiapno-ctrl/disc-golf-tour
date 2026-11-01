package com.discgolf.game.gfx

import android.opengl.GLES30
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pond water: a disc mesh of rings (so the shader knows how far each pixel is from the shore) drawn with a bright
 * turquoise gradient, drifting ripples, sun sparkle and a foamy, wobbling shore line. Needs blending on.
 */
class WaterShader {
    private val prog = linkProgram(VERT, FRAG)
    private val uMvp = GLES30.glGetUniformLocation(prog, "uMVP")
    private val uModel = GLES30.glGetUniformLocation(prog, "uModel")
    private val uLight = GLES30.glGetUniformLocation(prog, "uLightDir")
    private val uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
    private val uTime = GLES30.glGetUniformLocation(prog, "uTime")
    private val uSeed = GLES30.glGetUniformLocation(prog, "uSeed")
    private val mesh: Mesh

    init {
        val seg = 56; val rings = 5
        val v = ArrayList<Float>()
        v += 0f; v += 0f; v += 0f; v += 0f; v += 1f; v += 0f
        for (r in 1..rings) for (k in 0 until seg) {
            val a = k.toFloat() / seg * 2f * PI.toFloat(); val rr = r.toFloat() / rings
            v += cos(a) * rr; v += 0f; v += sin(a) * rr; v += 0f; v += 1f; v += 0f
        }
        val idx = ArrayList<Short>()
        for (k in 0 until seg) { idx += 0.toShort(); idx += (1 + (k + 1) % seg).toShort(); idx += (1 + k).toShort() }
        for (r in 1 until rings) for (k in 0 until seg) {
            val a = 1 + (r - 1) * seg + k; val b = 1 + (r - 1) * seg + (k + 1) % seg
            val c = a + seg; val d = b + seg
            idx += a.toShort(); idx += b.toShort(); idx += c.toShort()
            idx += b.toShort(); idx += d.toShort(); idx += c.toShort()
        }
        mesh = Mesh(v.toFloatArray(), idx.toShortArray())
    }

    fun begin(sun: FloatArray, cx: Float, cy: Float, cz: Float, time: Float) {
        GLES30.glUseProgram(prog)
        GLES30.glUniform3f(uLight, sun[0], sun[1], sun[2])
        GLES30.glUniform3f(uCam, cx, cy, cz)
        GLES30.glUniform1f(uTime, time)
    }

    /** Draw one pond whose centre is ([x], [y], [z]) and radii [rx] and [rz]. [mvp] and [model] already include the scale. */
    fun draw(mvp: FloatArray, model: FloatArray, seed: Float) {
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES30.glUniform1f(uSeed, seed)
        mesh.draw()
    }

    companion object {
        private const val VERT = """#version 300 es
layout(location = 0) in vec3 aPos;
uniform mat4 uMVP;
uniform mat4 uModel;
out vec3 vLocal;
out vec3 vWorld;
void main() {
    vLocal = aPos;
    vWorld = (uModel * vec4(aPos, 1.0)).xyz;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

        private const val FRAG = """#version 300 es
precision highp float;
in vec3 vLocal;
in vec3 vWorld;
uniform vec3 uLightDir;
uniform vec3 uCamPos;
uniform float uTime;
uniform float uSeed;
out vec4 fragColor;
float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
$GRADE_GLSL
void main() {
    vec2 w = vWorld.xz;
    float r = length(vLocal.xz);
    float ang = atan(vLocal.z, vLocal.x);
    float dist = length(vWorld - uCamPos);
    // Ripples: two crossing wave trains give a wobbly surface normal.
    float t = uTime;
    vec2 g = vec2(cos(w.x * 1.7 + w.y * 0.9 + t * 1.3) * 0.9 + cos(w.x * 3.9 - w.y * 2.3 + t * 1.9) * 0.5,
                  cos(w.y * 1.5 - w.x * 0.7 + t * 1.1) * 0.9 + cos(w.y * 4.3 + w.x * 1.7 - t * 1.7) * 0.5) * 0.10;
    vec3 n = normalize(vec3(-g.x, 1.0, -g.y));
    vec3 v = normalize(uCamPos - vWorld);
    vec3 l = normalize(uLightDir);
    // Shallow to deep turquoise from shore to middle, with drifting light and dark patches.
    float depth = smoothstep(0.95, 0.25, r);
    float blotch = vnoise(w * 0.35 + t * 0.05 + uSeed) * 0.6 + vnoise(w * 0.9 - t * 0.08) * 0.4;
    vec3 shallow = vec3(0.34, 0.92, 0.82);
    vec3 deep = vec3(0.06, 0.56, 0.80);
    vec3 col = mix(shallow, deep, depth);
    col *= 0.90 + 0.22 * blotch;
    // Bright caustic lines.
    float caus = pow(0.5 + 0.5 * sin((w.x + 1.3 * vnoise(w * 0.8 + t * 0.2)) * 2.6) * sin((w.y + 1.3 * vnoise(w * 0.8 - t * 0.2 + 7.0)) * 2.6), 6.0);
    col += vec3(0.5, 0.9, 0.8) * caus * 0.12 * (1.0 - depth * 0.4);
    // Sky reflection at grazing angles.
    float fres = pow(1.0 - max(dot(n, v), 0.0), 3.0);
    col = mix(col, vec3($HORIZON_R, $HORIZON_G, $HORIZON_B) * 1.02, clamp(fres * 0.85, 0.0, 0.7));
    // Sun glitter: a tight highlight plus tiny twinkling cells that fade out with distance.
    vec3 h = normalize(l + v);
    float spec = pow(max(dot(n, h), 0.0), 120.0);
    vec2 cell = floor(w * 7.0);
    float tw = hash(cell + uSeed);
    float twinkle = step(0.93, tw) * (1.0 - smoothstep(0.10, 0.28, length(fract(w * 7.0) - 0.5))) * pow(0.5 + 0.5 * sin(t * (2.0 + 4.0 * tw) + tw * 60.0), 3.0);
    float glint = (spec * 1.4 + twinkle * pow(max(dot(n, h), 0.0), 8.0) * 1.2) * (1.0 - smoothstep(40.0, 150.0, dist));
    col += vec3(1.0, 0.98, 0.9) * glint;
    // Foam along the shore: a wobbling band plus a thin bright line, and a soft fade outside the edge.
    float wob = 0.018 * sin(ang * 13.0 + t * 1.4 + uSeed) + 0.014 * sin(ang * 29.0 - t * 2.1) + 0.03 * (vnoise(w * 1.4 + t * 0.15) - 0.5);
    float rr = r + wob;
    float foam = smoothstep(0.885, 0.935, rr) * (1.0 - smoothstep(0.955, 0.99, rr));
    foam *= 0.65 + 0.35 * vnoise(w * 3.0 + t * 0.3);
    float line = smoothstep(0.012, 0.0, abs(rr - 0.83 - 0.012 * sin(t * 1.2 + ang * 5.0))) * 0.6;
    col = mix(col, vec3(0.97, 1.0, 1.0), clamp(foam + line * 0.7, 0.0, 0.95));
    float alpha = (0.90 - 0.12 * depth) * (1.0 - smoothstep(0.955, 1.0, r)) + foam * 0.1;
    float fog = smoothstep($FOG_START, $FOG_END, dist);
    fragColor = vec4(grade(mix(col, vec3($HORIZON_R, $HORIZON_G, $HORIZON_B), fog)), alpha);
}
"""
    }
}
