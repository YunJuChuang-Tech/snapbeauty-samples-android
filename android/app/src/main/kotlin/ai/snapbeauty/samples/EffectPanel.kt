package ai.snapbeauty.samples

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Complete shared controls for local camera and all RTC scenes. */
class EffectPanel(context: Context, initial: EffectState, private val effectResources: EffectResources, private val changed: (EffectState) -> Unit) : LinearLayout(context) {
    var state = initial; private set
    private var group = 0
    private val selected = intArrayOf(0, 0, 0, 0)
    private var available = true
    private val tabs = LinearLayout(context)
    private val options = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    // Horizontal strip: the sheet owns vertical gestures, so options never compete with collapse dragging.
    private val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(options) }
    private val label = TextView(context)
    private val amount = TextView(context)
    private val slider = SeekBar(context)
    private val restore = TextView(context)
    private val hint = TextView(context)
    private val settings = Button(context)
    private var updating = false
    init {
        orientation = VERTICAL
        selected[2] = effectResources.styles.indexOfFirst { it.key == state.style }.coerceAtLeast(0)
        selected[3] = effectResources.filters.indexOfFirst { it.key == state.filter }.coerceAtLeast(0)
        listOf("美肤", "美型", "妆容", "滤镜").forEachIndexed { index, name ->
            tabs.addView(text(name, 15).apply { gravity = Gravity.CENTER; setOnClickListener { if (group != index) { group = index; render(); revealSelected(false) } } }, LayoutParams(0, dp(44), 1f))
        }
        addView(tabs); addView(scroll, LayoutParams(-1, dp(48)))
        val values = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        label.setTextColor(TEXT); label.textSize = 12f; values.addView(label, LayoutParams(0, dp(26), 1f))
        restore.text = "恢复默认"; restore.textSize = 11f; restore.setTextColor(MUTED); restore.gravity = Gravity.CENTER; restore.setPadding(dp(10), 0, dp(10), 0)
        restore.setOnClickListener { state = state.withValue(parameterKey(), effectResources.defaults().value(parameterKey())); changed(state); keepScroll() }
        values.addView(restore, LayoutParams(-2, dp(32)))
        amount.setTextColor(ACCENT); amount.textSize = 13f; amount.typeface = Typeface.MONOSPACE; amount.gravity = Gravity.END; values.addView(amount, LayoutParams(dp(36), -2)); addView(values)
        slider.max = 100; slider.progressTintList = ColorStateList.valueOf(ACCENT); slider.thumbTintList = ColorStateList.valueOf(ACCENT)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (updating || !fromUser) return
                state = state.withValue(parameterKey(), progress / 100f); amount.text = display(progress); changed(state)
            }
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            // Refresh adjusted markers once per gesture instead of rebuilding options on every frame.
            override fun onStopTrackingTouch(seek: SeekBar) = keepScroll()
        }); addView(slider, LayoutParams(-1, dp(40)))
        val bottom = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        hint.setTextColor(MUTED); hint.textSize = 10f; bottom.addView(hint, LayoutParams(0, dp(38), 1f))
        settings.text = "人脸设置"; settings.textSize = 10f; settings.isAllCaps = false; settings.setTextColor(ACCENT)
        settings.background = rounded(0xff2b332d.toInt()); settings.contentDescription = "多人脸与处理预算设置"
        settings.setOnClickListener { trackingSettings() }; bottom.addView(settings, LayoutParams(dp(82), dp(32))); addView(bottom); render()
    }
    fun setAvailable(value: Boolean) { available = value; render() }
    fun reset() {
        state = effectResources.defaults(); selected.fill(0)
        selected[2] = effectResources.styles.indexOfFirst { it.key == state.style }.coerceAtLeast(0)
        changed(state); render(); revealSelected(false)
    }
    fun saveUi(out: Bundle) { out.putInt("effectTab", group); out.putIntArray("effectSelected", selected); out.putInt("effectScroll", scroll.scrollX) }
    fun restoreUi(saved: Bundle?) {
        group = saved?.getInt("effectTab", 0)?.coerceIn(0, 3) ?: 0
        saved?.getIntArray("effectSelected")?.takeIf { it.size == 4 }?.copyInto(selected)
        selected[0] = selected[0].coerceIn(0, EffectCatalog.skin.lastIndex); selected[1] = selected[1].coerceIn(0, EffectCatalog.shape.lastIndex)
        selected[2] = effectResources.styles.indexOfFirst { it.key == state.style }.coerceAtLeast(0)
        selected[3] = effectResources.filters.indexOfFirst { it.key == state.filter }.coerceAtLeast(0)
        render(); scroll.post { scroll.scrollTo(saved?.getInt("effectScroll", 0) ?: 0, 0) }
    }
    private fun keepScroll() { val position = scroll.scrollX; render(); scroll.post { scroll.scrollTo(position, 0) } }
    /** Keeps the picked option on screen after tab switches, so the slider never edits something invisible. */
    private fun revealSelected(smooth: Boolean) = scroll.post {
        val chip = options.getChildAt(selected[group]) ?: return@post
        val x = (chip.left + chip.width / 2 - scroll.width / 2).coerceAtLeast(0)
        if (smooth) scroll.smoothScrollTo(x, 0) else scroll.scrollTo(x, 0)
    }
    private fun adjusted(key: String?) = key != null && abs(state.value(key) - effectResources.defaults().value(key)) > .005f
    private fun bidirectional(key: String) = EffectCatalog.parameters.firstOrNull { it.key == key }?.neutral == .5f
    private fun display(progress: Int) = if (!bidirectional(parameterKey())) "$progress" else (progress - 50).let { if (it > 0) "+$it" else "$it" }
    private fun choices() = when (group) { 0 -> EffectCatalog.skin.map { EffectChoice(it.key, it.name) }; 1 -> EffectCatalog.shape.map { EffectChoice(it.key, it.name) }; 2 -> effectResources.styles; else -> effectResources.filters }
    private fun parameterKey() = when (group) { 0 -> EffectCatalog.skin[selected[0]].key; 1 -> EffectCatalog.shape[selected[1]].key; 2 -> "makeup"; else -> "filterIntensity" }
    private fun render() {
        selected[3] = effectResources.filters.indexOfFirst { it.key == state.filter }.coerceAtLeast(0)
        for (i in 0 until tabs.childCount) (tabs.getChildAt(i) as TextView).apply { setTextColor(if (group == i) ACCENT else MUTED); setTypeface(null, if (group == i) Typeface.BOLD else Typeface.NORMAL); alpha = if (available) 1f else .4f }
        options.removeAllViews()
        val choices = choices()
        choices.forEachIndexed { index, choice ->
            val picked = index == selected[group]
            val marked = group < 2 && adjusted(choice.key)
            val option = text(choice.name, 12).apply {
                gravity = Gravity.CENTER; minWidth = dp(56); setPadding(dp(14), 0, dp(14), 0); setTextColor(if (picked) BACKGROUND else TEXT)
                background = rounded(if (picked) ACCENT else 0xff2b332d.toInt()).apply { if (marked && !picked) setStroke(dp(1), ACCENT) }
                contentDescription = if (marked) "${choice.name}，已调整" else choice.name
                setOnClickListener {
                    if (index == selected[group] && group < 2) return@setOnClickListener
                    selected[group] = index
                    if (group == 2) { state = state.selectStyle(choice); changed(state) }
                    if (group == 3) { state = state.withFilter(choice.key); changed(state) }
                    keepScroll(); revealSelected(true)
                }
            }
            options.addView(option, LayoutParams(-2, dp(36)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        val key = parameterKey()
        label.text = when (group) { 2 -> "妆容浓度"; 3 -> "滤镜"; else -> choices[selected[group]].name }
        slider.contentDescription = "${label.text}强度"; updating = true; slider.progress = (state.value(key) * 100).roundToInt(); updating = false; amount.text = display(slider.progress)
        slider.isEnabled = available && (group < 2 || choices.getOrNull(selected[group])?.key != null)
        scroll.visibility = if (available) View.VISIBLE else View.GONE
        listOf(slider, label, amount, settings).forEach { it.visibility = scroll.visibility }
        restore.visibility = if (available && group < 2 && adjusted(key)) View.VISIBLE else View.INVISIBLE
        restore.contentDescription = "${label.text}恢复默认"
        val count = EffectCatalog.parameters.count { adjusted(it.key) }
        hint.text = when {
            !available -> "当前版本仅预览相机。"
            group == 2 && effectResources.makeupIssue != null -> effectResources.makeupIssue
            group == 2 && state.style == null -> "选择一套妆容后可调浓度"
            group == 3 && state.filter == null -> "选择滤镜后可调强度"
            group == 2 && state.filterFollowsMakeup -> "已搭配 ${EffectCatalog.filterName(state.filter!!)} · 浓度联动"
            group == 3 && state.filterFollowsMakeup -> "随妆容浓度联动 · 手动调节后独立"
            bidirectional(key) -> "左右滑动选项 · 0 为原始比例 · 已调 $count 项"
            else -> "左右滑动选项 · 描边为已调整 · 已调 $count 项"
        }
    }
    private fun trackingSettings() {
        val form = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(22), dp(8), dp(22), 0) }
        form.addView(text("最多处理人脸", 13))
        val faces = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("1 人", "2 人", "3 人", "4 人")); setSelection(state.maxFaces - 1) }; form.addView(faces)
        form.addView(text("多人脸处理预算", 13))
        val budgets = floatArrayOf(16.67f, 33.33f, 66.67f, 0f)
        val budget = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, arrayOf("16.7 ms", "33.3 ms", "66.7 ms", "不限制"))
            setSelection(budgets.indices.minBy { kotlin.math.abs(budgets[it] - state.frameBudgetMs) })
        }; form.addView(budget)
        AlertDialog.Builder(context).setTitle("人脸设置").setView(form).setPositiveButton("应用") { _, _ -> state = state.withTracking(faces.selectedItemPosition + 1, budgets[budget.selectedItemPosition]); changed(state); render() }.setNegativeButton("取消", null).show()
    }
    private fun text(value: String, size: Int) = TextView(context).apply { text = value; textSize = size.toFloat(); setTextColor(TEXT) }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(18).toFloat() }
}
