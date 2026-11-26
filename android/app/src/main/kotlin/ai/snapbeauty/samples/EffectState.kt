package ai.snapbeauty.samples

import android.os.Bundle

/** Immutable, normalized snapshots can safely cross UI, GL and RTC threads. */
class EffectState private constructor(values: Map<String, Float>, val style: String?, val filter: String?, val maxFaces: Int, val frameBudgetMs: Float, val filterFollowsMakeup: Boolean = false, val lipstick: String? = null) {
    private val values = values.mapValues { (_, v) -> if (v.isFinite()) v.coerceIn(0f, 1f) else 0f }
    constructor(smooth: Float, white: Float, rosy: Float, slim: Float, eyes: Float, makeup: Float, style: String?) :
        this(mapOf("smooth" to smooth, "white" to white, "rosy" to rosy, "slim" to slim, "eyes" to eyes, "makeup" to makeup), style, null, 1, 1000f / 30)
    val smooth get() = value("smooth")
    val white get() = value("white")
    val rosy get() = value("rosy")
    val slim get() = value("slim")
    val eyes get() = value("eyes")
    val makeup get() = value("makeup")
    val filterIntensity get() = value("filterIntensity")
    fun value(key: String) = values[key] ?: EffectCatalog.parameters.firstOrNull { it.key == key }?.neutral ?: when (key) { "filterIntensity" -> .5f; "makeup" -> .4f; else -> if (key.startsWith("lipstick:")) .6f else 0f }
    fun withValue(key: String, value: Float): EffectState {
        val updated = values + (key to value) + if (key == "makeup" && filterFollowsMakeup) mapOf("filterIntensity" to value) else emptyMap()
        return EffectState(updated, style, filter, maxFaces, frameBudgetMs, filterFollowsMakeup && key != "filterIntensity", lipstick)
    }
    /** Raw layer selection is also used to verify makeup independently of color grading. */
    fun withStyle(key: String?) = EffectState(values, key, filter, maxFaces, frameBudgetMs, false, lipstick)
    fun selectStyle(choice: EffectChoice?): EffectState {
        if (choice?.key == null) return EffectState(values, null, if (filterFollowsMakeup) null else filter, maxFaces, frameBudgetMs, false, lipstick)
        return EffectState(values + ("filterIntensity" to makeup), choice.key, choice.makeupFilter, maxFaces, frameBudgetMs, true, lipstick)
    }
    fun withFilter(key: String?) = EffectState(values, style, key, maxFaces, frameBudgetMs, false, lipstick)
    fun withLipstick(key: String?) = EffectState(values, style, filter, maxFaces, frameBudgetMs, filterFollowsMakeup, key?.takeIf { selected -> EffectCatalog.lipsticks.any { it.key == selected } })
    fun withTracking(faces: Int, budget: Float) = EffectState(values, style, filter, faces.coerceIn(1, 4), if (budget.isFinite()) budget.coerceAtLeast(0f) else 1000f / 30, filterFollowsMakeup, lipstick)
    fun save(out: Bundle) {
        out.putStringArray("effectKeys", values.keys.toTypedArray()); out.putFloatArray("effectValues", values.values.toFloatArray())
        out.putString("effectLipstick", lipstick); out.putString("effectStyle", style); out.putString("effectFilter", filter)
        out.putBoolean("filterFollowsMakeup", filterFollowsMakeup)
        out.putInt("effectFaces", maxFaces); out.putFloat("effectBudget", frameBudgetMs)
    }
    companion object {
        fun defaults() = EffectState(
            mapOf(
                "smooth" to .6f, "white" to .48f, "rosy" to .1f, "slim" to 0f, "eyes" to 0f, "makeup" to .6f,
                "delspot" to .38f, "removePouch" to .45f, "removeNasolabial" to .35f,
                "faceThree" to .28f, "eyeBright" to .28f, "aegyoSal" to .9f, "clarity" to .12f
            ),
            null, null, 1, 1000f / 30
        )
        fun maleDefaults() = EffectState(
            mapOf(
                "smooth" to .65f, "white" to .38f, "rosy" to .12f,
                "slim" to .24f, "cheekV" to .16f, "cheekSmall" to .15f, "eyes" to .18f, "nose" to .14f,
                "makeup" to .6f, "delspot" to .48f, "removePouch" to .55f, "removeNasolabial" to .35f,
                "faceThree" to .18f, "eyeBright" to .28f, "aegyoSal" to .4f,
                "sharpen" to .22f, "clarity" to .06f, "lipstick:coral" to .26f
            ),
            null, null, 1, 1000f / 30, false, "coral"
        )
        fun restore(saved: Bundle?, fallback: EffectState = defaults()): EffectState {
            val keys = saved?.getStringArray("effectKeys") ?: return fallback
            val values = saved.getFloatArray("effectValues") ?: return fallback
            if (keys.size != values.size) return fallback
            return EffectState(keys.zip(values.toList()).toMap(), saved.getString("effectStyle"), saved.getString("effectFilter"), 1, 1000f / 30, saved.getBoolean("filterFollowsMakeup", false), saved.getString("effectLipstick")?.takeIf { selected -> EffectCatalog.lipsticks.any { it.key == selected } })
                .withTracking(saved.getInt("effectFaces", 1), saved.getFloat("effectBudget", 1000f / 30))
        }
    }
}
