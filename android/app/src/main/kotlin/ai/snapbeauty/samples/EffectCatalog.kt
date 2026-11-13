package ai.snapbeauty.samples

data class EffectParameter(val key: String, val name: String, val neutral: Float = 0f)
data class EffectChoice(val key: String?, val name: String, val makeupFilter: String? = null)
data class EffectResources(val styles: List<EffectChoice>, val filters: List<EffectChoice>, val makeupIssue: String? = null) {
    /** First visit opens with the first complete style, so makeup is visible without hunting for it. */
    fun defaults() = EffectState.defaults().selectStyle(if (makeupIssue == null) styles.firstOrNull { it.key != null } else null)
}

/** Descriptions only; SDK units and calls remain in the adapter. */
object EffectCatalog {
    val skin = listOf(
        EffectParameter("smooth", "磨皮"), EffectParameter("white", "美白"), EffectParameter("rosy", "红润"),
        EffectParameter("sharpen", "锐化"), EffectParameter("clarity", "清晰"), EffectParameter("delspot", "祛斑痘"),
        EffectParameter("eyeBright", "亮眼"), EffectParameter("toothWhiten", "美牙"), EffectParameter("removePouch", "去黑眼圈"),
        EffectParameter("removeNasolabial", "去法令纹"), EffectParameter("faceThree", "五官立体")
    )
    val shape = listOf(
        EffectParameter("slim", "瘦脸"), EffectParameter("cheekV", "V 脸"), EffectParameter("cheekNarrow", "窄脸"),
        EffectParameter("cheekShort", "短脸"), EffectParameter("cheekSmall", "小脸"), EffectParameter("cheekbones", "颧骨"),
        EffectParameter("lowerJaw", "下颌骨"), EffectParameter("eyes", "大眼"), EffectParameter("eyeCircle", "圆眼"),
        EffectParameter("chin", "下巴", .5f), EffectParameter("forehead", "额头", .5f), EffectParameter("nose", "瘦鼻"),
        EffectParameter("mouth", "嘴型", .5f), EffectParameter("lipThick", "唇厚", .5f), EffectParameter("eyeHeight", "眼睛高度", .5f),
        EffectParameter("canthus", "开眼角"), EffectParameter("eyeLid", "眼睑下至"), EffectParameter("eyeSpace", "眼距", .5f),
        EffectParameter("eyeRotate", "眼睛倾斜", .5f), EffectParameter("longNose", "鼻长", .5f), EffectParameter("philtrum", "人中", .5f),
        EffectParameter("smile", "微笑嘴角"), EffectParameter("browHeight", "眉高", .5f), EffectParameter("browSpace", "眉间距", .5f),
        EffectParameter("browThick", "眉毛粗细", .5f)
    )
    val parameters = skin + shape
    fun filterName(key: String): String {
        val families = mapOf("bright" to "明亮", "rose" to "玫瑰", "stylized" to "个性", "mono" to "黑白", "cool" to "冷调", "peach" to "蜜桃", "warm" to "暖调", "fresh" to "清新", "muted" to "柔灰", "natural" to "自然")
        val family = key.trimEnd { it.isDigit() }
        return families[family]?.let { "$it ${key.removePrefix(family)}" } ?: "光谱 ${key.hashCode().toUInt().toString(16).takeLast(4)}"
    }
}
