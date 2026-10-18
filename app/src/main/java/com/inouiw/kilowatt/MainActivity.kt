package com.inouiw.kilowatt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.inouiw.kilowatt.india.KiloWattIndiaApp
import com.inouiw.kilowatt.ui.theme.KilowattTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KiloWattIndiaApp()
        }
    }
}

@Composable
fun KiloWattApp(viewModel: MeterViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val readings by viewModel.readings.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.HOME) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        viewModel.updateCameraPermission(isGranted)
    }

    LaunchedEffect(Unit) {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            viewModel.updateCameraPermission(granted = true)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (screen == AppScreen.HOME) {
        HomeDashboard(readings = readings, onScanClick = { screen = AppScreen.SCAN })
        return
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.hasCameraPermission) {
                CameraView(viewModel = viewModel)
                ScanningReticleOverlay(
                    isScanning = uiState.isScanning,
                    isLocked = uiState.isLocked,
                    onReticleTap = { }
                )
            } else {
                CameraPermissionRequestScreen {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

            TopHeaderBar(
                isScanning = uiState.isScanning,
                isLocked = uiState.isLocked,
                isVerified = uiState.isReadingVerified
            )

            TextButton(
                onClick = { screen = AppScreen.HOME },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 100.dp, end = 8.dp)
            ) {
                Text(text = "Home")
            }

            AnimatedVisibility(
                visible = uiState.isReadingVerified || uiState.hasFault,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ReadingResultSheet(
                    uiState = uiState,
                    onResetScan = { viewModel.resetScan() }
                )
            }
        }
    }
}

@Composable
fun CameraView(viewModel: MeterViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val analyzer = remember {
        MeterImageAnalyzer(
            context = context,
            isLocked = { viewModel.uiState.value.isLocked },
            onReadingDetected = { kwh, rawText, hasFault, faultKeyword ->
                viewModel.onMeterReadingDetected(kwh, rawText, hasFault, faultKeyword)
            }
        )
    }
    // Analyzer runs on the main executor, so closing here can't race with analyze()
    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(executor, analyzer)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                executor
            )

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ScanningReticleOverlay(
    isScanning: Boolean,
    isLocked: Boolean,
    onReticleTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LaserScan")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = !isLocked) { onReticleTap() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val reticleWidth = size.width * 0.8f
            val reticleHeight = 220.dp.toPx()
            val left = (size.width - reticleWidth) / 2f
            val top = (size.height - reticleHeight) / 2.4f
            val cornerLength = 36.dp.toPx()
            val strokeWidth = 4.dp.toPx()

            // Dim background exterior
            val outerPath = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
            }
            drawPath(
                path = outerPath,
                color = Color.Black.copy(alpha = 0.45f)
            )

            // Clear reticle viewport rectangle
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(reticleWidth, reticleHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                blendMode = BlendMode.Clear
            )

            // Reticle Border: Green when locked, Cyan when scanning
            val reticleColor = if (isLocked) Color(0xFF00E676) else Color(0xFF00E5FF)

            drawRoundRect(
                color = reticleColor.copy(alpha = 0.6f),
                topLeft = Offset(left, top),
                size = Size(reticleWidth, reticleHeight),
                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                style = Stroke(width = if (isLocked) 3.dp.toPx() else 2.dp.toPx())
            )

            // Corner Accent Bracket Lines
            // Top-Left
            drawLine(reticleColor, Offset(left, top + cornerLength), Offset(left, top), strokeWidth)
            drawLine(reticleColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth)
            // Top-Right
            drawLine(reticleColor, Offset((left + reticleWidth) - cornerLength, top), Offset(left + reticleWidth, top), strokeWidth)
            drawLine(reticleColor, Offset(left + reticleWidth, top), Offset(left + reticleWidth, top + cornerLength), strokeWidth)
            // Bottom-Left
            drawLine(reticleColor, Offset(left, (top + reticleHeight) - cornerLength), Offset(left, top + reticleHeight), strokeWidth)
            drawLine(reticleColor, Offset(left, top + reticleHeight), Offset(left + cornerLength, top + reticleHeight), strokeWidth)
            // Bottom-Right
            drawLine(reticleColor, Offset((left + reticleWidth) - cornerLength, top + reticleHeight), Offset(left + reticleWidth, top + reticleHeight), strokeWidth)
            drawLine(reticleColor, Offset(left + reticleWidth, (top + reticleHeight) - cornerLength), Offset(left + reticleWidth, top + reticleHeight), strokeWidth)

            // Animated Laser Line (only active when scanning and NOT locked)
            if (isScanning && !isLocked) {
                val laserY = top + (reticleHeight * laserProgress)
                drawLine(
                    color = Color(0xFF00FF88),
                    start = Offset(left + 8.dp.toPx(), laserY),
                    end = Offset((left + reticleWidth) - 8.dp.toPx(), laserY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 280.dp)
                .background(
                    if (isLocked) Color(0xFF00C853).copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.65f),
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isLocked) {
                Icon(
                    imageVector = Icons.Filled.TouchApp,
                    contentDescription = "Tap to Capture",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (isLocked) "Reading Locked In" else "Align meter in box or tap to lock",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TopHeaderBar(isScanning: Boolean, isLocked: Boolean, isVerified: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "KiloWatt AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Smart Meter Reader",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isLocked || isVerified -> Color(0xFF00E676).copy(alpha = 0.2f)
                    isScanning -> Color(0xFF29B6F6).copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ) {
                Text(
                    text = when {
                        isLocked || isVerified -> "READING LOCKED"
                        isScanning -> "SCANNING..."
                        else -> "READY"
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isLocked || isVerified -> Color(0xFF00E676)
                        isScanning -> Color(0xFF0288D1)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
fun CameraPermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Camera Access Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "KiloWatt uses your camera to scan electric meter readings in real-time, compute instant tariff quotes, and detect meter faults.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Grant Camera Permission")
            }
        }
    }
}
