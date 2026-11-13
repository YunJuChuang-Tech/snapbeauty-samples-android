package ai.snapbeauty.samples

/** All calls share one GL owner thread and current EGL context. */
interface BeautyProcessor {
    fun available(): Boolean
    fun label(): String
    fun apply(state: EffectState)
    /** Upright, unmirrored GL_TEXTURE_2D; output is borrowed until the next call. */
    fun process(texture: Int, width: Int, height: Int): Int
    fun status(): String
    fun resetTracking()
    fun release()
}
