package ai.snapbeauty.samples

import android.content.res.AssetManager
import ai.snapbeauty.core.SnapBeautyEngine

/**
 * Filter LUTs this app ships itself (assets/host-filters/luts/<id>.png) and registers with the SDK.
 * They are adapted from the RawTherapee Film Simulation Collection under CC BY-SA 4.0; attribution,
 * changes and the licence text ship next to them in assets/host-filters/ (NOTICE.md, LICENSE-CC-BY-SA-4.0.txt).
 */
object HostFilters {
    private const val DIR = "host-filters/luts"
    private const val SDK_DIR = "snapbeauty/luts"
    fun ids(assets: AssetManager): List<String> =
        assets.list(DIR).orEmpty().filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.sorted()
    fun builtIn(assets: AssetManager): List<String> =
        assets.list(SDK_DIR).orEmpty().filter { it.endsWith(".png") }.map { it.removeSuffix(".png") }.sorted()
    /** Asset path of a filter LUT: the SDK's built-in one, else this app's host pack. */
    fun lutPaths(id: String) = listOf("$SDK_DIR/$id.png", "$DIR/$id.png")
    /** Registers every host LUT on [engine]; afterwards setFilter(id, strength) works as for built-in filters. */
    fun register(assets: AssetManager, engine: SnapBeautyEngine) {
        for (id in ids(assets)) engine.registerFilter(id, assets.open("$DIR/$id.png").use { it.readBytes() })
    }
}
