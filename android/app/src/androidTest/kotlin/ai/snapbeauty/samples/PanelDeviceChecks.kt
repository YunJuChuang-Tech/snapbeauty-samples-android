package ai.snapbeauty.samples

import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar

object PanelDeviceChecks {
    @JvmStatic fun run(device: Instrumentation): Int {
        var checks = 0
        fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message); checks++ }
        val activity = device.startActivitySync(Intent(device.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val sheet = MainActivity::class.java.getDeclaredField("effectSheet").apply { isAccessible = true }.get(activity) as CollapsiblePanel
        fun height(): Int { var h = 0; device.checkedMain { h = sheet.height }; return h }
        val gl = MainActivity::class.java.getDeclaredField("gl").apply { isAccessible = true }.get(activity) as View
        fun glHeight(): Int { var h = 0; device.checkedMain { h = gl.height }; return h }
        fun pause(ms: Long) { SystemClock.sleep(ms) }
        var downTime = 0L; var x = 0f; var y = 0f
        fun touch(action: Int, dy: Float = 0f, dx: Float = 0f, target: View = sheet.getChildAt(0)) {
            device.checkedMain {
                if (action == MotionEvent.ACTION_DOWN) {
                    downTime = SystemClock.uptimeMillis()
                    val pos = IntArray(2); target.getLocationOnScreen(pos)
                    x = pos[0] + target.width / 2f; y = pos[1] + target.height / 2f
                }
                val pos = IntArray(2); sheet.getLocationOnScreen(pos)
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x + dx, y + dy, 0)
                event.offsetLocation(-pos[0].toFloat(), -pos[1].toFloat())
                sheet.dispatchTouchEvent(event); event.recycle()
            }
        }
        try {
            device.waitForIdleSync(); pause(300)
            device.checkedMain { sheet.collapse(false, animated = false) }; pause(50)
            val expanded = height(); val surface = glHeight()
            device.checkedMain { sheet.getChildAt(0).performClick() }; pause(45)
            val mid = height()
            pause(320); val closed = height()
            check(mid in (closed + 1) until expanded, "Tap collapse presents intermediate animation frames")
            check(sheet.collapsed && closed < expanded, "Tap settles at collapsed height")
            check(glHeight() == surface, "Preview surface keeps its size while the panel moves")
            device.checkedMain { sheet.getChildAt(0).performClick() }; pause(45)
            check(height() in (closed + 1) until expanded, "Tap expansion presents intermediate animation frames")
            pause(320); check(height() == expanded && !sheet.collapsed, "Expand restores full measured content")
            val distance = (expanded - closed) * .35f
            touch(MotionEvent.ACTION_DOWN); pause(60); touch(MotionEvent.ACTION_MOVE, distance); pause(40)
            val dragged = height()
            check(dragged in (closed + 1) until expanded, "Panel follows a finger before ACTION_UP")
            pause(160); touch(MotionEvent.ACTION_CANCEL, distance); pause(320)
            check(height() == expanded && !sheet.collapsed, "Cancelled drag settles back to its prior state")
            touch(MotionEvent.ACTION_DOWN); pause(60); touch(MotionEvent.ACTION_MOVE, (expanded - closed) * .8f); pause(160)
            touch(MotionEvent.ACTION_UP, (expanded - closed) * .8f); pause(320)
            check(sheet.collapsed && height() == closed, "Downward drag settles closed")
            touch(MotionEvent.ACTION_DOWN); pause(60); touch(MotionEvent.ACTION_MOVE, -(expanded - closed) * .8f); pause(160)
            check(height() > closed, "Collapsed handle follows upward finger movement")
            touch(MotionEvent.ACTION_UP, -(expanded - closed) * .8f); pause(320)
            check(!sheet.collapsed && height() == expanded, "Upward drag settles open")
            val slider = findSlider(sheet)
            if (slider != null) {
                touch(MotionEvent.ACTION_DOWN, target = slider); touch(MotionEvent.ACTION_MOVE, 70f, 100f); pause(50)
                check(height() == expanded, "Slider gestures do not collapse the panel")
                touch(MotionEvent.ACTION_CANCEL, 70f, 100f)
            }
            touch(MotionEvent.ACTION_DOWN); touch(MotionEvent.ACTION_MOVE, 10f, 180f); pause(50)
            check(height() == expanded, "Horizontal gestures do not drag the panel")
            touch(MotionEvent.ACTION_CANCEL, 10f, 180f)
            device.checkedMain { sheet.collapse(true, animated = false) }; pause(50)
            check(height() == closed, "State restoration can collapse without playing an animation")
        } finally { device.checkedMain { activity.finish() } }
        return checks
    }
    private fun findSlider(view: View): SeekBar? {
        if (view is SeekBar) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findSlider(view.getChildAt(i))?.let { return it }
        return null
    }
}
