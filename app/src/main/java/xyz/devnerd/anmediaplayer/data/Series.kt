package xyz.devnerd.anmediaplayer.data

import xyz.devnerd.anmediaplayer.data.source.naturalVideoSort

// TV-series detection + season/episode loading.

private val SEASON_RE = Regex("(?i)\\bseason\\b\\s*(\\d+)|^s(\\d{1,2})\\b")

fun isSeasonName(name: String): Boolean = SEASON_RE.containsMatchIn(name)

fun seasonNumber(name: String): Int {
    val m = SEASON_RE.find(name) ?: return 0
    return (m.groupValues[1].ifBlank { m.groupValues[2] }).toIntOrNull() ?: 0
}

data class SeasonRef(val number: Int, val rawName: String, val path: List<String>) {
    /** Tag in parens distinguishes variants, e.g. "Season 1 (Korean Language)" → "Korean Language". */
    val tag: String? get() = Regex("\\(([^)]*)\\)").find(rawName)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    val label: String get() {
        val base = if (number > 0) "Season $number" else cleanTitle(rawName)
        return tag?.let { "$base · $it" } ?: base
    }
}

/** A flat playback entry across seasons. */
data class EpisodeRef(val path: List<String>, val file: String, val durSec: Int)

/** Season folders under a series (or the folder itself if episodes are flat). Empty = not a series. */
fun detectSeasons(entries: List<Entry>, seriesPath: List<String>): List<SeasonRef> {
    val seasonDirs = entries.filter { it.isDir && isSeasonName(it.name) }
    return if (seasonDirs.isNotEmpty()) {
        seasonDirs.map { SeasonRef(seasonNumber(it.name), it.name, seriesPath + it.name) }
            .sortedBy { it.number }
    } else {
        emptyList()
    }
}

suspend fun loadEpisodes(serverId: String, seasonPath: List<String>): List<Entry> =
    naturalVideoSort(runCatching { MediaRepo.list(serverId, seasonPath) }.getOrDefault(emptyList()))

/** Full ordered episode list across all seasons — drives cross-season autoplay. */
suspend fun buildSeriesPlaylist(serverId: String, seasons: List<SeasonRef>): List<EpisodeRef> = buildList {
    for (s in seasons) {
        loadEpisodes(serverId, s.path).forEach { add(EpisodeRef(s.path, it.name, it.durSec ?: 0)) }
    }
}
