package com.priyanshu.lutcam

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlin.math.abs

/** Camera2 wrapper that streams preview frames into the renderer's SurfaceTexture. */
class CameraController(
    private val context: Context,
    private val onPreview: (contentW: Int, contentH: Int, front: Boolean) -> Unit,
    private val reportError: (String) -> Unit
) {
    var facing = CameraCharacteristics.LENS_FACING_BACK

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    @Volatile private var generation = 0
    @Volatile private var device: CameraDevice? = null
    @Volatile private var session: CameraCaptureSession? = null
    private var surface: Surface? = null
    private var surfaceOwner: SurfaceTexture? = null

    private fun ensureThread(): Handler {
        handler?.let { return it }
        val t = HandlerThread("camera").also { it.start() }
        thread = t
        return Handler(t.looper).also { handler = it }
    }

    @SuppressLint("MissingPermission")
    fun open(st: SurfaceTexture) {
        close()
        val gen = ++generation
        val h = ensureThread()
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val id = pickCamera(mgr, facing) ?: pickCamera(mgr, null)
            if (id == null) {
                reportError("Camera nahi mila")
                return
            }
            val chars = mgr.getCameraCharacteristics(id)
            val isFront = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = map?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()
            if (sizes.isEmpty()) {
                reportError("Preview size nahi mila")
                return
            }
            val size = chooseSize(sizes)
            val sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val swapped = sensor == 90 || sensor == 270
            val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
            val continuousAf = afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            st.setDefaultBufferSize(size.width, size.height)
            if (swapped) onPreview(size.height, size.width, isFront) else onPreview(size.width, size.height, isFront)

            if (surfaceOwner !== st) {
                surface = Surface(st)
                surfaceOwner = st
            }
            val sf = surface ?: return

            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    if (gen != generation) {
                        cam.close(); return
                    }
                    device = cam
                    startSession(cam, sf, gen, continuousAf)
                }

                override fun onDisconnected(cam: CameraDevice) {
                    cam.close()
                    if (device === cam) device = null
                }

                override fun onError(cam: CameraDevice, error: Int) {
                    cam.close()
                    if (device === cam) device = null
                    if (gen == generation) reportError("Camera error ($error). App dobara kholo.")
                }
            }, h)
        } catch (e: Exception) {
            reportError("Camera open nahi hua: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun startSession(cam: CameraDevice, sf: Surface, gen: Int, continuousAf: Boolean) {
        try {
            cam.createCaptureSession(listOf(sf), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (gen != generation) {
                        s.close(); return
                    }
                    session = s
                    try {
                        val b = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        b.addTarget(sf)
                        if (continuousAf) {
                            b.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        b.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        s.setRepeatingRequest(b.build(), null, handler)
                    } catch (e: Exception) {
                        reportError("Preview start nahi hua: ${e.message}")
                    }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    reportError("Camera session fail")
                }
            }, handler)
        } catch (e: Exception) {
            reportError("Camera session error: ${e.message}")
        }
    }

    fun close() {
        generation++
        try {
            session?.close()
        } catch (e: Exception) {
        }
        session = null
        try {
            device?.close()
        } catch (e: Exception) {
        }
        device = null
    }

    fun shutdown() {
        close()
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun pickCamera(mgr: CameraManager, want: Int?): String? {
        for (id in mgr.cameraIdList) {
            val f = mgr.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            if (want == null || f == want) return id
        }
        return null
    }

    /** Biggest 16:9 size up to 1920x1080, which crops least on tall phone screens. */
    private fun chooseSize(sizes: Array<Size>): Size {
        val fit = sizes.filter { it.width <= 1920 && it.height <= 1080 }
        val wide = fit.filter { abs(it.width.toFloat() / it.height - 16f / 9f) < 0.02f }
        return wide.maxByOrNull { it.width * it.height }
            ?: fit.maxByOrNull { it.width * it.height }
            ?: sizes[0]
    }
}
