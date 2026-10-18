package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.example.network.KanimeEpisodeDetails
import com.example.network.KanimeEpisodeItem
import com.example.network.KstreamMediaItem
import com.example.network.kstreamScraper
import com.example.ui.MainViewModel
import com.example.ui.components.AppLottieLoadingAnimation
import com.example.ui.components.ShimmerBrush
import com.example.ui.components.vaultTopGlow
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalBetaTestPrivacy
import com.example.ui.theme.LocalVaultPalette
import com.example.ui.theme.privacyImageBlur
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()

    var animeDetails by remember { mutableStateOf<KanimeAnimeDetails?>(null) }
    var isAnimeLoading by remember { mutableStateOf(true) }
    var selectedEpisodeUrl by remember { mutableStateOf(item.episodeUrl) }

    // Map caching loaded details for each episode
    var episodeDetailsMap by remember { mutableStateOf<Map<String, KanimeEpisodeDetails>>(emptyMap()) }
    var isEpisodeLoading by remember { mutableStateOf(false) }

    // State for description expansion
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // State for gallery active tapped image (showing popup play button)
    var activeGalleryIndex by remember { mutableStateOf<Int?>(null) }

    // 1. Initial Load: Fetch overall anime details and initial episode
    LaunchedEffect(item.id) {
        isAnimeLoading = true
        try {
            val result = kstreamScraper.getAnimeDetails(item)
            animeDetails = result

            // Cache the initial episode details if scraped
            if (result.defaultEpisodeDetails != null) {
                episodeDetailsMap = episodeDetailsMap + (result.defaultEpisodeDetails.episodeUrl to result.defaultEpisodeDetails)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("KanimeEpisodes", "Failed to load anime details: ${e.message}")
        } finally {
            isAnimeLoading = false
        }
    }

    // 2. Load Episode-Specific Details when selectedEpisodeUrl changes
    LaunchedEffect(selectedEpisodeUrl) {
        activeGalleryIndex = null // Reset gallery pop-up on episode switch
        if (!episodeDetailsMap.containsKey(selectedEpisodeUrl)) {
            isEpisodeLoading = true
            try {
                val epDetails = kstreamScraper.getEpisodeDetails(
                    episodeUrl = selectedEpisodeUrl,
                    fallbackTitle = item.title,
                    fallbackPoster = item.posterUrl
                )
                episodeDetailsMap = episodeDetailsMap + (selectedEpisodeUrl to epDetails)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.e("KanimeEpisodes", "Failed to load episode details: ${e.message}")
            } finally {
                isEpisodeLoading = false
            }
        }
    }

    // Current active episode details
    val currentEpisode = episodeDetailsMap[selectedEpisodeUrl]

    // Title: Displays episode numbering as on the website
    val displayTitle = currentEpisode?.title?.ifEmpty { null }
        ?: item.title

    // Poster of current episode
    val displayPoster = currentEpisode?.posterUrl?.ifEmpty { null }
        ?: animeDetails?.posterUrl
        ?: item.posterUrl

    // Date
    val displayDate = currentEpisode?.date?.ifEmpty { null } ?: "Recently"

    // Censored / Uncensored
    val isUncensored = currentEpisode?.isUncensored ?: false

    // Description
    val displayDescription = currentEpisode?.description?.ifEmpty { null }
        ?: animeDetails?.description?.ifEmpty { null }
        ?: "No description available."

    // Gallery
    val galleryImages = currentEpisode?.gallery.orEmpty()

    // List of all episodes
    val episodeList = animeDetails?.episodes.orEmpty().ifEmpty {
        val epNum = kstreamScraper.extractEpNumber(item.episodeUrl)
        listOf(
            KanimeEpisodeItem(
                episodeNumber = epNum,
                title = "Episode $epNum",
                episodeUrl = item.episodeUrl,
                thumbnailUrl = item.posterUrl
            )
        )
    }

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
            // 1. Top Section: Episode Poster on the left, Title with Episode Number and Badges in front
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Poster image (Aspect ratio 2:3)
                    var isImageLoaded by remember(displayPoster) { mutableStateOf(false) }
                    var isImageError by remember(displayPoster) { mutableStateOf(false) }

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
                            .background(palette.cardBg)
                            .vaultTopGlow(cornerRadius = 14.dp),
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
                                    .data(displayPoster)
                                    .crossfade(260)
                                    .build(),
                                contentDescription = displayTitle,
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

                    // In front: Episode title with number, date and censorship badges
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Title with episode numbering as on the website
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 23.sp
                            ),
                            color = palette.textPrimary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Badges under title: Date and Censored/Uncensored (small & circular)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Date Badge (Small & Circular/Pill)
                            Surface(
                                color = palette.cardBg,
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = palette.textSecondary.copy(alpha = 0.15f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_app_calendar),
                                        contentDescription = null,
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = displayDate,
                                        color = palette.textSecondary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Censorship Badge (Small & Circular/Pill)
                            // Uncensored in soft light green, Censored in soft light red
                            val badgeBg = if (isUncensored) Color(0x284CAF50) else Color(0x28EF5350)
                            val badgeTextColor = if (isUncensored) Color(0xFF66BB6A) else Color(0xFFEF5350)
                            val badgeLabel = if (isUncensored) "Uncensored" else "Censored"

                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(badgeTextColor)
                                    )
                                    Text(
                                        text = badgeLabel,
                                        color = badgeTextColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Description Card: 3 lines max by default, clickable to expand/collapse
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vaultTopGlow(cornerRadius = 14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        .testTag("kanime_desc_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = palette.cardBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Description",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = accent
                            )

                            Icon(
                                imageVector = if (isDescriptionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isDescriptionExpanded) "Collapse" else "Expand",
                                tint = palette.textSecondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isAnimeLoading && animeDetails == null && currentEpisode == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppLottieLoadingAnimation(modifier = Modifier.size(36.dp))
                            }
                        } else {
                            Text(
                                text = displayDescription,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    lineHeight = 20.sp
                                ),
                                color = palette.textPrimary.copy(alpha = 0.88f),
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (!isDescriptionExpanded) {
                                Text(
                                    text = "Tap to read more...",
                                    fontSize = 11.5.sp,
                                    color = accent.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 3. Episode Gallery Card: Directly under Description Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .vaultTopGlow(cornerRadius = 14.dp)
                        .testTag("kanime_gallery_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = palette.cardBg
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episode Gallery",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = accent
                            )

                            if (galleryImages.isNotEmpty()) {
                                Surface(
                                    color = palette.bg,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "${galleryImages.size} shots",
                                        color = palette.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (isEpisodeLoading && galleryImages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AppLottieLoadingAnimation(modifier = Modifier.size(44.dp))
                            }
                        } else if (galleryImages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp, horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No gallery screenshots available",
                                    color = palette.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(galleryImages, key = { index, url -> "$url-$index" }) { index, imgUrl ->
                                    val isSelected = activeGalleryIndex == index

                                    Box(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(palette.bg)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) accent else palette.textSecondary.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                activeGalleryIndex = if (isSelected) null else index
                                            }
                                            .testTag("kanime_gallery_item_$index"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(imgUrl)
                                                .crossfade(200)
                                                .build(),
                                            contentDescription = "Gallery shot ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .privacyImageBlur(isBetaTest)
                                        )

                                        // Pop-up Play Button overlay when tapped!
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = isSelected,
                                            enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                                            exit = fadeOut(tween(140)) + scaleOut(tween(140))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.45f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        // Play this episode's video!
                                                        if (currentEpisode != null) {
                                                            viewModel.playKanimeEpisodeDetails(currentEpisode)
                                                        } else {
                                                            viewModel.playKanimeItem(item)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .shadow(8.dp, CircleShape)
                                                        .clip(CircleShape)
                                                        .background(accent)
                                                        .testTag("kanime_gallery_play_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Play Video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(28.dp)
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
            }

            // 4. Episodes Section: Lists all available episodes
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Episodes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = palette.textPrimary
                    )

                    Text(
                        text = "${episodeList.size} Available",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = palette.textSecondary
                    )
                }
            }

            // Episodes List: Episode 1, Episode 2, ...
            if (isAnimeLoading && animeDetails == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppLottieLoadingAnimation(modifier = Modifier.size(50.dp))
                    }
                }
            } else {
                items(episodeList, key = { it.episodeUrl }) { ep ->
                    val isCurrent = ep.episodeUrl == selectedEpisodeUrl

                    KanimeEpisodeCard(
                        episode = ep,
                        isSelected = isCurrent,
                        onSelect = {
                            selectedEpisodeUrl = ep.episodeUrl
                        },
                        onPlay = {
                            viewModel.playKanimeEpisode(ep)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Episode item card displaying:
 * Episode 1, Episode 2, ...
 * with active selection indicator and tap-to-play affordance.
 */
@Composable
private fun KanimeEpisodeCard(
    episode: KanimeEpisodeItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .vaultTopGlow(cornerRadius = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
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
            // Circular Play Icon or Episode Number
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accent.copy(alpha = 0.22f) else palette.bg)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play ${episode.title}",
                    tint = if (isSelected) accent else palette.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Episode Title (e.g. "Episode 1", "Episode 2")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = if (isSelected) accent else palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isSelected) {
                    Text(
                        text = "Viewing Details",
                        fontSize = 11.5.sp,
                        color = accent.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Direct Play Button
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) accent else palette.bg,
                    contentColor = if (isSelected) Color.White else accent
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = "Play",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
