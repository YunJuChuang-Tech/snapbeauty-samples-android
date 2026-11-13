package ai.snapbeauty.samples

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.constants.ZegoVideoFrameFormat
import im.zego.zegoexpress.entity.ZegoVideoFrameParam
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Actual camera conversion with a recording consumer; no authenticated room is joined. */
class ZegoDeviceChecks private constructor(private val device: Instrumentation) {
    private var checks = 0
    private fun check(success: Boolean, message: String) { if (!success) throw AssertionError(message); checks++ }
    private fun configuration() {
        check(ZegoExpressEngine.getVersion().startsWith("3.25.0"), "Packaged ZEGO native libraries load without joining a room")
        val config = ZegoRoomConfig(" 4294967295 ", "test_room", " user_a ", " placeholder-only ")
        check(config.appId == 0xffffffffL && config.room == "test_room" && config.userId == "user_a", "ZEGO config normalizes fields and supports unsigned AppID")
        check(config.streamId == "beauty_test_room_user_a", "ZEGO stream ID is stable for room and user")
        check(!config.toString().contains("placeholder-only"), "Config diagnostics exclude Token")
        listOf(arrayOf("0", "r", "u", "x"), arrayOf("4294967296", "r", "u", "x"), arrayOf("1", "bad room", "u", "x"), arrayOf("1", "r", "bad user", "x"), arrayOf("1", "r", "u", "")).forEach { args ->
            var rejected = false
            try { ZegoRoomConfig(args[0], args[1], args[2], args[3]) } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected, "Invalid ZEGO config rejected before SDK initialization")
        }
        val params = ZegoFrameSink.parameters(3, 5)
        check(params.format == ZegoVideoFrameFormat.RGBA32 && params.width == 3 && params.height == 5, "ZEGO RGBA dimensions preserved")
        check(params.strides[0] == 12 && params.rotation == 0, "ZEGO stride is bytes with upright rotation")
    }
    private fun camera() {
        val activity = device.startActivitySync(Intent(device.targetContext, ZegoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ZegoActivity
        val controller = ZegoActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }.get(activity) as ZegoController
        val error = AtomicBoolean(); val publishing = AtomicBoolean(false); val received = AtomicLong()
        val sink = ZegoFrameSink(object : ZegoFrameSink.Target {
            override fun publishing() = publishing.get()
            override fun push(data: ByteBuffer, params: ZegoVideoFrameParam, timestampMs: Long) {
                if (!data.isDirect || data.remaining() != params.width * params.height * 4 || params.strides[0] != params.width * 4 || params.width >= params.height || params.rotation != 0 || kotlin.math.abs(timestampMs - System.currentTimeMillis()) > 5000) error.set(true)
                received.incrementAndGet()
            }
            override fun frameError() { error.set(true) }
        })
        var home: Activity? = null
        try {
            check(controller.state() == ZegoController.State.IDLE && controller.pushed.get() == 0L, "ZEGO local preview requires no room")
            device.runOnMainSync { activity.setFrameSink(sink) }
            SystemClock.sleep(400); check(sink.captured.get() == 0L, "Custom capture waits for publishing request")
            publishing.set(true); waitUntil(15000) { received.get() >= 3 }
            check(!error.get(), "Actual camera converts to direct, complete upright RGBA frames")
            check(sink.submitted.get() >= 3 && controller.pushed.get() == 0L, "Recording consumer performs no network push")
            home = device.startActivitySync(Intent(device.targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitUntil(5000) { sink.releases.get() > 0 }
            check(controller.state() == ZegoController.State.IDLE, "Backgrounding keeps ZEGO disconnected")
            home?.let { device.runOnMainSync(it::finish) }; home = null
            val before = received.get(); waitUntil(12000) { received.get() > before + 2 }; check(!error.get(), "Resumed camera recreates ZEGO readback resources")
        } finally { home?.let { device.runOnMainSync(it::finish) }; device.runOnMainSync(activity::finish) }
    }
    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { if (SystemClock.elapsedRealtime() > end) throw AssertionError("ZEGO camera/readback timed out"); SystemClock.sleep(50) }; checks++
    }
    companion object { @JvmStatic fun run(device: Instrumentation): Int = ZegoDeviceChecks(device).apply { configuration(); camera() }.checks }
}
