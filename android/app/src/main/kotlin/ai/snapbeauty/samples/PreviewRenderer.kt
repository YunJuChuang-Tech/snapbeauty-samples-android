package ai.snapbeauty.samples

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.roundToInt

class PreviewRenderer(context: Context, private val view: GLSurfaceView, private val listener: Listener) : GLSurfaceView.Renderer {
    interface FrameSink {
        fun frame(texture: Int, width: Int, height: Int, timestampNs: Long)
        fun release()
    }
    interface Listener {
        fun surfaceReady(texture: SurfaceTexture)
        fun status(message: String, effectsAvailable: Boolean)
        fun error(message: String)
    }
    @Volatile var sink: FrameSink? = null
    @Volatile var compare = false
    private val context = context.applicationContext
    private val frameReady = AtomicBoolean()
    private val cameraMatrix = FloatArray(16)
    private val combined = FloatArray(16)
    private val screen = FloatArray(16)
    @Volatile private var cameraTexture: SurfaceTexture? = null
    private var external: GlProgram? = null
    private var blit: GlProgram? = null
    private var processor: BeautyProcessor? = null
    private var oes = 0
    private var raw = 0
    private var fbo = 0
    private var frameWidth = 0
    private var frameHeight = 0
    private var width = 0
    private var height = 0
    private var cameraWidth = 0
    private var cameraHeight = 0
    private var lastTexture = 0
    private var mirror = false
    private var failed = false
    private var hasFrame = false
    @Volatile private var disposed = false
    @Volatile private var initialized = false
    private var statusAt = 0L
    private var state = EffectState.defaults()
    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        initialized = true; disposed = false; failed = false; hasFrame = false; lastTexture = 0
        raw = 0; fbo = 0; frameWidth = 0; frameHeight = 0; statusAt = 0
        try {
            external = GlProgram(true); blit = GlProgram(false)
            oes = GlProgram.texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            val texture = SurfaceTexture(oes).also { cameraTexture = it }
            texture.setOnFrameAvailableListener { frameReady.set(true); view.requestRender() }
            val effects = ProcessorFactory.create(context).also { processor = it }
            effects.apply(state); listener.status(effects.label(), effects.available()); listener.surfaceReady(texture)
        } catch (error: RuntimeException) { fail(error) } catch (error: LinkageError) { fail(error) }
    }
    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) { this.width = width; this.height = height }
    fun configure(cameraWidth: Int, cameraHeight: Int, rotation: Int, mirror: Boolean) {
        if (disposed) return
        this.cameraWidth = cameraWidth; this.cameraHeight = cameraHeight
        this.mirror = mirror; hasFrame = false; lastTexture = 0; frameReady.set(false)
        allocate(if (rotation % 180 == 0) cameraWidth else cameraHeight, if (rotation % 180 == 0) cameraHeight else cameraWidth)
        processor?.resetTracking()
    }
    fun setEffects(state: EffectState) { this.state = state; if (!failed && !disposed) processor?.apply(state) }
    override fun onDrawFrame(unused: GL10?) {
        if (disposed) return
        glBindFramebuffer(GL_FRAMEBUFFER, 0); glViewport(0, 0, width, height)
        glClearColor(.04f, .06f, .05f, 1f); glClear(GL_COLOR_BUFFER_BIT)
        val effects = processor ?: return
        if (failed || raw == 0 || width == 0 || height == 0) return
        try {
            if (frameReady.getAndSet(false)) {
                val texture = checkNotNull(cameraTexture)
                texture.updateTexImage(); texture.getTransformMatrix(cameraMatrix)
                val (uprightWidth, uprightHeight) = CameraFrameTransform.dimensions(cameraWidth, cameraHeight, cameraMatrix)
                if (frameWidth != uprightWidth || frameHeight != uprightHeight) {
                    allocate(uprightWidth, uprightHeight); effects.resetTracking()
                }
                CameraFrameTransform.unmirrored(cameraMatrix, combined)
                glBindFramebuffer(GL_FRAMEBUFFER, fbo); glViewport(0, 0, frameWidth, frameHeight)
                external!!.draw(oes, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, combined)
                lastTexture = effects.process(raw, frameWidth, frameHeight); hasFrame = true
                sink?.frame(if (compare) raw else lastTexture, frameWidth, frameHeight, texture.timestamp)
            }
            if (!hasFrame) return
            glBindFramebuffer(GL_FRAMEBUFFER, 0)
            val scale = max(width.toFloat() / frameWidth, height.toFloat() / frameHeight)
            val vw = (frameWidth * scale).roundToInt(); val vh = (frameHeight * scale).roundToInt()
            glViewport((width - vw) / 2, (height - vh) / 2, vw, vh)
            Matrix.setIdentityM(screen, 0)
            if (mirror) { Matrix.translateM(screen, 0, 1f, 0f, 0f); Matrix.scaleM(screen, 0, -1f, 1f, 1f) }
            blit!!.draw(if (compare) raw else lastTexture, GL_TEXTURE_2D, screen)
            val now = SystemClock.elapsedRealtime()
            if (now - statusAt >= 600) { statusAt = now; listener.status(effects.status(), effects.available()) }
        } catch (error: RuntimeException) { fail(error) } catch (error: LinkageError) { fail(error) }
    }
    private fun allocate(w: Int, h: Int) {
        if (raw != 0) glDeleteTextures(1, intArrayOf(raw), 0)
        if (fbo != 0) glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        frameWidth = w; frameHeight = h
        raw = GlProgram.texture(GL_TEXTURE_2D)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        val id = IntArray(1); glGenFramebuffers(1, id, 0); fbo = id[0]
        glBindFramebuffer(GL_FRAMEBUFFER, fbo); glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, raw, 0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "Camera framebuffer incomplete" }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }
    fun dispose() {
        if (disposed) return
        disposed = true; initialized = false
        sink?.release(); processor?.release(); processor = null
        cameraTexture?.let { it.setOnFrameAvailableListener(null); it.release() }; cameraTexture = null
        glDeleteTextures(2, intArrayOf(oes, raw), 0)
        if (fbo != 0) glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        external?.release(); blit?.release()
    }
    fun hasResources() = initialized
    fun isCurrentSurface(texture: SurfaceTexture) = !disposed && cameraTexture === texture
    private fun fail(error: Throwable) {
        failed = true; Log.e("SnapBeautySamples", "Preview processing failed", error)
        listener.error("效果引擎启动失败，请检查 SDK 配置后重试")
    }
}
