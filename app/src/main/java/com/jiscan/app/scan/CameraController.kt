package com.jiscan.app.scan

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class TorchMode { AUTO, ON, OFF }

/** Thin wrapper around CameraX LifecycleCameraController. */
class CameraController(context: Context) {
    val controller = LifecycleCameraController(context).apply {
        cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun bind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        controller.bindToLifecycle(lifecycleOwner)
        previewView.controller = controller
    }

    fun setTorch(mode: TorchMode) {
        try {
            // AUTO relies on the camera's auto exposure; torch is only forced when ON.
            controller.enableTorch(mode == TorchMode.ON)
        } catch (_: Exception) {}
    }

    fun isTorchOn(): Boolean = try { controller.isTorchOn } catch (_: Exception) { false }

    fun hasFlash(): Boolean = try { controller.cameraInfo?.hasFlashUnit() ?: false } catch (_: Exception) { false }

    fun capture(file: File, onResult: (Boolean, Exception?) -> Unit) {
        controller.takePicture(file, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onResult(true, null)
            }
            override fun onError(exception: ImageCaptureException) {
                onResult(false, exception)
            }
        })
    }

    fun shutdown() {
        try { executor.shutdownNow() } catch (_: Exception) {}
        try { controller.unbind() } catch (_: Exception) {}
    }
}
