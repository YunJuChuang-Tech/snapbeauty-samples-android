package ai.snapbeauty.samples

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.tencent.rtmp.ui.TXCloudVideoView
import com.tencent.trtc.TRTCCloud
import com.tencent.trtc.TRTCCloudDef.*
import com.tencent.trtc.TRTCCloudListener

class TrtcController(context: Context, private val local: TXCloudVideoView, private val remote: TXCloudVideoView, private val listener: Listener) {
    enum class State { IDLE, PREVIEW, JOINING, JOINED, LEAVING }
    interface Listener { fun state(state: State, message: String); fun remote(available: Boolean) }
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val videoUsers = linkedSetOf<String>()
    private var cloud: TRTCCloud? = null
    private var state = State.IDLE
    private var shownUser: String? = null
    private var front = true
    private var mic = false
    private var generation = 0
    private var exitTimeout: Runnable? = null
    private var joinTimeout: Runnable? = null
    val beauty = TrtcBeautyListener(app)
    fun state() = state
    private fun change(next: State, message: String) { state = next; listener.state(state, message) }
    private fun create(): TRTCCloud {
        cloud?.let { return it }
        val current = TRTCCloud.sharedInstance(app).also { cloud = it }
        val token = ++generation
        current.setListenerHandler(main)
        current.setListener(object : TRTCCloudListener() {
            private fun current() = token == generation && cloud != null
            override fun onEnterRoom(result: Long) {
                if (!current() || state == State.LEAVING) return
                joinTimeout?.let(main::removeCallbacks); joinTimeout = null
                if (result > 0) { if (mic) cloud?.startLocalAudio(TRTC_AUDIO_QUALITY_DEFAULT); change(State.JOINED, "已加入房间，等待对方进入") }
                else close("加入失败（$result），请检查 AppID、用户 ID 和 UserSig")
            }
            override fun onExitRoom(reason: Int) { if (current()) destroy("已退出房间") }
            override fun onError(code: Int, description: String?, extra: Bundle?) { if (current() && state != State.LEAVING) close("连接出现错误（$code），请检查配置后重试") }
            override fun onUserVideoAvailable(user: String, available: Boolean) {
                if (!current() || state != State.JOINED) return
                if (available) videoUsers.add(user) else videoUsers.remove(user); refreshRemote()
            }
            override fun onRemoteUserLeaveRoom(user: String, reason: Int) { if (current()) { videoUsers.remove(user); refreshRemote() } }
            override fun onConnectionLost() { if (current() && state == State.JOINED) listener.state(state, "连接中断，正在等待恢复…") }
            override fun onTryToReconnect() { if (current() && state == State.JOINED) listener.state(state, "正在重连…") }
            override fun onConnectionRecovery() { if (current() && state == State.JOINED) listener.state(state, "连接已恢复") }
        })
        val callback = current.setLocalVideoProcessListener(TRTC_VIDEO_PIXEL_FORMAT_Texture_2D, TRTC_VIDEO_BUFFER_TYPE_TEXTURE, beauty)
        check(callback == 0) { "TRTC texture callback registration failed: $callback" }
        current.setVideoEncoderParam(TRTCVideoEncParam().apply {
            videoResolution = TRTC_VIDEO_RESOLUTION_640_480; videoResolutionMode = TRTC_VIDEO_RESOLUTION_MODE_PORTRAIT
            videoFps = 15; videoBitrate = 800; enableAdjustRes = true
        })
        return current
    }
    fun preview() {
        if (state != State.IDLE) return
        try { create().startLocalPreview(front, local); change(State.PREVIEW, "仅本地预览，尚未加入房间") }
        catch (error: RuntimeException) { destroy("无法启动 TRTC，请检查 SDK 和设备后重试") }
        catch (error: LinkageError) { destroy("无法启动 TRTC，请检查 SDK 和设备后重试") }
    }
    fun join(config: TrtcRoomConfig, mic: Boolean) {
        if (state != State.IDLE && state != State.PREVIEW) return
        this.mic = mic
        try {
            val current = create(); if (state == State.IDLE) current.startLocalPreview(front, local)
            val params = TRTCParams().apply { sdkAppId = config.appId; roomId = config.roomId; userId = config.userId; userSig = config.userSig }
            change(State.JOINING, "正在加入房间…"); current.enterRoom(params, TRTC_APP_SCENE_VIDEOCALL)
            joinTimeout = Runnable { if (state == State.JOINING) close("加入超时，请检查网络或重新获取 UserSig") }.also { main.postDelayed(it, 20000) }
        } catch (error: RuntimeException) { close("无法加入房间，请检查配置后重试") }
        catch (error: LinkageError) { close("无法加入房间，请检查配置后重试") }
    }
    fun switchCamera() { cloud?.let { if (state != State.LEAVING) { front = !front; it.deviceManager.switchCamera(front); beauty.resetRequested.set(true) } } }
    fun microphone(enabled: Boolean) {
        mic = enabled; if (state == State.JOINED) { if (enabled) cloud?.startLocalAudio(TRTC_AUDIO_QUALITY_DEFAULT) else cloud?.stopLocalAudio() }
    }
    fun close(message: String) {
        if (state == State.LEAVING) return
        val current = cloud ?: run { change(State.IDLE, message); return }
        joinTimeout?.let(main::removeCallbacks); joinTimeout = null
        val inRoom = state == State.JOINING || state == State.JOINED
        change(State.LEAVING, "正在停止…")
        current.setLocalVideoProcessListener(TRTC_VIDEO_PIXEL_FORMAT_Texture_2D, TRTC_VIDEO_BUFFER_TYPE_TEXTURE, null)
        current.stopLocalAudio(); current.stopAllRemoteView(); current.stopLocalPreview()
        videoUsers.clear(); shownUser = null; listener.remote(false)
        if (inRoom) { current.exitRoom(); exitTimeout = Runnable { destroy(message) }.also { main.postDelayed(it, 5000) } } else destroy(message)
    }
    private fun destroy(message: String) {
        exitTimeout?.let(main::removeCallbacks); exitTimeout = null
        joinTimeout?.let(main::removeCallbacks); joinTimeout = null
        generation++; state = State.LEAVING
        cloud?.let { it.stopLocalAudio(); it.stopAllRemoteView(); it.stopLocalPreview(); it.setListener(null); TRTCCloud.destroySharedInstance() }; cloud = null
        shownUser = null; videoUsers.clear(); listener.remote(false)
        beauty.close { main.post { change(State.IDLE, message) } }
    }
    private fun refreshRemote() {
        val current = cloud ?: return
        if (state != State.JOINED) return
        val next = videoUsers.firstOrNull(); if (next == shownUser) return
        shownUser?.let { current.stopRemoteView(it, TRTC_VIDEO_STREAM_TYPE_BIG) }; shownUser = next
        next?.let { current.startRemoteView(it, TRTC_VIDEO_STREAM_TYPE_BIG, remote) }; listener.remote(next != null)
    }
}
