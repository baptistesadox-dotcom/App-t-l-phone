package fr.epure.launcher.data.system

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Lampe torche (flash de l'appareil photo arrière), sans autorisation particulière. */
class TorchController(context: Context) {

    private val cameras = context.applicationContext.getSystemService(CameraManager::class.java)
    private val cameraId: String? = runCatching {
        cameras?.cameraIdList?.firstOrNull { id ->
            val info = cameras.getCameraCharacteristics(id)
            info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()

    @Volatile
    private var on = false

    init {
        val id = cameraId
        if (id != null) {
            runCatching {
                cameras?.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(changedId: String, enabled: Boolean) {
                            if (changedId == id) on = enabled
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
            }
        }
    }

    val isAvailable: Boolean get() = cameraId != null

    /** Allume ou éteint la lampe ; renvoie le nouvel état, ou null si c'est impossible. */
    fun toggle(): Boolean? {
        val id = cameraId ?: return null
        return runCatching {
            cameras?.setTorchMode(id, !on)
            on = !on
            on
        }.onFailure { Log.w("Torch", "Lampe indisponible", it) }.getOrNull()
    }
}
