package app.embeddingspace.explorer.domain.design

import kotlin.math.pow

/** Colour maths without any Android classes, so the contrast rules can be unit tested. Colours are 0xRRGGBB ints. */
object Contrast {
    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(rgb: Int): Double =
        0.2126 * channel((rgb shr 16) and 0xff) + 0.7152 * channel((rgb shr 8) and 0xff) + 0.0722 * channel(rgb and 0xff)

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** White or near-black text, whichever reads better on [rgb]. */
    fun onColor(rgb: Int): Int = if (ratio(0xFFFFFF, rgb) >= ratio(0x111827, rgb)) 0xFFFFFF else 0x111827

    /** Blends [fg] over [bg] with [alpha] in 0..1. */
    fun over(fg: Int, bg: Int, alpha: Double): Int {
        fun mix(shift: Int): Int {
            val f = (fg shr shift) and 0xff
            val b = (bg shr shift) and 0xff
            return (f * alpha + b * (1 - alpha)).toInt().coerceIn(0, 255)
        }
        return (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}

/** All the colours of one theme. The Compose theme is built from this. */
data class ThemeTokens(
    val dark: Boolean,
    val bgTop: Int,
    val bgBottom: Int,
    val surface: Int,
    val surfaceHigh: Int,
    val onSurface: Int,
    val onSurfaceMuted: Int,
    val primary: Int,
    val onPrimary: Int,
    val accent: Int,
    val outline: Int,
    val danger: Int,
    val onDanger: Int,
    val success: Int,
) {
    /** Pairs that carry text, with a name for test messages. */
    fun textPairs(): List<Triple<String, Int, Int>> = listOf(
        Triple("onSurface on surface", onSurface, surface),
        Triple("onSurface on surfaceHigh", onSurface, surfaceHigh),
        Triple("muted on surface", onSurfaceMuted, surface),
        Triple("muted on surfaceHigh", onSurfaceMuted, surfaceHigh),
        Triple("onSurface on bgTop", onSurface, bgTop),
        Triple("onSurface on bgBottom", onSurface, bgBottom),
        Triple("muted on bgTop", onSurfaceMuted, bgTop),
        Triple("muted on bgBottom", onSurfaceMuted, bgBottom),
        Triple("onPrimary on primary", onPrimary, primary),
        Triple("onDanger on danger", onDanger, danger),
        Triple("accent on surface", accent, surface),
    )

    companion object {
        val Dark = ThemeTokens(
            dark = true,
            bgTop = 0x0A0F2E, bgBottom = 0x2B1459,
            surface = 0x161C42, surfaceHigh = 0x222A5C,
            onSurface = 0xF5F7FF, onSurfaceMuted = 0xB9C0E6,
            primary = 0xA18BFF, onPrimary = 0x0A0F2E,
            accent = 0x2EE6D6, outline = 0x3A4380,
            danger = 0xFF6B86, onDanger = 0x2A0610, success = 0x3DE08A,
        )
        val Light = ThemeTokens(
            dark = false,
            bgTop = 0xFAF7FF, bgBottom = 0xEDF2FF,
            surface = 0xFFFFFF, surfaceHigh = 0xE6DFFF,
            onSurface = 0x1B1840, onSurfaceMuted = 0x57547D,
            primary = 0x6A47F5, onPrimary = 0xFFFFFF,
            accent = 0x0B7F78, outline = 0xCFC8F0,
            danger = 0xC4163C, onDanger = 0xFFFFFF, success = 0x0E8F4F,
        )
    }
}

/**
 * Cluster colours: flat, medium-bright, one per cluster. The card outline, the dot on the card and the spoke to the
 * origin all use it. The first three are the owner's blue, green and salmon.
 */
object ClusterPalette {
    /** Light map. */
    val CARD_LIGHT = intArrayOf(
        0x4A90D9, 0x00B050, 0xF4A582, 0x9B6FD1, 0xF2B134, 0x2BB7B0,
        0xE86FA3, 0xE0524D, 0x8CC63F, 0x5B6CE0, 0xB07A4F, 0x7F95AD,
    )

    /** Dark map: the same hues, slightly lighter. */
    val CARD_DARK = intArrayOf(
        0x5EA3E8, 0x1FC468, 0xF7B396, 0xAD86DE, 0xF5C04F, 0x45C9C2,
        0xEE86B3, 0xEA6A65, 0xA0D45C, 0x7482E8, 0xC28C63, 0x9AAEC4,
    )

    fun colors(dark: Boolean): IntArray = if (dark) CARD_DARK else CARD_LIGHT

    /** Smallest colour distance between any two entries (simple RGB distance). */
    fun minDistance(set: IntArray): Double {
        var best = Double.MAX_VALUE
        for (i in set.indices) for (j in i + 1 until set.size) {
            val dr = ((set[i] shr 16) and 0xff) - ((set[j] shr 16) and 0xff)
            val dg = ((set[i] shr 8) and 0xff) - ((set[j] shr 8) and 0xff)
            val db = (set[i] and 0xff) - (set[j] and 0xff)
            best = minOf(best, kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble()))
        }
        return best
    }
}
