package com.family.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    TR("tr", "Türkçe"),
}

enum class SubtitleSize(val key: String, val spValue: Float) {
    NORMAL("normal", 20f),
    LARGE("large", 26f),
    XLARGE("xlarge", 32f),
}

data class WatchProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val isInProgress: Boolean
        get() = fraction in 0.01f..0.949f
}

class PlaylistRepository(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val prefs = context.getSharedPreferences("sources", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "playlist_cache.json")
    private var xtream: XtreamClient? = null

    // ---- saved sources ----
    private fun encode(s: Source): String? = when (s) {
        Source.Demo -> null
        is Source.M3u -> JSONObject().put("t", "m3u").put("url", s.url).toString()
        is Source.Xtream -> JSONObject().put("t", "xt").put("h", s.host).put("u", s.user).put("p", s.pass).toString()
    }

    private fun decode(text: String): Source? = runCatching {
        val o = JSONObject(text)
        when (o.getString("t")) {
            "m3u" -> Source.M3u(o.getString("url"))
            else -> Source.Xtream(o.getString("h"), o.getString("u"), o.getString("p"))
        }
    }.getOrNull()

    fun saved(): List<Source> =
        prefs.getStringSet("items", emptySet())!!.mapNotNull(::decode).sortedBy { it.label() }

    fun save(s: Source) {
        val e = encode(s) ?: return
        prefs.edit()
            .putStringSet("items", prefs.getStringSet("items", emptySet())!! + e)
            .putString("last_source", e)
            .apply()
    }

    fun lastSource(): Source? {
        val raw = prefs.getString("last_source", null)
        return raw?.let(::decode) ?: saved().firstOrNull()
    }

    fun forget(s: Source) {
        val e = encode(s) ?: return
        val editor = prefs.edit().putStringSet("items", prefs.getStringSet("items", emptySet())!! - e)
        if (prefs.getString("last_source", null) == e) {
            editor.remove("last_source")
            runCatching { cacheFile.delete() }
        }
        editor.apply()
    }

    // ---- instant startup cache ----
    fun loadCachedChannels(source: Source): List<Channel> = runCatching {
        val encodedSource = encode(source) ?: return emptyList()
        if (!cacheFile.exists()) return emptyList()
        val root = JSONObject(cacheFile.readText())
        if (root.optString("source") != encodedSource) return emptyList()
        val arr = root.optJSONArray("channels") ?: return emptyList()
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Channel(
                name = o.getString("n"),
                group = o.getString("g"),
                url = o.getString("u"),
                logo = o.optString("l").takeIf { it.isNotEmpty() },
                tvgId = o.optString("t").takeIf { it.isNotEmpty() },
                section = Section.valueOf(o.getString("s")),
                seriesId = if (o.has("sid")) o.getInt("sid") else null,
            )
        }
    }.getOrDefault(emptyList())

    private fun saveCachedChannels(source: Source, channels: List<Channel>) {
        val encodedSource = encode(source) ?: return
        runCatching {
            val arr = JSONArray()
            channels.forEach { ch ->
                val o = JSONObject()
                    .put("n", ch.name)
                    .put("g", ch.group)
                    .put("u", ch.url)
                    .put("s", ch.section.name)
                if (!ch.logo.isNullOrEmpty()) o.put("l", ch.logo)
                if (!ch.tvgId.isNullOrEmpty()) o.put("t", ch.tvgId)
                if (ch.seriesId != null) o.put("sid", ch.seriesId)
                arr.put(o)
            }
            val root = JSONObject()
                .put("source", encodedSource)
                .put("channels", arr)
            cacheFile.writeText(root.toString())
        }
    }

    fun attachClient(source: Source) {
        if (source is Source.Xtream) {
            xtream = XtreamClient(client, source.host, source.user, source.pass)
        }
    }

    // ---- parental lock (4-digit PIN + locked categories) ----
    fun parentalPin(): String? = prefs.getString("parental_pin", null)?.takeIf { it.length == 4 }

    fun setParentalPin(pin: String?) {
        val editor = prefs.edit()
        if (pin.isNullOrBlank()) {
            editor.remove("parental_pin")
        } else {
            editor.putString("parental_pin", pin)
        }
        editor.apply()
    }

    fun lockedCategories(): Set<String> =
        prefs.getStringSet("locked_categories", emptySet())?.toSet() ?: emptySet()

    fun toggleLockedCategory(catKey: String): Set<String> {
        val current = lockedCategories().toMutableSet()
        if (!current.add(catKey)) current.remove(catKey)
        prefs.edit().putStringSet("locked_categories", current).apply()
        return current
    }

    fun autoSeedAdultLocks(channels: List<Channel>): Set<String> {
        if (prefs.getBoolean("adult_locks_seeded", false)) return lockedCategories()
        val adultKeywords = listOf("xxx", "adult", "+18", "18+", "yetiskin", "yetişkin", "erotik", "porno")
        val autoLocked = channels
            .filter { ch -> adultKeywords.any { kw -> ch.group.contains(kw, ignoreCase = true) } }
            .map { categoryLockKey(it.section, it.group) }
            .toSet()
        val merged = lockedCategories() + autoLocked
        prefs.edit()
            .putStringSet("locked_categories", merged)
            .putBoolean("adult_locks_seeded", true)
            .apply()
        return merged
    }

    // ---- app settings (auto-login, language, subtitle size) ----
    fun autoLoginEnabled(): Boolean = prefs.getBoolean("auto_login", true)

    fun setAutoLoginEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_login", enabled).apply()
    }

    fun language(): AppLanguage =
        if (prefs.getString("language", "en") == "tr") AppLanguage.TR else AppLanguage.EN

    fun setLanguage(lang: AppLanguage) {
        prefs.edit().putString("language", lang.code).apply()
    }

    fun subtitleSize(): SubtitleSize = when (prefs.getString("subtitle_size", "normal")) {
        "large" -> SubtitleSize.LARGE
        "xlarge" -> SubtitleSize.XLARGE
        else -> SubtitleSize.NORMAL
    }

    fun setSubtitleSize(size: SubtitleSize) {
        prefs.edit().putString("subtitle_size", size.key).apply()
    }

    // ---- watch progress ----
    fun allProgress(): Map<String, WatchProgress> = runCatching {
        val root = JSONObject(prefs.getString("watch_progress", "{}"))
        buildMap {
            root.keys().forEach { url ->
                val obj = root.optJSONObject(url) ?: return@forEach
                val pos = obj.optLong("p", 0L)
                val dur = obj.optLong("d", 0L)
                if (pos > 0L && dur > 0L) put(url, WatchProgress(pos, dur))
            }
        }
    }.getOrDefault(emptyMap())

    fun saveProgress(url: String, positionMs: Long, durationMs: Long): Map<String, WatchProgress> {
        if (url.isBlank() || durationMs <= 0L) return allProgress()
        val root = runCatching { JSONObject(prefs.getString("watch_progress", "{}")) }.getOrDefault(JSONObject())
        val ratio = positionMs.toFloat() / durationMs.toFloat()
        if (positionMs < 5_000L) {
            return allProgress()
        } else if (ratio >= 0.95f) {
            root.put(url, JSONObject().put("p", durationMs).put("d", durationMs))
        } else {
            root.put(url, JSONObject().put("p", positionMs).put("d", durationMs))
        }
        prefs.edit().putString("watch_progress", root.toString()).apply()
        return allProgress()
    }

    fun seriesEpisodeMap(): Map<Int, String> = runCatching {
        val root = JSONObject(prefs.getString("series_last_ep", "{}"))
        buildMap {
            root.keys().forEach { k ->
                val id = k.toIntOrNull() ?: return@forEach
                val url = root.optString(k)
                if (url.isNotBlank()) put(id, url)
            }
        }
    }.getOrDefault(emptyMap())

    fun recordSeriesEpisode(seriesId: Int, episodeUrl: String): Map<Int, String> {
        val root = runCatching { JSONObject(prefs.getString("series_last_ep", "{}")) }.getOrDefault(JSONObject())
        root.put(seriesId.toString(), episodeUrl)
        prefs.edit().putString("series_last_ep", root.toString()).apply()
        return seriesEpisodeMap()
    }

    fun clearProgress(): Map<String, WatchProgress> {
        prefs.edit()
            .remove("watch_progress")
            .remove("series_last_ep")
            .apply()
        return emptyMap()
    }

    // ---- favorites & recently watched ----
    fun favorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun toggleFavorite(key: String): Set<String> {
        val current = favorites().toMutableSet()
        if (!current.add(key)) current.remove(key)
        prefs.edit().putStringSet("favorites", current).apply()
        return current
    }

    fun recent(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString("recent", "[]"))
        List(arr.length()) { i -> arr.getString(i) }
    }.getOrDefault(emptyList())

    fun recordRecent(key: String): List<String> {
        val updated = (listOf(key) + recent().filterNot { it == key }).take(30)
        val arr = JSONArray().apply { updated.forEach(::put) }
        prefs.edit().putString("recent", arr.toString()).apply()
        return updated
    }

    // ---- loading & Xtream info ----
    suspend fun load(source: Source): List<Channel> {
        val result = when (source) {
            Source.Demo -> withContext(Dispatchers.IO) {
                context.assets.open("sample.m3u").bufferedReader().use(M3uParser::parse)
            }
            is Source.M3u -> withContext(Dispatchers.IO) {
                val req = Request.Builder().url(source.url).header("User-Agent", "FamilyTV/1.0").build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    M3uParser.parse(r.body!!.charStream().buffered())
                }
            }
            is Source.Xtream -> {
                val c = XtreamClient(client, source.host, source.user, source.pass)
                xtream = c
                c.loadAll()
            }
        }
        withContext(Dispatchers.IO) { saveCachedChannels(source, result) }
        return result
    }

    suspend fun seriesDetails(seriesId: Int): SeriesDetails =
        withContext(Dispatchers.IO) { xtream?.seriesDetails(seriesId) ?: SeriesDetails() }

    suspend fun vodInfo(movieUrl: String): VodInfo? = withContext(Dispatchers.IO) {
        val vodId = extractStreamId(movieUrl) ?: return@withContext null
        xtream?.vodInfo(vodId)
    }

    suspend fun shortEpg(liveUrl: String): List<EpgProgram> = withContext(Dispatchers.IO) {
        val streamId = extractStreamId(liveUrl) ?: return@withContext emptyList()
        xtream?.shortEpg(streamId) ?: emptyList()
    }

    companion object {
        fun channelKey(ch: Channel): String =
            "${ch.section.name}:${ch.seriesId ?: ch.url.ifBlank { ch.name }}"

        fun categoryLockKey(section: Section, group: String): String =
            "${section.name}:$group"

        fun extractStreamId(url: String): Int? =
            url.substringAfterLast('/').substringBefore('.').toIntOrNull()
    }
}