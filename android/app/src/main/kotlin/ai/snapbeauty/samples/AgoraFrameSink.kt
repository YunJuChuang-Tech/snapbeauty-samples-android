package ai.snapbeauty.samples

import android.os.SystemClock
import android.util.Log
import io.agora.rtc2.video.AgoraVideoFrame
import java.util.concurrent.atomic.AtomicLong

class AgoraFrameSink(private val target: Target) : PreviewRenderer.FrameSink {
    interface Target {
        fun publishing(): Boolean
        fun push(frame: AgoraVideoFrame): Int
        fun frameError()
    }
    private var reader: RgbaFrameReader? = null
    private var lastNs = 0L
    private var failed = false
    val captured = AtomicLong()
    val accepted = AtomicLong()
    val releases = AtomicLong()
    override fun frame(texture: Int, width: Int, height: Int, timestampNs: Long) {
        if (failed || !target.publishing()) return
        val now = if (timestampNs > 0) timestampNs else SystemClock.elapsedRealtimeNanos()
        if (lastNs != 0L && now >= lastNs && now - lastNs < 1_000_000_000L / 15) return
        lastNs = now
        try {
            val current = reader ?: RgbaFrameReader().also { reader = it }
            val frame = wrap(current.read(texture, width, height), width, height, now)
            captured.incrementAndGet(); if (target.push(frame) == 0) accepted.incrementAndGet()
        } catch (error: RuntimeException) { fail(error) } catch (error: LinkageError) { fail(error) }
    }
    private fun fail(error: Throwable) { failed = true; Log.e("SnapBeautySamples", "Agora frame conversion failed", error); target.frameError() }
    override fun release() { reader?.let { it.release(); releases.incrementAndGet() }; reader = null; lastNs = 0; failed = false }
    companion object {
        fun wrap(rgba: ByteArray, width: Int, height: Int, timestampNs: Long): AgoraVideoFrame {
            require(width > 0 && height > 0 && width.toLong() * height * 4 == rgba.size.toLong() && timestampNs > 0) { "Invalid external RGBA frame" }
            return AgoraVideoFrame().apply {
                format = AgoraVideoFrame.FORMAT_RGBA; stride = width; this.height = height
                rotation = 0; timeStamp = timestampNs / 1_000_000L; buf = rgba
            }
        }
    }
}
