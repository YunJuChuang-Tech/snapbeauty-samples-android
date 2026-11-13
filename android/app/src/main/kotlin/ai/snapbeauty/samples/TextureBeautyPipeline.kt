package ai.snapbeauty.samples

import android.content.Context
import android.opengl.*

/** Normalize rotation for face tracking, then restore the host's original texture geometry. */
class TextureBeautyPipeline private constructor(app: Context?, supplied: BeautyProcessor?) {
    private lateinit var processor: BeautyProcessor
    private var blit: GlProgram? = null
    private val owner = Thread.currentThread()
    private val context = EGL14.eglGetCurrentContext()
    private val textures = IntArray(2)
    private val fbos = IntArray(2)
    private val rotationSampler = IntArray(1)
    private val isolated = SharedGlContext()
    private val transform = FloatArray(16)
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var angle = 0
    private var released = false
    constructor(app: Context) : this(app, null)
    constructor(processor: BeautyProcessor) : this(null, processor)
    init {
        check(context != EGL14.EGL_NO_CONTEXT) { "No current EGL context" }
        try {
            isolated.enter().use {
                try {
                    blit = GlProgram(false); GLES30.glGenSamplers(1, rotationSampler, 0)
                    GLES30.glSamplerParameteri(rotationSampler[0], GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                    GLES30.glSamplerParameteri(rotationSampler[0], GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                    GLES30.glSamplerParameteri(rotationSampler[0], GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES30.glSamplerParameteri(rotationSampler[0], GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    processor = supplied ?: ProcessorFactory.create(requireNotNull(app))
                    processor.apply(EffectState.defaults())
                } catch (error: Throwable) {
                    blit?.release(); GLES30.glDeleteSamplers(1, rotationSampler, 0)
                    if (::processor.isInitialized) processor.release()
                    throw error
                }
            }
        } catch (error: Throwable) { isolated.destroy(); throw error }
    }
    fun available() = processor.available()
    fun process(texture: Int, width: Int, height: Int, rotation: Int, state: EffectState, original: Boolean): Int {
        checkOwner()
        require(texture > 0 && width > 0 && height > 0 && rotation % 90 == 0) { "Invalid texture frame" }
        if (original) return texture
        isolated.enter().use {
            processor.apply(state)
            val degrees = (rotation % 360 + 360) % 360
            if (degrees == 0) return processor.process(texture, width, height)
            if (sourceWidth != width || sourceHeight != height || angle != degrees) allocate(width, height, degrees)
            val w = if (degrees % 180 == 0) width else height
            val h = if (degrees % 180 == 0) height else width
            draw(texture, fbos[0], w, h, degrees)
            val output = processor.process(textures[0], w, h)
            draw(output, fbos[1], width, height, -degrees)
            return textures[1]
        }
    }
    fun status() = processor.status()
    fun resetTracking() { checkOwner(); isolated.enter().use { processor.resetTracking() } }
    fun release() {
        if (released) return
        checkOwner(); released = true
        try {
            isolated.enter().use {
                processor.release(); blit?.release()
                GLES20.glDeleteTextures(2, textures, 0); GLES20.glDeleteFramebuffers(2, fbos, 0)
                GLES30.glDeleteSamplers(1, rotationSampler, 0)
            }
        } finally { isolated.destroy() }
    }
    private fun checkOwner() = check(!released && Thread.currentThread() === owner && EGL14.eglGetCurrentContext() == context) { "Beauty callback changed GL thread or context" }
    private fun allocate(w: Int, h: Int, rotation: Int) {
        GLES20.glDeleteTextures(2, textures, 0); GLES20.glDeleteFramebuffers(2, fbos, 0); GLES20.glGenFramebuffers(2, fbos, 0)
        val uprightW = if (rotation % 180 == 0) w else h
        val uprightH = if (rotation % 180 == 0) h else w
        for (i in 0..1) {
            textures[i] = GlProgram.texture(GLES20.GL_TEXTURE_2D)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, if (i == 0) uprightW else w, if (i == 0) uprightH else h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[i], 0)
            check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) == GLES20.GL_FRAMEBUFFER_COMPLETE) { "RTC framebuffer incomplete" }
        }
        sourceWidth = w; sourceHeight = h; angle = rotation; processor.resetTracking()
    }
    private fun draw(input: Int, fbo: Int, w: Int, h: Int, degrees: Int) {
        Matrix.setIdentityM(transform, 0)
        // Exact quarter-turn coefficients and nearest sampling preserve every texel byte.
        when ((degrees % 360 + 360) % 360) {
            90 -> { transform[0] = 0f; transform[1] = 1f; transform[4] = -1f; transform[5] = 0f; transform[12] = 1f }
            180 -> { transform[0] = -1f; transform[5] = -1f; transform[12] = 1f; transform[13] = 1f }
            270 -> { transform[0] = 0f; transform[1] = -1f; transform[4] = 1f; transform[5] = 0f; transform[13] = 1f }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo); GLES20.glViewport(0, 0, w, h)
        GLES30.glBindSampler(0, rotationSampler[0])
        try { blit!!.draw(input, GLES20.GL_TEXTURE_2D, transform) } finally { GLES30.glBindSampler(0, 0) }
    }
}
