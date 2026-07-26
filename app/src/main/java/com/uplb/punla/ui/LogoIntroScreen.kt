package com.uplb.punla.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.uplb.punla.R
import com.uplb.punla.ui.theme.LocalPunlaPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Logo animation shown once per process on cold launch (wired in
 * MainActivity's setContent, before PunlaApp). The leaf mark grows in with
 * a soft bounce while fading in, then the wordmark fades in underneath —
 * matching the web app's unused `.splash-name` styling (Fraunces 30px/600,
 * see PunlaTypography.headlineLarge) so this is finally rendered somewhere.
 *
 * Runs on the same Ink/LeafLight tones as the launcher icon
 * (ic_launcher_background / ic_launcher_foreground) so it reads as a
 * continuation of the tap-to-open icon rather than a separate asset.
 *
 * [userName] drives a "Good morning, Tj" style subtitle beneath the
 * wordmark — same time-of-day buckets and fallback-when-blank behavior as
 * the Dashboard greeting card, so the app leads with the same welcome the
 * moment it opens instead of only once you reach the home tab.
 */
@Composable
fun LogoIntroScreen(userName: String = "", onFinished: () -> Unit) {
    val palette = LocalPunlaPalette.current
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val displayGreeting = remember(userName) {
        if (userName.isNotBlank()) "$greeting, $userName" else null
    }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(320)) }
        logoScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
        textAlpha.animateTo(1f, tween(350))
        delay(550)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.ink),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = palette.paper,
                modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
            )
            if (displayGreeting != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    displayGreeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.paper.copy(alpha = 0.8f),
                    modifier = Modifier.graphicsLayer { alpha = textAlpha.value }
                )
            }
        }
    }
}
