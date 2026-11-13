package ai.snapbeauty.samples

import android.opengl.GLES20.*
import android.opengl.GLES30.*
import java.nio.ByteBuffer

/** Top-down tightly packed RGBA readback; preserves the host's pack and framebuffer state. */
class RgbaFrameReader {
    private val owner = Thread.currentThread()
    private var fbo = 0
    private var pixels: ByteBuffer? = null
    fun read(texture: Int, width: Int, height: Int): ByteArray {
        check(Thread.currentThread() === owner) { "RGBA reader changed GL thread" }
        require(texture > 0 && width > 0 && height > 0 && width.toLong() * height * 4 <= Int.MAX_VALUE) { "Invalid RGBA frame" }
        val size = width * height * 4
        val buffer = pixels?.takeIf { it.capacity() == size } ?: ByteBuffer.allocateDirect(size).also { pixels = it }
        val draw = get(GL_DRAW_FRAMEBUFFER_BINDING)
        val read = get(GL_READ_FRAMEBUFFER_BINDING)
        val pack = get(GL_PIXEL_PACK_BUFFER_BINDING)
        val alignment = get(GL_PACK_ALIGNMENT)
        val rowLength = get(GL_PACK_ROW_LENGTH)
        val skipRows = get(GL_PACK_SKIP_ROWS)
        val skipPixels = get(GL_PACK_SKIP_PIXELS)
        try {
            if (fbo == 0) { val id = IntArray(1); glGenFramebuffers(1, id, 0); fbo = id[0] }
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo)
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)
            check(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE) { "RGBA read framebuffer incomplete" }
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0); glPixelStorei(GL_PACK_ALIGNMENT, 1)
            glPixelStorei(GL_PACK_ROW_LENGTH, 0); glPixelStorei(GL_PACK_SKIP_ROWS, 0); glPixelStorei(GL_PACK_SKIP_PIXELS, 0)
            buffer.clear(); glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer)
            val topDown = ByteArray(size)
            val stride = width * 4
            for (y in 0 until height) { buffer.position((height - 1 - y) * stride); buffer.get(topDown, y * stride, stride) }
            return topDown
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw); glBindFramebuffer(GL_READ_FRAMEBUFFER, read)
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pack); glPixelStorei(GL_PACK_ALIGNMENT, alignment)
            glPixelStorei(GL_PACK_ROW_LENGTH, rowLength); glPixelStorei(GL_PACK_SKIP_ROWS, skipRows); glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels)
        }
    }
    fun release() {
        check(Thread.currentThread() === owner) { "RGBA release changed GL thread" }
        if (fbo != 0) glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        fbo = 0; pixels = null
    }
    private fun get(key: Int) = IntArray(1).also { glGetIntegerv(key, it, 0) }[0]
}
