package app.embeddingspace.explorer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.embeddingspace.explorer.data.ThemeMode
import app.embeddingspace.explorer.domain.design.ClusterPalette
import app.embeddingspace.explorer.domain.design.ThemeTokens

private fun Int.c() = Color(0xFF000000.toInt() or this)

/** The colours of the current theme, plus the cluster palette that goes with it. */
class Explorer(val tokens: ThemeTokens, val clusterColors: IntArray) {
    val dark: Boolean get() = tokens.dark
    val bgTop = tokens.bgTop.c()
    val bgBottom = tokens.bgBottom.c()
    val surface = tokens.surface.c()
    val surfaceHigh = tokens.surfaceHigh.c()
    val onSurface = tokens.onSurface.c()
    val muted = tokens.onSurfaceMuted.c()
    val primary = tokens.primary.c()
    val onPrimary = tokens.onPrimary.c()
    val accent = tokens.accent.c()
    val outline = tokens.outline.c()
    val danger = tokens.danger.c()
    val onDanger = tokens.onDanger.c()
    val success = tokens.success.c()

    /** The hub at the middle of the space: a neutral blue that is not one of the cluster colours. */
    val hub = Color(0xFF2F6FED)
    val background = Brush.verticalGradient(listOf(bgTop, bgBottom))

    fun cluster(i: Int): Color = clusterColors[((i % clusterColors.size) + clusterColors.size) % clusterColors.size].c()
}

val LocalExplorer = staticCompositionLocalOf { Explorer(ThemeTokens.Dark, ClusterPalette.CARD_DARK) }

/** Short name for use in composables: the current Explorer colours. */
val xc: Explorer
    @Composable get() = LocalExplorer.current

private val Display = FontFamily.SansSerif

val ExplorerTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),
)

val ExplorerShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun ExplorerTheme(mode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val dark = resolveDark(mode)
    val tokens = if (dark) ThemeTokens.Dark else ThemeTokens.Light
    val explorer = remember(dark) { Explorer(tokens, ClusterPalette.colors(dark)) }
    val scheme = remember(explorer) {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = explorer.primary, onPrimary = explorer.onPrimary,
            secondary = explorer.accent, onSecondary = explorer.bgTop,
            background = explorer.bgTop, onBackground = explorer.onSurface,
            surface = explorer.surface, onSurface = explorer.onSurface,
            surfaceVariant = explorer.surfaceHigh, onSurfaceVariant = explorer.muted,
            surfaceContainer = explorer.surface, surfaceContainerHigh = explorer.surfaceHigh,
            surfaceContainerHighest = explorer.surfaceHigh, surfaceContainerLow = explorer.surface,
            outline = explorer.outline, outlineVariant = explorer.outline,
            error = explorer.danger, onError = explorer.onDanger,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalExplorer provides explorer) {
        MaterialTheme(colorScheme = scheme, typography = ExplorerTypography, shapes = ExplorerShapes, content = content)
    }
}

/** The 0xRRGGBB int of a cluster colour, for picking readable text on top of it. */
@Composable
fun xcRgb(i: Int): Int {
    val set = LocalExplorer.current.clusterColors
    return set[((i % set.size) + set.size) % set.size]
}
