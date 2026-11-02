package io.github.sirionero.mycar.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.webmusic.WebMusic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The badges of the 0.7 intro's pages (see IntroScreen), drawn like the others in the 48-unit icon
 * box and always moving a little. Every clock is read in the drawing only, so a page doesn't
 * compose again at each frame.
 */

/** Music inside myCar: a note dancing on the beat, a tilt each way and a hop at each. */
@Composable
internal fun DancingNoteIcon(color: Color) {
    val beat = rememberInfiniteTransition(label = "note").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "note",
    )
    Icon(
        ImageVector.vectorResource(R.drawable.sym_music_note), contentDescription = null, tint = color,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val swing = sin(2 * PI * beat.value).toFloat()
            transformOrigin = TransformOrigin(0.5f, 0.9f)
            rotationZ = 10f * swing
            translationY = -abs(swing) * size.height * 0.08f
        },
    )
}

/**
 * The mini player over the whole map: the car's screen with a road on it, and a small player held,
 * carried to the other corner and set down, then back, as a finger moves it.
 */
@Composable
internal fun MiniPlayerIcon(color: Color, back: Color) {
    val clock = rememberInfiniteTransition(label = "mini").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "mini",
    )
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            drawRoundRect(color, Offset(3f, 9f), Size(42f, 30f), CornerRadius(6f), style = Stroke(3.5f))
            val road = Path().apply {
                moveTo(9f, 34f)
                cubicTo(18f, 31f, 22f, 16f, 39f, 14f)
            }
            drawPath(road, color.copy(alpha = 0.4f), style = Stroke(2.5f, cap = StrokeCap.Round))
            // Still, carried up to the left, still, carried back down to the right.
            val t = clock.value
            val (where, carry) = when {
                t < 0.3f -> 0f to 0f
                t < 0.5f -> (t - 0.3f) / 0.2f to (t - 0.3f) / 0.2f
                t < 0.8f -> 1f to 0f
                else -> (1f - (t - 0.8f) / 0.2f) to (t - 0.8f) / 0.2f
            }
            val at = lerp(Offset(23f, 27f), Offset(8f, 13f), FastOutSlowInEasing.transform(where))
            // A little bigger while it's held.
            val held = 1f + 0.15f * sin(PI * carry).toFloat()
            scale(held, held, at + Offset(9f, 4f)) {
                drawRoundRect(color, at, Size(18f, 8f), CornerRadius(4f))
                // The cover, and play.
                drawRoundRect(back, at + Offset(1.6f, 1.6f), Size(4.8f, 4.8f), CornerRadius(1.2f))
                val play = Path().apply {
                    moveTo(at.x + 10.5f, at.y + 2f)
                    lineTo(at.x + 14.5f, at.y + 4f)
                    lineTo(at.x + 10.5f, at.y + 6f)
                    close()
                }
                drawPath(play, back)
            }
        }
    }
}

/** Song lyrics: the lines of a verse lit one after another as they're sung, as synced lyrics are. */
@Composable
internal fun LyricsIcon(color: Color) {
    val clock = rememberInfiniteTransition(label = "lyrics").animateFloat(
        0f, LYRIC_LINES.size.toFloat(), infiniteRepeatable(tween(LYRIC_LINES.size * 1_100, easing = LinearEasing)),
        label = "lyrics",
    )
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            val t = clock.value
            val sung = t.toInt().coerceAtMost(LYRIC_LINES.lastIndex)
            val along = FastOutSlowInEasing.transform((t - sung).coerceIn(0f, 1f))
            LYRIC_LINES.forEachIndexed { i, length ->
                val y = 10.5f + i * 9f
                drawLine(color.copy(alpha = 0.3f), Offset(8f, y), Offset(8f + length, y), 5f, StrokeCap.Round)
                if (i == sung) drawLine(color, Offset(8f, y), Offset(8f + length * along, y), 5f, StrokeCap.Round)
            }
        }
    }
}

private val LYRIC_LINES = listOf(32f, 23f, 29f, 17f)

/** The map's arrow, found: rings going out from it as from a position just taken. */
@Composable
internal fun LocationIcon(color: Color) {
    val clock = rememberInfiniteTransition(label = "ping").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2_000, easing = LinearEasing)), label = "ping",
    )
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            val center = Offset(24f, 25f)
            repeat(2) { i ->
                val f = (clock.value + i * 0.5f) % 1f
                drawCircle(color.copy(alpha = 0.55f * (1f - f)), 9f + 13f * f, center, style = Stroke(2.5f))
            }
            val arrow = Path().apply {
                moveTo(24f, 12f)
                lineTo(33f, 36f)
                lineTo(24f, 31f)
                lineTo(15f, 36f)
                close()
            }
            drawPath(arrow, color)
            // Rounded corners, like the map's own.
            drawPath(arrow, color, style = Stroke(2.5f, join = StrokeJoin.Round))
        }
    }
}

/** The car's settings on the phone: a phone whose two switches flip, one after the other. */
@Composable
internal fun PhoneSwitchesIcon(color: Color, back: Color) {
    val clock = rememberInfiniteTransition(label = "switches").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing)), label = "switches",
    )
    Canvas(Modifier.fillMaxSize()) {
        inIconBox {
            drawRoundRect(color, Offset(12f, 3f), Size(24f, 42f), CornerRadius(6f), style = Stroke(3.5f))
            drawLine(color, Offset(21f, 8.5f), Offset(27f, 8.5f), 2.5f, StrokeCap.Round)
            listOf(19f to 0f, 31f to 0.25f).forEach { (y, lag) ->
                val on = flipped((clock.value - lag + 1f) % 1f)
                drawRoundRect(color.copy(alpha = 0.35f + 0.65f * on), Offset(17f, y - 3.5f), Size(14f, 7f), CornerRadius(3.5f))
                drawCircle(lerp(color, back, on), 2.6f, Offset(20.5f + 7f * on, y))
            }
        }
    }
}

/** A switch over one loop: turning on at the start, on for half the loop, turning off, off. */
private fun flipped(t: Float): Float = when {
    t < 0.1f -> FastOutSlowInEasing.transform(t / 0.1f)
    t < 0.5f -> 1f
    t < 0.6f -> 1f - FastOutSlowInEasing.transform((t - 0.5f) / 0.1f)
    else -> 0f
}

/** The music services that play inside myCar, drifting past in a row that fades at both ends. */
@Composable
internal fun ServiceTicker(c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit, key: Any) {
    // Apple's name stays off myCar's own pages (CLAUDE.md, design system), so that service isn't in the row.
    val names = remember { WebMusic.CATALOG.filter { it.id != "apple-music" }.map { it.name } + "Xonos" }
    val scroll = rememberInfiniteTransition(label = "services").animateFloat(
        0f, 1f, infiniteRepeatable(tween(names.size * 1_800, easing = LinearEasing)), label = "services",
    )
    Box(
        Modifier
            .padding(top = cq(6f))
            .fillMaxWidth()
            .up(entrance(key, 500, 700), cq(7f))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.15f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clipToBounds(),
    ) {
        Row(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                // The row is there twice, each name with its gap after it: sliding by half loops cleanly.
                .graphicsLayer { translationX = -size.width / 2f * scroll.value },
        ) {
            repeat(2) {
                names.forEach { name ->
                    Text(
                        name, color = c.onSecondaryC,
                        style = TextStyle(fontSize = cqSp(4.4f), fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier
                            .padding(end = cq(2.4f))
                            .clip(RoundedCornerShape(50))
                            .background(c.secondaryC)
                            .padding(horizontal = cq(4.4f), vertical = cq(2f)),
                    )
                }
            }
        }
    }
}
