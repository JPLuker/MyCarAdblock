package io.github.sirionero.mycar.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp

/*
 * The welcome page's last words, once the ship is in and its flag is up (PirateShip, by about
 * 3.5 s; its course is dotted on the water just before): a compass whose needle swings round and
 * settles on the way ahead, the motto ("We chart our own course.") coming in word by word beside
 * it, and under them how many bugs were found and fixed since the release before.
 */

/** When the compass comes, in ms from the page opening: after the flag is up and the course dotted. */
internal const val COURSE_AT_MS = 3700

@Composable
internal fun WelcomeCourse(
    key: Any, motto: String, bugs: String?,
    accent: Color, ink: Color, quiet: Color,
    cq: (Float) -> Dp, cqSp: (Float) -> TextUnit,
    modifier: Modifier = Modifier,
) {
    val compass = entrance(key, COURSE_AT_MS, 1500)
    val words = entrance(key, COURSE_AT_MS + 300, 1100)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Compass(compass, accent, quiet, Modifier.size(cq(7.5f)))
            Spacer(Modifier.width(cq(2.6f)))
            Text(
                comingIn(motto, words, ink),
                style = TextStyle(fontSize = cqSp(4.5f), fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, lineHeight = cqSp(5.6f)),
            )
        }
        if (bugs != null) {
            Text(
                bugs, color = quiet,
                style = TextStyle(fontSize = cqSp(3.5f), fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(top = cq(2.2f)).up(entrance(key, COURSE_AT_MS + 1500), cq(5f)),
            )
        }
    }
}

/**
 * [text] with its words in one after the other as [p] goes from 0 to 1, each fading up over a
 * stretch of [p]. Laid out whole from the start, so nothing moves as the words come. Languages
 * written without spaces come in a few letters at a time.
 */
private fun comingIn(text: String, p: Float, ink: Color): AnnotatedString {
    val pieces = if (text.contains(' ')) text.split(' ').map { "$it " } else text.chunked(2)
    val n = pieces.size
    return buildAnnotatedString {
        pieces.forEachIndexed { i, piece ->
            // Each piece takes about two steps to come in, the next starting a step after.
            val a = ((p * (n + 1) - i) / 2f).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = ink.copy(alpha = ink.alpha * a))) { append(if (i == n - 1) piece.trimEnd() else piece) }
        }
    }
}

/** A compass: it grows in, its needle swings round from the south and settles on the way ahead. */
@Composable
private fun Compass(p: Float, accent: Color, quiet: Color, modifier: Modifier) {
    Canvas(
        modifier.graphicsLayer {
            alpha = (p / 0.15f).coerceIn(0f, 1f)
            val grow = Grow.transform((p / 0.35f).coerceIn(0f, 1f))
            scaleX = 0.4f + 0.6f * grow
            scaleY = 0.4f + 0.6f * grow
        },
    ) {
        val r = size.minDimension / 2f
        val c = center
        // The ring and its marks: four long ones for north, east, south and west.
        drawCircle(accent.copy(alpha = 0.9f), radius = r * 0.9f, center = c, style = Stroke(width = r * 0.13f))
        for (i in 0 until 8) {
            val long = i % 2 == 0
            rotate(i * 45f, c) {
                drawLine(
                    accent.copy(alpha = if (long) 0.9f else 0.5f),
                    Offset(c.x, c.y - r * 0.78f), Offset(c.x, c.y - r * (if (long) 0.58f else 0.66f)),
                    strokeWidth = r * (if (long) 0.11f else 0.08f), cap = StrokeCap.Round,
                )
            }
        }
        // The needle: from the south, round and back, damped, to the way ahead (east-north-east).
        val swing = p.coerceIn(0f, 1f)
        val angle = HEADING + (START - HEADING) * exp(-4.5f * swing) * cos(2.6f * PI.toFloat() * swing)
        rotate(angle, c) {
            val half = r * 0.17f
            val north = Path().apply {
                moveTo(c.x, c.y - r * 0.62f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close()
            }
            val south = Path().apply {
                moveTo(c.x, c.y + r * 0.62f); lineTo(c.x + half, c.y); lineTo(c.x - half, c.y); close()
            }
            drawPath(south, quiet.copy(alpha = 0.55f))
            drawPath(north, accent)
        }
        drawCircle(accent, radius = r * 0.1f, center = c)
    }
}

/** Where the needle starts (south) and settles (the way ahead), in degrees from north. */
private const val START = 180f
private const val HEADING = 62f
private val Grow = CubicBezierEasing(0.3f, 1.5f, 0.5f, 1f)
