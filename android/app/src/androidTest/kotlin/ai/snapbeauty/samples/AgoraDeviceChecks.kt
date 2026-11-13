package ai.snapbeauty.samples

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.AgoraVideoFrame
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Recording receiver checks camera/GL conversion without network joins. */
class AgoraDeviceChecks private constructor(private val device: Instrumentation) {
    private var checks = 0
    private fun check(success: Boolean, message: String) { if (!success) throw AssertionError(message); checks++ }
    private fun configuration() {
        check(RtcEngine.getSdkVersion().startsWith("4.6.4"), "Packaged Agora native libraries load without joining a channel")
        val appId = "0123456789abcdef0123456789abcdef"
        val config = AgoraRoomConfig(" $appId", "test_channel", " 123 ", " placeholder-only ")
        check(config.appId == appId && config.channel == "test_channel" && config.uid == 123, "Agora config normalizes fields")
        check(!config.toString().contains("placeholder-only"), "Config diagnostics do not contain Token")
        val invalid = listOf(arrayOf("bad", "c", "1", "x"), arrayOf(appId, "bad channel", "1", "x"), arrayOf(appId, "c", "0", "x"), arrayOf(appId, "c", "2147483648", "x"), arrayOf(appId, "c", "1", ""))
        invalid.forEach { args ->
            var rejected = false
            try { AgoraRoomConfig(args[0], args[1], args[2], args[3]) } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected, "Invalid Agora config rejected before SDK initialization")
        }
        val rgba = ByteArray(3 * 5 * 4); val frame = AgoraFrameSink.wrap(rgba, 3, 5, 123_000_000L)
        check(frame.format == AgoraVideoFrame.FORMAT_RGBA && frame.stride == 3 && frame.height == 5, "Real Agora frame uses pixel stride and RGBA")
        check(frame.timeStamp == 123L && frame.rotation == 0 && frame.buf === rgba, "Agora frame keeps complete pixels and millisecond capture time")
    }
    private fun camera() {
        val activity = device.startActivitySync(Intent(device.targetContext, AgoraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AgoraActivity
        val controller = AgoraActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }.get(activity) as AgoraController
        val latest = AtomicReference<AgoraVideoFrame>(); val error = AtomicBoolean()
        val sink = AgoraFrameSink(object : AgoraFrameSink.Target {
            override fun publishing() = true
            override fun push(frame: AgoraVideoFrame): Int { latest.set(frame); return 0 }
            override fun frameError() { error.set(true) }
        })
        var home: Activity? = null
        try {
            check(controller.state() == AgoraController.State.IDLE && controller.pushed.get() == 0L, "Local preview starts without joining Agora")
            device.runOnMainSync { activity.setFrameSink(sink) }; waitUntil(15000) { sink.accepted.get() >= 3 }
            check(!error.get(), "Real camera/effects frames read back without conversion errors")
            val frame = latest.get(); check(frame != null && frame.stride < frame.height && frame.rotation == 0, "Actual camera frame is upright portrait")
            check(frame.buf.size == frame.stride * frame.height * 4, "Actual camera RGBA frame has complete packed rows")
            check(controller.pushed.get() == 0L, "Recording test consumer performs no network push")
            home = device.startActivitySync(Intent(device.targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitUntil(5000) { sink.releases.get() > 0 }
            check(controller.state() == AgoraController.State.IDLE, "Backgrounding preserves disconnected state")
            home?.let { device.runOnMainSync(it::finish) }; home = null
            val before = sink.accepted.get(); waitUntil(12000) { sink.accepted.get() > before + 2 }; check(!error.get(), "Resumed camera recreates readback resources")
        } finally { home?.let { device.runOnMainSync(it::finish) }; device.runOnMainSync(activity::finish) }
    }
    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { if (SystemClock.elapsedRealtime() > end) throw AssertionError("Agora camera/readback timed out"); SystemClock.sleep(50) }; checks++
    }
    companion object {
        @JvmStatic fun run(device: Instrumentation): Int = AgoraDeviceChecks(device).apply { configuration(); camera() }.checks
    }
}
