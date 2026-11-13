package ai.snapbeauty.samples

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Verify the installed app offers every scene and returns to the same home screen. */
object SceneDeviceChecks {
    @JvmStatic fun run(device: Instrumentation): Int {
        var checks = 0
        fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message); checks++ }
        val context = device.targetContext
        val launchers = context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
        check(launchers.size == 1 && launchers.single().activityInfo.name == HomeActivity::class.java.name, "Unified app has exactly one home launcher")
        val home = device.startActivitySync(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
        try {
            val scenes = listOf("01  相机美颜" to MainActivity::class.java, "02  腾讯云 TRTC" to TrtcActivity::class.java,
                "03  声网 Agora" to AgoraActivity::class.java, "04  即构 ZEGO" to ZegoActivity::class.java)
            for ((title, target) in scenes) {
                val monitor = device.addMonitor(target.name, null, false)
                try {
                    device.checkedMain {
                        val text = find(home.window.decorView, title)
                        check(text != null, "Home offers $title")
                        val card = text!!.parent as View
                        card.requestRectangleOnScreen(Rect(0, 0, card.width, card.height), true)
                    }
                    device.waitForIdleSync()
                    device.checkedMain {
                        val card = find(home.window.decorView, title)!!.parent as View
                        check(card.getGlobalVisibleRect(Rect()), "Scene card is reachable by scrolling: $title")
                        check(card.performClick(), "Scene entry handles a click: $title")
                    }
                    val activity = device.waitForMonitorWithTimeout(monitor, 5000)
                    check(activity != null && activity.javaClass == target && activity.packageName == context.packageName, "Scene opens inside the same application: $title")
                    device.waitForIdleSync(); device.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                    val end = SystemClock.elapsedRealtime() + 5000
                    var focused = false
                    while (!focused && SystemClock.elapsedRealtime() < end) {
                        device.checkedMain { focused = home.hasWindowFocus() }
                        if (!focused) SystemClock.sleep(50)
                    }
                    check(focused, "Back returns to the scene chooser: $title")
                } finally { device.removeMonitor(monitor) }
            }
        } finally { device.checkedMain(home::finish) }
        return checks
    }
    private fun find(view: View, title: String): TextView? {
        if (view is TextView && view.text.toString() == title) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i), title)?.let { return it }
        return null
    }
}
