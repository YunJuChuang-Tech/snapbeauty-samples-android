package ai.snapbeauty.samples

import android.content.res.AssetManager
import org.json.JSONObject
import java.security.MessageDigest

/** Validate artwork before showing a style; a callable SDK API does not imply assets exist. */
internal object MakeupAssets {
    data class Catalog(val keys: Set<String>, val complete: Boolean, val choices: List<EffectChoice> = emptyList())
    private var cached: Pair<AssetManager, Catalog>? = null
    @Synchronized fun load(assets: AssetManager): Catalog {
        cached?.takeIf { it.first === assets }?.let { return it.second }
        val catalog = runCatching {
            val manifest = assets.open("sample_makeup/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
            validate(manifest) { path -> assets.open(path).use { it.readBytes() } }
        }.getOrElse { Catalog(emptySet(), false) }
        cached = assets to catalog
        return catalog
    }
    internal fun validate(manifest: JSONObject, read: (String) -> ByteArray): Catalog {
        require(manifest.getInt("schema") == 1)
        val files = manifest.getJSONObject("files")
        val styles = manifest.getJSONArray("styles")
        val valid = mutableSetOf<String>()
        val checked = mutableMapOf<String, Boolean>()
        val choices = mutableListOf<EffectChoice>()
        for (i in 0 until styles.length()) {
            val style = styles.getJSONObject(i)
            val required = style.getJSONArray("files")
            val complete = (0 until required.length()).map { required.getString(it) }.all { path ->
                checked.getOrPut(path) {
                    runCatching {
                        require(path.startsWith("makeup/") && !path.contains(".."))
                        val bytes = read(path)
                        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                        bytes.isNotEmpty() && sha == files.getJSONObject(path).getString("sha256")
                    }.getOrDefault(false)
                }
            }
            val filter = style.getString("filter")
            require(filter.matches(Regex("[a-z]+[0-9]+")))
            val filterPresent = checked.getOrPut("snapbeauty/luts/$filter.png") {
                runCatching { read("snapbeauty/luts/$filter.png").isNotEmpty() }.getOrDefault(false)
            }
            if (required.length() > 0 && complete && filterPresent) {
                val key = style.getString("key")
                valid.add(key); choices.add(EffectChoice(key, style.getString("name"), filter))
            }
        }
        return Catalog(valid, valid.size == styles.length(), choices)
    }
}
