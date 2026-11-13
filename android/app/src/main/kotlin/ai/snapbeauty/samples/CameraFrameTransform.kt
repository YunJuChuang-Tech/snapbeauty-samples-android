package ai.snapbeauty.samples

import android.opengl.Matrix
import kotlin.math.abs

/** SurfaceTexture already includes the camera producer's sensor rotation and crop. */
object CameraFrameTransform {
    fun dimensions(width: Int, height: Int, producer: FloatArray): Pair<Int, Int> =
        if (abs(producer[1]) > abs(producer[0])) height to width else width to height

    fun unmirrored(producer: FloatArray, result: FloatArray) {
        producer.copyInto(result)
        // A regular SurfaceTexture includes the top-left -> GL bottom-left Y inversion.
        // A positive determinant indicates the producer added a reflection (front camera).
        // Undo that reflection before face tracking; local preview mirrors only at display.
        if (producer[0] * producer[5] - producer[1] * producer[4] > 0f) {
            Matrix.translateM(result, 0, 1f, 0f, 0f)
            Matrix.scaleM(result, 0, -1f, 1f, 1f)
        }
    }
}
