package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
  val logoScale = remember { Animatable(0.84f) }
  val contentAlpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    launch {
      logoScale.animateTo(
        targetValue = 1f,
        animationSpec =
          spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
          ),
      )
    }
    launch {
      contentAlpha.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 360),
      )
    }
  }

  val bgTop = Color(0xFFF4F8FC)
  val bgBottom = Color(0xFFFFFFFF)
  val brandBlue = Color(0xFF0B63B8)
  val brandBlueLight = Color(0xFF3898EC)
  val brandInk = Color(0xFF0E2239)
  val brandMuted = Color(0xFF4A627D)

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(colors = listOf(bgTop, bgBottom)))
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(brandBlueLight.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.minDimension * 0.58f,
              ),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.minDimension * 0.58f,
          )
        }
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 24.dp, vertical = 32.dp)
        .testTag("splash_screen"),
  ) {
    Column(
      modifier =
        Modifier.align(Alignment.Center)
          .alpha(contentAlpha.value)
          .padding(bottom = 36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.size(152.dp).scale(logoScale.value),
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
          Image(
            painter = painterResource(id = R.drawable.logo_principal),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = brandInk,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Control financiero personal • Offline-first",
        style = MaterialTheme.typography.bodyMedium,
        color = brandMuted,
        textAlign = TextAlign.Center,
      )
    }

    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .alpha(contentAlpha.value)
          .padding(bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      LinearProgressIndicator(
        modifier = Modifier.width(180.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = brandBlue,
        trackColor = brandBlue.copy(alpha = 0.16f),
      )
      Text(
        text = "Iniciando catálogos y saldos...",
        style = MaterialTheme.typography.labelMedium,
        color = brandMuted,
      )
    }
  }
}
