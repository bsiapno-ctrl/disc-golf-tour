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

$GRADE_GLSL
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
    // Sun: a bright disc with a thin soft edge, a tight halo and a wide warm bloom.
    float sunDisc = smoothstep(0.99955, 0.99975, s);
    col += vec3(1.0, 0.95, 0.80) * sunDisc * 1.6;
    col += vec3(1.0, 0.88, 0.62) * (pow(s, 220.0) * 0.45 + pow(s, 40.0) * 0.30 + pow(s, 7.0) * 0.18 + pow(s, 2.0) * 0.06);
    col += vec3(1.0, 0.95, 0.85) * 0.16 * (1.0 - smoothstep(0.0, 0.20, up));   // glowing horizon

    // Clouds, two stylised layers of fluffy cumulus: a high thin one and a nearer, puffier one. Each is a noise
    // density with a sharpened edge, lit from the sun side with a soft blue-grey underside and bright rim.
    if (ray.y > 0.015) {
        vec2 sd = normalize(sun.xz + vec2(0.001));
        for (int L = 0; L < 2; L++) {
            float fl = float(L);
            float hgt = 0.10 + 0.22 * fl;                    // layer 0 low and puffy, layer 1 high and wispy
            vec2 cp = ray.xz / (ray.y + hgt) * (0.85 + 0.5 * fl) + vec2(uTime * (0.010 + 0.006 * fl) + 3.0 * fl, 7.0 * fl);
            float sc = 0.80 + 1.0 * fl;
            float d = fbm(cp * sc);
            float d2 = fbm((cp + sd * 0.045) * sc);          // density a little toward the sun: less than d means the sun side is thin = lit edge
            float cover = 0.47 + 0.08 * fl;
            float cl = smoothstep(cover, cover + 0.13, d) * smoothstep(0.015, 0.20 + 0.10 * fl, ray.y);
            float lit = clamp(0.70 + (d - d2) * 6.0 + (d - cover) * 1.5, 0.0, 1.0);
            vec3 shade = vec3(0.72, 0.81, 0.96);
            vec3 cc = mix(shade, vec3(1.0, 1.0, 0.99), smoothstep(0.30, 0.80, lit));
            cc += vec3(1.0, 0.92, 0.75) * 0.12 * pow(s, 6.0) * (1.0 - smoothstep(0.0, 0.5, lit));   // silver lining near the sun
            col = mix(col, cc, cl * (L == 0 ? 0.97 : 0.60));
        }
    }
    // Distant hills and a tree line, three layers fading into the haze. Only drawn just above the horizon.
    if (ray.y < 0.30) {
        float az = atan(ray.x, ray.z);
        for (int k = 0; k < 3; k++) {
            float fk = float(k);
            float base = 0.17 - 0.034 * fk;
            float amp = 0.17 - 0.045 * fk;
            float freq = 2.0 + 2.3 * fk;
            float h = base + amp * (fbm(vec2(az * freq + fk * 9.0, fk * 3.1)) - 0.25);
            float edge = smoothstep(h + 0.0015, h - 0.0015, ray.y);
            vec3 far = vec3(0.60, 0.74, 0.94);
            vec3 mid = vec3(0.42, 0.68, 0.66);
            vec3 near = vec3(0.30, 0.60, 0.40);
            vec3 rc = k == 0 ? far : (k == 1 ? mid : near);
            float up01 = smoothstep(0.0, h, ray.y);
            float shade = 0.80 + 0.30 * up01;   // lighter toward the ridge top
            // Forest texture: speckled canopy that darkens in clumps, sunlit on the ridge, fading with distance (layer 0 is mostly mist).
            float forest = vnoise(vec2(az * (55.0 + 25.0 * fk), ray.y * (85.0 + 35.0 * fk)));
            float clump = fbm(vec2(az * 14.0 + fk * 5.0, ray.y * 22.0));
            shade *= 1.0 + (forest - 0.5) * (0.10 + 0.10 * fk) + (clump - 0.5) * 0.16;
            rc = mix(rc, horizon, 0.46 - 0.16 * fk - 0.10 * up01);   // mist, thicker far away and low down
            float lowMist = 1.0 - smoothstep(0.0, 0.06 + 0.02 * fk, ray.y);
            rc = mix(rc * shade, horizon, lowMist * (0.55 - 0.12 * fk));
            col = mix(col, rc, edge * (ray.y > -0.02 ? 1.0 : 0.0));
        }
    }
    // A few birds: little animated V-shapes (wings flapping) drifting across the sky.
    if (ray.y > 0.07 && ray.y < 0.45) {
        float baz = atan(ray.x, ray.z);
        float bel = asin(clamp(ray.y, 0.0, 1.0));
        for (int b = 0; b < 6; b++) {
            float fb = float(b);
            float a0 = fb * 1.047 + 0.4 * sin(fb * 2.3);
            float ba = a0 + uTime * (0.012 + 0.003 * fb);
            float be = 0.20 + 0.11 * fract(fb * 0.37 + 0.13) + 0.012 * sin(uTime * 0.7 + fb);
            vec2 q = vec2(mod(baz - ba + 3.14159, 6.28318) - 3.14159, bel - be) * 20.0;
            float flap = sin(uTime * (7.0 + fb) + fb * 1.7);
            float wing = abs(q.x);
            float yy = -wing * (0.30 + 0.35 * flap) + 0.10 * wing * wing;
            float bird = (1.0 - smoothstep(0.020, 0.050, abs(q.y - yy))) * (1.0 - smoothstep(0.70, 0.95, wing));
            col = mix(col, vec3(0.16, 0.20, 0.30), bird * 0.85);
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
    fragColor = vec4(grade(col), 1.0);
}
"""
    }
}
