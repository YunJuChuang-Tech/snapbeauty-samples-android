package ai.snapbeauty.samples

import android.os.Bundle
import android.text.InputType
import java.util.UUID

class ZegoActivity : RawRtcActivity() {
    private lateinit var controller: ZegoController
    private lateinit var sink: ZegoFrameSink
    override val destination = "房间"
    override fun sceneTitle() = "即构 ZEGO"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ZegoController(this, remote, object : ZegoController.Listener {
            override fun state(state: ZegoController.State, message: String) = sessionState(state == ZegoController.State.IDLE, state == ZegoController.State.JOINING, state == ZegoController.State.LEAVING, message)
            override fun remote(visible: Boolean) = showRemote(visible)
        })
        sink = ZegoFrameSink(controller); setFrameSink(sink)
    }
    override fun sessionIdle() = !::controller.isInitialized || controller.state() == ZegoController.State.IDLE
    override fun setMicrophone(enabled: Boolean) { if (::controller.isInitialized) controller.microphone(enabled) }
    override fun closeSession(message: String) { if (::controller.isInitialized) controller.close(message) }
    override fun showConnection() {
        val prefs = getPreferences(MODE_PRIVATE)
        connectionForm("即构房间配置", listOf(
            ConnectionField("AppID", prefs.getString("zegoAppId", "").orEmpty(), InputType.TYPE_CLASS_NUMBER),
            ConnectionField("房间名", prefs.getString("zegoChannel", "beauty-demo").orEmpty()),
            ConnectionField("用户 ID", prefs.getString("zegoUid", "user_${UUID.randomUUID().toString().take(6)}").orEmpty()),
            ConnectionField("Token（当前房间和用户 ID）", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        ), "两端使用相同 AppID 和房间名、不同用户 ID，以及各自对应的 Token。") { values ->
            val config = ZegoRoomConfig(values[0], values[1], values[2], values[3])
            prefs.edit().putString("zegoAppId", config.appId.toString()).putString("zegoChannel", config.room).putString("zegoUid", config.userId).apply()
            controller.join(config, microphone.isChecked)
        }
    }
}
