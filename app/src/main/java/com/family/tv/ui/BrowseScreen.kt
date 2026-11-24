package com.family.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.family.tv.data.Channel
import com.family.tv.data.EpgProgram
import com.family.tv.data.PlaylistRepository
import com.family.tv.data.Section
import com.family.tv.data.VodInfo
import com.family.tv.data.WatchProgress
import kotlinx.coroutines.delay

private const val CAT_CONTINUE = "__CONTINUE__"
private const val CAT_FAVORITES = "__FAVORITES__"
private const val CAT_RECENT = "__RECENT__"
private const val CAT_ALL = "__ALL__"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    section: Section,
    channels: List<Channel>,
    favorites: Set<String>,
    recent: List<String>,
    watchProgress: Map<String, WatchProgress>,
    seriesLastEpisode: Map<Int, String>,
    epgMap: Map<String, List<EpgProgram>>,
    parentalPin: String?,
    lockedCategories: Set<String>,
    unlockedCategoriesSession: Set<String>,
    selectedMovie: Channel?,
    selectedMovieInfo: VodInfo?,
    loadingMovieInfo: Boolean,
    selectedSeries: Channel? = null,
    isSeriesOpen: Boolean = false,
    strings: AppStrings,
    onFocusLiveChannel: (Channel) -> Unit,
    onPlay: (Channel) -> Unit,
    onPlaySelectedMovie: () -> Unit,
    onCloseMovieInfo: () -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onUnlockCategory: (Section, String) -> Unit,
    onBack: () -> Unit,
) {
    val isPosterSection = section == Section.MOVIES || section == Section.SERIES

    fun isGroupLocked(grp: String): Boolean {
        if (parentalPin == null) return false
        val key = PlaylistRepository.categoryLockKey(section, grp)
        return key in lockedCategories && key !in unlockedCategoriesSession
    }

    val sectionChannels = remember(channels, section) {
        channels.filter { it.section == section }
    }
    val unlockedSectionChannels = remember(sectionChannels, parentalPin, lockedCategories, unlockedCategoriesSession) {
        sectionChannels.filterNot { isGroupLocked(it.group) }
    }
    val channelByKey = remember(channels) {
        channels.associateBy { PlaylistRepository.channelKey(it) }
    }
    val favoriteChannels = remember(unlockedSectionChannels, favorites) {
        unlockedSectionChannels.filter { PlaylistRepository.channelKey(it) in favorites }
    }
    val recentChannels = remember(recent, channelByKey, section, parentalPin, lockedCategories, unlockedCategoriesSession) {
        recent.mapNotNull { channelByKey[it] }
            .filter { it.section == section && !isGroupLocked(it.group) }
    }
    val continueChannels = remember(unlockedSectionChannels, recentChannels, watchProgress, seriesLastEpisode, section) {
        if (!isPosterSection) {
            emptyList()
        } else {
            val orderedPool = (recentChannels + unlockedSectionChannels).distinctBy { PlaylistRepository.channelKey(it) }
            orderedPool.filter { ch ->
                when (ch.section) {
                    Section.MOVIES -> watchProgress[ch.url]?.isInProgress == true
                    Section.SERIES -> {
                        val epUrl = ch.seriesId?.let { seriesLastEpisode[it] }
                        epUrl != null && watchProgress[epUrl]?.isInProgress == true
                    }
                    Section.LIVE -> false
                }
            }
        }
    }
    val groups = remember(sectionChannels) {
        sectionChannels.map { it.group }.distinct()
    }

    var selectedCategory by remember(section) {
        mutableStateOf(
            when {
                isPosterSection -> CAT_ALL
                favoriteChannels.isNotEmpty() -> CAT_FAVORITES
                groups.firstOrNull { !isGroupLocked(it) } != null -> groups.first { !isGroupLocked(it) }
                else -> CAT_ALL
            }
        )
    }

    var pendingUnlockGroup by remember(section) { mutableStateOf<String?>(null) }
    var focusedLiveChannel by remember(section) { mutableStateOf<Channel?>(null) }

    // Debounced EPG fetch as you move across Live TV channels
    LaunchedEffect(focusedLiveChannel) {
        val ch = focusedLiveChannel ?: return@LaunchedEffect
        delay(350)
        onFocusLiveChannel(ch)
    }

    val sidebarListState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val selectedCategoryFocus = remember { FocusRequester() }
    val firstVisibleCardFocus = remember { FocusRequester() }
    val firstVisibleSidebarFocus = remember { FocusRequester() }
    val searchFieldFocus = remember { FocusRequester() }
    val cardFocusRequesters = remember(section) { mutableMapOf<String, FocusRequester>() }
    var lastFocusedItemKey by remember(section) { mutableStateOf<String?>(null) }
    var lastClickedCardKey by remember(section) { mutableStateOf<String?>(null) }
    var lastOverlayChannel by remember(section) { mutableStateOf<Channel?>(null) }
    var wasOverlayOpen by remember(section) { mutableStateOf(false) }

    var searchOpen by remember(section) { mutableStateOf(false) }
    var query by remember(section) { mutableStateOf("") }

    val displayedChannels = remember(
        sectionChannels,
        unlockedSectionChannels,
        continueChannels,
        favoriteChannels,
        recentChannels,
        selectedCategory,
        searchOpen,
        query,
    ) {
        if (searchOpen && query.isNotBlank()) {
            unlockedSectionChannels
                .filter { it.name.contains(query.trim(), ignoreCase = true) }
                .distinctBy { PlaylistRepository.channelKey(it) }
        } else {
            when (selectedCategory) {
                CAT_CONTINUE -> continueChannels
                CAT_FAVORITES -> favoriteChannels
                CAT_RECENT -> recentChannels
                CAT_ALL -> unlockedSectionChannels.distinctBy { PlaylistRepository.channelKey(it) }
                else -> sectionChannels.filter { it.group == selectedCategory }
            }
        }
    }

    val columnCount = if (isPosterSection) 4 else 3
    val hasCards = displayedChannels.isNotEmpty()
    val visibleGridKeys by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key.toString() }
        }
    }
    val firstVisibleGridIndex by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        }
    }
    val visibleSidebarKeys by remember {
        derivedStateOf {
            sidebarListState.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key.toString() }
        }
    }
    val firstVisibleSidebarIndex by remember {
        derivedStateOf {
            sidebarListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        }
    }
    val targetGridFocus = remember(lastFocusedItemKey, visibleGridKeys) {
        val key = lastFocusedItemKey
        if (key != null && key in visibleGridKeys) {
            cardFocusRequesters[key] ?: firstVisibleCardFocus
        } else {
            firstVisibleCardFocus
        }
    }
    val targetSidebarFocus = remember(selectedCategory, visibleSidebarKeys) {
        if (selectedCategory in visibleSidebarKeys) {
            selectedCategoryFocus
        } else {
            firstVisibleSidebarFocus
        }
    }

    val activeOverlayChannel = selectedMovie ?: selectedSeries
    val anyOverlayOpen = activeOverlayChannel != null || isSeriesOpen
    LaunchedEffect(anyOverlayOpen, activeOverlayChannel) {
        if (anyOverlayOpen) {
            wasOverlayOpen = true
            if (activeOverlayChannel != null) {
                lastOverlayChannel = activeOverlayChannel
            }
        } else if (wasOverlayOpen) {
            wasOverlayOpen = false
            val overlayCh = lastOverlayChannel
            val overlayKey = overlayCh?.let { PlaylistRepository.channelKey(it) }
            val matchIndex = if (overlayKey != null) {
                displayedChannels.indexOfFirst { PlaylistRepository.channelKey(it) == overlayKey }
            } else {
                -1
            }

            if (matchIndex >= 0) {
                val matched = displayedChannels[matchIndex]
                val itemKey = "${matched.section}_${matched.name}_${matched.url}_${matched.seriesId}"
                lastFocusedItemKey = itemKey
                lastClickedCardKey = itemKey
                val isVisible = gridState.layoutInfo.visibleItemsInfo.any { it.index == matchIndex }
                if (!isVisible) {
                    runCatching { gridState.scrollToItem(matchIndex) }
                }
            }

            repeat(5) {
                withFrameNanos { }
                val target = lastClickedCardKey?.let { cardFocusRequesters[it] } ?: targetSidebarFocus
                if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(25)
            }
        }
    }

    LaunchedEffect(selectedCategory, groups, isPosterSection) {
        lastFocusedItemKey = null
        gridState.scrollToItem(0)
        val offset = if (isPosterSection) 1 else 0
        val targetIndex = when (selectedCategory) {
            CAT_CONTINUE -> 0
            CAT_FAVORITES -> 0 + offset
            CAT_RECENT -> 1 + offset
            CAT_ALL -> 2 + offset
            else -> {
                val idx = groups.indexOf(selectedCategory)
                if (idx >= 0) 3 + offset + idx else 2 + offset
            }
        }
        val visible = sidebarListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!visible) {
            runCatching { sidebarListState.scrollToItem(targetIndex) }
        }
        if (!anyOverlayOpen && pendingUnlockGroup == null) {
            withFrameNanos { }
            runCatching { selectedCategoryFocus.requestFocus() }
        }
    }

    LaunchedEffect(section) {
        if (anyOverlayOpen || pendingUnlockGroup != null) return@LaunchedEffect
        repeat(3) {
            withFrameNanos { }
            if (runCatching { selectedCategoryFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(25)
        }
    }

    fun sidebarItemModifier(sidebarIndex: Int, isSelectedCategory: Boolean): Modifier =
        Modifier
            .focusProperties {
                if (hasCards) right = targetGridFocus
            }
            .then(if (isSelectedCategory) Modifier.focusRequester(selectedCategoryFocus) else Modifier)
            .then(if (sidebarIndex == firstVisibleSidebarIndex) Modifier.focusRequester(firstVisibleSidebarFocus) else Modifier)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !anyOverlayOpen && pendingUnlockGroup == null }
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .width(270.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF151821), RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = strings.sectionTitle(section),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    state = sidebarListState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    var sidebarIdx = 0
                    if (isPosterSection) {
                        val idx = sidebarIdx++
                        item(key = CAT_CONTINUE) {
                            val isCat = selectedCategory == CAT_CONTINUE
                            CategoryItem(
                                label = strings.continueWatchingLabel(continueChannels.size),
                                selected = !searchOpen && isCat,
                                onClick = { searchOpen = false; selectedCategory = CAT_CONTINUE },
                                modifier = sidebarItemModifier(idx, isCat),
                            )
                        }
                    }
                    val favIdx = sidebarIdx++
                    item(key = CAT_FAVORITES) {
                        val isCat = selectedCategory == CAT_FAVORITES
                        CategoryItem(
                            label = strings.favoritesLabel(favoriteChannels.size),
                            selected = !searchOpen && isCat,
                            onClick = { searchOpen = false; selectedCategory = CAT_FAVORITES },
                            modifier = sidebarItemModifier(favIdx, isCat),
                        )
                    }
                    val recIdx = sidebarIdx++
                    item(key = CAT_RECENT) {
                        val isCat = selectedCategory == CAT_RECENT
                        CategoryItem(
                            label = strings.recentLabel(recentChannels.size),
                            selected = !searchOpen && isCat,
                            onClick = { searchOpen = false; selectedCategory = CAT_RECENT },
                            modifier = sidebarItemModifier(recIdx, isCat),
                        )
                    }
                    val allIdx = sidebarIdx++
                    item(key = CAT_ALL) {
                        val isCat = selectedCategory == CAT_ALL
                        CategoryItem(
                            label = strings.allLabel(unlockedSectionChannels.size),
                            selected = !searchOpen && isCat,
                            onClick = { searchOpen = false; selectedCategory = CAT_ALL },
                            modifier = sidebarItemModifier(allIdx, isCat),
                        )
                    }
                    val baseGroupIdx = sidebarIdx
                    itemsIndexed(groups, key = { _, grp -> grp }) { gIdx, grp ->
                        val isCat = selectedCategory == grp
                        val locked = isGroupLocked(grp)
                        CategoryItem(
                            label = if (locked) "🔒 $grp" else grp,
                            selected = !searchOpen && isCat,
                            onClick = {
                                if (locked) {
                                    pendingUnlockGroup = grp
                                } else {
                                    searchOpen = false
                                    selectedCategory = grp
                                }
                            },
                            modifier = sidebarItemModifier(baseGroupIdx + gIdx, isCat),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val activeTitle = when {
                        searchOpen && query.isNotBlank() -> strings.searchHeader(query, displayedChannels.size)
                        selectedCategory == CAT_CONTINUE -> strings.continueWatchingHeader(displayedChannels.size)
                        selectedCategory == CAT_FAVORITES -> strings.favoritesHeader(displayedChannels.size)
                        selectedCategory == CAT_RECENT -> strings.recentHeader(displayedChannels.size)
                        selectedCategory == CAT_ALL -> strings.allHeader(section, displayedChannels.size)
                        else -> "$selectedCategory (${displayedChannels.size})"
                    }

                    val livePrograms = if (section == Section.LIVE) focusedLiveChannel?.let { epgMap[it.url] } else null
                    val epgSubtitle = remember(livePrograms, focusedLiveChannel) {
                        if (livePrograms.isNullOrEmpty()) null
                        else {
                            val now = livePrograms.getOrNull(0)
                            val next = livePrograms.getOrNull(1)
                            buildString {
                                if (now != null) append("${strings.nowPlayingTag}: ${now.title} (${now.startTime}–${now.endTime})")
                                if (next != null) append("   •   ${strings.upNextTag}: ${next.title} (${next.startTime})")
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = activeTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = epgSubtitle ?: strings.browseHint,
                            fontSize = 13.sp,
                            color = if (epgSubtitle != null) Color(0xFF93C5FD) else Color(0xFF8E95A5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val downFromTopBar = when {
                        searchOpen -> searchFieldFocus
                        hasCards -> targetGridFocus
                        else -> targetSidebarFocus
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val nextOpen = !searchOpen
                                searchOpen = nextOpen
                                if (!nextOpen) {
                                    query = ""
                                    val fallback = if (hasCards) targetGridFocus else targetSidebarFocus
                                    runCatching { fallback.requestFocus() }
                                }
                            },
                            modifier = Modifier.focusProperties {
                                left = FocusRequester.Cancel
                                down = downFromTopBar
                            },
                        ) {
                            Text(if (searchOpen) strings.closeSearch else strings.search)
                        }
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.focusProperties { down = downFromTopBar },
                        ) {
                            Text(strings.home)
                        }
                    }
                }

                if (searchOpen) {
                    BackHandler {
                        searchOpen = false
                        query = ""
                        val fallback = if (hasCards) targetGridFocus else targetSidebarFocus
                        runCatching { fallback.requestFocus() }
                    }
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        runCatching { searchFieldFocus.requestFocus() }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { androidx.compose.material3.Text(strings.searchPlaceholder(section), color = Color.Gray) },
                        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color(0xFF2D3343),
                            focusedContainerColor = Color(0xFF161922),
                            unfocusedContainerColor = Color(0xFF161922),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldFocus)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                down = if (hasCards) targetGridFocus else targetSidebarFocus
                            },
                    )
                }

                Spacer(Modifier.height(18.dp))

                if (displayedChannels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF141720), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = when {
                                searchOpen && query.isNotBlank() -> strings.noMatches(query)
                                selectedCategory == CAT_CONTINUE -> strings.noContinueWatching
                                selectedCategory == CAT_FAVORITES -> strings.noFavorites
                                selectedCategory == CAT_RECENT -> strings.noRecent
                                else -> strings.noItems
                            },
                            fontSize = 17.sp,
                            color = Color(0xFF9AA1B2),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnCount),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = displayedChannels,
                            key = { _, it -> "${it.section}_${it.name}_${it.url}_${it.seriesId}" },
                        ) { index, ch ->
                            val itemKey = "${ch.section}_${ch.name}_${ch.url}_${ch.seriesId}"
                            val cardKey = PlaylistRepository.channelKey(ch)
                            val isFav = cardKey in favorites
                            val progressFraction = when (ch.section) {
                                Section.MOVIES -> watchProgress[ch.url]?.fraction ?: 0f
                                Section.SERIES -> ch.seriesId?.let { id ->
                                    seriesLastEpisode[id]?.let { epUrl -> watchProgress[epUrl]?.fraction }
                                } ?: 0f
                                Section.LIVE -> 0f
                            }
                            val leftEdgeModifier = if (index % columnCount == 0) {
                                Modifier.focusProperties { left = targetSidebarFocus }
                            } else {
                                Modifier
                            }
                            val visibleAnchorModifier = if (index == firstVisibleGridIndex) {
                                Modifier.focusRequester(firstVisibleCardFocus)
                            } else {
                                Modifier
                            }
                            val cardFocus = remember(itemKey) {
                                cardFocusRequesters.getOrPut(itemKey) { FocusRequester() }
                            }
                            val cardModifier = leftEdgeModifier
                                .then(visibleAnchorModifier)
                                .focusRequester(cardFocus)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        lastFocusedItemKey = itemKey
                                        if (!isPosterSection) focusedLiveChannel = ch
                                    }
                                }

                            if (isPosterSection) {
                                PosterCard(
                                    ch = ch,
                                    isFavorite = isFav,
                                    progressFraction = progressFraction,
                                    onClick = {
                                        lastClickedCardKey = itemKey
                                        onPlay(ch)
                                    },
                                    onLongClick = { onToggleFavorite(ch) },
                                    modifier = cardModifier,
                                )
                            } else {
                                val currentProg = epgMap[ch.url]?.firstOrNull()?.title
                                LiveChannelCard(
                                    ch = ch,
                                    isFavorite = isFav,
                                    nowTitle = currentProg,
                                    onClick = {
                                        lastClickedCardKey = itemKey
                                        onPlay(ch)
                                    },
                                    onLongClick = { onToggleFavorite(ch) },
                                    modifier = cardModifier,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lightweight Movie Info Card Overlay
        if (selectedMovie != null) {
            MovieInfoOverlay(
                movie = selectedMovie,
                info = selectedMovieInfo,
                loading = loadingMovieInfo,
                isFavorite = PlaylistRepository.channelKey(selectedMovie) in favorites,
                hasProgress = watchProgress[selectedMovie.url]?.isInProgress == true,
                strings = strings,
                onPlay = onPlaySelectedMovie,
                onToggleFavorite = { onToggleFavorite(selectedMovie) },
                onDismiss = onCloseMovieInfo,
            )
        }

        // Parental Lock PIN Prompt Overlay
        val targetGroup = pendingUnlockGroup
        if (targetGroup != null) {
            PinDialog(
                title = strings.enterPinTitle,
                strings = strings,
                expectedPin = parentalPin,
                onSuccess = {
                    onUnlockCategory(section, targetGroup)
                    searchOpen = false
                    selectedCategory = targetGroup
                    pendingUnlockGroup = null
                },
                onDismiss = { pendingUnlockGroup = null },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieInfoOverlay(
    movie: Channel,
    info: VodInfo?,
    loading: Boolean,
    isFavorite: Boolean,
    hasProgress: Boolean,
    strings: AppStrings,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(movie.url) {
        withFrameNanos { }
        runCatching { playFocus.requestFocus() }
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .width(780.dp)
                .background(Color(0xFF171B26), RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFF2B3245), RoundedCornerShape(22.dp))
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            // Poster Preview
            Box(
                modifier = Modifier
                    .width(210.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222736)),
            ) {
                val accent = remember(movie.name) {
                    CardAccents[(movie.name.hashCode() and 0x7FFFFFFF) % CardAccents.size]
                }
                TypographicCardBody(ch = movie, accent = accent)
                if (!movie.logo.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.logo,
                        contentDescription = movie.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Metadata & Actions
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(315.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = movie.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val badges = listOfNotNull(
                        info?.year,
                        info?.rating?.let { "★ $it" },
                        info?.duration,
                        info?.genre,
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
                    } else {
                        Text(
                            text = movie.group,
                            fontSize = 14.sp,
                            color = Color(0xFF8E95A5),
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = when {
                            loading -> strings.loadingInfo
                            !info?.plot.isNullOrBlank() -> info!!.plot!!
                            else -> strings.noPlotAvailable
                        },
                        fontSize = 15.sp,
                        color = Color(0xFFD1D5DB),
                        lineHeight = 21.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.focusRequester(playFocus),
                    ) {
                        Text(if (hasProgress) strings.resumeMovie else strings.watchMovie)
                    }
                    OutlinedButton(onClick = onToggleFavorite) {
                        Text(if (isFavorite) strings.removeFavorite else strings.addFavorite)
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text(strings.back)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                isFocused -> MaterialTheme.colorScheme.primary
                selected -> Color(0xFF283149)
                else -> Color.Transparent
            },
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            contentColor = if (isFocused) Color(0xFF0B0D12) else Color.White,
            focusedContentColor = Color(0xFF0B0D12),
        ),
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        )
    }
}

private val CardAccents = listOf(
    Color(0xFF3B82F6),
    Color(0xFF8B5CF6),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    ch: Channel,
    isFavorite: Boolean,
    progressFraction: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = remember(ch.name) {
        CardAccents[(ch.name.hashCode() and 0x7FFFFFFF) % CardAccents.size]
    }
    var imageLoaded by remember(ch.logo) { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused },
        scale = CardDefaults.scale(focusedScale = 1.07f),
        colors = CardDefaults.colors(containerColor = Color(0xFF191D28)),
        border = CardDefaults.border(
            focusedBorder = Border(
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!imageLoaded) {
                TypographicCardBody(ch = ch, accent = accent)
            }

            if (!ch.logo.isNullOrBlank()) {
                AsyncImage(
                    model = ch.logo,
                    contentDescription = ch.name,
                    contentScale = ContentScale.Crop,
                    onSuccess = { imageLoaded = true },
                    onError = { imageLoaded = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (imageLoaded) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.72f),
                                1.0f to Color.Black.copy(alpha = 0.94f),
                            )
                        )
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 10.dp,
                            bottom = if (progressFraction > 0f) 14.dp else 10.dp,
                        ),
                ) {
                    Text(
                        text = ch.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                    )
                }
            }

            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction.coerceIn(0.03f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFFE50914)),
                    )
                }
            }

            if (isFavorite) {
                FavoriteBadge(Modifier.align(Alignment.TopEnd))
            }

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LiveChannelCard(
    ch: Channel,
    isFavorite: Boolean,
    nowTitle: String? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = remember(ch.name) {
        CardAccents[(ch.name.hashCode() and 0x7FFFFFFF) % CardAccents.size]
    }
    var imageLoaded by remember(ch.logo) { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .aspectRatio(16f / 10f)
            .onFocusChanged { isFocused = it.isFocused },
        scale = CardDefaults.scale(focusedScale = 1.07f),
        colors = CardDefaults.colors(containerColor = Color(0xFF191D28)),
        border = CardDefaults.border(
            focusedBorder = Border(
                BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!imageLoaded) {
                TypographicCardBody(ch = ch, accent = accent)
            }

            if (!ch.logo.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (imageLoaded) Color(0xFF222736) else Color.Transparent)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ch.logo,
                            contentDescription = ch.name,
                            contentScale = ContentScale.Fit,
                            onSuccess = { imageLoaded = true },
                            onError = { imageLoaded = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (imageLoaded) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = ch.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!nowTitle.isNullOrBlank()) {
                            Text(
                                text = nowTitle,
                                fontSize = 11.sp,
                                color = Color(0xFF93C5FD),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (isFavorite) {
                FavoriteBadge(Modifier.align(Alignment.TopEnd))
            }

            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                )
            }
        }
    }
}

@Composable
private fun FavoriteBadge(modifier: Modifier = Modifier) {
    Text(
        text = "★",
        color = Color(0xFFFFD54F),
        fontSize = 15.sp,
        modifier = modifier
            .padding(8.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun TypographicCardBody(ch: Channel, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.16f), Color(0xFF171B26))
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = ch.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 21.sp,
        )
    }
}