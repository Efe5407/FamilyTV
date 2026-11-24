package com.family.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.*
import coil.Coil
import coil.EventListener
import coil.ImageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.family.tv.data.Source
import com.family.tv.ui.*
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Coil.setImageLoader(buildImageLoader())

        if (savedInstanceState == null) {
            intent.getStringExtra("playlist_url")?.let { vm.load(Source.M3u(it)) }
            intent.getStringExtra("xt_host")?.let {
                vm.load(Source.Xtream(it, intent.getStringExtra("xt_user") ?: "", intent.getStringExtra("xt_pass") ?: ""))
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), shape = androidx.compose.ui.graphics.RectangleShape) {
                    val s by vm.state.collectAsState()
                    val strings = remember(s.language) { AppStrings(s.language) }
                    when {
                        s.nowPlaying != null -> {
                            val current = s.nowPlaying!!
                            val savedPos = s.watchProgress[current.url]?.let { wp ->
                                if (wp.fraction < 0.95f) wp.positionMs else 0L
                            } ?: 0L
                            val nextTitle = s.nextEpisode?.let { "S${it.season}E${it.number} • ${it.title}" }
                            val epgSubtitle = if (current.section == com.family.tv.data.Section.LIVE) {
                                s.epgMap[current.url]?.firstOrNull()?.let { "${it.title} (${it.startTime}–${it.endTime})" }
                            } else null
                            PlayerScreen(
                                channel = current,
                                subtitleOverride = epgSubtitle,
                                strings = strings,
                                subtitleSize = s.subtitleSize,
                                onSetSubtitleSize = vm::setSubtitleSize,
                                initialPositionMs = savedPos,
                                nextEpisodeTitle = nextTitle,
                                onPlayNextEpisode = if (s.nextEpisode != null) vm::playNextEpisode else null,
                                onSaveProgress = { pos, dur -> vm.saveWatchProgress(current.url, pos, dur) },
                                onSwitchChannel = vm::switchChannel,
                                onBack = vm::stopPlaying,
                            )
                        }
                        s.settingsOpen -> {
                            BackHandler { vm.closeSettings() }
                            SettingsScreen(
                                strings = strings,
                                channels = s.channels,
                                language = s.language,
                                subtitleSize = s.subtitleSize,
                                autoLogin = s.autoLogin,
                                parentalPin = s.parentalPin,
                                lockedCategories = s.lockedCategories,
                                updateState = s.updateState,
                                onCheckUpdate = vm::checkForUpdate,
                                onDownloadUpdate = vm::downloadAndInstallUpdate,
                                onInstallApk = vm::triggerApkInstall,
                                onSetLanguage = vm::setLanguage,
                                onSetSubtitleSize = vm::setSubtitleSize,
                                onSetAutoLogin = vm::setAutoLogin,
                                onSetParentalPin = vm::setParentalPin,
                                onToggleLockedCategory = vm::toggleLockedCategory,
                                onClearHistory = vm::clearWatchProgress,
                                onBack = vm::closeSettings,
                            )
                        }
                        s.channels.isNotEmpty() && s.selectedSection != null -> {
                            Box(Modifier.fillMaxSize()) {
                                BrowseScreen(
                                    section = s.selectedSection!!,
                                    channels = s.channels,
                                    favorites = s.favorites,
                                    recent = s.recent,
                                    watchProgress = s.watchProgress,
                                    seriesLastEpisode = s.seriesLastEpisode,
                                    epgMap = s.epgMap,
                                    parentalPin = s.parentalPin,
                                    lockedCategories = s.lockedCategories,
                                    unlockedCategoriesSession = s.unlockedCategoriesSession,
                                    selectedMovie = s.selectedMovie,
                                    selectedMovieInfo = s.selectedMovieInfo,
                                    loadingMovieInfo = s.loadingMovieInfo,
                                    selectedSeries = s.series,
                                    isSeriesOpen = s.series != null,
                                    strings = strings,
                                    onFocusLiveChannel = vm::fetchEpgIfNeeded,
                                    onPlay = vm::open,
                                    onPlaySelectedMovie = vm::playSelectedMovie,
                                    onCloseMovieInfo = vm::closeMovieInfo,
                                    onToggleFavorite = vm::toggleFavorite,
                                    onUnlockCategory = vm::unlockCategoryForSession,
                                    onBack = vm::closeSection,
                                )

                                if (s.series != null) {
                                    BackHandler { vm.closeSeries() }
                                    EpisodesScreen(
                                        series = s.series!!,
                                        seriesInfo = s.seriesInfo,
                                        episodes = s.episodes,
                                        watchProgress = s.watchProgress,
                                        strings = strings,
                                        onPlay = vm::playEpisode,
                                        onBack = vm::closeSeries,
                                    )
                                } else if (s.selectedMovie == null) {
                                    BackHandler { vm.closeSection() }
                                }
                            }
                        }
                        s.channels.isNotEmpty() -> {
                            BackHandler { vm.closePlaylist() }
                            HomeScreen(
                                channels = s.channels,
                                lastVisitedSection = s.lastVisitedSection,
                                strings = strings,
                                onSelectSection = vm::selectSection,
                                onOpenSettings = vm::openSettings,
                                onSwitchSource = vm::closePlaylist,
                            )
                        }
                        else -> SetupScreen(s, strings, vm::load)
                    }
                }
            }
        }
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun buildImageLoader(): ImageLoader {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }

        val okHttp = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAll[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) FamilyTV/1.0")
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    Log.w("CoilImg", "Failed: ${request.data} -> ${result.throwable.message}")
                }
            })
            .crossfade(true)
            .build()
    }
}