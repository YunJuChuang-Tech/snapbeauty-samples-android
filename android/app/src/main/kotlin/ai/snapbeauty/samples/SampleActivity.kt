package ai.snapbeauty.samples

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowInsets
import android.widget.*
import kotlin.math.roundToInt

internal val BACKGROUND = 0xff101311.toInt()
internal val PANEL = 0xff191e1a.toInt()
internal val ACCENT = 0xffddff9a.toInt()
internal val TEXT = 0xfff2f4ec.toInt()
internal val MUTED = 0xffa3ada4.toInt()

open class SampleActivity : Activity() {
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    protected fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    protected fun text(value: String, size: Int, color: Int = TEXT) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color)
    }
    protected fun round(color: Int, radius: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    protected fun button(value: String, accent: Boolean) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 12f
        setTextColor(if (accent) BACKGROUND else TEXT)
        background = round(if (accent) ACCENT else 0xff303a31.toInt())
    }
    protected fun field(form: LinearLayout, name: String, initial: String, input: Int): EditText {
        form.addView(text(name, 12, MUTED))
        return EditText(this).apply {
            setTextColor(TEXT); textSize = 14f; inputType = input; setSingleLine()
            isSaveEnabled = false; contentDescription = name; setText(initial)
            form.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
    }
    protected fun applyInsets(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
    }
}
