@file:OptIn(
    UnstableApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
)

package com.family.tv.ui

import android.util.TypedValue
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.family.tv.data.Channel
import com.family.tv.data.Section
import com.family.tv.data.SubtitleSize
import kotlinx.coroutines.delay
import java.util.Locale

private enum class AspectOption(
    val modeName: String,
    val resizeMode: Int,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    OVERSCAN("1.2x Crop", AspectRatioFrameLayout.RESIZE_MODE_FIT, scaleX = 1.2f, scaleY = 1.2f),
    FOUR_THREE("4:3", AspectRatioFrameLayout.RESIZE_MODE_FILL, scaleX = 0.75f, scaleY = 1f),
}

private data class TrackOption(
    val label: String,
    val group: TrackGroup,
    val trackIndex: Int,
    val selected: Boolean,
)

private enum class ActivePicker { NONE, AUDIO, SUBTITLES }

@Composable
fun PlayerScreen(
    channel: Channel,
    subtitleOverride: String? = null,
    strings: AppStrings,
    subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    onSetSubtitleSize: ((SubtitleSize) -> Unit)? = null,
    initialPositionMs: Long = 0L,
    nextEpisodeTitle: String? = null,
    onPlayNextEpisode: (() -> Unit)? = null,
    onSaveProgress: ((Long, Long) -> Unit)? = null,
    onSwitchChannel: ((Int) -> Unit)? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isLive = channel.section == Section.LIVE

    var hudVisible by remember(channel.url) { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableIntStateOf(0) }
    var activePicker by remember(channel.url) { mutableStateOf(ActivePicker.NONE) }
    var aspectIndex by remember { mutableIntStateOf(0) }

    var isPlaying by remember(channel.url) { mutableStateOf(true) }
    var isBuffering by remember(channel.url) { mutableStateOf(true) }
    var errorMessage by remember(channel.url) { mutableStateOf<String?>(null) }
    var positionMs by remember(channel.url) { mutableLongStateOf(0L) }
    var durationMs by remember(channel.url) { mutableLongStateOf(0L) }
    var tracks by remember(channel.url) { mutableStateOf(Tracks.EMPTY) }

    // YouTube-style timeline scrubber state
    var isScrubberFocused by remember(channel.url) { mutableStateOf(false) }
    var scrubPreviewMs by remember(channel.url) { mutableStateOf<Long?>(null) }
    var scrubTick by remember { mutableIntStateOf(0) }
    var lastScrubTimeMs by remember { mutableLongStateOf(0L) }
    var scrubStreak by remember { mutableIntStateOf(0) }
    var currentStepLabel by remember { mutableStateOf("") }

    // Auto-play next episode countdown state
    var upNextCountdown by remember(channel.url) { mutableStateOf<Int?>(null) }
    var upNextDismissed by remember(channel.url) { mutableStateOf(false) }

    val rootFocusRequester = remember { FocusRequester() }
    val scrubberFocus = remember { FocusRequester() }
    val primaryControlFocus = remember { FocusRequester() }
    val audioButtonFocus = remember { FocusRequester() }
    val subtitlesButtonFocus = remember { FocusRequester() }
    val playNowFocus = remember { FocusRequester() }
    var lastOpenedPicker by remember { mutableStateOf(ActivePicker.NONE) }

    val player = remember(channel.url) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("FamilyTV/1.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(load)
            .build().apply {
                setMediaItem(MediaItem.fromUri(channel.url))
                if (!isLive && initialPositionMs > 0L) {
                    seekTo(initialPositionMs)
                }
                playWhenReady = true
                prepare()
            }
    }

    // Auto-commit scrub preview 650ms after user stops holding Left/Right
    LaunchedEffect(scrubTick) {
        val target = scrubPreviewMs ?: return@LaunchedEffect
        delay(650)
        player.seekTo(target)
        positionMs = target
        scrubPreviewMs = null
        scrubStreak = 0
        currentStepLabel = ""
    }

    // Periodically save VOD watch progress (for red progress bar & auto-resume)
    LaunchedEffect(player, isLive) {
        if (!isLive && onSaveProgress != null) {
            while (true) {
                delay(10_000)
                val dur = player.duration
                if (dur > 0L) {
                    onSaveProgress(player.currentPosition.coerceAtLeast(0L), dur)
                }
            }
        }
    }

    // Auto-play next episode countdown timer
    LaunchedEffect(upNextCountdown) {
        val count = upNextCountdown ?: return@LaunchedEffect
        if (count > 0) {
            runCatching { playNowFocus.requestFocus() }
            delay(1_000)
            upNextCountdown = count - 1
        } else {
            upNextCountdown = null
            onPlayNextEpisode?.invoke()
        }
    }

    // Player listeners (auto-retry, track changes, buffering, errors, episode end)
    DisposableEffect(player) {
        var retries = 0
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                } else if (retries++ < 3) {
                    player.prepare()
                } else {
                    errorMessage = error.localizedMessage ?: "Playback failed"
                    hudVisible = true
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    retries = 0
                    errorMessage = null
                    durationMs = player.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED && !isLive) {
                    val dur = player.duration
                    if (dur > 0L) onSaveProgress?.invoke(dur, dur)
                    if (onPlayNextEpisode != null && !upNextDismissed && upNextCountdown == null) {
                        hudVisible = false
                        upNextCountdown = 5
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Lifecycle pause & cleanup
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(obs)
        onDispose {
            if (!isLive && onSaveProgress != null && player.duration > 0L) {
                onSaveProgress(player.currentPosition.coerceAtLeast(0L), player.duration)
            }
            lifecycle.removeObserver(obs)
            player.release()
        }
    }

    // Update VOD timeline every second while HUD is visible
    LaunchedEffect(player, hudVisible) {
        while (hudVisible) {
            if (scrubPreviewMs == null) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
            }
            durationMs = player.duration.coerceAtLeast(0L)
            delay(1_000)
        }
    }

    // Auto-hide HUD after 4.5s of inactivity when no picker or active scrub is open
    LaunchedEffect(hudVisible, hudInteractionTick, activePicker, errorMessage, scrubPreviewMs) {
        if (hudVisible && activePicker == ActivePicker.NONE && errorMessage == null && scrubPreviewMs == null) {
            delay(4_500)
            hudVisible = false
            isScrubberFocused = false
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Focus the primary transport button whenever the HUD appears
    LaunchedEffect(hudVisible) {
        if (hudVisible) {
            runCatching { primaryControlFocus.requestFocus() }
        } else if (upNextCountdown == null) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Restore focus to Audio or Subtitles button when closing the picker drawer
    LaunchedEffect(activePicker) {
        if (activePicker != ActivePicker.NONE) {
            lastOpenedPicker = activePicker
        } else if (hudVisible && lastOpenedPicker != ActivePicker.NONE) {
            hudInteractionTick++
            val target = when (lastOpenedPicker) {
                ActivePicker.AUDIO -> audioButtonFocus
                ActivePicker.SUBTITLES -> subtitlesButtonFocus
                ActivePicker.NONE -> primaryControlFocus
            }
            runCatching { target.requestFocus() }
            lastOpenedPicker = ActivePicker.NONE
        }
    }

    BackHandler {
        when {
            scrubPreviewMs != null -> {
                scrubPreviewMs = null
                scrubStreak = 0
                currentStepLabel = ""
            }
            upNextCountdown != null -> {
                upNextCountdown = null
                upNextDismissed = true
            }
            activePicker != ActivePicker.NONE -> activePicker = ActivePicker.NONE
            hudVisible -> {
                hudVisible = false
                isScrubberFocused = false
            }
            else -> onBack()
        }
    }

    val currentAspect = AspectOption.entries[aspectIndex]
    val audioOptions = remember(tracks) { extractTracks(tracks, C.TRACK_TYPE_AUDIO) }
    val subtitleOptions = remember(tracks) { extractTracks(tracks, C.TRACK_TYPE_TEXT) }
    val subtitlesDisabled = remember(tracks) {
        player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) ||
                subtitleOptions.none { it.selected }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    hudInteractionTick++
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            onSwitchChannel?.invoke(1)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            onSwitchChannel?.invoke(-1)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MENU -> {
                            if (!hudVisible && upNextCountdown == null) {
                                hudVisible = true
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                }
                false
            },
    ) {
        // Video Surface
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = currentAspect.scaleX,
                    scaleY = currentAspect.scaleY,
                ),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    resizeMode = currentAspect.resizeMode
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize.spValue)
                    }
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                if (view.resizeMode != currentAspect.resizeMode) {
                    view.resizeMode = currentAspect.resizeMode
                    view.requestLayout()
                }
                view.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize.spValue)
                }
            },
        )

        // Buffering or Error Status Badge
        if (isBuffering && errorMessage == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            ) {
                Text(strings.buffering, fontSize = 18.sp, color = Color.White)
            }
        }

        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = errorMessage ?: "Stream unavailable",
                    fontSize = 18.sp,
                    color = Color(0xFFFF8A80),
                )
                Button(
                    onClick = {
                        errorMessage = null
                        player.prepare()
                        player.playWhenReady = true
                    }
                ) {
                    Text(strings.retryNow)
                }
            }
        }

        // Auto-Play Next Episode Overlay Card
        val countdown = upNextCountdown
        if (countdown != null && onPlayNextEpisode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(44.dp)
                    .width(400.dp)
                    .background(Color(0xFF171B26).copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = strings.upNext,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!nextEpisodeTitle.isNullOrBlank()) {
                    Text(
                        text = nextEpisodeTitle,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = strings.playingInSeconds(countdown),
                    fontSize = 14.sp,
                    color = Color(0xFFB3BAC8),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            upNextCountdown = null
                            onPlayNextEpisode()
                        },
                        modifier = Modifier.focusRequester(playNowFocus),
                    ) {
                        Text(strings.playNow)
                    }
                    OutlinedButton(
                        onClick = {
                            upNextCountdown = null
                            upNextDismissed = true
                        }
                    ) {
                        Text(strings.cancel)
                    }
                }
            }
        }

        // Player HUD Overlay
        if (hudVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusProperties { canFocus = activePicker == ActivePicker.NONE }
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.80f),
                            0.28f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.92f),
                        )
                    )
                    .padding(horizontal = 44.dp, vertical = 32.dp),
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.name,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitleOverride ?: channel.group,
                            fontSize = 15.sp,
                            color = Color.LightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (isLive) {
                        Text(
                            text = strings.liveBadge,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .background(Color(0xFFD32F2F), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Focusable YouTube-Style Timeline Scrubber (Press Up to focus, Left/Right to fast-scrub)
                    if (!isLive && durationMs > 0L) {
                        val displayPos = scrubPreviewMs ?: positionMs
                        val progressFraction = (displayPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Floating Time & Speed Badge when focused on the bar
                            if (isScrubberFocused) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFF1F2430), RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFFE50914), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = formatTime(displayPos),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                        if (currentStepLabel.isNotEmpty()) {
                                            Text(
                                                text = currentStepLabel,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFFF6B6B),
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = formatTime(displayPos),
                                    fontSize = 14.sp,
                                    fontWeight = if (isScrubberFocused) FontWeight.Bold else FontWeight.Normal,
                                    color = Color.White,
                                )

                                // Interactive Focusable Bar
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                        .focusRequester(scrubberFocus)
                                        .focusProperties { down = primaryControlFocus }
                                        .onFocusChanged { isScrubberFocused = it.isFocused }
                                        .focusable()
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                val code = keyEvent.nativeKeyEvent.keyCode
                                                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                                                    hudInteractionTick++
                                                    val now = System.currentTimeMillis()
                                                    scrubStreak = if (now - lastScrubTimeMs < 350L) scrubStreak + 1 else 1
                                                    lastScrubTimeMs = now

                                                    val (stepMs, label) = when {
                                                        scrubStreak >= 14 -> 120_000L to "2m"
                                                        scrubStreak >= 8 -> 60_000L to "1m"
                                                        scrubStreak >= 4 -> 30_000L to "30s"
                                                        else -> 15_000L to "15s"
                                                    }
                                                    val dir = if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1L else -1L
                                                    val basePos = scrubPreviewMs ?: player.currentPosition.coerceAtLeast(0L)
                                                    val nextPos = (basePos + dir * stepMs).coerceIn(0L, durationMs)

                                                    scrubPreviewMs = nextPos
                                                    currentStepLabel = if (dir > 0) "+$label" else "-$label"
                                                    scrubTick++
                                                    return@onPreviewKeyEvent true
                                                }
                                                if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && scrubPreviewMs != null) {
                                                    val target = scrubPreviewMs!!
                                                    player.seekTo(target)
                                                    positionMs = target
                                                    scrubPreviewMs = null
                                                    scrubStreak = 0
                                                    currentStepLabel = ""
                                                    return@onPreviewKeyEvent true
                                                }
                                            }
                                            false
                                        },
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    val trackHeight = if (isScrubberFocused) 10.dp else 6.dp
                                    val thumbSize = if (isScrubberFocused) 20.dp else 12.dp
                                    val trackColor = if (isScrubberFocused) Color(0xFFE50914) else MaterialTheme.colorScheme.primary

                                    // Background Track
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(trackHeight)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.White.copy(alpha = if (isScrubberFocused) 0.35f else 0.22f)),
                                    ) {
                                        // Filled Progress
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progressFraction)
                                                .fillMaxHeight()
                                                .background(trackColor),
                                        )
                                    }

                                    // Scrubber Thumb Dot
                                    val thumbOffset = (maxWidth - thumbSize) * progressFraction
                                    Box(
                                        modifier = Modifier
                                            .offset(x = thumbOffset)
                                            .size(thumbSize)
                                            .background(Color.White, CircleShape)
                                            .border(
                                                width = if (isScrubberFocused) 3.dp else 1.5.dp,
                                                color = trackColor,
                                                shape = CircleShape,
                                            ),
                                    )
                                }

                                Text(formatTime(durationMs), fontSize = 14.sp, color = Color.LightGray)
                            }
                        }
                    }

                    // Bottom Action Row (Pressing Up from any button moves onto the scrubber bar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties {
                                if (!isLive && durationMs > 0L) {
                                    up = scrubberFocus
                                }
                            },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = {
                                hudInteractionTick++
                                if (player.isPlaying) player.pause() else player.play()
                            },
                            modifier = Modifier.focusRequester(primaryControlFocus),
                        ) {
                            Text(if (isPlaying) strings.pause else strings.play)
                        }

                        if (!isLive) {
                            OutlinedButton(
                                onClick = {
                                    hudInteractionTick++
                                    player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                }
                            ) {
                                Text("-10s")
                            }
                            OutlinedButton(
                                onClick = {
                                    hudInteractionTick++
                                    val maxPos = if (durationMs > 0L) durationMs else Long.MAX_VALUE
                                    player.seekTo((player.currentPosition + 10_000L).coerceAtMost(maxPos))
                                }
                            ) {
                                Text("+10s")
                            }
                        } else if (onSwitchChannel != null) {
                            OutlinedButton(
                                onClick = {
                                    hudInteractionTick++
                                    onSwitchChannel(-1)
                                }
                            ) {
                                Text(strings.prevCh)
                            }
                            OutlinedButton(
                                onClick = {
                                    hudInteractionTick++
                                    onSwitchChannel(1)
                                }
                            ) {
                                Text(strings.nextCh)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                hudInteractionTick++
                                aspectIndex = (aspectIndex + 1) % AspectOption.entries.size
                            }
                        ) {
                            Text(strings.aspectLabel(currentAspect.modeName))
                        }

                        OutlinedButton(
                            onClick = {
                                hudInteractionTick++
                                activePicker = ActivePicker.AUDIO
                            },
                            modifier = Modifier.focusRequester(audioButtonFocus),
                        ) {
                            val currentAudio = audioOptions.firstOrNull { it.selected }?.label ?: strings.defaultTrack
                            Text(strings.audioLabel(currentAudio), maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                hudInteractionTick++
                                activePicker = ActivePicker.SUBTITLES
                            },
                            modifier = Modifier.focusRequester(subtitlesButtonFocus),
                        ) {
                            val currentSub = if (subtitlesDisabled) strings.off else (subtitleOptions.firstOrNull { it.selected }?.label ?: strings.on)
                            Text(strings.subtitlesLabel(currentSub), maxLines = 1)
                        }
                    }
                }
            }
        }

        // Modal Track Picker Overlay (Audio / Subtitles + Subtitle Size)
        if (activePicker != ActivePicker.NONE) {
            TrackPickerOverlay(
                title = if (activePicker == ActivePicker.AUDIO) strings.selectAudio else strings.selectSubtitles,
                strings = strings,
                options = if (activePicker == ActivePicker.AUDIO) audioOptions else subtitleOptions,
                showOffOption = activePicker == ActivePicker.SUBTITLES,
                isOffSelected = subtitlesDisabled,
                subtitleSize = if (activePicker == ActivePicker.SUBTITLES) subtitleSize else null,
                onSelectSubtitleSize = onSetSubtitleSize,
                onSelectOff = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    activePicker = ActivePicker.NONE
                },
                onSelectTrack = { opt ->
                    val trackType = if (activePicker == ActivePicker.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(trackType, false)
                        .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex))
                        .build()
                    activePicker = ActivePicker.NONE
                },
                onDismiss = { activePicker = ActivePicker.NONE },
            )
        }
    }
}

