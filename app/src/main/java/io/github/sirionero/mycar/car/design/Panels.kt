package io.github.sirionero.mycar.car.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance

/*
 * The panels of a split screen - the map and what sits beside it, the parts of a Home page - as soft
 * cards raised off the wallpaper: well rounded, a short shadow on the wallpaper round them and a rim
 * of light along their top, with the wallpaper showing in the gaps between them and round them.
 *
 * The shadow is drawn, not blurred: short gradients just outside the outline, a little lower than
 * the panel. Short on purpose, as wide soft gradients turn to bands on the way to the car; and only
 * outside, so a see-through panel (the widgets' glass) isn't darkened from below.
 */

/** A panel's outline: [MyCarRadius.Panel] at every corner. */
val PanelShape = RoundedCornerShape(MyCarRadius.Panel)

/**
 * Raises this element off the wallpaper as a panel: its shadow round it, its content clipped to
 * [PanelShape], a rim of light over the content's edge.
 *
 * [coverCorners] is for content that may not keep to the clip, which then has what lies under the
 * panels (the wallpaper, or the screen's ground without one) painted again over its corners. Another
 * app's map is a SurfaceView: Android composes its surface as a rectangle of its own under the
 * window, and only the hole the view clears in the window follows a rounded clip. A WebView drawn
 * through Vulkan gets a rectangular clip only.
 */
fun Modifier.raisedPanel(coverCorners: Boolean = false): Modifier = composed {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    drawBehind { drawPanelShadow(MyCarRadius.Panel.toPx(), dark) }
        .then(if (coverCorners) Modifier.groundOutside() else Modifier)
        .clip(PanelShape)
        .drawWithContent {
            drawContent()
            drawPanelRim(MyCarRadius.Panel.toPx(), dark)
        }
}

/** What lies under the panels, over this element's content outside [PanelShape]: see [raisedPanel]. */
private fun Modifier.groundOutside(): Modifier = composed {
    val wallpaper = rememberWallpaper()
    if (wallpaper != null) {
        wallpaperOutside(wallpaper, PanelShape)
    } else {
        val ground = MaterialTheme.colorScheme.background
        drawWithContent {
            drawContent()
            clipPath(outlinePath(PanelShape.createOutline(size, layoutDirection, this)), ClipOp.Difference) { drawRect(ground) }
        }
    }
}

/**
 * The panel's shadow, a [radius]-cornered outline [MyCarLift.ShadowDrop] lower than the panel and
 * fading out over [MyCarLift.ShadowSpread] round it: straight runs along the sides, rings round the
 * corners. Drawn outside the panel only.
 */
internal fun DrawScope.drawPanelShadow(radius: Float, dark: Boolean) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val spread = MyCarLift.ShadowSpread.toPx()
    val drop = MyCarLift.ShadowDrop.toPx()
    val r = radius.coerceIn(0f, minOf(w, h) / 2)
    val strength = if (dark) MyCarLift.SHADOW_DARK else MyCarLift.SHADOW_LIGHT
    val full = Color.Black.copy(alpha = strength)
    val fade = SHADOW_FADE.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha * strength) }.toTypedArray()
    // Round a corner the fade starts at the corner's circle, r from its centre, and ends spread further.
    val inner = if (r + spread > 0f) r / (r + spread) else 0f
    val ring = (listOf(0f to full) + SHADOW_FADE.map { (at, alpha) -> inner + at * (1f - inner) to Color.Black.copy(alpha = alpha * strength) }).toTypedArray()
    val top = drop
    val bottom = h + drop
    val panel = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
    clipPath(panel, ClipOp.Difference) {
        val run = (w - 2 * r).coerceAtLeast(0f)
        val side = (h - 2 * r).coerceAtLeast(0f)
        drawRect(Brush.verticalGradient(*fade, startY = top, endY = top - spread), Offset(r, top - spread), Size(run, spread))
        drawRect(Brush.verticalGradient(*fade, startY = bottom, endY = bottom + spread), Offset(r, bottom), Size(run, spread))
        drawRect(Brush.horizontalGradient(*fade, startX = 0f, endX = -spread), Offset(-spread, top + r), Size(spread, side))
        drawRect(Brush.horizontalGradient(*fade, startX = w, endX = w + spread), Offset(w, top + r), Size(spread, side))
        // The strip the drop leaves under the panel, between the corners.
        drawRect(full, Offset(r, h), Size(run, drop))
        // Each corner: the square from its circle's centre out, the rings round that centre.
        val reach = r + spread
        fun corner(cx: Float, cy: Float, left: Float, up: Float) =
            drawRect(Brush.radialGradient(*ring, center = Offset(cx, cy), radius = reach), Offset(left, up), Size(reach, reach))
        corner(r, top + r, r - reach, top + r - reach)
        corner(w - r, top + r, w - r, top + r - reach)
        corner(r, bottom - r, r - reach, bottom - r)
        corner(w - r, bottom - r, w - r, bottom - r)
    }
}

/**
 * The panel's rim: light along its top edge, fading down its sides, as on the dock's glass and the
 * widgets. Inside the outline, over the content.
 */
internal fun DrawScope.drawPanelRim(radius: Float, dark: Boolean) {
    val width = MyCarLift.Rim.toPx()
    val (top, middle, foot) = if (dark) RIM_DARK else RIM_LIGHT
    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = top),
            0.3f to Color.White.copy(alpha = middle),
            1f to Color.White.copy(alpha = foot),
        ),
        topLeft = Offset(width / 2, width / 2),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((radius - width / 2).coerceAtLeast(0f)),
        style = Stroke(width),
    )
}

/** How the shadow fades, from the panel's edge (0) to the end of its spread (1): soft, nearly gone halfway. */
private val SHADOW_FADE = listOf(0f to 1f, 0.25f to 0.6f, 0.5f to 0.28f, 0.75f to 0.09f, 1f to 0f)

/** The rim's light at the top, a third of the way down and at the foot: on a dark screen, and on a light one. */
private val RIM_DARK = Triple(0.3f, 0.08f, 0.05f)
private val RIM_LIGHT = Triple(0.85f, 0.35f, 0.2f)
