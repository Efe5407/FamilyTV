package com.family.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.family.tv.data.Channel
import com.family.tv.data.Episode
import com.family.tv.data.VodInfo
import com.family.tv.data.WatchProgress
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodesScreen(
    series: Channel,
    seriesInfo: VodInfo? = null,
    episodes: List<Episode>?,
    watchProgress: Map<String, WatchProgress>,
    strings: AppStrings,
    onPlay: (Episode) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val targetEpisodeFocus = remember { FocusRequester() }

    if (onBack != null) {
        BackHandler { onBack() }
    }

    val targetIndex = remember(episodes, watchProgress) {
        if (episodes.isNullOrEmpty()) 0
        else episodes.indexOfLast { watchProgress[it.url]?.isInProgress == true }.takeIf { it >= 0 } ?: 0
    }

    LaunchedEffect(episodes, targetIndex) {
        if (!episodes.isNullOrEmpty()) {
            if (targetIndex > 0) {
                runCatching { listState.scrollToItem(targetIndex) }
            }
            repeat(4) {
                withFrameNanos { }
                if (runCatching { targetEpisodeFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(25)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Series Info Header Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF151821), RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!series.logo.isNullOrBlank()) {
                AsyncImage(
                    model = series.logo,
                    contentDescription = series.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(105.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF222736)),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = series.name,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val badges = listOfNotNull(
                    seriesInfo?.year,
                    seriesInfo?.rating?.let { "★ $it" },
                    seriesInfo?.duration,
                    seriesInfo?.genre ?: series.group,
                )
                if (badges.isNotEmpty()) {
                    Text(
                        text = badges.joinToString("   •   "),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF93C5FD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (!seriesInfo?.plot.isNullOrBlank()) {
                    Text(
                        text = seriesInfo!!.plot!!,
                        fontSize = 14.sp,
                        color = Color(0xFFD1D5DB),
                        lineHeight = 20.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.focusProperties {
                        if (!episodes.isNullOrEmpty()) down = targetEpisodeFocus
                    },
                ) {
                    Text(strings.back)
                }
            }
        }

        // Episodes List
        when {
            episodes == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.loadingEpisodes, fontSize = 18.sp, color = Color(0xFF9AA1B2))
                }
            }
            episodes.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.noEpisodes, fontSize = 18.sp, color = Color(0xFF9AA1B2))
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(episodes) { index, ep ->
                        val progress = watchProgress[ep.url]?.fraction ?: 0f
                        val mod = if (index == targetIndex) {
                            Modifier.fillMaxWidth().focusRequester(targetEpisodeFocus)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        Surface(
                            onClick = { onPlay(ep) },
                            modifier = mod,
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color(0xFF151821),
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                focusedContentColor = Color(0xFF0B0D12),
                            ),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 22.dp, vertical = 15.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${strings.seasonLabel(ep.season)} • E${ep.number} — ${ep.title}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 16.dp),
                                    )
                                    Text(
                                        text = strings.playArrow,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                if (progress > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .background(Color.White.copy(alpha = 0.15f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                                                .fillMaxHeight()
                                                .background(Color(0xFFE50914)),
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