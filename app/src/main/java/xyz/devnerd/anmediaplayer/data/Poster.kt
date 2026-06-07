package xyz.devnerd.anmediaplayer.data

import androidx.compose.ui.graphics.Color

/**
 * Deterministic two-stop cover gradient seeded from a name, mirroring posterFor()
 * in data.jsx: hsl(h 58% 44%) → hsl(h2 54% 26%).
 */
data class Poster(val c1: Color, val c2: Color, val glyphHue: Float)

private fun hash(seed: String): Long {
    var h = 0L
    for (ch in seed) h = (h * 31 + ch.code) and 0xFFFFFFFFL
    return h
}

fun posterFor(seed: String): Poster {
    val h = hash(seed.ifEmpty { "x" })
    val hue = (h % 360).toInt()
    val hue2 = ((hue + 30 + ((h shr 8) % 50)) % 360).toInt()
    return Poster(
        c1 = Color.hsl(hue.toFloat(), 0.58f, 0.44f),
        c2 = Color.hsl(hue2.toFloat(), 0.54f, 0.26f),
        glyphHue = hue.toFloat(),
    )
}
