package ai.snapbeauty.samples

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

class TrtcDeviceChecks private constructor(private val device: Instrumentation) {
    private var checks = 0
    private fun check(success: Boolean, message: String) { if (!success) throw AssertionError(message); checks++ }
    private fun configuration() {
        val valid = TrtcRoomConfig(" 1 ", "1001", " user_a ", " placeholder-only ")
        check(valid.appId == 1 && valid.roomId == 1001 && valid.userId == "user_a", "Room fields normalize consistently")
        check(!valid.toString().contains("placeholder-only"), "Config string does not expose UserSig")
        val invalid = listOf(arrayOf("0", "1001", "user", "x"), arrayOf("1", "0", "user", "x"), arrayOf("2147483648", "1", "user", "x"), arrayOf("1", "1001", "invalid user", "x"), arrayOf("1", "1001", "user", ""))
        invalid.forEach { args ->
            var rejected = false
            try { TrtcRoomConfig(args[0], args[1], args[2], args[3]) } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected, "Invalid room configuration rejected before networking")
        }
    }
    private fun cameraLifecycle() {
        val activity = device.startActivitySync(Intent(device.targetContext, TrtcActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val controller = TrtcActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }.get(activity) as TrtcController
        var home: Activity? = null
        try {
            waitUntil(15000) { controller.beauty.frames.get() >= 3 }
            device.checkedMain {
                check(controller.state() == TrtcController.State.PREVIEW, "Entering TRTC starts local preview without a button click or room join")
                val mic = TrtcActivity::class.java.getDeclaredField("mic").apply { isAccessible = true }.get(activity) as android.widget.Switch
                check(!mic.isChecked, "Automatic local preview keeps the microphone off")
            }
            check(controller.beauty.contexts.get() >= 1, "TRTC creates its real processing context")
            check(controller.beauty.status.startsWith("实时美颜"), "Real TRTC frames traverse the beauty pipeline")
            val before = controller.beauty.frames.get(); main { controller.beauty.original = true }; waitUntil(5000) { controller.beauty.frames.get() > before + 2 }
            check(controller.beauty.status == "正在查看原图", "Original comparison stays live in TRTC")
            main { controller.beauty.original = false; controller.beauty.effects = EffectState(0f, .8f, 0f, 0f, 0f, 0f, null) }
            val switched = controller.beauty.frames.get(); main(controller::switchCamera); waitUntil(7000) { controller.beauty.frames.get() > switched + 5 }
            check(controller.beauty.status.startsWith("实时美颜"), "TRTC camera switch continues processing")
            main { controller.close("test stopped") }
            try { waitUntil(7000) { idle(controller) && controller.beauty.releases.get() == controller.beauty.contexts.get() } }
            catch (error: AssertionError) { throw AssertionError("Stop: contexts=${controller.beauty.contexts.get()}, releases=${controller.beauty.releases.get()}, frames=${controller.beauty.frames.get()}, idle=${idle(controller)}", error) }
            check(controller.beauty.releases.get() > 0, "Stopping releases beauty on its owning callback thread")
            val contexts = controller.beauty.contexts.get(); val frames = controller.beauty.frames.get()
            main(controller::preview); waitUntil(12000) { controller.beauty.frames.get() > frames + 3 }
            check(controller.beauty.contexts.get() > contexts, "Preview can restart with a fresh GL context")
            home = device.startActivitySync(Intent(device.targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil(7000) { idle(controller) && controller.beauty.releases.get() == controller.beauty.contexts.get() }
            check(idle(controller), "Backgrounding stops RTC camera without auto-rejoin")
        } finally { home?.let { main(it::finish) }; main { controller.close("test finished"); activity.finish() } }
    }
    private fun idle(controller: TrtcController): Boolean {
        val state = AtomicReference<TrtcController.State>(); main { state.set(controller.state()) }; return state.get() == TrtcController.State.IDLE
    }
    private fun main(action: Runnable) = device.runOnMainSync(action)
    private fun waitUntil(timeout: Long, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) { if (SystemClock.elapsedRealtime() > end) throw AssertionError("RTC lifecycle timed out"); SystemClock.sleep(50) }; checks++
    }
    companion object { @JvmStatic fun run(device: Instrumentation): Int = TrtcDeviceChecks(device).apply { configuration(); cameraLifecycle() }.checks }
}
