package ai.snapbeauty.samples

import android.content.Context
import android.util.Log
import com.tencent.trtc.TRTCCloudDef
import com.tencent.trtc.TRTCCloudListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** The synchronous worker completes before TRTC reuses the borrowed frame. */
class TrtcBeautyListener(app: Context) : TRTCCloudListener.TRTCVideoFrameListener {
    private val app = app.applicationContext
    @Volatile private var worker: RtcBeautyWorker? = null
    private var cleaning = false
    private val closeWaiters = mutableListOf<Runnable>()
    private var failed = false
    @Volatile var effects = EffectState.defaults()
    @Volatile var original = false
    @Volatile var status = "正在准备美颜…"
    val frames = AtomicLong()
    val contexts = AtomicLong()
    val releases = AtomicLong()
    val resetRequested = AtomicBoolean()
    @Synchronized override fun onGLContextCreated() {
        failed = false
        try { worker = RtcBeautyWorker(app); contexts.incrementAndGet() }
        catch (error: RuntimeException) { failure(error) } catch (error: LinkageError) { failure(error) }
    }
    override fun onProcessVideoFrame(input: TRTCCloudDef.TRTCVideoFrame?, output: TRTCCloudDef.TRTCVideoFrame): Int {
        val source = input?.texture ?: return 0
        val target = output.texture ?: TRTCCloudDef.TRTCTexture().also { output.texture = it }
        target.textureId = source.textureId
        output.width = input.width; output.height = input.height; output.rotation = input.rotation; output.timestamp = input.timestamp
        output.pixelFormat = input.pixelFormat; output.bufferType = input.bufferType
        target.eglContext10 = source.eglContext10; target.eglContext14 = source.eglContext14
        val current = worker
        if (!failed && current != null) {
            try {
                if (!current.available) { status = "此设备不支持当前美颜 SDK，已使用原图"; return 0 }
                target.textureId = current.process(source.textureId, input.width, input.height, input.rotation * 90, effects, original, resetRequested.getAndSet(false))
                frames.incrementAndGet(); status = if (original) "正在查看原图" else "实时美颜 · ${current.status}"
            } catch (error: RuntimeException) { failure(error) } catch (error: LinkageError) { failure(error) }
        }
        return 0
    }
    override fun onGLContextDestory() = close { }
    @Synchronized fun close(complete: Runnable) {
        val current = worker; worker = null
        if (current == null) { if (cleaning) closeWaiters.add(complete) else complete.run(); return }
        cleaning = true; closeWaiters.add(complete)
        current.close {
            if (!current.cleanupFailed) releases.incrementAndGet()
            val waiters = synchronized(this) { cleaning = false; closeWaiters.toList().also { closeWaiters.clear() } }
            waiters.forEach(Runnable::run)
        }
    }
    private fun failure(error: Throwable) { failed = true; status = "美颜处理失败，已使用原图；请停止后重试"; Log.e("SnapBeautySamples", "TRTC beauty callback failed", error) }
}
