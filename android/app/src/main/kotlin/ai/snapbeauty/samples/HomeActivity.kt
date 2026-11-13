package ai.snapbeauty.samples

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView

/** One app, with a separate entry for each sample scenario. */
class HomeActivity : SampleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = column().apply {
            gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(40), dp(24), dp(32)); setBackgroundColor(BACKGROUND)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(BACKGROUND); addView(root) }
        applyInsets(scroll); setContentView(scroll)
        root.addView(ActionIcon(this), LinearLayout.LayoutParams(dp(64), dp(64)))
        root.addView(text("SnapBeauty\nSamples", 32).apply { setTypeface(null, Typeface.BOLD); setPadding(0, dp(24), 0, dp(12)) })
        root.addView(text("选一个场景，马上开始。", 15, MUTED).apply { setPadding(0, 0, 0, dp(32)) })
        card(root, "01  相机美颜", "本地预览 · 参数调节 · 原图对比", MainActivity::class.java)
        card(root, "02  腾讯云 TRTC", "美颜视频通话 · 双端画面", TrtcActivity::class.java)
        card(root, "03  声网 Agora", "自定义视频 · 美颜通话", AgoraActivity::class.java)
        card(root, "04  即构 ZEGO", "自定义采集 · 美颜推拉流", ZegoActivity::class.java)
        root.addView(text("简单一点，自然一点。", 12, MUTED).apply { setPadding(0, dp(30), 0, 0) })
    }
    private fun card(root: LinearLayout, title: String, subtitle: String, target: Class<*>) {
        val card = column().apply {
            setPadding(dp(20), dp(22), dp(20), dp(22)); background = round(0xff202921.toInt())
            contentDescription = "$title，$subtitle"; isClickable = true; isFocusable = true
            setOnClickListener { startActivity(Intent(this@HomeActivity, target)) }
        }
        card.addView(text(title, 18, ACCENT).apply { setTypeface(null, Typeface.BOLD) })
        card.addView(text(subtitle, 12, MUTED).apply { setPadding(0, dp(8), 0, 0) })
        root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }
}
