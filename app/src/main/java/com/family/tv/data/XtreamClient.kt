package com.family.tv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

data class VodInfo(
    val plot: String? = null,
    val year: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val genre: String? = null,
)

data class SeriesDetails(
    val info: VodInfo? = null,
    val episodes: List<Episode> = emptyList(),
)

data class EpgProgram(
    val title: String,
    val startTime: String,
    val endTime: String,
)

class XtreamClient(
    private val http: OkHttpClient,
    host: String,
    private val user: String,
    private val pass: String,
) {
    companion object {
        const val LIVE_EXT = "ts"
    }

    private val base: String = normalize(host)

    private fun normalize(h: String): String {
        var s = h.trim()
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return runCatching { URI(s).let { "${it.scheme}://${it.authority}" } }.getOrDefault(s.trimEnd('/'))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun api(action: String? = null, extra: String = ""): String =
        "$base/player_api.php?username=${enc(user)}&password=${enc(pass)}" +
                (if (action != null) "&action=$action" else "") + extra

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "FamilyTV/1.0").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Server answered HTTP ${r.code}")
            return r.body!!.string()
        }
    }

    private fun array(text: String): JSONArray =
        if (text.trimStart().startsWith("[")) JSONArray(text) else JSONArray()

    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k).trim().takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.img(vararg keys: String): String? {
        val raw = keys.firstNotNullOfOrNull { str(it) } ?: return null
        return when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("//") -> "http:$raw"
            raw.startsWith("/") -> "$base$raw"
            else -> "$base/$raw"
        }
    }

    private fun decodeEpgText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return runCatching {
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val decoded = String(bytes, Charsets.UTF_8).trim()
            if (decoded.isNotEmpty() && decoded.none { it.code < 32 && it != '\n' && it != '\r' && it != '\t' }) {
                decoded
            } else {
                raw
            }
        }.getOrDefault(raw)
    }

    fun authenticate() {
        val text = get(api())
        val ui = runCatching { JSONObject(text).optJSONObject("user_info") }.getOrNull()
            ?: error("That doesn't look like an Xtream server – check the address and port")
        if (ui.optInt("auth", 0) != 1) error("Login failed – check username and password")
        val status = ui.optString("status")
        if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) error("Account status: $status")
    }

    private fun categories(action: String): Map<String, String> {
        val a = array(get(api(action)))
        return (0 until a.length()).associate {
            val o = a.getJSONObject(it); o.optString("category_id") to o.optString("category_name")
        }
    }

    private fun live(): List<Channel> {
        val cats = categories("get_live_categories")
        val a = array(get(api("get_live_streams")))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Channel(
                name = o.optString("name"),
                group = cats[o.optString("category_id")] ?: "Other",
                url = "$base/live/$user/$pass/${o.optString("stream_id")}.$LIVE_EXT",
                logo = o.img("stream_icon", "cover"),
                tvgId = o.str("epg_channel_id"),
                section = Section.LIVE,
            )
        }
    }

    private fun movies(): List<Channel> {
        val cats = categories("get_vod_categories")
        val a = array(get(api("get_vod_streams")))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Channel(
                name = o.optString("name"),
                group = cats[o.optString("category_id")] ?: "Other",
                url = "$base/movie/$user/$pass/${o.optString("stream_id")}.${o.str("container_extension") ?: "mp4"}",
                logo = o.img("stream_icon", "cover", "movie_image"),
                tvgId = null,
                section = Section.MOVIES,
            )
        }
    }

    private fun series(): List<Channel> {
        val cats = categories("get_series_categories")
        val a = array(get(api("get_series")))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Channel(
                name = o.optString("name"),
                group = cats[o.optString("category_id")] ?: "Other",
                url = "",
                logo = o.img("cover", "stream_icon"),
                tvgId = null,
                section = Section.SERIES,
                seriesId = o.optInt("series_id"),
            )
        }
    }

    suspend fun loadAll(): List<Channel> = withContext(Dispatchers.IO) {
        authenticate()
        val results = listOf(
            async { runCatching { live() } },
            async { runCatching { movies() } },
            async { runCatching { series() } },
        ).awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }
    }

    fun vodInfo(vodId: Int): VodInfo? = runCatching {
        val root = JSONObject(get(api("get_vod_info", "&vod_id=$vodId")))
        val info = root.optJSONObject("info") ?: return@runCatching null
        val release = info.str("releasedate") ?: info.str("releaseDate")
        VodInfo(
            plot = info.str("plot") ?: info.str("description"),
            year = release?.take(4)?.takeIf { it.all(Char::isDigit) },
            rating = info.str("rating")?.takeIf { it != "0" && it != "0.0" },
            duration = info.str("duration"),
            genre = info.str("genre"),
        )
    }.getOrNull()

    fun shortEpg(streamId: Int): List<EpgProgram> = runCatching {
        val root = JSONObject(get(api("get_short_epg", "&stream_id=$streamId&limit=2")))
        val listings = root.optJSONArray("epg_listings") ?: return@runCatching emptyList()
        List(listings.length()) { i ->
            val o = listings.getJSONObject(i)
            val startRaw = o.str("start") ?: ""
            val endRaw = o.str("end") ?: ""
            val startShort = startRaw.substringAfter(' ', startRaw).take(5)
            val endShort = endRaw.substringAfter(' ', endRaw).take(5)
            EpgProgram(
                title = decodeEpgText(o.str("title")),
                startTime = startShort,
                endTime = endShort,
            )
        }.filter { it.title.isNotBlank() }
    }.getOrDefault(emptyList())

    fun seriesDetails(seriesId: Int): SeriesDetails {
        val j = JSONObject(get(api("get_series_info", "&series_id=$seriesId")))
        val infoObj = j.optJSONObject("info")
        val info = infoObj?.let {
            val release = it.str("releaseDate") ?: it.str("releasedate")
            VodInfo(
                plot = it.str("plot") ?: it.str("description"),
                year = release?.take(4)?.takeIf { y -> y.all(Char::isDigit) },
                rating = it.str("rating")?.takeIf { r -> r != "0" && r != "0.0" },
                duration = it.str("episode_run_time")?.let { m -> "${m}m" },
                genre = it.str("genre"),
            )
        }

        val out = ArrayList<Episode>()
        fun addSeason(season: Int, arr: JSONArray) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val num = o.optInt("episode_num", i + 1)
                out += Episode(
                    season = season,
                    number = num,
                    title = o.str("title") ?: "Episode $num",
                    url = "$base/series/$user/$pass/${o.optString("id")}.${o.str("container_extension") ?: "mp4"}",
                )
            }
        }
        when (val raw = j.opt("episodes")) {
            is JSONObject -> raw.keys().forEach { k -> raw.optJSONArray(k)?.let { addSeason(k.toIntOrNull() ?: 0, it) } }
            is JSONArray -> for (i in 0 until raw.length()) raw.optJSONArray(i)?.let { addSeason(i + 1, it) }
        }
        return SeriesDetails(
            info = info,
            episodes = out.sortedWith(compareBy({ it.season }, { it.number })),
        )
    }
}