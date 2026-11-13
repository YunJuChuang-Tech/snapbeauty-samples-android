package ai.snapbeauty.samples

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.*
import android.opengl.GLES20.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Checks resource packaging and real face rendering, not just successful setter calls. */
object MakeupDeviceChecks {
    @JvmStatic fun run(device: Instrumentation): Int {
        var checks = 0
        fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message); checks++ }
        val assets = device.targetContext.assets
        val manifest = assets.open("sample_makeup/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        fun read(path: String) = assets.open(path).use { it.readBytes() }
        val complete = MakeupAssets.validate(manifest, ::read)
        check(complete.complete && complete.keys.size == 15, "Every offered makeup style has complete hash-verified artwork")
        val missing = MakeupAssets.validate(manifest) { path -> if (path.endsWith("blush_01.webp")) error("missing fixture") else read(path) }
        check("dawn" !in missing.keys && !missing.complete, "Missing artwork is rejected before an engine can silently fall back")
        val corrupted = MakeupAssets.validate(manifest) { path -> if (path.endsWith("lip_01.json")) byteArrayOf(1, 2) else read(path) }
        check("dawn" !in corrupted.keys, "Corrupt artwork is rejected before showing a style")
        val missingFilter = MakeupAssets.validate(manifest) { path -> if (path == "snapbeauty/luts/rose3.png") error("missing filter") else read(path) }
        check("dawn" !in missingFilter.keys && !missingFilter.complete, "A preset with a missing associated LUT is not offered")
        val hashes = mutableSetOf<String>()
        val files = manifest.getJSONObject("files")
        for (path in files.keys()) {
            val bytes = read(path)
            val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
            check(hashes.add(md5) && md5 == files.getJSONObject(path).getString("md5"), "Generated asset has a unique recorded MD5: $path")
        }
        val fixture = File(device.targetContext.filesDir, "makeup-fixture.jpg")
        if (!fixture.isFile) {
            android.util.Log.i("SnapBeautySamples", "Makeup packaging checked; face image checks skipped (no local fixture)")
            return checks
        }
        val bitmap = checkNotNull(BitmapFactory.decodeFile(fixture.path))
        val width = bitmap.width; val height = bitmap.height
        // Optional independently detected feature rectangles, kept beside the local portrait.
        val regionsFile = File(fixture.parentFile, "makeup-fixture-regions.json")
        val regions = if (regionsFile.isFile) JSONObject(regionsFile.readText()).also {
            check(it.getInt("width") == width && it.getInt("height") == height, "Feature regions match portrait dimensions")
        }.getJSONObject("regions") else null
        val outputDir = File(device.targetContext.filesDir, "makeup-regression").apply { mkdirs() }
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2); check(EGL14.eglInitialize(display, version, 0, version, 1), "Makeup EGL initialized")
        val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 0x40, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE), 0, configs, 0, 1, count, 0)
        val context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, surface, surface, context), "Makeup EGL current")
        val uploaded = GlProgram.texture(GL_TEXTURE_2D); GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle()
        val upright = GlProgram.texture(GL_TEXTURE_2D); glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
        val fbos = IntArray(1); glGenFramebuffers(1, fbos, 0); glBindFramebuffer(GL_FRAMEBUFFER, fbos[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, upright, 0)
        val blit = GlProgram(false)
        val flip = FloatArray(16).apply { Matrix.setIdentityM(this, 0); Matrix.translateM(this, 0, 0f, 1f, 0f); Matrix.scaleM(this, 0, 1f, -1f, 1f) }
        glViewport(0, 0, width, height); blit.draw(uploaded, GL_TEXTURE_2D, flip)
        val processor = ProcessorFactory.create(device.targetContext)
        val reader = RgbaFrameReader()
        val report = JSONArray()
        try {
            val original = reader.read(upright, width, height)
            save(outputDir, "original", original, width, height)
            val neutral = EffectState(0f, 0f, 0f, 0f, 0f, 0f, null)
            val outputHashes = mutableSetOf<String>()
            for (style in complete.choices.mapNotNull { it.key }) {
                processor.apply(neutral.withStyle(style).withValue("makeup", 1f))
                var rendered = upright
                repeat(12) { rendered = processor.process(upright, width, height) }
                check(processor.status().startsWith("已识别"), "Actual portrait is detected for $style: ${processor.status()}")
                check(rendered != upright, "Makeup does not silently return original texture: $style")
                val full = reader.read(rendered, width, height)
                val changed = (0 until width * height).count { pixel -> (0..2).any { full[pixel * 4 + it] != original[pixel * 4 + it] } }
                check(changed > 100, "Visible face pixels change for $style (changed=$changed)")
                val digest = MessageDigest.getInstance("MD5").digest(full).joinToString("") { "%02x".format(it) }
                check(outputHashes.add(digest), "Different style produces different image bytes: $style")
                save(outputDir, style, full, width, height)
                val regionCounts = JSONObject()
                if (regions != null) for (name in regions.keys()) {
                    val box = regions.getJSONArray(name)
                    val left = box.getInt(0); val top = box.getInt(1); val right = box.getInt(2); val bottom = box.getInt(3)
                    check(left >= 0 && top >= 0 && right <= width && bottom <= height && left < right && top < bottom, "Valid $name bounds")
                    var changedRegion = 0
                    for (y in top until bottom) for (x in left until right) {
                        val offset = (y * width + x) * 4
                        if ((0..2).any { kotlin.math.abs((full[offset + it].toInt() and 255) - (original[offset + it].toInt() and 255)) > 12 }) changedRegion++
                    }
                    regionCounts.put(name, changedRegion)
                    when {
                        name == "forehead" -> check(changedRegion == 0, "Makeup does not paint the forehead: $style")
                        name.contains("Cheek") && style in listOf("neon", "ink") -> check(changedRegion < 20, "No-blush style keeps cheeks clear: $style")
                        else -> check(changedRegion > 20, "Makeup reaches independently located $name pixels: $style")
                    }
                }
                processor.apply(neutral.withStyle(style).withValue("makeup", .4f)); repeat(3) { rendered = processor.process(upright, width, height) }
                val partial = reader.read(rendered, width, height)
                check(!partial.contentEquals(full) && !partial.contentEquals(original), "Makeup 40% is visibly different from 0% and 100%: $style")
                processor.apply(neutral.withStyle(style)); rendered = processor.process(upright, width, height)
                check(reader.read(rendered, width, height).contentEquals(original), "Makeup 0% restores every original RGBA byte: $style")
                check(reader.read(upright, width, height).contentEquals(original), "Original portrait remains untouched: $style")
                val preset = complete.choices.first { it.key == style }
                val combined = neutral.selectStyle(preset).withValue("makeup", 1f)
                processor.apply(combined); repeat(3) { rendered = processor.process(upright, width, height) }
                check(!reader.read(rendered, width, height).contentEquals(full), "Preset LUT adds real whole-frame color beyond makeup layers: $style")
                processor.apply(combined.withValue("makeup", 0f))
                check(reader.read(processor.process(upright, width, height), width, height).contentEquals(original), "Zero linked strength disables both makeup and LUT: $style")
                report.put(JSONObject().put("style", style).put("changedPixels", changed).put("outputMd5", digest).put("featureChangedPixels", regionCounts).put("status", processor.status()))
            }
            processor.apply(neutral); check(reader.read(processor.process(upright, width, height), width, height).contentEquals(original), "No-makeup restores the original portrait")
            check(glGetError() == GL_NO_ERROR, "No GLES error after all makeup styles")
            File(outputDir, "results.json").writeText(JSONObject().put("width", width).put("height", height).put("styles", report).toString(2))
        } finally {
            processor.release(); reader.release(); blit.release()
            glDeleteTextures(2, intArrayOf(uploaded, upright), 0); glDeleteFramebuffers(1, fbos, 0)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface); EGL14.eglDestroyContext(display, context); EGL14.eglTerminate(display)
        }
        return checks
    }
    private fun save(dir: File, name: String, rgba: ByteArray, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
