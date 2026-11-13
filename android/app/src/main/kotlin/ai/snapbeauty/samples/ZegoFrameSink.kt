package ai.snapbeauty.samples

import android.os.SystemClock
import android.util.Log
import im.zego.zegoexpress.entity.ZegoVideoFrameParam
import im.zego.zegoexpress.constants.ZegoVideoFrameFormat
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/** Top-down RGBA capture gated by SDK onStart/onStop callbacks. */
class ZegoFrameSink(private val target: Target) : PreviewRenderer.FrameSink {
    interface Target {
        fun publishing(): Boolean
        fun push(data: ByteBuffer, params: ZegoVideoFrameParam, timestampMs: Long)
        fun frameError()
    }
    private var reader: RgbaFrameReader? = null
    private var direct: ByteBuffer? = null
    private var lastNs = 0L
    private var failed = false
    val captured = AtomicLong()
    val submitted = AtomicLong()
    val releases = AtomicLong()
    override fun frame(texture: Int, width: Int, height: Int, timestampNs: Long) {
        if (failed || !target.publishing()) return
        val now = if (timestampNs > 0) timestampNs else SystemClock.elapsedRealtimeNanos()
        if (lastNs != 0L && now >= lastNs && now - lastNs < 1_000_000_000L / 15) return
        lastNs = now
        try {
            val current = reader ?: RgbaFrameReader().also { reader = it }
            val rgba = current.read(texture, width, height)
            val buffer = direct?.takeIf { it.capacity() == rgba.size } ?: ByteBuffer.allocateDirect(rgba.size).also { direct = it }
            buffer.clear(); buffer.put(rgba); buffer.flip(); captured.incrementAndGet()
            // ZEGO expects UNIX milliseconds, while SurfaceTexture supplies a monotonic capture time.
            val referenceMs = System.currentTimeMillis() + (now - SystemClock.elapsedRealtimeNanos()) / 1_000_000L
            target.push(buffer, parameters(width, height), referenceMs); submitted.incrementAndGet()
        } catch (error: RuntimeException) { fail(error) } catch (error: LinkageError) { fail(error) }
    }
    private fun fail(error: Throwable) { failed = true; Log.e("SnapBeautySamples", "ZEGO frame conversion failed", error); target.frameError() }
    override fun release() { reader?.let { it.release(); releases.incrementAndGet() }; reader = null; direct = null; lastNs = 0; failed = false }
    companion object {
        fun parameters(width: Int, height: Int): ZegoVideoFrameParam {
            require(width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE) { "Invalid ZEGO RGBA dimensions" }
            return ZegoVideoFrameParam().apply { format = ZegoVideoFrameFormat.RGBA32; this.width = width; this.height = height; rotation = 0; strides[0] = width * 4 }
        }
    }
}
