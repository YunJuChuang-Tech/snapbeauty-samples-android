package ai.snapbeauty.samples

import android.opengl.GLES30.*

/** Host textures use bottom-up GL coordinates; this SDK consumes top-down image rows. */
internal class SdkTextureBridge {
    private val textures = IntArray(2)
    private val fbos = IntArray(3)
    private var width = 0
    private var height = 0
    fun flip(input: Int, w: Int, h: Int, slot: Int): Int {
        val read = get(GL_READ_FRAMEBUFFER_BINDING)
        val draw = get(GL_DRAW_FRAMEBUFFER_BINDING)
        val scissor = glIsEnabled(GL_SCISSOR_TEST)
        try {
            if (width != w || height != h) {
                release(); glGenFramebuffers(3, fbos, 0)
                for (i in 0..1) {
                    textures[i] = GlProgram.texture(GL_TEXTURE_2D)
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, null)
                    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbos[i])
                    glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[i], 0)
                    check(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE)
                }
                width = w; height = h
            }
            glDisable(GL_SCISSOR_TEST)
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbos[2])
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, input, 0)
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbos[slot])
            glBlitFramebuffer(0, 0, w, h, 0, h, w, 0, GL_COLOR_BUFFER_BIT, GL_NEAREST)
            return textures[slot]
        } finally {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw)
            if (scissor) glEnable(GL_SCISSOR_TEST)
        }
    }
    fun release() {
        glDeleteTextures(2, textures, 0); glDeleteFramebuffers(3, fbos, 0)
        textures.fill(0); fbos.fill(0); width = 0; height = 0
    }
    private fun get(key: Int) = IntArray(1).also { glGetIntegerv(key, it, 0) }[0]
}
