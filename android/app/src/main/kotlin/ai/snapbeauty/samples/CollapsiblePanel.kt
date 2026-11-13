package ai.snapbeauty.samples

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

/** A clipped sheet with fixed-size contents: dragging changes reveal height, never effect state. */
class CollapsiblePanel(context: Context, private val onCollapse: () -> Unit) : LinearLayout(context) {
    val content = LinearLayout(context).apply { orientation = VERTICAL; setPadding(0, 0, 0, dp(12)) }
    var collapsed = false; private set
    /** Currently visible body height, so siblings can compensate without resizing heavy views. */
    val revealedBody get() = (bodyHeight * reveal).roundToInt()
    private var reveal = 1f
    private var bodyHeight = 0
    private var animator: ValueAnimator? = null
    private val touch = ViewConfiguration.get(context)
    private val caption = TextView(context).apply { textSize = 11f; setTextColor(ACCENT); gravity = Gravity.CENTER }
    private val handle = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true }
    private var startX = 0f
    private var startY = 0f
    private var startReveal = 1f
    private var dragging = false
    private var rejectGesture = false
    private var startedOnHandle = false
    private var scrollTarget: ScrollView? = null
    private var velocity: VelocityTracker? = null

    init {
        orientation = VERTICAL; setPadding(dp(20), 0, dp(20), 0)
        clipChildren = true
        background = GradientDrawable().apply { setColor(PANEL); cornerRadius = dp(24).toFloat() }
        val grip = View(context).apply { background = GradientDrawable().apply { setColor(0xff657365.toInt()); cornerRadius = dp(2).toFloat() } }
        handle.addView(grip, LayoutParams(dp(30), dp(3)).apply { bottomMargin = dp(5) }); handle.addView(caption)
        addView(handle, LayoutParams(-1, dp(48))); addView(content)
        handle.setOnClickListener { collapse(!collapsed) }
        updateHeader()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Keep the full contents measured even while clipped; otherwise opening has no target height.
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        bodyHeight = (measuredHeight - handle.measuredHeight).coerceAtLeast(0)
        val height = handle.measuredHeight + (bodyHeight * reveal).roundToInt()
        setMeasuredDimension(measuredWidth, resolveSize(height, heightMeasureSpec))
    }
    fun collapse(value: Boolean, animated: Boolean = true) {
        animator?.cancel(); animator = null
        if (value && !collapsed) onCollapse()
        val moveFocus = value && content.hasFocus()
        collapsed = value; updateHeader()
        if (moveFocus) handle.requestFocus()
        val target = if (value) 0f else 1f
        if (!animated || !isLaidOut || (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) || abs(target - reveal) < .001f) {
            reveal(target); return
        }
        animator = ValueAnimator.ofFloat(reveal, target).apply {
            duration = (260 * abs(target - reveal)).roundToInt().coerceIn(140, 260).toLong()
            interpolator = PathInterpolator(.2f, 0f, 0f, 1f)
            addUpdateListener { reveal(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) { reveal(target); animator = null } }
            })
            start()
        }
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            animator?.cancel(); animator = null
            startX = event.rawX; startY = event.rawY; startReveal = reveal
            dragging = false; rejectGesture = false; scrollTarget = null
            startedOnHandle = event.y < handle.bottom
            var target: View? = hit(this, event.x, event.y)
            while (target != null && target !== this) {
                if (target is SeekBar) rejectGesture = true
                if (target is ScrollView) scrollTarget = target
                target = target.parent as? View
            }
            velocity?.recycle(); velocity = VelocityTracker.obtain()
        }
        // The sheet itself moves during layout; velocity must stay in screen coordinates.
        val screenEvent = MotionEvent.obtain(event)
        screenEvent.offsetLocation(event.rawX - event.x, event.rawY - event.y)
        velocity?.addMovement(screenEvent); screenEvent.recycle()
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            rejectGesture = true
            if (dragging) { dragging = false; collapse(collapsed) }
        }
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            velocity?.recycle(); velocity = null
            // A tap on a child may have interrupted an animation without becoming a drag.
            if (!dragging && animator == null && reveal > 0f && reveal < 1f) collapse(collapsed)
        }
        return handled
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE && canDrag(event)) {
            beginDrag()
            reveal((startReveal - (event.rawY - startY) / bodyHeight).coerceIn(0f, 1f))
        }
        return dragging
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && canDrag(event)) beginDrag()
                if (dragging && bodyHeight > 0) reveal((startReveal - (event.rawY - startY) / bodyHeight).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                velocity?.computeCurrentVelocity(1000, touch.scaledMaximumFlingVelocity.toFloat())
                val speed = velocity?.yVelocity ?: 0f
                val fast = abs(speed) > maxOf(touch.scaledMinimumFlingVelocity.toFloat(), dp(350).toFloat())
                collapse(if (fast) speed > 0 else reveal < .5f)
            }
            MotionEvent.ACTION_CANCEL -> if (dragging) collapse(collapsed)
        }
        return true
    }
    private fun canDrag(event: MotionEvent): Boolean {
        if (rejectGesture || bodyHeight == 0) return false
        if (dragging) return true
        val dy = event.rawY - startY; val dx = event.rawX - startX
        if (abs(dy) <= touch.scaledTouchSlop || abs(dy) <= abs(dx)) return false
        return if (dy > 0) reveal > 0f && (startedOnHandle || scrollTarget?.canScrollVertically(-1) != true)
        else reveal < 1f && (startedOnHandle || scrollTarget?.canScrollVertically(1) != true)
    }
    private fun beginDrag() {
        if (dragging) return
        dragging = true; onCollapse(); parent?.requestDisallowInterceptTouchEvent(true)
    }
    private fun reveal(value: Float) {
        reveal = value
        content.visibility = if (value == 0f) View.INVISIBLE else View.VISIBLE
        content.importantForAccessibility = if (value == 0f) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        requestLayout()
    }
    private fun updateHeader() {
        caption.text = if (collapsed) "展开效果面板  ↑" else "收起面板  ↓"
        handle.contentDescription = if (collapsed) "展开效果面板" else "收起效果面板"
        if (Build.VERSION.SDK_INT >= 30) handle.stateDescription = if (collapsed) "已收起" else "已展开"
    }
    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null; velocity?.recycle(); velocity = null
        reveal(if (collapsed) 0f else 1f)
        super.onDetachedFromWindow()
    }
    private fun hit(group: ViewGroup, x: Float, y: Float): View {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            val cx = x + group.scrollX - child.left; val cy = y + group.scrollY - child.top
            if (child.visibility == View.VISIBLE && cx >= 0 && cy >= 0 && cx < child.width && cy < child.height) {
                return if (child is ViewGroup) hit(child, cx, cy) else child
            }
        }
        return group
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
