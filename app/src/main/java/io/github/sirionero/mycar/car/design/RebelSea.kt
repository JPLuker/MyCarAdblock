package io.github.sirionero.mycar.car.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.sin

/*
 * The myCar wallpaper, "Rebel" (the user's wish): the waves of Waves, and among them a pirate ship
 * flying the skull and crossbones that rides out every storm. Now and then a big wave rolls over it
 * and it goes under; then it comes back up, flag and all. Drawn only, on the wallpaper's clock, as
 * calm as the other moving ones: the dive and the rise take seconds, nothing flashes.
 */

/** [Motion.Rebel]: the bands at the back, the ship riding the second, the bands in front over it. */
internal fun DrawScope.drawRebel(screen: Size, rebel: Motion.Rebel, seconds: Double, fade: Float) {
    // Nothing to draw on, and the foam's steps along the crest would be 0 wide (a loop for ever).
    if (screen.width <= 0f || screen.height <= 0f) return
    val shipX = screen.width * SHIP_AT
    // How far into the storm: 0 most of the time, up to 1 as the big wave goes over.
    val u = ((seconds % STORM_S) / STORM_S).toFloat()
    val over = window(u, 0.70f, 0.90f)
    // The ship pushed down as the wave comes, then springing back up past its line and settling.
    val after = ((u - 0.84f) / 0.16f).coerceIn(0f, 1f)
    val spring = if (u < 0.84f) 0f else (exp(-4f * after) * sin(PI.toFloat() * 2.2f * after))
    rebel.colors.forEachIndexed { i, color ->
        if (i == 2) drawShip(screen, shipX, seconds, over, spring, fade)
        drawBand(screen, i, color, seconds, fade, bump = if (i == 2) over else 0f, at = shipX)
    }
}

/** 0 outside [from]..[to], rising and falling smoothly to 1 in the middle. */
private fun window(u: Float, from: Float, to: Float): Float =
    if (u <= from || u >= to) 0f else sin(PI.toFloat() * (u - from) / (to - from)).let { it * it }

/** Band [i]'s surface at [x], as Waves draws it, raised round [at] by [bump] (the big wave). */
private fun surface(screen: Size, i: Int, x: Float, seconds: Double, bump: Float, at: Float): Float {
    val base = screen.height * (0.42f + 0.14f * i)
    val height = screen.height * (0.05f + 0.025f * i)
    val length = screen.width * (1.1f + 0.4f * i)
    val travel = seconds * screen.width * (0.035 + 0.025 * i)
    val angle = 2 * PI * (x + travel) / length + i * 1.7
    val wave = base + height * (sin(angle) + 0.35 * sin(2.3 * angle + seconds * 0.4)).toFloat()
    if (bump <= 0f) return wave
    // A swell as tall as the ship, as wide as a few ships, centred on it.
    val d = (x - at) / (screen.height * 0.32f)
    return wave - bump * screen.height * BIG_WAVE * exp(-d * d)
}

private fun DrawScope.drawBand(screen: Size, i: Int, color: Color, seconds: Double, fade: Float, bump: Float, at: Float) {
    val step = screen.width / STEPS
    val path = Path().apply {
        moveTo(0f, screen.height)
        for (s in 0..STEPS) {
            val x = s * step
            lineTo(x, surface(screen, i, x, seconds, bump, at))
        }
        lineTo(screen.width, screen.height)
        close()
    }
    val top = screen.height * (0.42f + 0.14f * i) - screen.height * (0.05f + 0.025f * i) - bump * screen.height * BIG_WAVE
    drawPath(
        path,
        Brush.verticalGradient(
            0f to color.copy(alpha = 0.55f * fade),
            1f to color.copy(alpha = 0.12f * fade),
            startY = top,
            endY = screen.height,
        ),
    )
    // Foam along the crest of the big wave, while it's up.
    if (bump > 0.05f) {
        val foam = Path()
        val from = (at - screen.height * 0.5f).coerceAtLeast(0f)
        val to = (at + screen.height * 0.5f).coerceAtMost(screen.width)
        var x = from
        var first = true
        while (x <= to) {
            val y = surface(screen, i, x, seconds, bump, at)
            if (first) { foam.moveTo(x, y); first = false } else foam.lineTo(x, y)
            x += step
        }
        drawPath(foam, Color.White.copy(alpha = 0.5f * bump * fade), style = Stroke(width = screen.height * 0.004f, cap = StrokeCap.Round))
    }
}

/**
 * The ship on the second band at [x]: pitched by the swell, pushed down by the big wave ([over])
 * and sprung back ([spring]). Drawn in its own units: the waterline at y = 0, the bow to the
 * right, the main masthead at y = -64.
 */
