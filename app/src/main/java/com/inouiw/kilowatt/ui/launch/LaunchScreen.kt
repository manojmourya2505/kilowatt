package com.inouiw.kilowatt.ui.launch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inouiw.kilowatt.R
import com.inouiw.kilowatt.ui.components.KwDot
import com.inouiw.kilowatt.ui.components.LocalReduceMotion
import com.inouiw.kilowatt.ui.theme.KwColor
import com.inouiw.kilowatt.ui.theme.KwType
import kotlinx.coroutines.delay

/** Shows the logo for 2.4 s, or until tapped, then calls [onReady]. */
@Composable
fun LaunchScreen(onReady: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    // The Neural Vision Core is not wired into this screen yet, so it waits the spec's 2.4 s.
    LaunchedEffect(Unit) {
        delay(2400)
        onReady()
    }

    val transition = rememberInfiniteTransition(label = "launch")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "glow",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val glowAlpha = if (reduceMotion) 0.6f else glow
    val pulseAlpha = if (reduceMotion) 1f else pulse

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KwColor.Bg)
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onReady() },
    ) {
        Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .background(
                        Brush.radialGradient(colors = listOf(KwColor.Mint.copy(alpha = 0.22f * glowAlpha), Color.Transparent)),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(KwColor.Surface2)
                        .border(1.dp, KwColor.Border, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_kilowatt_mark),
                        contentDescription = "KiloWatt logo",
                        modifier = Modifier.size(104.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = buildAnnotatedString {
                    append("Kilo")
                    withStyle(SpanStyle(color = KwColor.Mint)) { append("Watt") }
                },
                style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
                color = KwColor.Text,
            )
            Spacer(Modifier.height(8.dp))
            Text(text = "Smart Clean Energy Scanner", style = TextStyle(fontSize = 14.sp), color = KwColor.Muted)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .clip(RoundedCornerShape(50))
                .background(KwColor.Card)
                .border(1.dp, KwColor.Border, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KwDot(color = KwColor.Mint.copy(alpha = pulseAlpha), size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(text = "Neural Vision Core Ready", style = KwType.Caption, color = KwColor.Text)
        }
    }
}
