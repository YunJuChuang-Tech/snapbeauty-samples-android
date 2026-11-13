package ai.snapbeauty.samples

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real Camera2 producer regression. Evidence stays in app external files, never source control. */
class CameraDeviceChecks(private val device: Instrumentation) {
    private var checks = 0
    private fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message); checks++ }
    private fun field(obj: Any, name: String): Any = obj.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(obj)!!
    fun run(): Int {
        val activity = device.startActivitySync(Intent(device.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val renderer = field(activity, "renderer") as PreviewRenderer
        val gl = field(activity, "gl") as GLSurfaceView
        val output = File(device.targetContext.filesDir, "camera-regression").apply { mkdirs() }
        val log = StringBuilder()
        fun onGl(block: () -> Unit) {
            val done = CountDownLatch(1); var failure: Throwable? = null
            gl.queueEvent { try { block() } catch (error: Throwable) { failure = error } finally { done.countDown() } }
            if (!done.await(5, TimeUnit.SECONDS)) throw AssertionError("Camera GL callback timed out")
            failure?.let { throw it }
        }
        fun capture(front: Boolean, name: String) {
            val end = SystemClock.elapsedRealtime() + 12000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < end) {
                onGl { ready = field(renderer, "hasFrame") as Boolean && field(renderer, "mirror") == front }
                if (!ready) SystemClock.sleep(100)
            }
            check(ready, "Real camera frames arrive: $name")
            onGl {
                val producer = (field(renderer, "cameraMatrix") as FloatArray).copyOf()
                val combined = field(renderer, "combined") as FloatArray
                val cw = field(renderer, "cameraWidth") as Int; val ch = field(renderer, "cameraHeight") as Int
                val fw = field(renderer, "frameWidth") as Int; val fh = field(renderer, "frameHeight") as Int
                val dimensions = CameraFrameTransform.dimensions(cw, ch, producer)
                check(fw == dimensions.first && fh == dimensions.second, "FBO follows actual producer axes: $name")
                check(fw < fh && cw > ch, "Portrait producer keeps reciprocal non-square dimensions: $name")
                check(combined[0] * combined[5] - combined[1] * combined[4] < 0f, "SDK input removes producer reflection: $name")
                check(kotlin.math.abs(combined[1]) > .9f && kotlin.math.abs(combined[4]) > .9f, "Producer quarter-turn survives normalization: $name")
                check(!(field(renderer, "failed") as Boolean), "Camera processor stays healthy: $name")
                val viewport = IntArray(4); android.opengl.GLES20.glGetIntegerv(android.opengl.GLES20.GL_VIEWPORT, viewport, 0)
                check(kotlin.math.abs(viewport[2].toFloat() / fw - viewport[3].toFloat() / fh) < .01f, "Display uses uniform scaling, never aspect stretching: $name")
                log.append("$name buffer=${cw}x$ch fbo=${fw}x$fh displayMirror=$front\nproducer=${producer.contentToString()}\nnormalized=${combined.contentToString()}\n")
            }
            SystemClock.sleep(350)
            device.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
            }
        }
        try {
            capture(true, "front")
            device.runOnMainSync { find(activity.window.decorView, "切换前后摄像头")!!.performClick() }
            capture(false, "back")
            device.runOnMainSync { find(activity.window.decorView, "切换前后摄像头")!!.performClick() }
            capture(true, "front-return")
            val home = device.startActivitySync(Intent(device.targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            check(!renderer.hasResources(), "Background releases camera GL resources")
            device.runOnMainSync(home::finish)
            capture(true, "front-resume")
            return checks
        } finally {
            File(output, "matrices.txt").writeText(log.toString())
            device.runOnMainSync(activity::finish)
        }
    }
    private fun find(root: View, description: String): View? {
        if (root.contentDescription == description) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), description)?.let { return it }
        return null
    }
}
