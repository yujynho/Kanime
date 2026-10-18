package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.network.KanimeAnimeDetails
import com.example.network.KanimeEpisodeItem
import com.example.network.KstreamMediaItem
import com.example.network.cleanHanimeTitle
import com.example.network.kstreamScraper
import com.example.ui.MainViewModel
import com.example.ui.components.AppLottieLoadingAnimation
import com.example.ui.components.ShimmerBrush
import com.example.ui.components.vaultTopGlow
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalBetaTestPrivacy
import com.example.ui.theme.LocalVaultPalette
import com.example.ui.theme.privacyImageBlur

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KanimeEpisodesScreen(
    viewModel: MainViewModel,
    item: KstreamMediaItem,
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val isBetaTest = LocalBetaTestPrivacy.current

    var details by remember { mutableStateOf<KanimeAnimeDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Fetch anime details and all episodes
    LaunchedEffect(item.id) {
        isLoading = true
        errorMessage = null
        try {
            val result = kstreamScraper.getAnimeDetails(item)
            details = result
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            errorMessage = e.message ?: "Failed to load episodes"
        } finally {
            isLoading = false
        }
    }

    // Cleaned title without episode number (available immediately for instant display)
    val cleanTitle = remember(item.title, details?.title) {
        details?.title ?: cleanHanimeTitle(item.title)
    }
    val posterUrl = details?.posterUrl ?: item.posterUrl

    BackHandler {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("kanime_episodes_screen"),
        containerColor = palette.bg,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Episodes",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = palette.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier.testTag("kanime_episodes_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.bg,
                    titleContentColor = palette.textPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Section: Poster on the left, hanime name (without episode number) in front of it
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster image on the left (Aspect ratio 2:3)
                    var isImageLoaded by remember(posterUrl) { mutableStateOf(false) }
                    var isImageError by remember(posterUrl) { mutableStateOf(false) }

                    val imageAlpha by animateFloatAsState(
                        targetValue = if (isImageLoaded && !isImageError) 1f else 0f,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        label = "ep_poster_alpha"
                    )
                    val imageScale by animateFloatAsState(
                        targetValue = if (isImageLoaded && !isImageError) 1f else 0.96f,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                        label = "ep_poster_scale"
                    )

                    Box(
                        modifier = Modifier
                            .width(115.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.cardBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isImageLoaded && !isImageError) {
                            val shimmerBrush = ShimmerBrush(targetValue = 900f)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(palette.cardBg)
                                    .background(shimmerBrush)
                            )
                        }

                        if (!isImageError) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(posterUrl)
                                    .crossfade(260)
                                    .build(),
                                contentDescription = cleanTitle,
                                contentScale = ContentScale.Crop,
                                onSuccess = {
                                    isImageLoaded = true
                                    isImageError = false
                                },
                                onError = {
                                    isImageLoaded = true
                                    isImageError = true
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = imageAlpha
                                        scaleX = imageScale
                                        scaleY = imageScale
                                    }
                                    .privacyImageBlur(isBetaTest)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_nav_home),
                                contentDescription = "Image Error",
                                tint = palette.textSecondary.copy(alpha = 0.35f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // In front of poster: Hanime name without episode number
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = cleanTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                lineHeight = 24.sp
                            ),
                            color = palette.textPrimary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Tags / Badges
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = accent.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (details != null) "${details?.episodes?.size ?: 1} Episodes" else "Anime",
                                    color = accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "4K / HD",
                                    color = palette.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Description Section (Under poster & title)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.cardBg)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Description",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = accent
                    )

                    if (isLoading && details == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AppLottieLoadingAnimation(modifier = Modifier.size(40.dp))
                        }
                    } else {
                        val desc = details?.description.orEmpty().ifEmpty { "No description available." }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp
                            ),
                            color = palette.textPrimary.copy(alpha = 0.88f)
                        )
                    }
                }
            }

            // 3. Episodes Section (Under description)
            item {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = palette.textPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Episodes List: Episode 1, Episode 2, ...
            if (isLoading && details == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppLottieLoadingAnimation(modifier = Modifier.size(54.dp))
                    }
                }
            } else {
                val episodeList = details?.episodes.orEmpty()
                if (episodeList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No episodes available",
                                color = palette.textSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(episodeList, key = { it.episodeUrl }) { ep ->
                        KanimeEpisodeCard(
                            episode = ep,
                            onClick = { viewModel.playKanimeEpisode(ep) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Episode item card displaying:
 * Episode 1, Episode 2, ...
 * with play button and tap-to-play affordance.
 */
@Composable
private fun KanimeEpisodeCard(
    episode: KanimeEpisodeItem,
    onClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .vaultTopGlow(cornerRadius = 14.dp)
            .clickable(onClick = onClick)
            .testTag("kanime_episode_item_${episode.episodeNumber}"),
        shape = RoundedCornerShape(14.dp),
        color = palette.cardBg,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Circular Play Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play ${episode.title}",
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Episode Title (e.g. "Episode 1", "Episode 2")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Stream badge
            Surface(
                color = palette.bg,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Play",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}
