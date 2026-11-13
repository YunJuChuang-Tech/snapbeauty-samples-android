package ai.snapbeauty.samples

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.*
import im.zego.zegoexpress.constants.*
import im.zego.zegoexpress.entity.*
import im.zego.zegoexpress.entity.ZegoRoomConfig as SdkRoomConfig
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class ZegoController(context: Context, private val remote: SurfaceView, private val listener: Listener) : ZegoFrameSink.Target {
    enum class State { IDLE, JOINING, JOINED, LEAVING }
    interface Listener { fun state(state: State, message: String); fun remote(visible: Boolean) }
    private val app = context.applicationContext as Application
    private val main = Handler(Looper.getMainLooper())
    private val engineLock = Any()
    private val streams = linkedSetOf<String>()
    @Volatile private var state = State.IDLE
    @Volatile private var generation = 0
    @Volatile private var captureRequested = false
    private var engine: ZegoExpressEngine? = null
    private var roomId = ""
    private var streamId = ""
    private var userId = ""
    private var shown: String? = null
    private var mic = false
    private var timeout: Runnable? = null
    val pushed = AtomicLong()
    fun state() = state
    override fun publishing() = state == State.JOINED && captureRequested
    fun join(config: ZegoRoomConfig, microphone: Boolean) {
        if (state != State.IDLE) return
        mic = microphone; roomId = config.room; streamId = config.streamId; userId = config.userId
        val token = ++generation; change(State.JOINING, "正在加入房间…")
        timeout = Runnable { if (token == generation && state == State.JOINING) close("加入超时，请检查网络或重新获取 Token") }.also { main.postDelayed(it, 20000) }
        lifecycle.execute {
            if (token != generation) return@execute
            try {
                check(owner == null || owner === this) { "Previous ZEGO session is stopping" }
                val profile = ZegoEngineProfile().apply { appID = config.appId; appSign = ""; application = app; scenario = ZegoScenario.STANDARD_VIDEO_CALL }
                synchronized(engineLock) {
                    val current = checkNotNull(ZegoExpressEngine.createEngine(profile, events(token))) { "ZEGO initialization failed" }.also { engine = it; owner = this }
                    if (token != generation) return@synchronized
                    current.enableCustomVideoCapture(true, ZegoCustomVideoCaptureConfig().apply { bufferType = ZegoVideoBufferType.RAW_DATA })
                    current.setCustomVideoCaptureHandler(object : IZegoCustomVideoCaptureHandler() {
                        override fun onStart(channel: ZegoPublishChannel) { if (token == generation && channel == ZegoPublishChannel.MAIN) captureRequested = true }
                        override fun onStop(channel: ZegoPublishChannel) { if (token == generation && channel == ZegoPublishChannel.MAIN) captureRequested = false }
                    })
                    current.setVideoConfig(ZegoVideoConfig().apply { captureWidth = 480; captureHeight = 640; encodeWidth = 480; encodeHeight = 640; fps = 15; bitrate = 800 })
                    current.enableAudioCaptureDevice(mic); current.muteMicrophone(!mic)
                    val room = SdkRoomConfig().apply { this.token = config.token; isUserStatusNotify = true }
                    current.loginRoom(config.room, ZegoUser(config.userId), room)
                }
            } catch (error: RuntimeException) { joinFailed(token) } catch (error: LinkageError) { joinFailed(token) }
        }
    }
    private fun joinFailed(token: Int) { main.post { if (token == generation) close("无法加入房间，请核对 AppID、用户 ID 和 Token") } }
    private fun events(token: Int) = object : IZegoEventHandler() {
        private fun event(action: () -> Unit) { main.post { if (token == generation && state != State.LEAVING) action() } }
        override fun onRoomStateChanged(room: String?, reason: ZegoRoomStateChangedReason, code: Int, extra: JSONObject?) = event {
            when (reason) {
                ZegoRoomStateChangedReason.LOGINED -> {
                    timeout?.let(main::removeCallbacks); change(State.JOINED, "已加入房间，等待对方进入")
                    synchronized(engineLock) { engine?.startPublishingStream(streamId) }; refreshRemote()
                }
                ZegoRoomStateChangedReason.LOGIN_FAILED, ZegoRoomStateChangedReason.RECONNECT_FAILED, ZegoRoomStateChangedReason.KICK_OUT -> close("房间连接失败（$code），请重新获取 Token 后重试")
                ZegoRoomStateChangedReason.RECONNECTING -> listener.state(state, "正在重连…")
                ZegoRoomStateChangedReason.RECONNECTED -> listener.state(state, "连接已恢复")
                else -> Unit
            }
        }
        override fun onRoomStreamUpdate(room: String?, update: ZegoUpdateType, values: ArrayList<ZegoStream>, extra: JSONObject?) = event {
            for (stream in values) {
                if (stream.user?.userID == userId) continue
                if (update == ZegoUpdateType.ADD) streams.add(stream.streamID) else streams.remove(stream.streamID)
            }
            refreshRemote()
        }
        override fun onRoomTokenWillExpire(room: String?, seconds: Int) = event { close("Token 即将过期，请获取新 Token 后重新加入") }
        override fun onPublisherStateUpdate(stream: String?, publishState: ZegoPublisherState?, code: Int, extra: JSONObject?) {
            if (code != 0) event { close("推流失败（$code），请检查房间和 Token 权限") }
        }
    }
    override fun push(data: ByteBuffer, params: ZegoVideoFrameParam, timestampMs: Long) {
        synchronized(engineLock) {
            val current = engine ?: return
            if (!publishing()) return
            current.sendCustomVideoCaptureRawData(data, data.remaining(), params, timestampMs); pushed.incrementAndGet()
        }
    }
    override fun frameError() {
        val token = generation; main.post { if (token == generation && state != State.IDLE) close("视频转换失败，已停止推流；本地仍可预览") }
    }
    fun microphone(enabled: Boolean) {
        mic = enabled; val token = generation
        lifecycle.execute { synchronized(engineLock) { if (token == generation) { engine?.enableAudioCaptureDevice(enabled); engine?.muteMicrophone(!enabled) } } }
    }
    fun close(message: String) {
        if (state == State.LEAVING) return
        if (state == State.IDLE) { listener.state(state, message); return }
        val token = ++generation; captureRequested = false; change(State.LEAVING, "正在停止推流…")
        timeout?.let(main::removeCallbacks); timeout = null; streams.clear(); shown = null; listener.remote(false)
        lifecycle.execute {
            synchronized(engineLock) {
                val current = engine
                if (current == null || owner !== this) {
                    if (owner === this) owner = null
                    main.post { if (token == generation) change(State.IDLE, message) }; return@synchronized
                }
                current.stopPublishingStream(); current.logoutRoom(roomId)
                // Native destruction is asynchronous. Do not allow a replacement engine before completion.
                ZegoExpressEngine.destroyEngine {
                    lifecycle.execute {
                        synchronized(engineLock) { engine = null; if (owner === this) owner = null }
                        main.post { if (token == generation) change(State.IDLE, message) }
                    }
                }
            }
        }
    }
    private fun refreshRemote() {
        val next = streams.firstOrNull(); if (next == shown) return
        synchronized(engineLock) {
            val current = engine ?: return
            if (state != State.JOINED) return
            shown?.let(current::stopPlayingStream); shown = next
            next?.let { current.startPlayingStream(it, ZegoCanvas(remote)) }
        }
        listener.remote(next != null)
    }
    private fun change(next: State, message: String) { state = next; listener.state(state, message) }
    companion object {
        private val lifecycle = Executors.newSingleThreadExecutor { Thread(it, "zego-lifecycle") }
        private var owner: ZegoController? = null
    }
}
