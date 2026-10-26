package com.jiotv.androidtv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.jiotv.androidtv.R
import com.jiotv.androidtv.server.EmbeddedServerState
import com.jiotv.androidtv.ui.theme.Jtv
import com.jiotv.androidtv.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SplashScreen(
    viewModel: MainViewModel,
    onSplashFinished: () -> Unit
) {
    val c = Jtv.colors
    val serverState by viewModel.serverState.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Smooth Entrance Animations
    val scaleAnim = remember { Animatable(0.75f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        alphaAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 600)
        )
    }

    // Auto-advance logic: wait at least 1300ms, then advance when channels are loaded or on timeout
    LaunchedEffect(channels, serverState, isLoading) {
        delay(1200) // Minimum branded splash display duration
        val isReady = channels.isNotEmpty() || (serverState as? EmbeddedServerState.Running)?.isReady == true
        if (isReady || !isLoading) {
            delay(300)
            onSplashFinished()
        } else {
            // Safety timeout after 2800ms
            delay(1500)
            onSplashFinished()
        }
    }

    val statusText = when {
        channels.isNotEmpty() -> "Loading channels (${channels.size})..."
        serverState is EmbeddedServerState.Running -> "Streaming engine ready..."
        serverState is EmbeddedServerState.Starting -> "Starting NTV engine..."
        serverState is EmbeddedServerState.PreparingBinary -> "Preparing NTV server..."
        else -> "Welcome to NTV"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .focusable()
            .onKeyEvent { event ->
                // Allow user to skip immediately with any key
                if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.Back) {
                    onSplashFinished()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
        ) {
            // NTV Brand Logo
            Image(
                painter = painterResource(id = R.drawable.ntv_splash_logo),
                contentDescription = "NTV Logo",
                modifier = Modifier
                    .size(170.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Brand Typography
            Text(
                text = "NTV",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "LIVE TV & STREAMING",
                color = Color(0xFF38BDF8), // Electric cyan
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Subdued Loading Indicator
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(0xFFA855F7), // Glowing purple
                strokeWidth = 2.5.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = statusText,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Version badge at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(alphaAnim.value)
        ) {
            Text(
                text = "v1.1",
                color = Color(0xFF475569),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
