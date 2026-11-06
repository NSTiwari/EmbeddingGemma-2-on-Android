package app.embeddingspace.explorer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.embeddingspace.explorer.domain.model.MediaType
import app.embeddingspace.explorer.ui.media.ThumbCache
import app.embeddingspace.explorer.ui.theme.xc
import androidx.compose.foundation.clickable as clickableBase

val CardShape = RoundedCornerShape(20.dp)
val PillShape = RoundedCornerShape(50)

fun MediaType.icon(): ImageVector = when (this) {
    MediaType.IMAGE -> Icons.Rounded.Image
    MediaType.AUDIO -> Icons.Rounded.GraphicEq
    MediaType.VIDEO -> Icons.Rounded.Videocam
    MediaType.TEXT -> Icons.Rounded.TextSnippet
}

/** Rounded 20 dp surface with a hairline border. The basic building block of every screen. */
@Composable
fun Card20(
    modifier: Modifier = Modifier,
    color: Color = xc.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .clip(CardShape)
        .background(color)
        .border(BorderStroke(1.dp, xc.outline.copy(alpha = 0.55f)), CardShape)
    Box(if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base) { content() }
}

/** A pill with an icon and an optional short label. At least 48 dp tall so it is easy to hit. */
@Composable
fun IconPill(
    icon: ImageVector,
    label: String? = null,
    description: String,
    selected: Boolean = false,
    tint: Color = xc.onSurface,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (selected) xc.primary else xc.surfaceHigh
    val fg = if (selected) xc.onPrimary else tint
    Row(
        modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(PillShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (label == null) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Big gradient call to action. */
@Composable
fun GlowButton(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val brush = Brush.horizontalGradient(listOf(xc.primary, xc.accent))
    Row(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .clip(PillShape)
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(xc.outline, xc.outline)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = xc.onPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = xc.onPrimary, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A quiet text-and-icon button on a surface. */
@Composable
fun SoftButton(
    label: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val fg = if (danger) xc.danger else xc.onSurface
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(PillShape)
            .background(xc.surfaceHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Coloured dot with an optional media glyph inside. */
@Composable
fun ClusterDot(color: Color, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun TypeBadge(type: MediaType, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.62f))
    }
}

/** A big number with a one-word caption, for headers and stat strips. */
@Composable
fun BigNumber(value: String, caption: String, modifier: Modifier = Modifier, color: Color = xc.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(value, color = color, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(caption, color = xc.muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = xc.muted, style = MaterialTheme.typography.labelMedium, modifier = modifier.padding(horizontal = 4.dp))
}

/** Square thumbnail cut from a file. Falls back to a tinted glyph when there is no picture. */
@Composable
fun Thumb(path: String?, type: MediaType, size: Dp, tint: Color, modifier: Modifier = Modifier, corner: Dp = 14.dp) {
    val shape = RoundedCornerShape(corner)
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    val bmp = remember(path, px) { path?.let { ThumbCache.loadSquare(it, px.coerceAtMost(256)) } }
    Box(modifier.size(size).clip(shape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(type.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** What an empty screen shows: a small constellation and one short line. */
@Composable
fun EmptyState(line: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val colors = listOf(xc.cluster(0), xc.cluster(6), xc.cluster(9), xc.cluster(2), xc.cluster(11))
    val link = xc.muted.copy(alpha = 0.5f)
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(140.dp)) {
            val pts = listOf(Offset(0.18f, 0.62f), Offset(0.42f, 0.25f), Offset(0.78f, 0.36f), Offset(0.62f, 0.78f), Offset(0.3f, 0.85f))
            val px = pts.map { Offset(it.x * size.width, it.y * size.height) }
            val order = listOf(0, 1, 2, 3, 4, 0, 2)
            for (k in 0 until order.size - 1) {
                drawLine(link, px[order[k]], px[order[k + 1]], strokeWidth = 3f, cap = StrokeCap.Round)
            }
            px.forEachIndexed { i, o ->
                val r = size.width * (0.05f + 0.012f * i)
                drawCircle(colors[i].copy(alpha = 0.25f), r * 2.4f, o)
                drawCircle(colors[i], r, o)
            }
            drawCircle(link, size.width * 0.46f, Offset(size.width / 2, size.height / 2), style = Stroke(2f))
        }
        Spacer(Modifier.height(16.dp))
        Text(line, color = xc.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Header line used on the tab screens: a big title and an optional trailing slot. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = xc.onSurface, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f), maxLines = 1)
        trailing?.invoke()
    }
}
