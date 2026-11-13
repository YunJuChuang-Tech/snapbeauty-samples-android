package ai.snapbeauty.samples

import android.app.Instrumentation
import ai.snapbeauty.core.FaceParams
import ai.snapbeauty.core.SkinParams

/** Cross-check the panel vocabulary against the SDK's typed public parameter objects. */
object SdkEffectChecks {
    @JvmStatic fun run(device: Instrumentation): Int {
        var checks = 0
        fun check(value: Boolean, message: String) { if (!value) throw AssertionError(message); checks++ }
        val aliases = mapOf("slim" to "faceSlimming", "eyes" to "eyeScale", "cheekV" to "faceVTaper", "cheekNarrow" to "faceNarrowing", "cheekShort" to "faceShortening", "cheekSmall" to "faceShrinking", "cheekbones" to "cheekboneWidth", "lowerJaw" to "jawWidth", "eyeCircle" to "eyeRoundness", "chin" to "chinLength", "forehead" to "foreheadHeight", "nose" to "noseWidth", "mouth" to "mouthSize", "lipThick" to "lipFullness", "eyeHeight" to "eyePosition", "canthus" to "eyeCornerOpening", "eyeLid" to "eyelidFold", "eyeSpace" to "eyeDistance", "eyeRotate" to "eyeTilt", "longNose" to "noseLength", "philtrum" to "philtrumLength", "smile" to "mouthCorners", "browHeight" to "browPosition", "browSpace" to "browDistance", "browThick" to "browThickness", "sharpen" to "sharpness", "clarity" to "texture", "delspot" to "blemishRemoval", "eyeBright" to "eyeBrightening", "toothWhiten" to "teethWhitening", "removePouch" to "undereyeBags", "removeNasolabial" to "smileLines", "faceThree" to "faceContour", "smooth" to "smoothing", "white" to "brightening", "rosy" to "rosiness")
        var state = EffectState.defaults()
        EffectCatalog.parameters.forEachIndexed { index, p -> state = state.withValue(p.key, (index + 1) / 40f) }
        val shape = ProcessorFactory.faceParams(state); val detail = ProcessorFactory.skinParams(state)
        for (p in EffectCatalog.parameters) {
            val name = aliases[p.key] ?: p.key
            val target = if (p in EffectCatalog.shape) shape else detail
            val actual = target.javaClass.getMethod("get" + name.replaceFirstChar { it.uppercase() }).invoke(target) as Float
            check(actual == state.value(p.key) * (if (p.key == "smooth") 6f else 1f), "Distinct parameter reaches typed SDK field: ${p.key}")
        }
        check(ProcessorFactory.faceParams(EffectState.defaults()) == FaceParams(), "Default bidirectional controls equal SDK neutral shape")
        check(ProcessorFactory.skinParams(EffectState(0f, 0f, 0f, 0f, 0f, 0f, null)) == SkinParams(), "Default extra skin controls equal SDK neutral detail")
        check(ai.snapbeauty.core.SnapBeautyEngine.VERSION == "1.3.0", "Published SDK version is 1.3.0")
        val resources = ProcessorFactory.resources(device.targetContext)
        val keys = device.targetContext.assets.list("snapbeauty/luts").orEmpty().filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.toSet()
        check(resources.filters.mapNotNull { it.key }.toSet() == keys, "Filter menu exactly matches supplied AAR resources")
        check(resources.styles.size == 16, "All 15 public whole-makeup styles plus no makeup are exposed")
        for (choice in resources.styles.filter { it.key != null }) {
            check(choice.makeupFilter in keys, "Preset references a supplied, renamed LUT: ${choice.key}")
            val paired = EffectState.defaults().selectStyle(choice).withValue("makeup", .8f)
            check(paired.filter == choice.makeupFilter && paired.filterIntensity == .8f, "Makeup intensity drives associated filter: ${choice.key}")
            val bundle = android.os.Bundle(); paired.save(bundle)
            val restoredPair = EffectState.restore(bundle).withValue("makeup", .25f)
            check(restoredPair.filterFollowsMakeup && restoredPair.filterIntensity == .25f, "Restored paired state keeps intensity linkage: ${choice.key}")
            val manual = paired.withFilter("warm1").withValue("filterIntensity", .37f).withValue("makeup", .2f)
            check(manual.filter == "warm1" && manual.filterIntensity == .37f && !manual.filterFollowsMakeup, "Manual filter survives subsequent makeup intensity changes: ${choice.key}")
            check(paired.selectStyle(null).filter == null && manual.selectStyle(null).filter == "warm1", "No makeup clears only the automatic preset filter: ${choice.key}")
            check(manual.selectStyle(choice).filter == choice.makeupFilter, "Selecting a preset again restores its LUT: ${choice.key}")
        }
        return checks
    }
}
