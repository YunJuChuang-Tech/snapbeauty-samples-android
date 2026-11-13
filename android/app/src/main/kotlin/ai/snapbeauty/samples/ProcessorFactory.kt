package ai.snapbeauty.samples

import android.content.Context
import ai.snapbeauty.core.SnapBeautyEngine
import ai.snapbeauty.core.FaceParams
import ai.snapbeauty.core.SkinParams
import kotlin.math.roundToInt

object ProcessorFactory {
    fun resources(context: Context): EffectResources {
        val filters = context.assets.list("snapbeauty/luts").orEmpty().filter { it.endsWith(".png") }
            .map { it.removeSuffix(".png") }.sorted().map { EffectChoice(it, EffectCatalog.filterName(it)) }
        val makeup = MakeupAssets.load(context.assets)
        return EffectResources(listOf(EffectChoice(null, "无妆")) + makeup.choices,
            listOf(EffectChoice(null, "原色")) + filters,
            if (makeup.complete) null else "妆容资源不完整，请重新安装完整示例")
    }
    internal fun faceParams(state: EffectState) = FaceParams(
                faceSlimming = state.slim, faceVTaper = state.value("cheekV"), faceNarrowing = state.value("cheekNarrow"), faceShortening = state.value("cheekShort"),
                faceShrinking = state.value("cheekSmall"), cheekboneWidth = state.value("cheekbones"), jawWidth = state.value("lowerJaw"),
                eyeScale = state.eyes, eyeRoundness = state.value("eyeCircle"), chinLength = state.value("chin"), foreheadHeight = state.value("forehead"),
                noseWidth = state.value("nose"), mouthSize = state.value("mouth"), lipFullness = state.value("lipThick"), eyePosition = state.value("eyeHeight"),
                eyeCornerOpening = state.value("canthus"), eyelidFold = state.value("eyeLid"), eyeDistance = state.value("eyeSpace"), eyeTilt = state.value("eyeRotate"),
                noseLength = state.value("longNose"), philtrumLength = state.value("philtrum"), mouthCorners = state.value("smile"),
                browPosition = state.value("browHeight"), browDistance = state.value("browSpace"), browThickness = state.value("browThick"))
    internal fun skinParams(state: EffectState) = SkinParams(
                smoothing = state.smooth * 6f, brightening = state.white, rosiness = state.rosy,
                sharpness = state.value("sharpen"), texture = state.value("clarity"), blemishRemoval = state.value("delspot"),
                eyeBrightening = state.value("eyeBright"), teethWhitening = state.value("toothWhiten"), undereyeBags = state.value("removePouch"),
                smileLines = state.value("removeNasolabial"), faceContour = state.value("faceThree"))
    fun create(context: Context): BeautyProcessor = object : BeautyProcessor {
        private val makeup = MakeupAssets.load(context.assets)
        private val engine = SnapBeautyEngine(context.applicationContext)
        private val bridge = SdkTextureBridge()
        private var missingMakeup = false
        private var neutral = false
        override fun available() = SnapBeautyEngine.isSupported()
        override fun label() = "美颜与美妆"
        override fun apply(state: EffectState) {
            engine.skin = skinParams(state)
            engine.face = faceParams(state)
            missingMakeup = state.style != null && state.style !in makeup.keys
            neutral = EffectCatalog.parameters.all { state.value(it.key) == it.neutral } &&
                (state.style == null || missingMakeup || state.makeup <= 0f) && (state.filter == null || state.filterIntensity <= 0f)
            engine.setMakeup(if (missingMakeup || state.makeup <= 0f) null else state.style, state.makeup)
            engine.setFilter(state.filter, state.filterIntensity)
            engine.maxFaces = state.maxFaces; engine.frameBudgetMs = state.frameBudgetMs
        }
        override fun process(texture: Int, width: Int, height: Int): Int {
            if (neutral || !available()) return texture
            val input = bridge.flip(texture, width, height, 0)
            val output = engine.process(input, width, height)
            return if (output == input) texture else bridge.flip(output, width, height, 1)
        }
        override fun status() = if (missingMakeup) "妆容资源不完整，请重新安装完整示例" else
            (if (engine.faceDetected) "已识别 ${engine.faceCount} 人脸" else "将面部放入画面") +
            "  ·  ${engine.lastCostMs.roundToInt()} ms"
        override fun resetTracking() = engine.resetSmoothing()
        override fun release() { engine.release(); bridge.release() }
    }
}