@Composable
private fun TrackPickerOverlay(
    title: String,
    strings: AppStrings,
    options: List<TrackOption>,
    showOffOption: Boolean,
    isOffSelected: Boolean,
    subtitleSize: SubtitleSize? = null,
    onSelectSubtitleSize: ((SubtitleSize) -> Unit)? = null,
    onSelectOff: () -> Unit,
    onSelectTrack: (TrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { listFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(Color(0xFF1A1C20))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedButton(onClick = onDismiss) { Text(strings.close) }
            }

            if (subtitleSize != null && onSelectSubtitleSize != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(strings.subtitleSizeTitle, fontSize = 13.sp, color = Color(0xFF9AA1B2))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubtitleSize.entries.forEach { size ->
                            if (size == subtitleSize) {
                                Button(onClick = { onSelectSubtitleSize(size) }) {
                                    Text("✓ ${strings.subtitleSizeLabel(size)}", fontSize = 13.sp)
                                }
                            } else {
                                OutlinedButton(onClick = { onSelectSubtitleSize(size) }) {
                                    Text(strings.subtitleSizeLabel(size), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (options.isEmpty() && !showOffOption) {
                Text(strings.noTracks, color = Color.LightGray, fontSize = 15.sp)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(listFocus),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showOffOption) {
                        item {
                            Surface(
                                onClick = onSelectOff,
                                modifier = Modifier.fillMaxWidth(),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isOffSelected) MaterialTheme.colorScheme.secondaryContainer else Color.White.copy(alpha = 0.06f),
                                ),
                            ) {
                                Text(
                                    text = if (isOffSelected) "✓ ${strings.off}" else strings.off,
                                    modifier = Modifier.padding(14.dp),
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }

                    items(options) { opt ->
                        Surface(
                            onClick = { onSelectTrack(opt) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (opt.selected && !(showOffOption && isOffSelected)) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.White.copy(alpha = 0.06f)
                                },
                            ),
                        ) {
                            val check = if (opt.selected && !(showOffOption && isOffSelected)) "✓ " else ""
                            Text(
                                text = "$check${opt.label}",
                                modifier = Modifier.padding(14.dp),
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractTracks(tracks: Tracks, trackType: Int): List<TrackOption> {
    val result = mutableListOf<TrackOption>()
    var counter = 1
    for (groupInfo in tracks.groups) {
        if (groupInfo.type != trackType) continue
        for (i in 0 until groupInfo.length) {
            if (!groupInfo.isTrackSupported(i)) continue
            val fmt = groupInfo.getTrackFormat(i)
            val lang = fmt.language?.let { Locale.forLanguageTag(it).displayLanguage }
                ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
            val label = listOfNotNull(fmt.label, lang)
                .distinct()
                .joinToString(" • ")
                .ifBlank { "Track $counter" }
            result += TrackOption(
                label = label,
                group = groupInfo.mediaTrackGroup,
                trackIndex = i,
                selected = groupInfo.isTrackSelected(i),
            )
            counter++
        }
    }
    return result
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}