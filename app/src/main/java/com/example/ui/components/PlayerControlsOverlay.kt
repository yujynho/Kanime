package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.network.StreamQuality

fun formatQualityDisplayLabel(raw: String): String {
    val lower = raw.lowercase().trim()
    return when {
        lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> "2160p"
        lower.contains("1440") || lower.contains("2k") || lower.contains("qhd") -> "1440p"
        lower.contains("1080") || lower.contains("fhd") || lower == "hd" -> "1080p"
        lower.contains("720") -> "720p"
        lower.contains("480") -> "480p"
        lower.contains("360") && !lower.contains("vr") -> "360p"
        else -> {
            val digits = raw.filter { it.isDigit() }
            if (digits.isNotEmpty()) "${digits}p" else "1080p"
        }
    }
}

@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    isFullscreen: Boolean,
    isLandscape: Boolean,
    title: String,
    isPlaying: Boolean,
    currentPos: Long,
    bufferedPos: Long,
    duration: Long,
    isBuffering: Boolean = false,
    is4kOrHdr: Boolean = false,
    isVrMode: Boolean = false,
    vrUseGyro: Boolean = true,
    vrStereoMode: Int = androidx.media3.common.C.STEREO_MODE_MONO,
    qualities: List<StreamQuality> = emptyList(),
    selectedQualityIndex: Int = 0,
    onSelectQuality: ((Int) -> Unit)? = null,
    onToggleVrMode: (() -> Unit)? = null,
    onToggleVrGyro: (() -> Unit)? = null,
    onRecenterVr: (() -> Unit)? = null,
    onToggleVrStereoMode: (() -> Unit)? = null,
    onBack: () -> Unit,
    onRewind10s: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onForward10s: () -> Unit,
    onEnterPip: () -> Unit,
    onOpenExternal: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onSeekLive: (targetMs: Long) -> Unit,
    onSeekFinal: (targetMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettingsPopup by remember { mutableStateOf(false) }
    var isQualityExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible || showSettingsPopup,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        ) {
            // Dismiss scrim when popup is open
            if (showSettingsPopup) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showSettingsPopup = false
                            isQualityExpanded = false
                        }
                )
            }

            // Top Bar
            if (isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (is4kOrHdr) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.75f))
                            ) {
                                Text(
                                    text = "4K HDR",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color.White.copy(alpha = 0.20f), CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .testTag("back_player_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_back),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.20f), CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("back_inline_player"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_back),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Center 3-Button Controls (10s Rewind, Play/Pause, 10s Forward) - Hidden during Buffering
            AnimatedVisibility(
                visible = !isBuffering,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                val centerIconSize = if (isFullscreen) 52.dp else 42.dp
                val centerGap = if (isFullscreen) 40.dp else 18.dp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(centerGap)
                ) {
                    IconButton(
                        onClick = onRewind10s,
                        modifier = Modifier.size(centerIconSize)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_rewind),
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 36.dp else 28.dp)
                        )
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(centerIconSize)
                            .testTag(if (isFullscreen) "play_pause_button" else "inline_play_pause_button")
                    ) {
                        Icon(
                            painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 38.dp else 30.dp)
                        )
                    }

                    IconButton(
                        onClick = onForward10s,
                        modifier = Modifier.size(centerIconSize)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_player_forward),
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 36.dp else 28.dp)
                        )
                    }
                }
            }

            // Bottom Timeline & Actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                    .padding(
                        horizontal = if (isFullscreen) 36.dp else 16.dp,
                        vertical = if (isFullscreen) 8.dp else 6.dp
                    )
                    .align(Alignment.BottomCenter)
            ) {
                // 1. Scrubber Bar at the top of bottom controls with reduced length
                PlayerTimelineScrubber(
                    currentPos = currentPos,
                    bufferedPos = bufferedPos,
                    duration = duration,
                    isFullscreen = isFullscreen,
                    onScrubbingChanged = onScrubbingChanged,
                    onSeekLive = onSeekLive,
                    onSeekFinal = onSeekFinal,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(if (isFullscreen) 4.dp else 2.dp))

                // 2. Action buttons on the LEFT and Duration text on the RIGHT
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Actions on the LEFT
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 8.dp else 6.dp)
                    ) {

                            // VR 360° Magic Window Suite (Only enabled in Fullscreen)
                            if (isFullscreen && onToggleVrMode != null) {
                                if (isVrMode) {
                                    // 1. Stereo Demux Switcher (Mono 360° vs SBS Single-Eye Demux)
                                    if (onToggleVrStereoMode != null) {
                                        val isSbs = vrStereoMode == androidx.media3.common.C.STEREO_MODE_LEFT_RIGHT
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color.Black.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, if (isSbs) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.35f)),
                                            modifier = Modifier
                                                .height(26.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable(onClick = onToggleVrStereoMode)
                                                .testTag("vr_stereo_toggle_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = if (isSbs) "SBS 3D" else "360°",
                                                    color = if (isSbs) Color(0xFF38BDF8) else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // 2. Gyroscope Motion Toggle (Gyro Window vs Touch Pan Only)
                                    if (onToggleVrGyro != null) {
                                        PlayerOverlayActionButton(
                                            onClick = onToggleVrGyro,
                                            iconRes = R.drawable.ic_vr_gyro,
                                            contentDescription = if (vrUseGyro) "Gyroscope Motion Active" else "Touch Pan Only",
                                            testTag = "vr_gyro_toggle_button",
                                            isFullscreen = isFullscreen,
                                            isActive = vrUseGyro
                                        )
                                    }

                                    // 3. Recenter View Camera Button
                                    if (onRecenterVr != null) {
                                        PlayerOverlayActionButton(
                                            onClick = onRecenterVr,
                                            iconRes = R.drawable.ic_vr_recenter,
                                            contentDescription = "Recenter Camera View",
                                            testTag = "vr_recenter_button",
                                            isFullscreen = isFullscreen,
                                            isActive = false
                                        )
                                    }
                                }

                                // 4. VR Magic Window Master Button
                                PlayerOverlayActionButton(
                                    onClick = onToggleVrMode,
                                    iconRes = R.drawable.ic_player_vr,
                                    contentDescription = "VR 360° Magic Window",
                                    testTag = "vr_player_button",
                                    isFullscreen = isFullscreen,
                                    isActive = isVrMode
                                )
                            }

                            // Pop-Up Window / PiP Button
                            PlayerOverlayActionButton(
                                onClick = onEnterPip,
                                iconRes = R.drawable.ic_player_pip,
                                contentDescription = "Picture in Picture",
                                testTag = if (isFullscreen) "popup_player_button" else "popup_inline_button",
                                isFullscreen = isFullscreen,
                                isActive = false
                            )

                            // External Player Button
                            PlayerOverlayActionButton(
                                onClick = onOpenExternal,
                                iconRes = R.drawable.ic_player_external,
                                contentDescription = "Open External",
                                testTag = if (isFullscreen) "open_external_player_button" else "external_player_inline_button",
                                isFullscreen = isFullscreen,
                                isActive = false
                            )

                            // Fullscreen / Rotate Button
                            PlayerOverlayActionButton(
                                onClick = onToggleFullscreen,
                                iconRes = if (isFullscreen) {
                                    if (isLandscape) R.drawable.ic_player_fullscreen_exit else R.drawable.ic_player_fullscreen
                                } else {
                                    R.drawable.ic_player_fullscreen
                                },
                                contentDescription = "Fullscreen Mode",
                                testTag = if (isFullscreen) "fullscreen_toggle_button" else "fullscreen_inline_button",
                                isFullscreen = isFullscreen,
                                isActive = isFullscreen
                            )

                        // Settings Button with Popup appearing to its right into open space
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Settings Button Icon
                            PlayerOverlayActionButton(
                                onClick = {
                                    showSettingsPopup = !showSettingsPopup
                                    if (!showSettingsPopup) isQualityExpanded = false
                                },
                                iconRes = R.drawable.ic_player_settings,
                                contentDescription = "Settings",
                                testTag = if (isFullscreen) "settings_player_button" else "settings_inline_button",
                                isFullscreen = isFullscreen,
                                isActive = showSettingsPopup
                            )

                            // Popup appearing to the right of the settings button
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showSettingsPopup,
                                enter = fadeIn(tween(140)) + slideInHorizontally(tween(140)) { -it / 2 } + scaleIn(initialScale = 0.9f),
                                exit = fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 2 } + scaleOut(targetScale = 0.9f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF141722),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.widthIn(min = 90.dp, max = 115.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .animateContentSize(tween(150)),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // "Quality" Item Box
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isQualityExpanded) Color.White.copy(alpha = 0.12f) else Color.Transparent
                                                )
                                                .clickable { isQualityExpanded = !isQualityExpanded }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Quality",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        // Smoothly expanded list of qualities: ONLY pure labels like 1080p, 2160p
                                        AnimatedVisibility(
                                            visible = isQualityExpanded,
                                            enter = expandVertically(tween(150)) + fadeIn(tween(150)),
                                            exit = shrinkVertically(tween(130)) + fadeOut(tween(130))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 2.dp),
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                val cleanQualities = remember(qualities) {
                                                    val rawList = if (qualities.isNotEmpty()) qualities else listOf(StreamQuality(quality = "1080p", url = ""))
                                                    val mapped = rawList.mapIndexed { idx, q ->
                                                        idx to formatQualityDisplayLabel(q.quality)
                                                    }
                                                    val seen = mutableSetOf<String>()
                                                    mapped.filter { seen.add(it.second) }
                                                }

                                                cleanQualities.forEach { (originalIdx, label) ->
                                                    val isSelected = originalIdx == selectedQualityIndex
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.22f) else Color.Transparent
                                                            )
                                                            .clickable {
                                                                onSelectQuality?.invoke(originalIdx)
                                                                showSettingsPopup = false
                                                                isQualityExpanded = false
                                                            }
                                                            .padding(vertical = 5.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Video Duration on the RIGHT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatPlayerDuration(currentPos),
                            color = Color.White,
                            fontSize = if (isFullscreen) 13.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = " / ${formatPlayerDuration(duration)}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isFullscreen) 13.sp else 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerOverlayActionButton(
    onClick: () -> Unit,
    iconRes: Int,
    contentDescription: String,
    testTag: String,
    isFullscreen: Boolean,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (isFullscreen) 34.dp else 28.dp
    val iconSize = if (isFullscreen) 20.dp else 17.dp

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (isActive) Color(0xFF38BDF8) else Color.White,
            modifier = Modifier.size(iconSize)
        )
    }
}

