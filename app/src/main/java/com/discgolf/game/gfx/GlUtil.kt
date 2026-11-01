package com.discgolf.game.gfx

import android.opengl.GLES30

/** Compile and link a GLSL ES 3.00 program. Throws with the driver's message if it fails. */
fun linkProgram(vs: String, fs: String): Int {
    fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Shader compile failed: " + GLES30.glGetShaderInfoLog(s))
        return s
    }
    val v = compile(GLES30.GL_VERTEX_SHADER, vs)
    val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
    val p = GLES30.glCreateProgram()
    GLES30.glAttachShader(p, v)
    GLES30.glAttachShader(p, f)
    GLES30.glLinkProgram(p)
    val ok = IntArray(1)
    GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
    if (ok[0] == 0) throw RuntimeException("Shader link failed: " + GLES30.glGetProgramInfoLog(p))
    return p
}

/** Fog starts and ends at these distances, in every shader, so the ground, trees and sky agree. */
const val FOG_START = 45f
const val FOG_END = 340f
/** Colour of the sky at the horizon, which is also the fog colour. */
const val HORIZON_R = 0.80f
const val HORIZON_G = 0.89f
const val HORIZON_B = 0.95f

/**
 * Final colour grade shared by every fragment shader (no post-processing pass): a touch more saturation, a gentle S-curve
 * for contrast, and warm sunlit highlights. Spliced into the shaders as `$GRADE_GLSL`.
 */
const val GRADE_GLSL = """vec3 grade(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    float l = dot(c, vec3(0.30, 0.59, 0.11));
    c = mix(vec3(l), c, 1.10);
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.24);
    c *= mix(vec3(0.99, 1.0, 1.02), vec3(1.05, 1.015, 0.93), smoothstep(0.35, 0.95, l));
    return c;
}
"""
