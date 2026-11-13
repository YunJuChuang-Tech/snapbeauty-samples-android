package ai.snapbeauty.samples

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.widget.*

/** Shared controls for RTC SDKs consuming our own camera's processed RGBA frames. */
abstract class RawRtcActivity : MainActivity() {
    protected lateinit var remote: SurfaceView
    protected lateinit var microphone: Switch
    private lateinit var channelStatus: TextView
    private lateinit var join: Button
    private lateinit var leave: Button
    private var changingMic = false
    protected abstract val destination: String
    protected abstract fun sessionIdle(): Boolean
    protected abstract fun setMicrophone(enabled: Boolean)
    protected abstract fun closeSession(message: String)
    protected abstract fun showConnection()
    protected fun sessionState(idle: Boolean, joining: Boolean, leaving: Boolean, message: String) {
        channelStatus.text = message; join.isEnabled = idle; leave.isEnabled = !idle && !leaving
        join.alpha = if (join.isEnabled) 1f else .4f; leave.alpha = if (leave.isEnabled) 1f else .4f
        microphone.isEnabled = !joining && !leaving
    }
    protected fun showRemote(visible: Boolean) { remote.visibility = if (visible) View.VISIBLE else View.GONE }
    override fun permissionDescription() = "本地预览不发送画面；加入${destination}后发送处理后的画面，麦克风可选。"
    override fun decoratePreview(preview: FrameLayout) {
        remote = SurfaceView(this).apply { setZOrderMediaOverlay(true); contentDescription = "远端视频画面"; visibility = View.GONE }
        preview.addView(remote, FrameLayout.LayoutParams(dp(108), dp(144), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(84), dp(12), 0) })
    }
    override fun appendSceneControls(root: LinearLayout) {
        val panel = column().apply { setPadding(dp(20), dp(8), dp(20), dp(10)); setBackgroundColor(BACKGROUND) }
        val status = row().apply { gravity = Gravity.CENTER_VERTICAL }
        channelStatus = text("本地预览，尚未加入$destination", 11); status.addView(channelStatus, LinearLayout.LayoutParams(0, -2, 1f))
        microphone = Switch(this).apply { text = "麦克风"; textSize = 12f; setTextColor(MUTED); contentDescription = "麦克风，默认关闭" }
        status.addView(microphone, LinearLayout.LayoutParams(-2, dp(40))); panel.addView(status)
        microphone.setOnCheckedChangeListener { _, enabled ->
            if (!changingMic) {
                if (enabled && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    setMic(false)
                    if (!sessionIdle()) Toast.makeText(this, "请退出${destination}后开启麦克风权限", Toast.LENGTH_SHORT).show()
                    else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 31)
                } else setMicrophone(enabled)
            }
        }
        val actions = row()
        join = button("加入$destination", true).apply {
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) Toast.makeText(this@RawRtcActivity, "请先开启相机权限", Toast.LENGTH_SHORT).show()
                else showConnection()
            }
        }
        leave = button("退出$destination", false).apply { isEnabled = false; alpha = .4f; setOnClickListener { closeSession("已退出${destination}，本地相机继续预览") } }
        actions.addView(join, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        actions.addView(leave, LinearLayout.LayoutParams(0, dp(44), 1f)); panel.addView(actions)
        panel.addView(text("只在加入${destination}后发送画面 · 切到后台自动退出", 10, MUTED).apply { setPadding(0, dp(7), 0, 0) })
        root.addView(panel)
    }
    protected data class ConnectionField(val label: String, val initial: String, val input: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
    protected fun connectionForm(title: String, fields: List<ConnectionField>, hint: String, submit: (List<String>) -> Unit) {
        val form = column().apply { setPadding(dp(22), dp(4), dp(22), 0) }
        val inputs = fields.map { field(form, it.label, it.initial, it.input) }
        if (android.os.Build.VERSION.SDK_INT >= 26) inputs.last().importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        form.addView(text(hint, 12, MUTED).apply { setPadding(0, dp(10), 0, 0) })
        val scroll = ScrollView(this).apply { addView(form) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("加入", null).setNegativeButton("取消", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try { submit(inputs.map { it.text.toString() }); inputs.last().setText(""); dialog.dismiss() }
                catch (error: IllegalArgumentException) {
                    val message = error.message.orEmpty()
                    val index = fields.indexOfFirst { message.startsWith(it.label) }.takeIf { it >= 0 } ?: inputs.lastIndex
                    inputs[index].error = message; inputs[index].requestFocus()
                }
            }
        }
        dialog.show()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 31) { val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED; setMic(granted); setMicrophone(granted) }
    }
    override fun onPause() { closeSession("已退出${destination}，可重新加入"); super.onPause() }
    override fun onDestroy() { closeSession("已停止"); super.onDestroy() }
    private fun setMic(value: Boolean) { changingMic = true; microphone.isChecked = value; changingMic = false }
}
