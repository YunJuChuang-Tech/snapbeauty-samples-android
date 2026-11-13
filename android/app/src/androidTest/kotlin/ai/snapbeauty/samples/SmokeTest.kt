package ai.snapbeauty.samples

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.opengl.*
import android.opengl.GLES20.*
import android.opengl.GLES30.*
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.nio.ByteBuffer
import java.io.File

/** Device-only checks using real GLES and the supplied SDK. */
class SmokeTest : Instrumentation() {
    private var checks = 0
    private var suite: String? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); suite = arguments?.getString("suite"); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            require(suite in listOf(null, "makeup", "panel", "trtc")) { "Unknown suite: $suite" }
            if (suite == null || suite == "trtc") checks += SceneDeviceChecks.run(this)
            if (suite == null) { runGpuChecks(); runUiChecks(); checks += EffectDeviceChecks.run(this); checks += CameraDeviceChecks(this).run() }
            if (suite == null || suite == "panel") checks += PanelDeviceChecks.run(this)
            if (suite == null || suite == "makeup") checks += MakeupDeviceChecks.run(this)
            if (suite == null) checks += SdkEffectChecks.run(this)
            if (suite == null || suite == "trtc") checks += TrtcDeviceChecks.run(this)
            if (suite == null) {
                checks += AgoraDeviceChecks.run(this)
                checks += ZegoDeviceChecks.run(this)
            }
            val scope = "GPU pixels, clockwise rotation, GL state, beauty, gestures, lifecycle and available RTC checks"
            result.putString("stream", "PASS: $checks checks ($scope)\n")
            finish(Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL after $checks checks: ${android.util.Log.getStackTraceString(failure)}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private fun check(condition: Boolean, message: String) { if (!condition) throw AssertionError(message); checks++ }
    private fun runGpuChecks() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2); check(EGL14.eglInitialize(display, version, 0, version, 1), "EGL initialize")
        val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0)
        check(count[0] > 0, "GLES3 pbuffer config")
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 64, EGL14.EGL_HEIGHT, 64, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, surface, surface, context), "EGL makeCurrent")
        var processor: BeautyProcessor? = null; var blit: GlProgram? = null
        var source = 0; var target = 0; var fbo = 0
        try {
            val side = 64; val image = ByteArray(side * side * 4)
            for (y in 0 until side) for (x in 0 until side) {
                val i = (y * side + x) * 4
                image[i] = (if (x < 32) 110 else 170).toByte(); image[i + 1] = (if (y < 32) 80 else 130).toByte(); image[i + 2] = 70; image[i + 3] = 255.toByte()
            }
            source = texture(image, side, side); target = texture(null, side, side)
            fbo = framebuffer(target); blit = GlProgram(false)
            val identity = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }; glViewport(0, 0, side, side)
            blit.draw(source, GL_TEXTURE_2D, identity); check(image.contentEquals(read(side)), "2D blit preserves all RGBA bytes")
            val mirror = FloatArray(16).apply { Matrix.setIdentityM(this, 0); Matrix.translateM(this, 0, 1f, 0f, 0f); Matrix.scaleM(this, 0, -1f, 1f, 1f) }
            blit.draw(source, GL_TEXTURE_2D, mirror); val mirrored = read(side)
            check(mirrored[0] == image[(side - 1) * 4] && mirrored[(side - 1) * 4] == image[0], "Front preview mirrors only horizontally")
            processor = ProcessorFactory.create(targetContext)
            if (processor.available()) {
                val neutral = EffectState(0f, 0f, 0f, 0f, 0f, 0f, null); processor.apply(neutral)
                var processed = processor.process(source, side, side); draw(blit, fbo, processed, identity, side)
                check(image.contentEquals(read(side)), "Neutral SDK output is pixel identical")
                processor.apply(EffectState(0f, 1f, 0f, 0f, 0f, 0f, null)); processed = processor.process(source, side, side); draw(blit, fbo, processed, identity, side)
                check(!image.contentEquals(read(side)), "Whitening changes real SDK output")
                draw(blit, fbo, source, identity, side); check(image.contentEquals(read(side)), "Original input remains untouched after SDK processing")
                processor.apply(neutral); processor.resetTracking(); processed = processor.process(source, side, side); draw(blit, fbo, processed, identity, side)
                check(image.contentEquals(read(side)), "SDK returns to neutral after reset")
                val resources = ProcessorFactory.resources(targetContext)
                for (filter in resources.filters.filter { it.key != null }) {
                    processor.apply(neutral.withFilter(filter.key).withValue("filterIntensity", 1f))
                    processed = processor.process(source, side, side); draw(blit, fbo, processed, identity, side)
                    check(!image.contentEquals(read(side)), "Supplied LUT changes real SDK pixels: ${filter.key}")
                }
                processor.apply(neutral.withFilter(resources.filters.last().key).withValue("filterIntensity", 0f))
                processed = processor.process(source, side, side); draw(blit, fbo, processed, identity, side)
                check(image.contentEquals(read(side)), "Zero filter intensity restores all original bytes")
                for (style in resources.styles) {
                    processor.apply(neutral.withStyle(style.key).withValue("makeup", 1f))
                    processor.process(source, side, side)
                }
                val engine = processor.javaClass.getDeclaredField("engine").apply { isAccessible = true }.get(processor)
                processor.apply(neutral.withTracking(4, 66.67f))
                check(engine.javaClass.getMethod("getMaxFaces").invoke(engine) == 4 && engine.javaClass.getMethod("getFrameBudgetMs").invoke(engine) == 66.67f, "Multi-face settings reach actual SDK public properties")
                processor.apply(EffectState.defaults())
                check(engine.javaClass.getMethod("getMaxFaces").invoke(engine) == 1, "Reset restores SDK single-face limit")
            } else { processor.apply(EffectState.defaults()); check(processor.process(source, side, side) == source, "Preview flavor passes through without fake effects") }
            runCameraTransformChecks(blit); runTexturePipelineChecks(blit, fbo); runRgbaChecks(); check(glGetError() == GL_NO_ERROR, "No GLES error")
        } finally {
            processor?.release(); blit?.release(); glDeleteTextures(2, intArrayOf(source, target), 0); glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, context); EGL14.eglTerminate(display)
        }
    }
    private fun runCameraTransformChecks(blit: GlProgram) {
        val w = 48; val h = 32
        val evidence = File(targetContext.filesDir, "gpu-regression").apply { mkdirs() }
        // Every pixel differs. A circle and coordinate ramps expose both stretching and quarter turns.
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            pixels[i] = (x * 4).toByte(); pixels[i + 1] = (y * 6).toByte()
            pixels[i + 2] = if ((x - 24) * (x - 24) + (y - 16) * (y - 16) < 100) 220.toByte() else 30
            pixels[i + 3] = 255.toByte()
        }
        val source = texture(pixels, w, h)
        File(evidence, "source-48x32.rgba").writeBytes(pixels)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        try {
            for (turn in 0..3) for (front in listOf(false, true)) {
                // Independent producer fixtures: top-left camera origin, sensor quarter-turn, optional reflection.
                val entries = arrayOf(floatArrayOf(1f, 0f, 0f, -1f, 0f, 1f), floatArrayOf(0f, 1f, 1f, 0f, 0f, 0f),
                    floatArrayOf(-1f, 0f, 0f, 1f, 1f, 0f), floatArrayOf(0f, -1f, -1f, 0f, 1f, 1f))[turn]
                val producer = FloatArray(16).apply { Matrix.setIdentityM(this, 0); this[0] = entries[0]; this[1] = entries[1]; this[4] = entries[2]; this[5] = entries[3]; this[12] = entries[4]; this[13] = entries[5] }
                if (front) { Matrix.translateM(producer, 0, 1f, 0f, 0f); Matrix.scaleM(producer, 0, -1f, 1f, 1f) }
                val original = producer.copyOf(); val normalized = FloatArray(16); CameraFrameTransform.unmirrored(producer, normalized)
                check(producer.contentEquals(original), "Producer matrix remains immutable: $turn/$front")
                val ow = if (turn % 2 == 0) w else h; val oh = if (turn % 2 == 0) h else w
                check(CameraFrameTransform.dimensions(w, h, producer) == ow to oh, "Non-square camera dimensions: $turn/$front")
                val target = texture(null, ow, oh); val fbo = framebuffer(target)
                try {
                    for (displayMirror in listOf(false, front).distinct()) {
                        val transform = normalized.copyOf()
                        if (displayMirror) { Matrix.translateM(transform, 0, 1f, 0f, 0f); Matrix.scaleM(transform, 0, -1f, 1f, 1f) }
                        glBindFramebuffer(GL_FRAMEBUFFER, fbo); glViewport(0, 0, ow, oh); blit.draw(source, GL_TEXTURE_2D, transform)
                        val expected = ByteArray(pixels.size)
                        for (y in 0 until oh) for (x in 0 until ow) {
                            val ux = if (displayMirror) ow - 1 - x else x
                            val sx = when (turn) { 0 -> ux; 1 -> y; 2 -> w - 1 - ux; else -> w - 1 - y }
                            val sy = when (turn) { 0 -> h - 1 - y; 1 -> ux; 2 -> y; else -> h - 1 - ux }
                            pixels.copyInto(expected, (y * ow + x) * 4, (sy * w + sx) * 4, (sy * w + sx) * 4 + 4)
                        }
                        val actual = read(ow, oh)
                        val sample = "$turn-front-$front-mirror-$displayMirror-${ow}x$oh"
                        File(evidence, "$sample-gpu.rgba").writeBytes(actual); File(evidence, "$sample-cpu.rgba").writeBytes(expected)
                        check(actual.contentEquals(expected), "All camera pixels/circle proportions match CPU oracle: $turn/$front/mirror=$displayMirror")
                        if (turn == 1 && front && !displayMirror) {
                            val legacy = original.copyOf(); Matrix.translateM(legacy, 0, .5f, .5f, 0f); Matrix.rotateM(legacy, 0, -270f, 0f, 0f, 1f); Matrix.translateM(legacy, 0, -.5f, -.5f, 0f)
                            blit.draw(source, GL_TEXTURE_2D, legacy)
                            check(!read(ow, oh).contentEquals(expected), "Captured front producer rejects legacy double rotation")
                        }
                    }
                } finally { glDeleteFramebuffers(1, intArrayOf(fbo), 0); glDeleteTextures(1, intArrayOf(target), 0) }
            }
        } finally { glDeleteTextures(1, intArrayOf(source), 0) }
    }
    private fun runRgbaChecks() {
        val w = 3; val h = 5; val bottomUp = ByteArray(w * h * 4) { (it + 10).toByte() }; val texture = texture(bottomUp, w, h)
        val pbo = IntArray(1); glGenBuffers(1, pbo, 0); glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo[0]); glBufferData(GL_PIXEL_PACK_BUFFER, 1024, null, GL_STREAM_READ)
        glPixelStorei(GL_PACK_ALIGNMENT, 8); glPixelStorei(GL_PACK_ROW_LENGTH, 7); glPixelStorei(GL_PACK_SKIP_ROWS, 1); glPixelStorei(GL_PACK_SKIP_PIXELS, 2)
        val draw = get(GL_DRAW_FRAMEBUFFER_BINDING); val read = get(GL_READ_FRAMEBUFFER_BINDING)
        val reader = RgbaFrameReader()
        try {
            val topDown = reader.read(texture, w, h); val expected = ByteArray(bottomUp.size)
            for (y in 0 until h) bottomUp.copyInto(expected, y * w * 4, (h - 1 - y) * w * 4, (h - y) * w * 4)
            check(topDown.contentEquals(expected), "Odd-width RGBA reads top-down with all channels preserved")
            val second = reader.read(texture, w, h); check(second !== topDown && topDown.contentEquals(second), "External frames own independent byte arrays")
            check(get(GL_PIXEL_PACK_BUFFER_BINDING) == pbo[0], "Readback preserves host PBO binding")
            check(get(GL_PACK_ALIGNMENT) == 8, "Readback restores pack alignment")
            check(get(GL_PACK_ROW_LENGTH) == 7, "Readback restores pack row length")
            check(get(GL_PACK_SKIP_ROWS) == 1, "Readback restores skipped rows")
            check(get(GL_PACK_SKIP_PIXELS) == 2, "Readback restores skipped pixels")
            check(get(GL_DRAW_FRAMEBUFFER_BINDING) == draw, "Readback restores draw framebuffer")
            check(get(GL_READ_FRAMEBUFFER_BINDING) == read, "Readback restores read framebuffer")
        } finally {
            reader.release(); glBindBuffer(GL_PIXEL_PACK_BUFFER, 0); glDeleteBuffers(1, pbo, 0); glDeleteTextures(1, intArrayOf(texture), 0)
            glPixelStorei(GL_PACK_ALIGNMENT, 4); glPixelStorei(GL_PACK_ROW_LENGTH, 0); glPixelStorei(GL_PACK_SKIP_ROWS, 0); glPixelStorei(GL_PACK_SKIP_PIXELS, 0)
        }
    }
    private fun runTexturePipelineChecks(blit: GlProgram, targetFbo: Int) {
        val width = 48; val height = 64; val pixels = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val i = (y * width + x) * 4
            pixels[i] = (if (x < width / 2) 40 else 160).toByte(); pixels[i + 1] = (if (y < height / 2) 70 else 190).toByte(); pixels[i + 2] = 100; pixels[i + 3] = 255.toByte()
        }
        val source = texture(pixels, width, height); val received = IntArray(4)
        val spy = object : BeautyProcessor {
            override fun available() = true
            override fun label() = "test"
            override fun apply(state: EffectState) = Unit
            override fun process(texture: Int, width: Int, height: Int): Int {
                received[0] = width; received[1] = height
                val fbo = framebuffer(texture); val corner = ByteBuffer.allocateDirect(4)
                glReadPixels(0, 0, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, corner)
                received[2] = corner.get(0).toInt() and 255; received[3] = corner.get(1).toInt() and 255
                glDeleteFramebuffers(1, intArrayOf(fbo), 0); return texture
            }
            override fun status() = "test"
            override fun resetTracking() = Unit
            override fun release() = Unit
        }
        val pipeline = TextureBeautyPipeline(spy)
        try {
            val neutral = EffectState(0f, 0f, 0f, 0f, 0f, 0f, null); val identity = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
            val corners = arrayOf(intArrayOf(40, 70), intArrayOf(160, 70), intArrayOf(160, 190), intArrayOf(40, 190))
            for (turns in 0..3) {
                val output = pipeline.process(source, width, height, turns * 90, neutral, false)
                check(received[0] == (if (turns % 2 == 0) width else height) && received[1] == (if (turns % 2 == 0) height else width), "Upright dimensions, rotation $turns")
                check(received[2] == corners[turns][0] && received[3] == corners[turns][1], "Clockwise upright corner, rotation $turns")
                glBindFramebuffer(GL_FRAMEBUFFER, targetFbo); glViewport(0, 0, width, height); blit.draw(output, GL_TEXTURE_2D, identity)
                val roundTrip = read(width, height); val differences = pixels.indices.count { pixels[it] != roundTrip[it] }
                check(differences == 0, "Non-square round trip preserves frame, rotation $turns, differences=$differences")
            }
            val vao = IntArray(1); val buffer = IntArray(1)
            glGenVertexArrays(1, vao, 0); glBindVertexArray(vao[0]); glGenBuffers(1, buffer, 0); glBindBuffer(GL_ARRAY_BUFFER, buffer[0])
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, source); glActiveTexture(GL_TEXTURE3)
            glViewport(3, 4, 23, 27); glEnable(GL_BLEND); glEnable(GL_CULL_FACE); glColorMask(false, true, false, true)
            pipeline.process(source, width, height, 90, neutral, false)
            val viewport = IntArray(4); val mask = BooleanArray(4); glGetIntegerv(GL_VIEWPORT, viewport, 0); glGetBooleanv(GL_COLOR_WRITEMASK, mask, 0)
            check(viewport.contentEquals(intArrayOf(3, 4, 23, 27)), "Host viewport restored")
            check(get(GL_ACTIVE_TEXTURE) == GL_TEXTURE3, "Host texture unit restored")
            check(get(GL_VERTEX_ARRAY_BINDING) == vao[0], "Host VAO restored")
            check(get(GL_ARRAY_BUFFER_BINDING) == buffer[0], "Host vertex buffer restored")
            check(glIsEnabled(GL_BLEND) && glIsEnabled(GL_CULL_FACE) && !mask[0] && mask[1] && !mask[2] && mask[3], "Host render flags restored")
            glActiveTexture(GL_TEXTURE0); check(get(GL_TEXTURE_BINDING_2D) == source, "Host texture binding restored")
            check(pipeline.process(source, width, height, 90, neutral, true) == source, "RTC original comparison bypasses processing")
            glBindVertexArray(0); glBindBuffer(GL_ARRAY_BUFFER, 0); glDeleteVertexArrays(1, vao, 0); glDeleteBuffers(1, buffer, 0)
            glDisable(GL_BLEND); glDisable(GL_CULL_FACE); glColorMask(true, true, true, true)
        } finally { pipeline.release(); glDeleteTextures(1, intArrayOf(source), 0) }
    }
    private fun runUiChecks() {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync()
        try {
            checkedMain {
                val root = activity.window.decorView
                check(find(root, "SNAPBEAUTY") != null, "Main screen renders")
                val compare = findDescription(root, "按住查看原图，松开恢复效果"); check(compare != null, "Comparison is accessible")
                requireNotNull(compare)
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, compare.width / 2f, compare.height / 2f, 0); compare.dispatchTouchEvent(down); down.recycle()
                check(find(root, "正在查看原图") != null, "Holding comparison enters original mode")
                val cancel = MotionEvent.obtain(now, now + 1, MotionEvent.ACTION_CANCEL, 0f, 0f, 0); compare.dispatchTouchEvent(cancel); cancel.recycle()
                check(find(root, "按住对比原图") != null, "Canceled gesture restores processed mode")
                val reset = findDescription(root, "重置全部效果"); check(reset != null, "Reset is accessible"); reset!!.performClick()
                check(find(root, "按住对比原图") != null, "Reset exits original comparison")
            }
            val monitor = addMonitor(MainActivity::class.java.name, null, false); checkedMain(activity::recreate)
            val recreated = waitForMonitorWithTimeout(monitor, 5000); removeMonitor(monitor)
            check(recreated != null && !recreated.isFinishing, "Activity recreation creates a live activity")
            recreated?.let { checkedMain(it::finish) }
        } finally { checkedMain(activity::finish) }
    }
    private fun find(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), text)?.let { return it }
        return null
    }
    private fun findDescription(root: View, text: String): View? {
        if (root.contentDescription?.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findDescription(root.getChildAt(i), text)?.let { return it }
        return null
    }
    private fun get(key: Int) = IntArray(1).also { glGetIntegerv(key, it, 0) }[0]
    private fun texture(pixels: ByteArray?, width: Int, height: Int): Int = GlProgram.texture(GL_TEXTURE_2D).also {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels?.let(ByteBuffer::wrap))
    }
    private fun framebuffer(texture: Int): Int = IntArray(1).also {
        glGenFramebuffers(1, it, 0); glBindFramebuffer(GL_FRAMEBUFFER, it[0]); glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
    }[0]
    private fun draw(program: GlProgram, fbo: Int, texture: Int, transform: FloatArray, side: Int) { glBindFramebuffer(GL_FRAMEBUFFER, fbo); glViewport(0, 0, side, side); program.draw(texture, GL_TEXTURE_2D, transform) }
    private fun read(width: Int, height: Int = width): ByteArray {
        val buffer = ByteBuffer.allocateDirect(width * height * 4); glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
        return ByteArray(buffer.capacity()).also { buffer.rewind(); buffer.get(it) }
    }
}
