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
    vec3 zenith = vec3(0.12, 0.40, 0.92);
    vec3 midSky = vec3(0.36, 0.68, 1.0);
    vec3 col = mix(horizon, midSky, smoothstep(0.0, 0.28, up));
    col = mix(col, zenith, smoothstep(0.25, 0.95, up));

    vec3 sun = normalize(uSunDir);
    float s = max(dot(ray, sun), 0.0);
    col += vec3(1.0, 0.92, 0.75) * (pow(s, 600.0) * 1.2 + pow(s, 24.0) * 0.22 + pow(s, 5.0) * 0.14);   // sun and soft bloom
    col += vec3(1.0, 0.95, 0.85) * 0.16 * (1.0 - smoothstep(0.0, 0.20, up));   // glowing horizon

    // Clouds: a flat layer high above, projected from the view ray.
    if (ray.y > 0.02) {
        vec2 cp = ray.xz / (ray.y + 0.12) * 0.9 + vec2(uTime * 0.012, 0.0);
        float c = fbm(cp * 1.4);
        float cloud = smoothstep(0.38, 0.62, c) * smoothstep(0.02, 0.28, ray.y);
        vec3 cloudCol = mix(vec3(0.70, 0.80, 0.96), vec3(1.0, 0.99, 0.96), smoothstep(0.50, 0.80, c));
        col = mix(col, cloudCol, cloud * 0.92);
    }
    // Distant hills and a tree line, three layers fading into the haze. Only drawn just above the horizon.
    if (ray.y < 0.30) {
        float az = atan(ray.x, ray.z);
        for (int k = 0; k < 2; k++) {
            float fk = float(k);
            float base = 0.15 - 0.040 * fk;
            float amp = 0.17 - 0.06 * fk;
            float freq = 2.2 + 2.6 * fk;
            float h = base + amp * (fbm(vec2(az * freq + fk * 9.0, fk * 3.1)) - 0.25);
            float edge = smoothstep(h + 0.0015, h - 0.0015, ray.y);
            vec3 far = vec3(0.58, 0.74, 0.92);
            vec3 mid = vec3(0.34, 0.64, 0.56);
            vec3 rc = k == 0 ? far : mid;
            float shade = 0.80 + 0.30 * smoothstep(0.0, h, ray.y);   // lighter toward the ridge top
            rc = mix(rc, horizon, 0.38 - 0.16 * fk * 1.0 - 0.1 * smoothstep(0.0, h, ray.y));   // haze, thicker far away and low down
            col = mix(col, rc * shade, edge * (ray.y > -0.02 ? 1.0 : 0.0));
        }
    }
    // Two tree lines in front of the hills: rows of round treetops (scallops of varied size), the far row hazier and paler.
    if (ray.y < 0.16) {
        float az = atan(ray.x, ray.z);
        for (int t = 0; t < 2; t++) {
            float ft = float(t);
            float cells = az * (95.0 - 35.0 * ft);
            float id = floor(cells);
            float f = fract(cells) * 2.0 - 1.0;
            float big = 0.5 + 0.5 * hash(vec2(id, 3.0 + ft));
            // Second, offset row of smaller crowns fills the gaps between the big ones.
            float cells2 = az * (140.0 - 50.0 * ft) + 0.5;
            float id2 = floor(cells2);
            float f2 = fract(cells2) * 2.0 - 1.0;
            float big2 = 0.4 + 0.4 * hash(vec2(id2, 7.0 + ft));
            float bump = max((0.006 + 0.012 * big) * sqrt(max(1.0 - f * f, 0.0)), (0.004 + 0.008 * big2) * sqrt(max(1.0 - f2 * f2, 0.0)));
            float h = 0.016 - 0.005 * ft + 0.010 * (fbm(vec2(az * 5.0 + ft * 4.0, 2.0 + ft)) - 0.3) + bump;
            float edge = smoothstep(h + 0.0012, h - 0.0012, ray.y);
            vec3 tc = t == 0 ? vec3(0.24, 0.52, 0.34) : vec3(0.16, 0.46, 0.20);
            // Sunlit crowns on top, shaded below, a little variation tree to tree.
            float top = smoothstep(0.0, h, ray.y);
            tc *= 0.74 + 0.42 * top + 0.12 * (hash(vec2(id, 9.0)) - 0.5);
            tc = mix(tc, horizon, 0.34 - 0.20 * ft);
            col = mix(col, tc, edge * (ray.y > -0.02 ? 1.0 : 0.0));
        }
    }
    fragColor = vec4(col, 1.0);
}
"""
    }
}
