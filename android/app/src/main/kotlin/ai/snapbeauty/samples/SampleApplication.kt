package ai.snapbeauty.samples

import android.app.Application
import ai.snapbeauty.core.SnapBeauty
import android.util.Log
import android.widget.Toast

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SnapBeauty.setLicenseErrorListener { status ->
            Log.e("SnapBeautySamples", "SDK authorization error: ${status.code}; original frames retained")
            Toast.makeText(this, "美颜授权不可用，当前显示原图。请检查应用授权配置。", Toast.LENGTH_LONG).show()
        }
        val license = runCatching { assets.open("snapbeauty.license").bufferedReader().use { it.readText() } }.getOrDefault("")
        SnapBeauty.init(this, license)
    }
}
