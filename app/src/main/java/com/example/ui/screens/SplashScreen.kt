package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalLudoTheme
import com.example.ui.theme.ludoShadow
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val theme = LocalLudoTheme.current
    val scale = remember { Animatable(0.7f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(350))
        scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 360f))
        delay(1250L)
        onSplashFinished()
    }

    val titleColor = theme.accessibleAccentOn(theme.blue, theme.bg)
    val badgeBg = lerp(theme.surface, theme.yellow, if (theme.isDarkSurface) 0.22f else 0.16f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .safeDrawingPadding()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSplashFinished
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    this.alpha = alpha.value
                }
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .ludoShadow(theme.cardShadow, 36.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .border(3.dp, theme.surface, RoundedCornerShape(36.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_ludo_hero),
                    contentDescription = stringResource(R.string.cd_hero_art),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = titleColor,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .ludoShadow(theme.cardShadow, 999.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(badgeBg)
                    .border(1.dp, theme.chipLine, RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.surfaceText,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Tap anywhere to start",
                style = MaterialTheme.typography.labelMedium,
                color = theme.mutedText
            )
        }
    }
}
