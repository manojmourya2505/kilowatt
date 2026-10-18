package com.inouiw.kilowatt.ui.balcony

import android.opengl.GLSurfaceView
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.inouiw.kilowatt.ar.ArBalconyRenderer
import com.inouiw.kilowatt.ar.BalconyArState
import com.inouiw.kilowatt.domain.Estimates
import com.inouiw.kilowatt.ui.components.KwCard
import com.inouiw.kilowatt.ui.components.KwPill
import com.inouiw.kilowatt.ui.components.formatInr
import com.inouiw.kilowatt.ui.theme.KwColor
import com.inouiw.kilowatt.ui.theme.KwRadius
import com.inouiw.kilowatt.ui.theme.KwType
import java.util.Locale

// Tariff used only for the offset estimate on this screen. The rate is confirmed from the bill, see TariffSchedule.
private const val ESTIMATE_RATE_INR_PER_KWH = 1.95

/**
 * The balcony survey: live ARCore camera, the measured surface drawn on it, and the estimate.
 * [renderer] is null when ARCore is not available on this phone.
 */
@Composable
fun BalconyScreen(
    renderer: ArBalconyRenderer?,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onSaved: (spanM: Float, areaM2: Float, panels: Int) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val glViewHolder = remember { arrayOfNulls<GLSurfaceView>(1) }

    // ARCore and the GL view follow the screen's lifecycle
    DisposableEffect(lifecycleOwner, renderer) {
        fun resume() {
            onResume()
            glViewHolder[0]?.onResume()
        }
        fun pause() {
            glViewHolder[0]?.onPause()
            onPause()
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

    val stateFlow = renderer?.state ?: remember { MutableStateFlow(BalconyArState()) }
    val arState by stateFlow.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(KwColor.Camera)) {
        when {
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

                BalconyOverlay(arState = arState, onSaved = onSaved)
            }
        }
    }
}

@Composable
private fun BalconyOverlay(
    arState: BalconyArState,
    onSaved: (spanM: Float, areaM2: Float, panels: Int) -> Unit,
) {
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
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(KwRadius.Inner)).background(KwColor.GoldFill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(imageVector = Icons.Filled.WbSunny, contentDescription = null, tint = KwColor.Gold, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    val title = when {
                        plane == null -> "Waiting for a measured balcony surface"
                        !ready -> "Measuring the balcony surface…"
                        else -> "Recommended fit: ${Estimates.panelsFor(plane.areaM2)}× 550W balcony units"
                    }
                    Text(text = title, style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
                }
                if (plane != null && ready) {
                    val panels = Estimates.panelsFor(plane.areaM2)
                    val generationKwh = Estimates.yearlyGenerationKwh(panels)
                    val offsetInr = (generationKwh * ESTIMATE_RATE_INR_PER_KWH).toLong()
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
                if (plane != null && ready) onSaved(plane.spanM, plane.areaM2, Estimates.panelsFor(plane.areaM2))
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
