package ai.snapbeauty.samples

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.HorizontalScrollView
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File

object EffectDeviceChecks {
    @JvmStatic fun run(device: Instrumentation): Int {
        var checks = 0
        fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message); checks++ }
        check(EffectCatalog.skin.size == 11 && EffectCatalog.shape.size == 25, "Complete public parameter catalog")
        var snapshot = EffectState.defaults()
        EffectCatalog.parameters.forEachIndexed { i, p -> snapshot = snapshot.withValue(p.key, (i + 1) / 40f) }
        val resources = ProcessorFactory.resources(device.targetContext)
        snapshot = snapshot.withStyle(resources.styles.last().key).withFilter(resources.filters.last().key).withValue("makeup", .83f).withValue("filterIntensity", .67f).withTracking(4, 66.67f)
        val saved = Bundle(); snapshot.save(saved); val restored = EffectState.restore(saved)
        for (p in EffectCatalog.parameters) check(snapshot.value(p.key) == restored.value(p.key), "Bundle preserves ${p.key}")
        check(restored.style == snapshot.style && restored.filter == snapshot.filter && restored.makeup == .83f && restored.filterIntensity == .67f, "Bundle preserves makeup/filter and their independent amounts")
        check(restored.maxFaces == 4 && restored.frameBudgetMs == 66.67f, "Bundle preserves multi-face budget")
        check(snapshot.withValue("white", 2f).white == 1f && snapshot.withTracking(8, -1f).maxFaces == 4, "Public state clamps invalid limits")
        val activity = device.startActivitySync(Intent(device.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var recreated: MainActivity? = null
        try {
            device.checkedMain {
                val panel = panel(activity); panel.setAvailable(true)
                check(panel.state.style == resources.styles.first { it.key != null }.key, "First visit selects the first style")
                for ((tab, params) in listOf("美肤" to EffectCatalog.skin, "美型" to EffectCatalog.shape)) {
                    find(panel, tab)!!.performClick()
                    for (p in params) {
                        find(panel, p.name)!!.performClick()
                        val slider = findDescription(panel, "${p.name}强度") as SeekBar
                        val before = panel.state.value(p.key)
                        slider.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                        check(panel.state.value(p.key) > before, "Actual UI changes independent control: ${p.key}")
                    }
                }
                for ((tab, choices) in listOf("妆容" to resources.styles, "滤镜" to resources.filters)) {
                    find(panel, tab)!!.performClick()
                    choices.forEach { choice ->
                        find(panel, choice.name)!!.performClick()
                        check((if (tab == "妆容") panel.state.style else panel.state.filter) == choice.key, "UI selects public resource: ${choice.name}")
                        if (tab == "妆容" && choice.key != null) check(panel.state.filter == choice.makeupFilter && panel.state.filterFollowsMakeup, "Actual UI selects the style's LUT: ${choice.name}")
                    }
                }
                find(panel, "美型")!!.performClick(); find(panel, "下巴")!!.performClick()
                check(findDescription(panel, "下巴强度") != null, "Selected parameter exposes readable slider label")
            }
            fun clickText(value: String) {
                val end = SystemClock.elapsedRealtime() + 3000
                while (SystemClock.elapsedRealtime() < end) {
                    val node = device.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText(value)?.firstOrNull { it.text?.toString() == value }
                    if (node != null && (node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)) return
                    SystemClock.sleep(50)
                }
                throw AssertionError("Settings UI missing: $value")
            }
            device.checkedMain { find(panel(activity), "人脸设置")!!.performClick() }
            clickText("1 人"); clickText("4 人"); clickText("33.3 ms"); clickText("66.7 ms"); clickText("应用")
            device.waitForIdleSync()
            check(panel(activity).state.maxFaces == 4 && panel(activity).state.frameBudgetMs == 66.67f, "Actual settings dialog applies face limit and budget")
            SystemClock.sleep(2200) // Let the reset toast leave the compositor before evidence capture.
            val evidence = File(device.targetContext.filesDir, "panel-regression").apply { mkdirs() }
            for (tab in listOf("美肤", "美型", "妆容", "滤镜")) {
                device.checkedMain { find(panel(activity), tab)!!.performClick() }; device.waitForIdleSync()
                device.checkedMain { scroll(panel(activity))!!.let { it.scrollTo(it.getChildAt(0).width, 0) } }; device.waitForIdleSync()
                val last = when (tab) { "美肤" -> EffectCatalog.skin.last().name; "美型" -> EffectCatalog.shape.last().name; "妆容" -> resources.styles.last().name; else -> resources.filters.last().name }
                device.checkedMain { check(find(panel(activity), last)!!.getGlobalVisibleRect(android.graphics.Rect()), "Final option is reachable by scrolling: $tab") }
                SystemClock.sleep(250) // UI idle does not imply the latest frame has been presented.
                device.uiAutomation.takeScreenshot()?.let { bitmap -> File(evidence, "$tab.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            }
            device.checkedMain { find(panel(activity), "美型")!!.performClick(); find(panel(activity), "下巴")!!.performClick() }
            val before = panel(activity).state
            val monitor = device.addMonitor(MainActivity::class.java.name, null, false)
            device.checkedMain(activity::recreate)
            recreated = device.waitForMonitorWithTimeout(monitor, 5000) as? MainActivity; device.removeMonitor(monitor)
            check(recreated != null, "Full-panel activity recreates")
            device.checkedMain {
                val panel = panel(recreated!!)
                EffectCatalog.parameters.forEach { check(panel.state.value(it.key) == before.value(it.key), "Activity retains adjusted ${it.key}") }
                check(panel.state.style == before.style && panel.state.filter == before.filter, "Activity retains whole style/filter selection")
                check(panel.state.maxFaces == 4 && panel.state.frameBudgetMs == 66.67f, "Activity retains settings dialog changes")
                check(findDescription(panel, "下巴强度") != null, "Activity retains tab and selected control")
                panel.reset()
                val defaults = resources.defaults()
                EffectCatalog.parameters.forEach { check(panel.state.value(it.key) == defaults.value(it.key), "Reset restores ${it.key}, including 50-neutral") }
                check(panel.state.style == resources.styles.first { it.key != null }.key && panel.state.filter == defaults.filter && panel.state.filterFollowsMakeup && panel.state.maxFaces == 1, "Reset restores first paired style and face settings")
            }
        } finally { device.checkedMain { recreated?.finish(); activity.finish() } }
        return checks
    }
    private fun panel(activity: MainActivity) = MainActivity::class.java.getDeclaredField("effectsPanel").apply { isAccessible = true }.get(activity) as EffectPanel
    private fun scroll(root: View): HorizontalScrollView? {
        if (root is HorizontalScrollView) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) scroll(root.getChildAt(i))?.let { return it }
        return null
    }
    private fun find(root: View, text: String): View? {
        if (root is TextView && root.text.toString() == text) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) find(root.getChildAt(i), text)?.let { return it }
        return null
    }
    private fun findDescription(root: View, description: String): View? {
        if (root.contentDescription == description) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) findDescription(root.getChildAt(i), description)?.let { return it }
        return null
    }
}
