package ai.snapbeauty.samples

import android.content.Context
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

/** Synchronous texture handoff with cleanup independent of future capture callbacks. */
class RtcBeautyWorker(app: Context) {
    private val thread = HandlerThread("rtc-beauty")
    private val handler: Handler
    private val display = EGL14.eglGetCurrentDisplay()
    private val host = EGL14.eglGetCurrentContext()
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var pipeline: TextureBeautyPipeline? = null
    private val closing = AtomicBoolean()
    private val queueLock = Any()
    val available: Boolean
    @Volatile var cleanupFailed = false
    @Volatile var status = "正在准备美颜…"
    init {
        check(host != EGL14.EGL_NO_CONTEXT) { "No RTC context to share" }
        thread.start(); handler = Handler(thread.looper)
        try { available = invoke { createContext(); TextureBeautyPipeline(app).also { pipeline = it }.available() } }
        catch (error: Throwable) { handler.post { destroyContext(); thread.quitSafely() }; throw error }
    }
    fun process(texture: Int, width: Int, height: Int, rotation: Int, effects: EffectState, original: Boolean, reset: Boolean): Int {
        if (closing.get()) return texture
        GLES20.glFinish()
        return invoke {
            val current = pipeline
            if (current == null || closing.get()) texture else {
                if (reset) current.resetTracking()
                current.process(texture, width, height, rotation, effects, original).also { status = current.status() }
            }
        }
    }
    fun close(complete: Runnable) {
        synchronized(queueLock) {
            if (!closing.compareAndSet(false, true)) return
            handler.post {
                try { pipeline?.release(); pipeline = null }
                catch (error: Throwable) { cleanupFailed = true; Log.e("SnapBeautySamples", "RTC worker cleanup failed", error) }
                finally { destroyContext(); thread.quitSafely(); complete.run() }
            }
        }
    }
    private fun createContext() {
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0) && count[0] > 0) { "No RTC worker EGL config" }
        context = EGL14.eglCreateContext(display, configs[0], host, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, context)) { "Cannot start RTC beauty worker" }
    }
    private fun destroyContext() {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
    }
    private fun <T> invoke(action: Callable<T>): T {
        val task = FutureTask(action)
        synchronized(queueLock) { check(!closing.get() && handler.post(task)) { "RTC beauty worker has stopped" } }
        var interrupted = false
        try {
            while (true) {
                try { return task.get() }
                catch (error: InterruptedException) { interrupted = true }
                catch (error: ExecutionException) { throw error.cause ?: error }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
}
