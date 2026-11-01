package com.discgolf.game.gfx

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Picks an RGBA8888 + 24-bit depth surface with multisampling: tries 4x, then 2x, then none, so edges (above all the
 * character outline) stay smooth on devices that allow it and the game still starts on those that do not.
 */
class MsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    /** Samples actually granted (0 = no multisampling), for logging. */
    var samples = 0; private set

    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        for (renderable in intArrayOf(RENDERABLE_ES3, RENDERABLE_ES2)) {
            for (want in intArrayOf(4, 2, 0)) {
                val cfg = pick(egl, display, renderable, want)
                if (cfg != null) { samples = want; android.util.Log.i("Msaa", "EGL config with $want samples"); return cfg }
            }
        }
        throw IllegalArgumentException("no EGL config")
    }

    private fun pick(egl: EGL10, display: EGLDisplay, renderable: Int, samples: Int): EGLConfig? {
        val spec = ArrayList<Int>()
        spec += listOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_RENDERABLE_TYPE, renderable)
        if (samples > 0) spec += listOf(EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, samples)
        spec += EGL10.EGL_NONE
        val num = IntArray(1)
        if (!egl.eglChooseConfig(display, spec.toIntArray(), null, 0, num) || num[0] <= 0) return null
        val configs = arrayOfNulls<EGLConfig>(num[0])
        if (!egl.eglChooseConfig(display, spec.toIntArray(), configs, num[0], num)) return null
        val v = IntArray(1)
        fun attr(c: EGLConfig, a: Int) = if (egl.eglGetConfigAttrib(display, c, a, v)) v[0] else 0
        // The list is sorted by EGL; take the first that really has what was asked for.
        for (c in configs) {
            if (c == null) continue
            if (attr(c, EGL10.EGL_RED_SIZE) != 8 || attr(c, EGL10.EGL_ALPHA_SIZE) != 8 || attr(c, EGL10.EGL_DEPTH_SIZE) < 24) continue
            val got = if (attr(c, EGL10.EGL_SAMPLE_BUFFERS) > 0) attr(c, EGL10.EGL_SAMPLES) else 0
            if (samples == 0 && got != 0) continue
            if (samples > 0 && got < samples) continue
            return c
        }
        return null
    }

    private companion object { const val RENDERABLE_ES2 = 0x4; const val RENDERABLE_ES3 = 0x40 }
}
