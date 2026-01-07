package com.company.fieldapp.ui.attendance

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun CameraPreviewScreen(
    onImageCaptured: (File) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProviderFuture.get().unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val cameraSelector = if (
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                        ) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )

                    } catch (e: Exception) {
                        onError(e.message ?: "Camera bind failed")
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // Button positioned at bottom with padding
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .height(56.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter),
            onClick = {
                val photoFile = File(
                    context.cacheDir,
                    "selfie_${System.currentTimeMillis()}.jpg"
                )

                val outputOptions =
                    ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture?.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(
                            outputFileResults: ImageCapture.OutputFileResults
                        ) {
                            onImageCaptured(photoFile)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            onError(exception.message ?: "Capture failed")
                        }
                    }
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text(
                text = "Capture Selfie",
                color = Color.White
            )
        }
    }
}