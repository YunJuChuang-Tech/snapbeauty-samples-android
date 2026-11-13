package ai.snapbeauty.samples

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Camera ownership stays on one handler; generations reject stale callbacks. */
class CameraSource(context: Context, private val listener: Listener) {
    interface Listener {
        fun configured(width: Int, height: Int, rotation: Int, mirror: Boolean)
        fun error(message: String)
    }
    private val context = context.applicationContext
    private val thread = HandlerThread("sample-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var generation = 0
    @Volatile private var stopped = false
    fun start(texture: SurfaceTexture, front: Boolean, displayDegrees: Int) {
        if (!stopped) handler.post { open(texture, front, displayDegrees) }
    }
    private fun open(texture: SurfaceTexture, front: Boolean, displayDegrees: Int) {
        close()
        val token = generation
        if (stopped) return
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            listener.error("需要相机权限才能预览"); return
        }
        try {
            val manager = context.getSystemService(CameraManager::class.java)
            val selected = manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            }
            if (selected == null) { listener.error(if (front) "此设备没有前置摄像头" else "此设备没有后置摄像头"); return }
            val characteristics = manager.getCameraCharacteristics(selected)
            val map = checkNotNull(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)) { "Camera output unavailable" }
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            val size = sizes.filter { it.width <= 1280 && it.height <= 720 }.minByOrNull {
                abs(it.width.toDouble() / it.height - 4.0 / 3.0) * 10000 + abs(it.width * it.height - 640 * 480)
            } ?: sizes[0]
            val sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val rotation = (sensor + (if (front) displayDegrees else -displayDegrees) + 360) % 360
            texture.setDefaultBufferSize(size.width, size.height)
            val output = Surface(texture).also { surface = it }
            listener.configured(size.width, size.height, rotation, front)
            manager.openCamera(selected, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (stopped || token != generation) { device.close(); return }
                    camera = device
                    try {
                        device.createCaptureSession(listOf(output), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(capture: CameraCaptureSession) {
                                if (stopped || token != generation) { capture.close(); return }
                                session = capture
                                try {
                                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(output)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                                    }
                                    capture.setRepeatingRequest(request.build(), null, handler)
                                } catch (error: CameraAccessException) { fail(token, "相机启动失败，请重试") }
                                catch (error: IllegalStateException) { fail(token, "相机启动失败，请重试") }
                            }
                            override fun onConfigureFailed(capture: CameraCaptureSession) { capture.close(); fail(token, "相机配置失败，请重试") }
                        }, handler)
                    } catch (error: CameraAccessException) { fail(token, "相机连接失败，请重试") }
                    catch (error: IllegalStateException) { fail(token, "相机连接失败，请重试") }
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); fail(token, "相机已断开，请重试") }
                override fun onError(device: CameraDevice, error: Int) { device.close(); fail(token, "相机暂不可用，请关闭其他相机应用后重试") }
            }, handler)
        } catch (error: CameraAccessException) { fail(token, "无法打开相机，请检查权限后重试") }
        catch (error: SecurityException) { fail(token, "无法打开相机，请检查权限后重试") }
        catch (error: IllegalStateException) { fail(token, "无法打开相机，请检查权限后重试") }
    }
    private fun fail(token: Int, message: String) {
        if (token != generation || stopped) return
        close(); listener.error(message)
    }
    private fun close() {
        generation++
        session?.close(); session = null
        camera?.close(); camera = null
        surface?.release(); surface = null
    }
    fun stop(): Boolean {
        stopped = true
        val done = CountDownLatch(1)
        handler.post { close(); done.countDown(); thread.quitSafely() }
        return try { done.await(2, TimeUnit.SECONDS) }
        catch (error: InterruptedException) { Thread.currentThread().interrupt(); false }
    }
}
