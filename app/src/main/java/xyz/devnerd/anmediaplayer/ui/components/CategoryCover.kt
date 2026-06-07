package xyz.devnerd.anmediaplayer.ui.components

import xyz.devnerd.anmediaplayer.R

/** Bundled placeholder cover for well-known root categories, matched on folder name. */
fun categoryCover(name: String): Int? {
    val n = name.lowercase()
    return when {
        n.contains("imdb") -> R.drawable.cat_imdb
        Regex("anim|cartoon|anime").containsMatchIn(n) -> R.drawable.cat_animation
        Regex("korea|k[ .-]?drama|kdrama").containsMatchIn(n) -> R.drawable.cat_korean
        Regex("south|tamil|telugu|malayalam|kannada").containsMatchIn(n) -> R.drawable.cat_south
        n.contains("english") && Regex("tv|series|web").containsMatchIn(n) -> R.drawable.cat_eng_tv_series
        n.contains("english") -> R.drawable.cat_english
        n.contains("hindi") || n.contains("bollywood") -> R.drawable.cat_hindi
        Regex("tv series|web series|\\bseries\\b").containsMatchIn(n) -> R.drawable.cat_eng_tv_series
        else -> null
    }
}
