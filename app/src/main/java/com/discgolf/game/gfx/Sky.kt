package com.discgolf.game.gfx

import android.opengl.GLES30

/** A full-screen gradient sky with a sun glow and soft drifting clouds, drawn first with no depth. */
class Sky {
    private val prog = linkProgram(VERT, FRAG)
    private val uInvVp = GLES30.glGetUniformLocation(prog, "uInvVP")
    private val uCam = GLES30.glGetUniformLocation(prog, "uCamPos")
    private val uSun = GLES30.glGetUniformLocation(prog, "uSunDir")
    private val uTime = GLES30.glGetUniformLocation(prog, "uTime")

    fun draw(invVp: FloatArray, camX: Float, camY: Float, camZ: Float, sun: FloatArray, time: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(uInvVp, 1, false, invVp, 0)
        GLES30.glUniform3f(uCam, camX, camY, camZ)
        GLES30.glUniform3f(uSun, sun[0], sun[1], sun[2])
        GLES30.glUniform1f(uTime, time)
        GLES30.glBindVertexArray(0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    companion object {
        private const val VERT = """#version 300 es
uniform mat4 uInvVP;
out vec3 vWorldFar;
void main() {
    vec2 p = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);
    vec4 w = uInvVP * vec4(p, 1.0, 1.0);
    vWorldFar = w.xyz / w.w;
    gl_Position = vec4(p, 0.999, 1.0);
}
"""

        private const val FRAG = """#version 300 es
precision highp float;
in vec3 vWorldFar;
uniform vec3 uCamPos;
uniform vec3 uSunDir;
uniform float uTime;
out vec4 fragColor;

float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { v += a * vnoise(p); p = p * 2.02 + 11.7; a *= 0.5; }
    return v;
}

void main() {
    vec3 ray = normalize(vWorldFar - uCamPos);
    float up = clamp(ray.y, 0.0, 1.0);
    vec3 horizon = vec3($HORIZON_R, $HORIZON_G, $HORIZON_B);
    vec3 zenith = vec3(0.22, 0.46, 0.84);
    vec3 col = mix(horizon, zenith, pow(up, 0.55));

    vec3 sun = normalize(uSunDir);
    float s = max(dot(ray, sun), 0.0);
    col += vec3(1.0, 0.92, 0.75) * (pow(s, 600.0) * 1.2 + pow(s, 24.0) * 0.22);

    // Clouds: a flat layer high above, projected from the view ray.
    if (ray.y > 0.02) {
        vec2 cp = ray.xz / (ray.y + 0.12) * 0.9 + vec2(uTime * 0.012, 0.0);
        float c = fbm(cp * 1.4);
        float cloud = smoothstep(0.50, 0.82, c) * smoothstep(0.02, 0.28, ray.y);
        vec3 cloudCol = mix(vec3(0.78, 0.82, 0.90), vec3(1.0), smoothstep(0.45, 0.85, c));
        col = mix(col, cloudCol, cloud * 0.85);
    }
    fragColor = vec4(col, 1.0);
}
"""
    }
}
