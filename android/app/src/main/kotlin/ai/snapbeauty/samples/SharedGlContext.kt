package ai.snapbeauty.samples

import android.opengl.*

/** Shares textures while isolating the host's VAO, VBO and pixel-store state. */
class SharedGlContext {
    private val owner = Thread.currentThread()
    private val display = EGL14.eglGetCurrentDisplay()
    private val host = EGL14.eglGetCurrentContext()
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    init {
        check(host != EGL14.EGL_NO_CONTEXT) { "No host EGL context" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE)
        check(EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No GLES3 sharing config" }
        context = EGL14.eglCreateContext(display, configs[0], host, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Cannot share host textures" }
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        if (surface == EGL14.EGL_NO_SURFACE) { destroy(); error("Cannot create beauty EGL surface") }
    }
    fun enter(): Scope {
        check(Thread.currentThread() === owner && EGL14.eglGetCurrentContext() == host) { "RTC callback changed EGL owner" }
        val draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        GLES20.glFinish()
        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "Cannot enter beauty EGL context" }
        return Scope(draw, read)
    }
    fun destroy() {
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        surface = EGL14.EGL_NO_SURFACE; context = EGL14.EGL_NO_CONTEXT
    }
    inner class Scope(private val draw: EGLSurface, private val read: EGLSurface) : AutoCloseable {
        override fun close() {
            GLES20.glFinish()
            check(EGL14.eglMakeCurrent(display, draw, read, host)) { "Cannot restore host EGL context" }
        }
    }
}
