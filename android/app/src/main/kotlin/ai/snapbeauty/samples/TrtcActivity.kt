package ai.snapbeauty.samples

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import com.tencent.rtmp.ui.TXCloudVideoView
import java.util.UUID
import kotlin.math.roundToInt

class TrtcActivity : SampleActivity() {
    private lateinit var controller: TrtcController
    private lateinit var roomStatus: TextView
    private lateinit var beautyStatus: TextView
    private lateinit var preview: Button
    private lateinit var join: Button
    private lateinit var leave: Button
    private lateinit var compare: Button
    private lateinit var flip: Button
    private lateinit var mic: Switch
    private lateinit var smooth: SeekBar
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var changingMic = false
    private var pendingCamera = 0
    private var previewOnEntry = true
    private var state = EffectState.defaults()
    private val refresh = object : Runnable {
        override fun run() {
            if (active) {
                beautyStatus.text = if (controller.state() == TrtcController.State.IDLE) "预览或加入房间后，即可体验效果" else controller.beauty.status
                main.postDelayed(this, 700)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); state = EffectState.restore(savedInstanceState, ProcessorFactory.resources(this).defaults()); buildUi() }
    private fun buildUi() {
        val root = column().apply { setBackgroundColor(BACKGROUND) }; applyInsets(root); setContentView(root)
        val header = row().apply { setPadding(dp(16), dp(8), dp(16), dp(8)); gravity = Gravity.CENTER_VERTICAL }
        header.addView(button("返回", false).apply { contentDescription = "返回场景选择"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(dp(64), dp(48)))
        header.addView(text("TRTC 视频通话", 18).apply { setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -2, 1f))
        flip = button("翻转", false).apply { contentDescription = "切换前后摄像头"; setOnClickListener { controller.switchCamera() } }
        header.addView(flip, LinearLayout.LayoutParams(dp(64), dp(48))); root.addView(header)
        val video = FrameLayout(this); root.addView(video, LinearLayout.LayoutParams(-1, 0, 1f))
        val local = TXCloudVideoView(this).apply { contentDescription = "本地视频画面" }; video.addView(local, FrameLayout.LayoutParams(-1, -1))
        val waiting = text("先预览，再开始通话。", 18, MUTED).apply { gravity = Gravity.CENTER }; video.addView(waiting, FrameLayout.LayoutParams(-1, -1))
        val remote = TXCloudVideoView(this).apply { setBackgroundColor(0xff263027.toInt()); contentDescription = "对方视频画面"; visibility = View.GONE }
        video.addView(remote, FrameLayout.LayoutParams(dp(112), dp(154), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(12), dp(12), 0) })
        beautyStatus = text("预览或加入房间后，即可体验效果", 11).apply { gravity = Gravity.CENTER; setPadding(dp(12), dp(8), dp(12), dp(8)); background = round(0xc9101311.toInt()) }
        video.addView(beautyStatus, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(12), 0, dp(12), dp(12)) })
        val panel = column().apply { setPadding(dp(20), dp(14), dp(20), dp(14)); background = round(PANEL) }; root.addView(panel)
        roomStatus = text("本地预览无需填写房间配置", 12).apply { setPadding(0, 0, 0, dp(10)) }; panel.addView(roomStatus)
        val actions = row()
        preview = button("仅预览", false).apply { setOnClickListener { cameraAction(1) } }
        join = button("加入房间", true).apply { setOnClickListener { cameraAction(2) } }
        leave = button("停止", false).apply { setOnClickListener { controller.close("已停止，可重新预览或加入房间") } }
        listOf(preview, join, leave).forEach { actions.addView(it, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) }) }; panel.addView(actions)
        val parameter = row().apply { gravity = Gravity.CENTER_VERTICAL }; parameter.addView(text("磨皮", 13))
        smooth = slider("磨皮强度", .35f) { setState(state.withValue("smooth", it)) }
        parameter.addView(smooth, LinearLayout.LayoutParams(0, dp(48), 1f))
        parameter.addView(button("更多效果", false).apply { setOnClickListener { showEffects() } }, LinearLayout.LayoutParams(dp(90), dp(48))); panel.addView(parameter)
        val audio = row().apply { gravity = Gravity.CENTER_VERTICAL }
        mic = Switch(this).apply { text = "麦克风"; textSize = 13f; setTextColor(TEXT); contentDescription = "麦克风，默认关闭" }
        audio.addView(mic, LinearLayout.LayoutParams(0, dp(48), 1f))
        mic.setOnCheckedChangeListener { _, enabled ->
            if (!changingMic) {
                if (enabled && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    setMic(false)
                    if (controller.state() in listOf(TrtcController.State.JOINING, TrtcController.State.JOINED)) Toast.makeText(this, "请退出房间后开启麦克风权限", Toast.LENGTH_SHORT).show()
                    else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 21)
                } else controller.microphone(enabled)
            }
        }
        compare = button("按住对比原图", true).apply { contentDescription = "按住查看原图，松开恢复效果" }
        audio.addView(compare, LinearLayout.LayoutParams(dp(172), dp(48))); panel.addView(audio)
        compare.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { controller.beauty.original = true; compare.text = "正在查看原图" }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopCompare(); if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick() }
            }; true
        }
        compare.setOnClickListener { }
        compare.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) { controller.beauty.original = !controller.beauty.original; compare.text = if (controller.beauty.original) "点击恢复效果" else "按住对比原图"; return true }
                return super.performAccessibilityAction(host, action, args)
            }
        }
        panel.addView(text("麦克风默认关闭 · 切到后台自动退出房间", 10, MUTED).apply { setPadding(0, dp(8), 0, 0) })
        controller = TrtcController(this, local, remote, object : TrtcController.Listener {
            override fun state(state: TrtcController.State, message: String) {
                roomStatus.text = message
                val idle = state == TrtcController.State.IDLE
                preview.isEnabled = idle; join.isEnabled = idle || state == TrtcController.State.PREVIEW
                leave.isEnabled = !idle && state != TrtcController.State.LEAVING; flip.isEnabled = leave.isEnabled
                listOf(preview, join, leave, flip).forEach { it.alpha = if (it.isEnabled) 1f else .4f }
                mic.isEnabled = state != TrtcController.State.JOINING && state != TrtcController.State.LEAVING
                waiting.visibility = if (idle) View.VISIBLE else View.GONE; if (idle) stopCompare()
            }
            override fun remote(available: Boolean) { remote.visibility = if (available) View.VISIBLE else View.GONE }
        })
        setState(state); leave.isEnabled = false; flip.isEnabled = false; leave.alpha = .4f; flip.alpha = .4f
    }
    private fun cameraAction(action: Int) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { if (action == 1) controller.preview() else showRoom(); return }
        pendingCamera = action
        val prefs = getPreferences(MODE_PRIVATE)
        if (prefs.getBoolean("cameraAsked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this).setTitle("开启相机权限").setMessage("允许使用相机后，即可预览或加入视频房间。")
                .setPositiveButton("去设置") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }.setNegativeButton("取消", null).show()
        } else { prefs.edit().putBoolean("cameraAsked", true).apply(); requestPermissions(arrayOf(Manifest.permission.CAMERA), 20) }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (requestCode == 20) { val action = pendingCamera; pendingCamera = 0; if (granted) cameraAction(action) else Toast.makeText(this, "相机权限未开启", Toast.LENGTH_SHORT).show() }
        if (requestCode == 21) { setMic(granted); controller.microphone(granted) }
    }
    private fun showRoom() {
        val prefs = getPreferences(MODE_PRIVATE)
        val form = column().apply { setPadding(dp(22), dp(4), dp(22), 0) }
        val app = field(form, "SDKAppID", prefs.getString("appId", "").orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val room = field(form, "房间号", prefs.getString("roomId", "1001").orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val user = field(form, "用户 ID", prefs.getString("userId", "user_${UUID.randomUUID().toString().take(6)}").orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val signature = field(form, "UserSig（对应当前用户）", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD).apply { if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS }
        form.addView(text("两端填写相同 AppID 和房间号，使用不同用户 ID 及各自的 UserSig。", 12, MUTED).apply { setPadding(0, dp(10), 0, 0) })
        val dialog = AlertDialog.Builder(this).setTitle("房间配置").setView(ScrollView(this).apply { addView(form) }).setPositiveButton("加入", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val config = TrtcRoomConfig(app.text.toString(), room.text.toString(), user.text.toString(), signature.text.toString())
                    prefs.edit().putString("appId", config.appId.toString()).putString("roomId", config.roomId.toString()).putString("userId", config.userId).apply()
                    signature.setText(""); dialog.dismiss(); controller.join(config, mic.isChecked)
                } catch (error: IllegalArgumentException) {
                    val message = error.message.orEmpty()
                    val invalid = when { message.startsWith("SDKAppID") -> app; message.startsWith("房间号") -> room; message.startsWith("用户 ID") -> user; else -> signature }
                    invalid.error = message; invalid.requestFocus()
                }
            }
        }; dialog.show()
    }
    private fun showEffects() {
        val panel = EffectPanel(this, state, ProcessorFactory.resources(this)) { setState(it) }
        panel.setPadding(dp(16), dp(8), dp(16), dp(8))
        AlertDialog.Builder(this).setTitle("美肤 · 美型 · 妆容 · 滤镜").setView(panel).setPositiveButton("完成", null)
            .setNeutralButton("恢复默认") { _, _ -> setState(ProcessorFactory.resources(this).defaults()); stopCompare() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); state.save(outState) }
    private fun slider(label: String, amount: Float, change: (Float) -> Unit) = SeekBar(this).apply {
        max = 100; progress = (amount * 100).roundToInt(); contentDescription = label
        progressTintList = ColorStateList.valueOf(ACCENT); thumbTintList = ColorStateList.valueOf(ACCENT)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) change(progress / 100f) }
            override fun onStartTrackingTouch(seek: SeekBar) = Unit
            override fun onStopTrackingTouch(seek: SeekBar) = Unit
        })
    }
    private fun setState(next: EffectState) { state = next; controller.beauty.effects = next; smooth.progress = (next.smooth * 100).roundToInt() }
    private fun setMic(value: Boolean) { changingMic = true; mic.isChecked = value; changingMic = false }
    private fun stopCompare() { controller.beauty.original = false; compare.text = "按住对比原图" }
    override fun onResume() {
        super.onResume(); active = true; main.post(refresh)
        if (previewOnEntry) { previewOnEntry = false; cameraAction(1) }
    }
    override fun onPause() { active = false; main.removeCallbacks(refresh); stopCompare(); controller.close("已停止，可重新预览或加入房间"); super.onPause() }
    override fun onDestroy() { controller.close("已停止"); super.onDestroy() }
}
