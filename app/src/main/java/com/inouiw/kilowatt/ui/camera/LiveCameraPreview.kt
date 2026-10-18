package com.inouiw.kilowatt.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inouiw.kilowatt.ui.theme.KwColor
import com.inouiw.kilowatt.ui.theme.KwType
import java.util.concurrent.Executors

/**
 * Live back-camera preview with an optional analyzer. Asks for camera permission first.
 * TextureView mode is used so the preview draws correctly under Compose layers.
 */
@Composable
fun LiveCameraPreview(modifier: Modifier = Modifier, analyzer: ImageAnalysis.Analyzer? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Box(modifier = modifier.background(KwColor.Camera), contentAlignment = Alignment.Center) {
            Text(text = "Camera permission needed", style = KwType.Caption, color = KwColor.Muted)
        }
        return
    }

    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val boundUseCases = remember { mutableListOf<UseCase>() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                providerHolder[0] = provider
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val useCases = mutableListOf<UseCase>(preview)
                if (analyzer != null) {
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(analysisExecutor, analyzer) }
                    useCases += analysis
                }
                provider.unbindAll()
                boundUseCases.clear()
                boundUseCases.addAll(useCases)
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )

    // Remove only this screen's use cases, so a screen that has just bound its own preview keeps working
    DisposableEffect(Unit) {
        onDispose {
            val provider = providerHolder[0]
            if (provider != null) {
                boundUseCases.forEach { if (provider.isBound(it)) provider.unbind(it) }
            }
        }
    }
}
