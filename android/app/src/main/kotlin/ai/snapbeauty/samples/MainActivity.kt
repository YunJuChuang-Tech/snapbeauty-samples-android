package ai.snapbeauty.samples

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

open class MainActivity : SampleActivity(), PreviewRenderer.Listener {
    private lateinit var gl: GLSurfaceView
    private lateinit var renderer: PreviewRenderer
    private var surface: SurfaceTexture? = null
    private var camera: CameraSource? = null
    private lateinit var status: TextView
    private lateinit var compareLabel: TextView
    private lateinit var errorTitle: TextView
    private lateinit var errorDetail: TextView
    private lateinit var errorBox: LinearLayout
    private lateinit var retry: Button
    private lateinit var effectsPanel: EffectPanel
    private lateinit var effectSheet: CollapsiblePanel
    private lateinit var femaleBtn: TextView
    private lateinit var maleBtn: TextView
    private var initialState = EffectState.defaults()
    @Volatile private var cameraEpoch = 0
    @Volatile private var active = false
    private var front = true
    private var glReady = false
    private var available = false
    private var hasError = false
    private var askedPermission = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialState = EffectState.restore(savedInstanceState, ProcessorFactory.resources(this).defaults())
        front = savedInstanceState?.getBoolean("front", true) ?: true
        askedPermission = getPreferences(MODE_PRIVATE).getBoolean("askedPermission", false)
        buildUi()
        effectsPanel.restoreUi(savedInstanceState)
        updateGenderToggle()
        effectSheet.collapse(savedInstanceState?.getBoolean("effectPanelCollapsed", false) ?: false, animated = false)
    }
    private fun updateGenderToggle() {
        val male = effectsPanel.malePreset
        femaleBtn.setTextColor(if (!male) BACKGROUND else TEXT)
        femaleBtn.setTypeface(null, if (!male) Typeface.BOLD else Typeface.NORMAL)
        femaleBtn.background = if (!male) round(ACCENT, 14) else null
        maleBtn.setTextColor(if (male) BACKGROUND else TEXT)
        maleBtn.setTypeface(null, if (male) Typeface.BOLD else Typeface.NORMAL)
        maleBtn.background = if (male) round(ACCENT, 14) else null
    }
    private fun buildUi() {
        val root = column().apply { setBackgroundColor(BACKGROUND) }
        applyInsets(root); setContentView(root)
        val header = row().apply { setPadding(dp(16), dp(6), dp(14), dp(6)); gravity = Gravity.CENTER_VERTICAL }
        header.addView(ActionIcon(this, "mark"), LinearLayout.LayoutParams(dp(28), dp(28)))
        val title = column().apply { setPadding(dp(8), 0, 0, 0) }
        title.addView(text("SNAPBEAUTY", 12).apply { setTypeface(null, Typeface.BOLD); letterSpacing = .11f })
        title.addView(text("SAMPLES  /  ${sceneTitle()}", 9, MUTED))
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("01", 11, ACCENT).apply { typeface = Typeface.MONOSPACE }); root.addView(header)
        val preview = FrameLayout(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, 0, 1f))
        gl = GLSurfaceView(this).apply { setEGLContextClientVersion(3); preserveEGLContextOnPause = false }
        renderer = PreviewRenderer(this, gl, this); gl.setRenderer(renderer); gl.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var tapX = 0f; var tapY = 0f; var tapTime = 0L
        gl.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { tapX = ev.x; tapY = ev.y; tapTime = ev.eventTime }
                MotionEvent.ACTION_UP -> if (ev.eventTime - tapTime < 300 && kotlin.math.hypot((ev.x - tapX).toDouble(), (ev.y - tapY).toDouble()) < slop) {
                    effectSheet.collapse(!effectSheet.collapsed)
                }
            }
            true
        }
        preview.addView(gl, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        val topBar = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), 0) }
        status = text("正在准备相机…", 10).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6)); background = round(0x88101311.toInt(), 16); maxLines = 1
        }
        topBar.addView(status, LinearLayout.LayoutParams(-2, -2))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        val genderCapsule = row().apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(3), dp(3), dp(3), dp(3)); background = round(0x99101311.toInt(), 16)
        }
        femaleBtn = text("女生", 11).apply {
            gravity = Gravity.CENTER; setPadding(dp(11), dp(4), dp(11), dp(4)); contentDescription = "切换女生清透妆预设"
            setOnClickListener { effectsPanel.applyGenderPreset(false); updateGenderToggle(); stopCompare() }
        }
        maleBtn = text("男生", 11).apply {
            gravity = Gravity.CENTER; setPadding(dp(11), dp(4), dp(11), dp(4)); contentDescription = "切换男生原生感预设"
            setOnClickListener { effectsPanel.applyGenderPreset(true); updateGenderToggle(); stopCompare() }
        }
        genderCapsule.addView(femaleBtn); genderCapsule.addView(maleBtn)
        topBar.addView(genderCapsule, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        val switch = text("翻转", 11).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(6), dp(12), dp(6))
            contentDescription = "切换前后摄像头"; background = round(0x99101311.toInt(), 16)
            setOnClickListener { front = !front; startCamera() }
        }
        topBar.addView(switch, LinearLayout.LayoutParams(-2, -2))
        preview.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        val bottomOverlay = column().apply { clipChildren = false }
        val quickRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val reset = text("重置", 11, TEXT).apply {
            gravity = Gravity.CENTER; setPadding(dp(12), dp(5), dp(12), dp(5)); background = round(0x99101311.toInt(), 15)
            contentDescription = "重置全部效果"
            setOnClickListener {
                effectsPanel.reset(); stopCompare()
                Toast.makeText(this@MainActivity, "已恢复默认效果", Toast.LENGTH_SHORT).show()
            }
        }
        quickRow.addView(reset, LinearLayout.LayoutParams(-2, dp(28)))
        val compare = row().apply { gravity = Gravity.CENTER; setPadding(dp(12), 0, dp(12), 0); background = round(0xd9ddff9a.toInt(), 15); contentDescription = "按住查看原图，松开恢复效果" }
        compareLabel = text("按住对比原图", 11, BACKGROUND).apply { setTypeface(null, Typeface.BOLD) }; compare.addView(compareLabel)
        quickRow.addView(compare, LinearLayout.LayoutParams(-2, dp(28)).apply { leftMargin = dp(8) })
        compare.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { renderer.compare = true; compareLabel.text = "正在查看原图"; gl.requestRender() }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopCompare(); if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick() }
            }; true
        }
        compare.setOnClickListener { }
        compare.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    renderer.compare = !renderer.compare; compareLabel.text = if (renderer.compare) "点击恢复效果" else "按住对比原图"
                    gl.requestRender(); return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        bottomOverlay.addView(quickRow, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END; rightMargin = dp(12); bottomMargin = dp(6) })
        effectSheet = CollapsiblePanel(this) { stopCompare() }; bottomOverlay.addView(effectSheet, LinearLayout.LayoutParams(-1, -2))
        val panel = effectSheet.content
        effectsPanel = EffectPanel(this, initialState, ProcessorFactory.resources(this)) { sendState() }
        effectsPanel.setAvailable(false); panel.addView(effectsPanel)
        updateGenderToggle()
        preview.addView(bottomOverlay, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        errorBox = column().apply { gravity = Gravity.CENTER; setPadding(dp(28), dp(20), dp(28), dp(20)); setBackgroundColor(0xef101311.toInt()) }
        errorTitle = text("让美好进入画面", 23).apply { gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) }; errorBox.addView(errorTitle)
        errorDetail = text(permissionDescription(), 13, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(16)) }; errorBox.addView(errorDetail)
        retry = button("开启相机", true).apply { setOnClickListener { retry() } }
        errorBox.addView(retry, LinearLayout.LayoutParams(dp(180), dp(48)))
        preview.addView(errorBox, FrameLayout.LayoutParams(-1, -1)); errorBox.visibility = View.GONE
        decoratePreview(preview); appendSceneControls(root)
    }
    protected open fun sceneTitle() = "相机体验"
    protected open fun permissionDescription() = "仅在预览时使用相机，不录音、不上传画面。"
    protected open fun decoratePreview(preview: FrameLayout) = Unit
    protected open fun appendSceneControls(root: LinearLayout) = Unit
    internal fun setFrameSink(sink: PreviewRenderer.FrameSink) { renderer.sink = sink }
    private fun sendState() {
        val state = effectsPanel.state
        gl.queueEvent { renderer.setEffects(state) }; gl.requestRender()
    }
    private fun stopCompare() { renderer.compare = false; compareLabel.text = "按住对比原图"; gl.requestRender() }
    override fun surfaceReady(texture: SurfaceTexture) = runOnUiThread {
        if (active && renderer.isCurrentSurface(texture)) { surface = texture; glReady = true; startCamera() }
    }
    override fun status(message: String, effectsAvailable: Boolean) = runOnUiThread {
        if (active) { if (available != effectsAvailable) { available = effectsAvailable; effectsPanel.setAvailable(available) }; if (!hasError) status.text = message }
    }
    override fun error(message: String) = runOnUiThread { if (active) showError("暂时无法预览", message, "重试") }
    private fun startCamera() {
        val texture = surface ?: return
        if (!active) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { showPermission(); return }
        camera?.stop(); camera = null
        hasError = false; errorBox.visibility = View.GONE; status.text = "正在连接相机…"
        val epoch = ++cameraEpoch
        val source = CameraSource(this, object : CameraSource.Listener {
            override fun configured(width: Int, height: Int, rotation: Int, mirror: Boolean) {
                gl.queueEvent { if (active && epoch == cameraEpoch) renderer.configure(width, height, rotation, mirror) }
            }
            override fun error(message: String) = runOnUiThread { if (active && epoch == cameraEpoch) showError("暂时无法预览", message, "重试") }
        }).also { camera = it }
        val degrees = when (windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
        source.start(texture, front, degrees)
    }
    private fun showError(title: String, detail: String, action: String) {
        hasError = true; errorTitle.text = title; errorDetail.text = detail; retry.text = action; errorBox.visibility = View.VISIBLE
    }
    private fun showPermission() = showError("让美好进入画面", permissionDescription(), "开启相机")
    private fun retry() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (askedPermission && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            else { askedPermission = true; getPreferences(MODE_PRIVATE).edit().putBoolean("askedPermission", true).apply(); requestPermissions(arrayOf(Manifest.permission.CAMERA), 10) }
        } else if (hasError && glReady) { stopPreview(); gl.onPause(); gl.onResume() } else startCamera()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCamera()
            else showError("相机权限尚未开启", "允许相机权限后即可体验本地预览。", "重新开启")
        }
    }
    override fun onResume() {
        super.onResume(); active = true; gl.onResume(); sendState()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) showPermission()
    }
    override fun onPause() { active = false; stopCompare(); stopPreview(); gl.onPause(); super.onPause() }
    private fun stopPreview() {
        cameraEpoch++; camera?.stop(); camera = null; surface = null
        if (glReady || renderer.hasResources()) {
            val done = CountDownLatch(1)
            gl.queueEvent { try { renderer.dispose() } finally { done.countDown() } }
            try { done.await(2, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        glReady = false
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); effectsPanel.state.save(outState); effectsPanel.saveUi(outState); outState.putBoolean("front", front)
        outState.putBoolean("effectPanelCollapsed", effectSheet.collapsed)
    }
    private fun action(icon: String, label: String) = column().apply {
        gravity = Gravity.CENTER; isClickable = true; isFocusable = true
        addView(ActionIcon(this@MainActivity, icon), LinearLayout.LayoutParams(dp(26), dp(26)))
        addView(text(label, 10).apply { gravity = Gravity.CENTER })
    }
}
