package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalAccentColor

@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    bottomPadding: Dp = 20.dp,
    endPadding: Dp = 20.dp
) {
    val haptic = LocalHapticFeedback.current
    val accent = LocalAccentColor.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = if (isPressed) 0.90f else 1.0f

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)) +
                scaleIn(initialScale = 0.75f, animationSpec = tween(250)) +
                slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)) +
                scaleOut(targetScale = 0.75f, animationSpec = tween(200)) +
                slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding, end = endPadding)
                .size(size)
                .scale(scale)
                .shadow(elevation = 8.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(accent)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_arrow_up_circle),
                contentDescription = "Scroll to top",
                tint = Color.White,
                modifier = Modifier.size(size * 0.65f)
            )
        }
    }
}