private fun DrawScope.drawShip(screen: Size, x: Float, seconds: Double, over: Float, spring: Float, fade: Float) {
    val k = screen.height * SHIP_SCALE
    val y = surface(screen, 1, x, seconds, 0f, x) + screen.height * (0.13f * over - 0.04f * spring)
    val dx = screen.width * 0.01f
    val slope = (surface(screen, 1, x + dx, seconds, 0f, x) - surface(screen, 1, x - dx, seconds, 0f, x)) / (2 * dx)
    val roll = -atan(slope) * (180f / PI.toFloat()) * 0.5f + 2f * sin(seconds * 2 * PI / 5.0).toFloat() + 14f * over * sin(seconds * 2 * PI / 1.6).toFloat()
    val alpha = 0.9f * (0.55f + 0.45f * fade)
    translate(x, y) {
        rotate(roll, pivot = Offset.Zero) {
            scale(k, k, pivot = Offset.Zero) {
                // Masts and yards.
                for ((mx, top) in MASTS) drawLine(MAST.copy(alpha = alpha), Offset(mx, -8f), Offset(mx, top), strokeWidth = 1.6f)
                // Sails: cream, bellied by the wind.
                val belly = 2.2f + 0.8f * sin(seconds * 2 * PI / 3.0).toFloat()
                for ((mx, top, bottom, half) in SAILS) {
                    val sail = Path().apply {
                        moveTo(mx - half, top)
                        lineTo(mx + half, top)
                        quadraticTo(mx + half + belly, (top + bottom) / 2f, mx + half * 0.92f, bottom)
                        lineTo(mx - half * 0.92f, bottom)
                        quadraticTo(mx - half + belly, (top + bottom) / 2f, mx - half, top)
                        close()
                    }
                    drawPath(sail, SAIL.copy(alpha = alpha))
                }
                // The hull, with its gilded rail.
                drawPath(HULL, Brush.verticalGradient(listOf(WOOD.copy(alpha = alpha), DARK_WOOD.copy(alpha = alpha)), startY = -14f, endY = 8f))
                drawPath(RAIL, GOLD.copy(alpha = alpha), style = Stroke(width = 1.1f, cap = StrokeCap.Round))
                // The skull and crossbones at the main masthead, flying.
                drawFlag(seconds, alpha)
            }
        }
    }
}

private fun DrawScope.drawFlag(seconds: Double, alpha: Float) {
    val left = MAIN_X + 0.8f
    val top = MAIN_TOP + 0.5f
    val w = 18f
    val h = 12f
    val strips = 8
    val flag = Path()
    // The top edge, then the bottom edge back, rippling more towards the fly.
    for (j in 0..strips) {
        val p = j / strips.toFloat()
        val wave = sin(seconds * 2 * PI / 0.9 - p * 5.0).toFloat() * 1.3f * p
        if (j == 0) flag.moveTo(left, top + wave) else flag.lineTo(left + w * p, top + wave)
    }
    for (j in strips downTo 0) {
        val p = j / strips.toFloat()
        val wave = sin(seconds * 2 * PI / 0.9 - p * 5.0).toFloat() * 1.3f * p
        flag.lineTo(left + w * p, top + h + wave)
    }
    flag.close()
    drawPath(flag, Color(0xFF111114).copy(alpha = alpha))
    // The skull and the bones, on the flag's middle (rippling with it a little).
    val sway = sin(seconds * 2 * PI / 0.9 - 2.5).toFloat() * 0.6f
    val c = Offset(left + w * 0.5f, top + h * 0.42f + sway)
    val bone = Color.White.copy(alpha = alpha)
    drawLine(bone, c + Offset(-4.3f, -3f), c + Offset(4.3f, 4f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    drawLine(bone, c + Offset(4.3f, -3f), c + Offset(-4.3f, 4f), strokeWidth = 1.1f, cap = StrokeCap.Round)
    drawCircle(bone, radius = 2.8f, center = c + Offset(0f, -0.7f))
    drawCircle(Color(0xFF111114).copy(alpha = alpha), radius = 0.65f, center = c + Offset(-1f, -0.9f))
    drawCircle(Color(0xFF111114).copy(alpha = alpha), radius = 0.65f, center = c + Offset(1f, -0.9f))
}

private val HULL = Path().apply {
    moveTo(-34f, -14f)
    cubicTo(-33f, -6f, -31f, -1f, -28f, 2f)
    cubicTo(-20f, 8f, 18f, 8.6f, 28f, 1.5f)
    cubicTo(31.5f, -1f, 34f, -6f, 36f, -11.5f)
    lineTo(27f, -10f)
    cubicTo(18f, -7.6f, -8f, -6.6f, -18f, -8.6f)
    lineTo(-24f, -10.5f)
    lineTo(-24.5f, -14f)
    close()
}
private val RAIL = Path().apply {
    moveTo(-34f, -14f)
    lineTo(-24.5f, -14f)
    lineTo(-24f, -10.5f)
    lineTo(-18f, -8.6f)
    cubicTo(-8f, -6.6f, 18f, -7.6f, 27f, -10f)
    lineTo(36f, -11.5f)
}

private const val MAIN_X = -1f
private const val MAIN_TOP = -64f
private val MASTS = listOf(Pair(MAIN_X, MAIN_TOP), Pair(15f, -50f), Pair(-20f, -45f))

/** A sail: its mast, top, bottom and half width. */
private data class Sail(val mast: Float, val top: Float, val bottom: Float, val half: Float)
private val SAILS = listOf(
    Sail(MAIN_X, -52f, -36f, 12f), Sail(MAIN_X, -33f, -13f, 15f),
    Sail(15f, -44f, -31f, 9f), Sail(15f, -28f, -12f, 11f),
    Sail(-20f, -40f, -26f, 8f), Sail(-20f, -24f, -12f, 9f),
)

private val WOOD = Color(0xFF7A4B2A)
private val DARK_WOOD = Color(0xFF2E1A0D)
private val MAST = Color(0xFF3A2414)
private val SAIL = Color(0xFFEDE3CC)
private val GOLD = Color(0xFFE0B04A)

/** Where the ship sails, as a part of the screen's width, and its size as a part of its height per unit. */
private const val SHIP_AT = 0.7f
private const val SHIP_SCALE = 0.0036f
/** How often the big wave comes, in seconds (it divides the wallpaper's minute, like the waves). */
private const val STORM_S = 30.0
/** How tall the big wave rises over the second band's line, as a part of the screen's height. */
private const val BIG_WAVE = 0.25f
private const val STEPS = 64
