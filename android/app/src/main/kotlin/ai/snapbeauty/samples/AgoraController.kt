package ai.snapbeauty.samples

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import io.agora.rtc2.*
import io.agora.rtc2.video.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class AgoraController(context: Context, private val remote: SurfaceView, private val listener: Listener) : AgoraFrameSink.Target {
    enum class State { IDLE, JOINING, JOINED, LEAVING }
    interface Listener { fun state(state: State, message: String); fun remote(visible: Boolean) }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val engineLock = Any()
    private val users = linkedSetOf<Int>()
    @Volatile private var state = State.IDLE
    @Volatile private var generation = 0
    private var engine: RtcEngine? = null
    private var shown: Int? = null
    private var mic = false
    private var timeout: Runnable? = null
    val pushed = AtomicLong()
    fun state() = state
    override fun publishing() = state == State.JOINED
    fun join(config: AgoraRoomConfig, microphone: Boolean) {
        if (state != State.IDLE) return
        mic = microphone; val token = ++generation; change(State.JOINING, "正在加入频道…")
        timeout = Runnable { if (token == generation && state == State.JOINING) close("加入超时，请检查网络或重新获取 Token") }.also { main.postDelayed(it, 20000) }
        lifecycle.execute {
            if (token != generation) return@execute
            try {
                check(owner == null || owner === this) { "Another Agora session is stopping" }
                val rtc = RtcEngineConfig().apply {
                    mContext = app; mAppId = config.appId; mChannelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                    mEventHandler = events(token); mAutoRegisterAgoraExtensions = false
                }
                synchronized(engineLock) {
                    val current = RtcEngine.create(rtc).also { engine = it; owner = this }
                    if (token != generation) return@synchronized
                    success(current.enableVideo()); success(current.enableLocalAudio(mic))
                    success(current.setExternalVideoSource(true, false, Constants.ExternalVideoSourceType.VIDEO_FRAME))
                    success(current.setVideoEncoderConfiguration(VideoEncoderConfiguration(480, 640,
                        VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15, 800, VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT)))
                    val options = ChannelMediaOptions().apply {
                        publishCameraTrack = false; publishCustomVideoTrack = true; customVideoTrackId = 0
                        publishMicrophoneTrack = mic; autoSubscribeAudio = true; autoSubscribeVideo = true
                    }
                    success(current.joinChannel(config.token, config.channel, config.uid, options))
                }
            } catch (error: Exception) { joinFailed(token) } catch (error: LinkageError) { joinFailed(token) }
        }
    }
    private fun joinFailed(token: Int) { main.post { if (token == generation) close("无法加入频道，请核对 App ID、频道、UID 和 Token") } }
    private fun events(token: Int) = object : IRtcEngineEventHandler() {
        private fun event(action: () -> Unit) { main.post { if (token == generation && state != State.LEAVING) action() } }
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) = event {
            timeout?.let(main::removeCallbacks); change(State.JOINED, "已加入频道，等待对方进入"); refreshRemote()
        }
        override fun onError(err: Int) = event { close("连接出现错误（$err），请检查配置后重试") }
        override fun onUserJoined(uid: Int, elapsed: Int) = event { users.add(uid); refreshRemote() }
        override fun onUserOffline(uid: Int, reason: Int) = event { users.remove(uid); refreshRemote() }
        override fun onTokenPrivilegeWillExpire(token: String?) = event { close("Token 即将过期，请获取新 Token 后重新加入") }
        override fun onRequestToken() = event { close("Token 已失效，请获取新 Token 后重新加入") }
        override fun onConnectionStateChanged(connection: Int, reason: Int) = event {
            when {
                connection == Constants.CONNECTION_STATE_FAILED -> close("连接失败，请检查网络和 Token 后重试")
                connection == Constants.CONNECTION_STATE_RECONNECTING && state == State.JOINED -> listener.state(state, "正在重连…")
                connection == Constants.CONNECTION_STATE_CONNECTED && state == State.JOINED -> listener.state(state, "连接已恢复")
            }
        }
    }
    override fun push(frame: AgoraVideoFrame): Int {
        val result = synchronized(engineLock) { if (state != State.JOINED) return -1; engine?.pushExternalVideoFrameById(frame, 0) ?: return -1 }
        if (result == 0) pushed.incrementAndGet() else {
            val token = generation; main.post { if (token == generation && state == State.JOINED) close("视频推送失败（$result），请退出后重试") }
        }
        return result
    }
    override fun frameError() {
        val token = generation; main.post { if (token == generation && state != State.IDLE) close("视频转换失败，已停止发送；本地相机仍可预览") }
    }
    fun microphone(value: Boolean) {
        mic = value; if (state != State.JOINED) return
        val token = generation
        lifecycle.execute {
            synchronized(engineLock) {
                val current = engine ?: return@synchronized
                if (token != generation) return@synchronized
                var result = current.enableLocalAudio(value)
                if (result == 0) result = current.updateChannelMediaOptions(ChannelMediaOptions().apply { publishMicrophoneTrack = value })
                if (result != 0) main.post { if (token == generation) close("麦克风设置失败，请重新加入频道") }
            }
        }
    }
    fun close(message: String) {
        if (state == State.LEAVING) return
        if (state == State.IDLE) { listener.state(state, message); return }
        val token = ++generation; change(State.LEAVING, "正在停止发送…")
        timeout?.let(main::removeCallbacks); timeout = null; users.clear(); shown = null; listener.remote(false)
        lifecycle.execute {
            synchronized(engineLock) { if (engine != null && owner === this) { engine?.leaveChannel(); RtcEngine.destroy(); engine = null; owner = null } }
            main.post { if (token == generation) change(State.IDLE, message) }
        }
    }
    private fun refreshRemote() {
        val next = users.firstOrNull(); if (next == shown) return
        synchronized(engineLock) {
            val current = engine ?: return
            if (state != State.JOINED) return
            shown?.let { current.setupRemoteVideo(VideoCanvas(null, VideoCanvas.RENDER_MODE_HIDDEN, it)) }
            shown = next; next?.let { current.setupRemoteVideo(VideoCanvas(remote, VideoCanvas.RENDER_MODE_HIDDEN, it)) }
        }
        listener.remote(next != null)
    }
    private fun change(next: State, message: String) { state = next; listener.state(state, message) }
    companion object {
        private val lifecycle = Executors.newSingleThreadExecutor { Thread(it, "agora-lifecycle") }
        private var owner: AgoraController? = null // Lifecycle executor only.
        private fun success(result: Int) = check(result == 0) { "Agora operation failed: $result" }
    }
}
