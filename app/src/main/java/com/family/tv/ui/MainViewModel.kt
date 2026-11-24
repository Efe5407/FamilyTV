package com.family.tv.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.family.tv.BuildConfig
import com.family.tv.data.AppLanguage
import com.family.tv.data.Channel
import com.family.tv.data.EpgProgram
import com.family.tv.data.Episode
import com.family.tv.data.PlaylistRepository
import com.family.tv.data.Section
import com.family.tv.data.Source
import com.family.tv.data.SubtitleSize
import com.family.tv.data.VodInfo
import com.family.tv.data.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionName: String, val apkUrl: String) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class ReadyToInstall(val apkFile: File, val needsPermission: Boolean = false) : UpdateState
    data class Error(val message: String) : UpdateState
}

data class UiState(
    val loading: Boolean = false,
    val error: String? = null,
    val channels: List<Channel> = emptyList(),
    val selectedSection: Section? = null,
    val lastVisitedSection: Section = Section.LIVE,
    val settingsOpen: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val recent: List<String> = emptyList(),
    val watchProgress: Map<String, WatchProgress> = emptyMap(),
    val seriesLastEpisode: Map<Int, String> = emptyMap(),
    val nowPlaying: Channel? = null,
    val nextEpisode: Episode? = null,
    val saved: List<Source> = emptyList(),
    val series: Channel? = null,
    val seriesInfo: VodInfo? = null,
    val episodes: List<Episode>? = null,
    val selectedMovie: Channel? = null,
    val selectedMovieInfo: VodInfo? = null,
    val loadingMovieInfo: Boolean = false,
    val epgMap: Map<String, List<EpgProgram>> = emptyMap(),
    val parentalPin: String? = null,
    val lockedCategories: Set<String> = emptySet(),
    val unlockedCategoriesSession: Set<String> = emptySet(),
    val language: AppLanguage = AppLanguage.EN,
    val autoLogin: Boolean = true,
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val updateState: UpdateState = UpdateState.Idle,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PlaylistRepository(app)
    private val updateClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow(
        UiState(
            saved = repo.saved(),
            favorites = repo.favorites(),
            recent = repo.recent(),
            watchProgress = repo.allProgress(),
            seriesLastEpisode = repo.seriesEpisodeMap(),
            parentalPin = repo.parentalPin(),
            lockedCategories = repo.lockedCategories(),
            language = repo.language(),
            autoLogin = repo.autoLoginEnabled(),
            subtitleSize = repo.subtitleSize(),
        )
    )
    val state: StateFlow<UiState> = _state

    init {
        if (repo.autoLoginEnabled()) {
            repo.lastSource()?.let { src ->
                repo.attachClient(src)
                val cached = repo.loadCachedChannels(src)
                if (cached.isNotEmpty()) {
                    val locks = repo.autoSeedAdultLocks(cached)
                    _state.update { it.copy(channels = cached, lockedCategories = locks) }
                    load(src, background = true)
                } else {
                    load(src, background = false)
                }
            }
        }
    }

    fun load(source: Source, background: Boolean = false) {
        if (!background) {
            _state.update { it.copy(loading = true, error = null) }
        }
        viewModelScope.launch {
            runCatching { repo.load(source) }
                .onSuccess { list ->
                    repo.save(source)
                    val locks = repo.autoSeedAdultLocks(list)
                    _state.update {
                        it.copy(
                            loading = false,
                            channels = list,
                            lockedCategories = locks,
                            saved = repo.saved(),
                        )
                    }
                }
                .onFailure { e ->
                    if (!background) {
                        _state.update { it.copy(loading = false, error = e.message ?: "Failed to load") }
                    }
                }
        }
    }

    fun forget(source: Source) { repo.forget(source); _state.update { it.copy(saved = repo.saved()) } }

    fun selectSection(section: Section) {
        _state.update {
            it.copy(
                selectedSection = section,
                lastVisitedSection = section,
                settingsOpen = false,
            )
        }
    }

    fun closeSection() {
        _state.update { it.copy(selectedSection = null, selectedMovie = null, selectedMovieInfo = null) }
    }

    fun openSettings() { _state.update { it.copy(settingsOpen = true) } }
    fun closeSettings() { _state.update { it.copy(settingsOpen = false) } }

    fun setLanguage(lang: AppLanguage) {
        repo.setLanguage(lang)
        _state.update { it.copy(language = lang) }
    }

    fun setAutoLogin(enabled: Boolean) {
        repo.setAutoLoginEnabled(enabled)
        _state.update { it.copy(autoLogin = enabled) }
    }

    fun setSubtitleSize(size: SubtitleSize) {
        repo.setSubtitleSize(size)
        _state.update { it.copy(subtitleSize = size) }
    }

    // ---- GitHub Releases Updater ----
    fun checkForUpdate() {
        if (_state.value.updateState is UpdateState.Checking ||
            _state.value.updateState is UpdateState.Downloading
        ) return

        _state.update { it.copy(updateState = UpdateState.Checking) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder()
                        .url("https://api.github.com/repos/Efe5407/FamilyTV/releases/latest")
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "FamilyTV/${BuildConfig.VERSION_NAME}")
                        .build()

                    updateClient.newCall(req).execute().use { resp ->
                        if (resp.code == 404) return@withContext UpdateState.UpToDate
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val json = JSONObject(resp.body?.string().orEmpty())
                        val rawTag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
                        val assets = json.optJSONArray("assets")
                        var apkUrl: String? = null
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    apkUrl = asset.optString("browser_download_url")
                                    break
                                }
                            }
                        }

                        if (rawTag.isNotBlank() && !apkUrl.isNullOrBlank() &&
                            isNewerVersion(rawTag, BuildConfig.VERSION_NAME)
                        ) {
                            UpdateState.Available(versionName = rawTag, apkUrl = apkUrl)
                        } else {
                            UpdateState.UpToDate
                        }
                    }
                }
            }.onSuccess { result ->
                _state.update { it.copy(updateState = result) }
            }.onFailure { e ->
                _state.update { it.copy(updateState = UpdateState.Error(e.message ?: "Network error")) }
            }
        }
    }

    fun downloadAndInstallUpdate(apkUrl: String) {
        if (_state.value.updateState is UpdateState.Downloading) return
        _state.update { it.copy(updateState = UpdateState.Downloading(0)) }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val targetFile = File(app.cacheDir, "familytv-update.apk")
                    if (targetFile.exists()) targetFile.delete()

                    val req = Request.Builder()
                        .url(apkUrl)
                        .header("User-Agent", "FamilyTV/${BuildConfig.VERSION_NAME}")
                        .build()

                    updateClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        val body = resp.body ?: error("Empty response")
                        val totalBytes = body.contentLength()
                        var downloaded = 0L
                        var lastPct = -1

                        body.byteStream().use { input ->
                            targetFile.outputStream().use { output ->
                                val buf = ByteArray(16 * 1024)
                                while (true) {
                                    val read = input.read(buf)
                                    if (read == -1) break
                                    output.write(buf, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0L) {
                                        val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            _state.update { it.copy(updateState = UpdateState.Downloading(pct)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    targetFile
                }
            }.onSuccess { apkFile ->
                triggerApkInstall(apkFile)
            }.onFailure { e ->
                _state.update { it.copy(updateState = UpdateState.Error(e.message ?: "Download failed")) }
            }
        }
    }

    fun triggerApkInstall(apkFile: File) {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            _state.update { it.copy(updateState = UpdateState.ReadyToInstall(apkFile, needsPermission = true)) }
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${app.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(permIntent) }
            return
        }

        _state.update { it.copy(updateState = UpdateState.ReadyToInstall(apkFile, needsPermission = false)) }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { app.startActivity(installIntent) }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val rParts = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val lParts = local.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    // ---- Parental Lock ----
    fun setParentalPin(pin: String?) {
        repo.setParentalPin(pin)
        _state.update {
            it.copy(
                parentalPin = repo.parentalPin(),
                unlockedCategoriesSession = if (pin == null) emptySet() else it.unlockedCategoriesSession,
            )
        }
    }

    fun toggleLockedCategory(section: Section, group: String) {
        val key = PlaylistRepository.categoryLockKey(section, group)
        val updated = repo.toggleLockedCategory(key)
        _state.update { it.copy(lockedCategories = updated) }
    }

    fun unlockCategoryForSession(section: Section, group: String) {
        val key = PlaylistRepository.categoryLockKey(section, group)
        _state.update { it.copy(unlockedCategoriesSession = it.unlockedCategoriesSession + key) }
    }

    // ---- Watch Progress & Favorites ----
    fun saveWatchProgress(url: String, positionMs: Long, durationMs: Long) {
        val updated = repo.saveProgress(url, positionMs, durationMs)
        _state.update { it.copy(watchProgress = updated) }
    }

    fun clearWatchProgress() {
        val cleared = repo.clearProgress()
        _state.update { it.copy(watchProgress = cleared, seriesLastEpisode = emptyMap()) }
    }

    fun toggleFavorite(c: Channel) {
        val key = PlaylistRepository.channelKey(c)
        val updated = repo.toggleFavorite(key)
        _state.update { it.copy(favorites = updated) }
    }

    // ---- EPG Program Guide ----
    fun fetchEpgIfNeeded(ch: Channel) {
        if (ch.section != Section.LIVE || ch.url.isBlank()) return
        if (_state.value.epgMap.containsKey(ch.url)) return
        viewModelScope.launch {
            val programs = repo.shortEpg(ch.url)
            _state.update { it.copy(epgMap = it.epgMap + (ch.url to programs)) }
        }
    }

    // ---- Open Live, Movie Info, or Series ----
    fun open(c: Channel) {
        val updatedRecent = repo.recordRecent(PlaylistRepository.channelKey(c))
        if (c.section == Section.MOVIES) {
            _state.update {
                it.copy(
                    selectedMovie = c,
                    selectedMovieInfo = null,
                    loadingMovieInfo = true,
                    recent = updatedRecent,
                )
            }
            viewModelScope.launch {
                val info = repo.vodInfo(c.url)
                _state.update { s ->
                    if (s.selectedMovie?.url == c.url) {
                        s.copy(selectedMovieInfo = info, loadingMovieInfo = false)
                    } else s
                }
            }
            return
        }

        val id = c.seriesId
        if (id == null) {
            fetchEpgIfNeeded(c)
            _state.update { it.copy(nowPlaying = c, nextEpisode = null, recent = updatedRecent) }
            return
        }
        _state.update { it.copy(series = c, seriesInfo = null, episodes = null, recent = updatedRecent) }
        viewModelScope.launch {
            runCatching { repo.seriesDetails(id) }
                .onSuccess { details ->
                    _state.update { it.copy(seriesInfo = details.info, episodes = details.episodes) }
                }
                .onFailure { _state.update { s -> s.copy(episodes = emptyList()) } }
        }
    }

    fun playSelectedMovie() {
        val movie = _state.value.selectedMovie ?: return
        _state.update {
            it.copy(
                nowPlaying = movie,
                selectedMovie = null,
                selectedMovieInfo = null,
                nextEpisode = null,
            )
        }
    }

    fun closeMovieInfo() {
        _state.update { it.copy(selectedMovie = null, selectedMovieInfo = null, loadingMovieInfo = false) }
    }

    fun playEpisode(e: Episode) {
        val s = _state.value.series ?: return
        val title = "${s.name} – S${e.season}E${e.number}"
        val eps = _state.value.episodes.orEmpty()
        val idx = eps.indexOfFirst { it.season == e.season && it.number == e.number && it.url == e.url }
        val nextEp = if (idx >= 0 && idx + 1 < eps.size) eps[idx + 1] else null
        val updatedSeriesMap = s.seriesId?.let { repo.recordSeriesEpisode(it, e.url) } ?: _state.value.seriesLastEpisode
        _state.update {
            it.copy(
                nowPlaying = s.copy(name = title, url = e.url, seriesId = null),
                nextEpisode = nextEp,
                seriesLastEpisode = updatedSeriesMap,
            )
        }
    }

    fun playNextEpisode() {
        val next = _state.value.nextEpisode ?: return
        playEpisode(next)
    }

    fun switchChannel(delta: Int) {
        val current = _state.value.nowPlaying ?: return
        if (current.section != Section.LIVE) return
        val inGroup = _state.value.channels.filter { it.section == Section.LIVE && it.group == current.group }
        val pool = if (inGroup.size > 1) inGroup else _state.value.channels.filter { it.section == Section.LIVE }
        if (pool.isEmpty()) return
        val idx = pool.indexOfFirst { it.url == current.url && it.name == current.name }
        val nextIdx = if (idx == -1) 0 else (idx + delta + pool.size) % pool.size
        val nextChannel = pool[nextIdx]
        val updatedRecent = repo.recordRecent(PlaylistRepository.channelKey(nextChannel))
        fetchEpgIfNeeded(nextChannel)
        _state.update { it.copy(nowPlaying = nextChannel, nextEpisode = null, recent = updatedRecent) }
    }

    fun stopPlaying() { _state.update { it.copy(nowPlaying = null, nextEpisode = null) } }
    fun closeSeries() { _state.update { it.copy(series = null, seriesInfo = null, episodes = null) } }
    fun closePlaylist() {
        _state.update {
            it.copy(
                channels = emptyList(),
                selectedSection = null,
                settingsOpen = false,
                series = null,
                episodes = null,
                selectedMovie = null,
                selectedMovieInfo = null,
                nextEpisode = null,
            )
        }
    }
}