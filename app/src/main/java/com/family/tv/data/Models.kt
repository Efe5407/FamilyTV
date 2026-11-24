package com.family.tv.data

enum class Section(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series") }

data class Channel(
    val name: String,
    val group: String,
    val url: String,          // empty for a series entry (episodes are fetched on demand)
    val logo: String?,
    val tvgId: String?,       // EPG key
    val section: Section,
    val seriesId: Int? = null,
)

data class Episode(val season: Int, val number: Int, val title: String, val url: String)

/** Where a playlist comes from. */
sealed interface Source {
    data object Demo : Source
    data class M3u(val url: String) : Source
    data class Xtream(val host: String, val user: String, val pass: String) : Source

    fun label(): String = when (this) {
        Demo -> "Demo playlist"
        is M3u -> url.take(60)
        is Xtream -> "$host  ($user)"
    }
}