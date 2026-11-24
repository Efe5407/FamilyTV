package com.family.tv.data

import java.io.BufferedReader

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    /** Streams line by line, so large (50k+ entry) playlists don't blow up memory. */
    fun parse(reader: BufferedReader): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var attrs: Map<String, String> = emptyMap()
        var extGrp: String? = null
        var pending = false

        reader.forEachLine { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    // Display name = text after the first comma that follows the last quoted attribute
                    val from = maxOf(line.lastIndexOf('"'), 0)
                    val comma = line.indexOf(',', from)
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    pending = true
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> extGrp = line.substringAfter(':').trim()
                line.startsWith("#") -> Unit // #EXTM3U, #EXTVLCOPT, etc.
                pending -> {
                    val group = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: extGrp ?: "Uncategorized"
                    out += Channel(
                        name = name.ifBlank { attrs["tvg-name"] ?: "Unnamed" },
                        group = group,
                        url = line,
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() },
                        tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
                        section = classify(line, group),
                    )
                    pending = false; extGrp = null; attrs = emptyMap()
                }
            }
        }
        return out
    }

    /** Xtream-style URLs contain /movie/ or /series/; otherwise fall back to group-name hints. */
    private fun classify(url: String, group: String): Section {
        val u = url.lowercase(); val g = group.lowercase()
        return when {
            "/series/" in u || "series" in g -> Section.SERIES
            "/movie/" in u || "movie" in g || "vod" in g || "film" in g -> Section.MOVIES
            else -> Section.LIVE
        }
    }
}
