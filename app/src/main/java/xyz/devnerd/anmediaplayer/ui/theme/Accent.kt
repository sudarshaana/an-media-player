package xyz.devnerd.anmediaplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Swappable accents. Each only re-maps the `primary` family per scheme so the rest
 * of the M3 scheme stays coherent. Mirrors `ACCENTS` in the design's theme.jsx.
 */
enum class Accent(
    val key: String,
    val label: String,
    val swatch: Color,
    val light: PrimaryFamily,
    val dark: PrimaryFamily,
) {
    PURPLE(
        "purple", "Purple", Color(0xFF6750A4),
        PrimaryFamily(0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D),
        PrimaryFamily(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF),
    ),
    GREEN(
        "green", "Forest", Color(0xFF1F8A5B),
        PrimaryFamily(0xFF1F6E45, 0xFFFFFFFF, 0xFFA7F2C2, 0xFF002111),
        PrimaryFamily(0xFF8BD6A7, 0xFF003820, 0xFF00522F, 0xFFA7F2C2),
    ),
    AMBER(
        "amber", "Amber", Color(0xFFE5A00D),
        PrimaryFamily(0xFF7E5700, 0xFFFFFFFF, 0xFFFFDDB0, 0xFF281800),
        PrimaryFamily(0xFFF8BD49, 0xFF422C00, 0xFF5F4100, 0xFFFFDDB0),
    ),
    AZURE(
        "azure", "Azure", Color(0xFF2A6FDB),
        PrimaryFamily(0xFF1F5DC4, 0xFFFFFFFF, 0xFFD9E2FF, 0xFF001A41),
        PrimaryFamily(0xFFAFC6FF, 0xFF002C71, 0xFF00419E, 0xFFD9E2FF),
    ),
    CRIMSON(
        "crimson", "Crimson", Color(0xFFD64541),
        PrimaryFamily(0xFFB72623, 0xFFFFFFFF, 0xFFFFDAD5, 0xFF410001),
        PrimaryFamily(0xFFFFB4AA, 0xFF690002, 0xFF930007, 0xFFFFDAD5),
    );

    companion object {
        fun fromKey(key: String?): Accent = entries.firstOrNull { it.key == key } ?: PURPLE
    }
}

/** The four M3 primary-family roles an accent overrides. */
data class PrimaryFamily(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
)
