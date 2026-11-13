package ai.snapbeauty.samples

import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Original full-screen texture blitter. */
class GlProgram(external: Boolean) {
    val id: Int
    private val quad = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val uv = floats(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    init {
        val vertex = "attribute vec2 position; attribute vec2 texCoord; uniform mat4 transform; varying highp vec2 uv;" +
            "void main(){ gl_Position=vec4(position,0.,1.); uv=(transform*vec4(texCoord,0.,1.)).xy; }"
        val fragment = (if (external) "#extension GL_OES_EGL_image_external : require\n" else "") +
            "precision highp float; varying highp vec2 uv; uniform " +
            (if (external) "samplerExternalOES" else "sampler2D") +
            " image; void main(){ gl_FragColor=texture2D(image,uv); }"
        val vs = shader(GL_VERTEX_SHADER, vertex)
        val fs = shader(GL_FRAGMENT_SHADER, fragment)
        id = glCreateProgram()
        glAttachShader(id, vs); glAttachShader(id, fs); glLinkProgram(id)
        glDeleteShader(vs); glDeleteShader(fs)
        val ok = IntArray(1)
        glGetProgramiv(id, GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { glGetProgramInfoLog(id) }
    }
    fun draw(texture: Int, target: Int, transform: FloatArray) {
        glUseProgram(id); glBindBuffer(GL_ARRAY_BUFFER, 0)
        glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST)
        glDisable(GL_CULL_FACE); glColorMask(true, true, true, true)
        glActiveTexture(GL_TEXTURE0); glBindTexture(target, texture)
        val p = glGetAttribLocation(id, "position")
        val t = glGetAttribLocation(id, "texCoord")
        quad.position(0); uv.position(0)
        glEnableVertexAttribArray(p); glEnableVertexAttribArray(t)
        glVertexAttribPointer(p, 2, GL_FLOAT, false, 0, quad)
        glVertexAttribPointer(t, 2, GL_FLOAT, false, 0, uv)
        glUniformMatrix4fv(glGetUniformLocation(id, "transform"), 1, false, transform, 0)
        glUniform1i(glGetUniformLocation(id, "image"), 0)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisableVertexAttribArray(p); glDisableVertexAttribArray(t)
    }
    fun release() = glDeleteProgram(id)
    companion object {
        fun texture(target: Int): Int {
            val ids = IntArray(1)
            glGenTextures(1, ids, 0); glBindTexture(target, ids[0])
            glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            return ids[0]
        }
        private fun shader(type: Int, source: String): Int {
            val id = glCreateShader(type)
            glShaderSource(id, source); glCompileShader(id)
            val ok = IntArray(1)
            glGetShaderiv(id, GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { glGetShaderInfoLog(id) }
            return id
        }
        private fun floats(vararg values: Float) = ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(values); position(0) }
    }
}
