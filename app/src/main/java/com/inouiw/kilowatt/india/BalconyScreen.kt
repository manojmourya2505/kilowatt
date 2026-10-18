package com.inouiw.kilowatt.india

import android.Manifest
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

// Estimate assumptions, labelled "Estimate" on screen.
private const val PANEL_AREA_M2 = 2.6f         // about 2.3 m x 1.1 m for a 550 W unit; check the datasheet
private const val USABLE_FRACTION = 0.6f       // keep 40% of the balcony free for access and clearance
private const val PANEL_KW = 0.55f             // 550 W units
private const val YIELD_KWH_PER_KWP = 1300f    // approximate yearly yield per kWp
private const val TARIFF_INR_PER_KWH = 7.85

@Composable
fun BalconyScreen(onSaved: (spanM: Float, areaM2: Float, panels: Int) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val session = remember(granted) { if (granted) createArSession(context) else null }
    DisposableEffect(session) {
        onDispose { session?.close() }
    }
    val renderer = remember(session) { session?.let { ArBalconyRenderer(it) } }
    val glViewHolder = remember { arrayOfNulls<GLSurfaceView>(1) }

    // ARCore and the GL view follow the screen's lifecycle
    DisposableEffect(lifecycleOwner, session) {
        fun resume() {
            try {
                session?.resume()
            } catch (e: Exception) {
                Log.e(TAG, "ARCore resume failed", e)
            }
            glViewHolder[0]?.onResume()
        }
        fun pause() {
            glViewHolder[0]?.onPause()
            session?.pause()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            pause()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val stateFlow: Flow<BalconyArState> = renderer?.state ?: flowOf(BalconyArState())
    val arState by stateFlow.collectAsState(initial = BalconyArState())

    Box(modifier = Modifier.fillMaxSize().background(KwColor.Camera)) {
        when {
            !granted -> Text(
                text = "Camera permission needed for the balcony survey",
                style = KwType.Caption,
                color = KwColor.Muted,
                modifier = Modifier.align(Alignment.Center),
            )
            renderer == null -> Text(
                text = "ARCore is not available on this phone. The balcony survey needs an ARCore-supported device.",
                style = KwType.Body,
                color = KwColor.FaultText,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            )
            else -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            setEGLContextClientVersion(2)
                            setRenderer(renderer)
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            glViewHolder[0] = this
                        }
                    },
                )
                // Dim the live camera to 35% behind the overlay (spec)
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))

                // The measured balcony surface, drawn where ARCore found it
                arState.plane?.let { plane ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            val first = plane.cornersPx.first()
                            moveTo(first.first, first.second)
                            plane.cornersPx.drop(1).forEach { lineTo(it.first, it.second) }
                            close()
                        }
                        drawPath(path, KwColor.CyanFill)
                        drawPath(
                            path,
                            KwColor.Cyan,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))),
                        )
                    }
                }

                BalconyOverlay(
                    arState = arState,
                    onSaved = onSaved,
                )
            }
        }
    }
}

@Composable
private fun BalconyOverlay(arState: BalconyArState, onSaved: (spanM: Float, areaM2: Float, panels: Int) -> Unit) {
    val plane = arState.plane
    val ready = plane?.stable == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(KwRadius.Pill))
                .background(KwColor.GlassFillStrong)
                .border(1.dp, KwColor.Border, RoundedCornerShape(KwRadius.Pill))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Balcony AR Solar", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = KwColor.Text)
            when {
                ready -> KwPill("● PLANE TRACKED", KwColor.Cyan, KwColor.CyanFill)
                plane != null -> KwPill("● MEASURING", KwColor.Gold, KwColor.GoldFill)
                arState.cameraTracking -> KwPill("● SEARCHING", KwColor.Gold, KwColor.GoldFill)
                else -> KwPill("● STARTING", KwColor.Muted, KwColor.Card)
            }
        }

        Spacer(Modifier.weight(1f))

        if (plane == null) {
            Text(
                text = "Move your phone slowly across the balcony",
                style = KwType.Body,
                color = KwColor.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(KwRadius.Card))
                    .background(KwColor.GlassFill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Text(
                text = if (ready) {
                    String.format(Locale.UK, "Measured span %.1f m · area %.1f m²", plane.spanM, plane.areaM2)
                } else {
                    "Measuring… keep the phone steady over the balcony"
                },
                style = KwType.Caption,
                color = KwColor.Cyan,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(KwRadius.Pill))
                    .background(KwColor.GlassFill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
        }

        KwCard(color = KwColor.GlassFillStrong, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(KwRadius.Inner))
                            .background(KwColor.GoldFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Filled.WbSunny, contentDescription = null, tint = KwColor.Gold, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    val title = when {
                        plane == null -> "Waiting for a measured balcony surface"
                        !ready -> "Measuring the balcony surface…"
                        else -> "Recommended fit: ${panelsFor(plane.areaM2)}× 550W balcony units"
                    }
                    Text(text = title, style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
                }
                if (plane != null && ready) {
                    val panels = panelsFor(plane.areaM2)
                    val generationKwh = panels * PANEL_KW * YIELD_KWH_PER_KWP
                    val offsetInr = (generationKwh * TARIFF_INR_PER_KWH).toLong()
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FitStat(Modifier.weight(1f), String.format(Locale.UK, "%,.0f kWh", generationKwh), "Estimate · generation / year", KwColor.Text)
                        FitStat(Modifier.weight(1f), "~${formatInr(offsetInr)}", "Estimate · offset / year", KwColor.Gold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (plane != null && ready) onSaved(plane.spanM, plane.areaM2, panelsFor(plane.areaM2))
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(KwRadius.Inner),
            colors = ButtonDefaults.buttonColors(containerColor = KwColor.Mint, contentColor = KwColor.OnMint),
        ) {
            Text(text = "Lock Dimensions & Save to Quote", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

// Panels that fit in the usable part of the measured area
private fun panelsFor(areaM2: Float): Int =
    ((areaM2 * USABLE_FRACTION) / PANEL_AREA_M2).toInt().coerceAtLeast(0)

@Composable
private fun FitStat(modifier: Modifier, value: String, caption: String, valueColor: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(KwRadius.Inner))
            .background(KwColor.Surface2)
            .padding(12.dp),
    ) {
        Text(text = value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = valueColor)
        Text(text = caption, style = KwType.Caption, color = KwColor.Muted)
    }
}

// Null when ARCore is unsupported or cannot start on this phone
private fun createArSession(context: Context): Session? = try {
    val availability = ArCoreApk.getInstance().checkAvailability(context)
    if (availability.isUnsupported) {
        null
    } else {
        Session(context).also { session ->
            val config = Config(session).apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
            session.configure(config)
        }
    }
} catch (e: Exception) {
    Log.e("BalconyScreen", "ARCore session could not start", e)
    null
}

private const val TAG = "BalconyScreen"
