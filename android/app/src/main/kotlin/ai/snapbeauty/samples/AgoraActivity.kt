package ai.snapbeauty.samples

import android.os.Bundle
import android.text.InputType
import kotlin.random.Random

class AgoraActivity : RawRtcActivity() {
    private lateinit var controller: AgoraController
    private lateinit var sink: AgoraFrameSink
    override val destination = "频道"
    override fun sceneTitle() = "声网 Agora"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = AgoraController(this, remote, object : AgoraController.Listener {
            override fun state(state: AgoraController.State, message: String) = sessionState(state == AgoraController.State.IDLE, state == AgoraController.State.JOINING, state == AgoraController.State.LEAVING, message)
            override fun remote(visible: Boolean) = showRemote(visible)
        })
        sink = AgoraFrameSink(controller); setFrameSink(sink)
    }
    override fun sessionIdle() = !::controller.isInitialized || controller.state() == AgoraController.State.IDLE
    override fun setMicrophone(enabled: Boolean) { if (::controller.isInitialized) controller.microphone(enabled) }
    override fun closeSession(message: String) { if (::controller.isInitialized) controller.close(message) }
    override fun showConnection() {
        val prefs = getPreferences(MODE_PRIVATE)
        connectionForm("声网频道配置", listOf(
            ConnectionField("App ID", prefs.getString("agoraAppId", "").orEmpty()),
            ConnectionField("频道名", prefs.getString("agoraChannel", "beauty-demo").orEmpty()),
            ConnectionField("UID", prefs.getString("agoraUid", Random.nextInt(1000, 901000).toString()).orEmpty(), InputType.TYPE_CLASS_NUMBER),
            ConnectionField("Token（当前频道和 UID）", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        ), "两端使用相同 App ID 和频道名、不同 UID，以及各自对应的 Token。") { values ->
            val config = AgoraRoomConfig(values[0], values[1], values[2], values[3])
            prefs.edit().putString("agoraAppId", config.appId).putString("agoraChannel", config.channel).putString("agoraUid", config.uid.toString()).apply()
            controller.join(config, microphone.isChecked)
        }
    }
}
