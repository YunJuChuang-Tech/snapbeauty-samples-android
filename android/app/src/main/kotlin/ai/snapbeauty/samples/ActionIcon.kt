package ai.snapbeauty.samples

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Original geometric icons; no external icon pack. */
class ActionIcon @JvmOverloads constructor(context: Context, private val kind: String = "mark") : View(context) {
    private val paint = Paint(3)
    private val arrow = Path()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.translate(width / 2f, height / 2f)
        val scale = minOf(width, height) / 32f
        canvas.scale(scale, scale)
        paint.color = ACCENT; paint.strokeWidth = 1.7f; paint.style = Paint.Style.STROKE
        when (kind) {
            "switch" -> {
                canvas.drawRoundRect(-10f, -7f, 10f, 8f, 3f, 3f, paint); canvas.drawCircle(0f, 0f, 3.5f, paint)
                arrow.reset(); arrow.moveTo(-7f, -11f); arrow.lineTo(7f, -11f); arrow.lineTo(4f, -14f); canvas.drawPath(arrow, paint)
                arrow.reset(); arrow.moveTo(7f, 12f); arrow.lineTo(-7f, 12f); arrow.lineTo(-4f, 15f); canvas.drawPath(arrow, paint)
            }
            "reset" -> {
                canvas.drawArc(-9f, -9f, 9f, 9f, -60f, 290f, false, paint)
                arrow.reset(); arrow.moveTo(-10f, -9f); arrow.lineTo(-9f, -2f); arrow.lineTo(-3f, -5f); canvas.drawPath(arrow, paint)
            }
            "compare" -> {
                canvas.drawRoundRect(-10f, -9f, 10f, 9f, 3f, 3f, paint); canvas.drawLine(0f, -13f, 0f, 13f, paint)
                paint.style = Paint.Style.FILL; paint.alpha = 90; canvas.drawRect(-7f, -6f, -2f, 6f, paint); paint.alpha = 255
            }
            else -> {
                canvas.drawOval(-9f, -11f, 9f, 11f, paint); canvas.drawArc(-5f, -2f, 5f, 6f, 20f, 140f, false, paint)
                canvas.drawLine(9f, -14f, 9f, -6f, paint); canvas.drawLine(5f, -10f, 13f, -10f, paint)
            }
        }
        canvas.restore()
    }
}
