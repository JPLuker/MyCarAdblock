package io.github.sirionero.mycar.car.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.requireDensity

/*
 * The panels of a split screen - the map and what sits beside it, the parts of a Home page - as soft
 * cards raised off the wallpaper: well rounded, a short shadow on the wallpaper round them and a rim
 * of light along their top, with the wallpaper showing in the gaps between them and round them.
 *
 * The shadow is drawn, not blurred: short gradients just outside the outline, a little lower than
 * the panel. Short on purpose, as wide soft gradients turn to bands on the way to the car; and only
 * outside, so a see-through panel (the widgets' glass) isn't darkened from below.
 *
 * Nothing here clips to the outside of a rounded shape: pieces cut with Path.combine, rectangle clips
 * and small layers cleared inside a circle do the same with plainer drawing.
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
    drawWithCache {
        // Worked out again only when the panel's size changes.
        val shadow = panelShadow(size, MyCarRadius.Panel.toPx(), MyCarLift.ShadowSpread.toPx(), MyCarLift.ShadowDrop.toPx(), dark)
        onDrawBehind { shadow.forEach { (piece, brush) -> drawPath(piece, brush) } }
    }
        .then(if (coverCorners) Modifier.groundOutside() else Modifier)
        .then(PanelCornersElement)
        .clip(PanelShape)
        .drawWithContent {
            drawContent()
            drawPanelRim(MyCarRadius.Panel.toPx(), dark)
        }
}

/**
 * Where a raised panel is and how round its corners are, for what's inside it: the knob's ring
 * (car/FocusRing.kt) round a button in one of its corners follows the panel's rounder corner there,
 * which would otherwise cut the ring off. Found with findNearestAncestor([Key]).
 */
internal class PanelCornersNode : Modifier.Node(), TraversableNode, LayoutAwareModifierNode {
    override val traverseKey: Any get() = Key

    /** The panel on screen, once placed. */
    var coordinates: LayoutCoordinates? = null
        private set

    /** Its corners' radius in pixels, at its own density. */
    var radiusPx = 0f
        private set

    override fun onPlaced(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        radiusPx = with(requireDensity()) { MyCarRadius.Panel.toPx() }
    }

    companion object Key
}

private object PanelCornersElement : ModifierNodeElement<PanelCornersNode>() {
    override fun create() = PanelCornersNode()
    override fun update(node: PanelCornersNode) = Unit
    override fun hashCode() = javaClass.hashCode()
    override fun equals(other: Any?) = other === this
}

/** What lies under the panels, over this element's content outside [PanelShape]: see [raisedPanel]. */
private fun Modifier.groundOutside(): Modifier = composed {
    val wallpaper = rememberWallpaper()
    if (wallpaper != null) {
        wallpaperOutside(wallpaper, MyCarRadius.Panel)
    } else {
        val ground = MaterialTheme.colorScheme.background
        drawWithContent {
            drawContent()
            paintCorners(MyCarRadius.Panel.toPx()) { drawRect(ground) }
        }
    }
}

/**
 * [paint] over this element's corners, outside a [radius] rounding: each corner's square in a layer of
 * its own, painted, then cleared inside the corner's circle, which is the panel's own.
 */
internal fun DrawScope.paintCorners(radius: Float, paint: DrawScope.() -> Unit) {
    val w = size.width
    val h = size.height
    val r = radius.coerceIn(0f, minOf(w, h) / 2)
    if (r <= 0f) return
    val canvas = drawContext.canvas
    val layer = Paint()
    for ((x, y) in listOf(0f to 0f, w - r to 0f, 0f to h - r, w - r to h - r)) {
        canvas.saveLayer(Rect(x, y, x + r, y + r), layer)
        clipRect(x, y, x + r, y + r) { paint() }
        // The circle's centre is the square's corner that points into the panel.
        val centre = Offset(if (x == 0f) r else x, if (y == 0f) r else y)
        drawCircle(Color.Black, radius = r, center = centre, blendMode = BlendMode.Clear)
        canvas.restore()
    }
}

/**
 * The pieces of a panel's shadow, each with its brush: a [radius]-cornered outline [drop] lower than
 * the panel, fading out over [spread] round it - straight runs along the sides, rings round the
 * corners. Each piece is cut to what lies outside the panel, so none is drawn under it.
 */
internal fun panelShadow(size: Size, radius: Float, spread: Float, drop: Float, dark: Boolean): List<Pair<Path, Brush>> {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f || spread <= 0f) return emptyList()
    val r = radius.coerceIn(0f, minOf(w, h) / 2)
    val strength = if (dark) MyCarLift.SHADOW_DARK else MyCarLift.SHADOW_LIGHT
    val full = Color.Black.copy(alpha = strength)
    val fade = SHADOW_FADE.map { (at, alpha) -> at to Color.Black.copy(alpha = alpha * strength) }.toTypedArray()
    // Round a corner the fade starts at the corner's circle, r from its centre, and ends spread further:
    // four stops at most, as glassFinish has (five or more brought the emulator's graphics down).
    val inner = r / (r + spread)
    val ring = (listOf(0f to full) + SHADOW_FADE.map { (at, alpha) -> inner + at * (1f - inner) to Color.Black.copy(alpha = alpha * strength) }).toTypedArray()
    val top = drop
    val bottom = h + drop
    val run = (w - 2 * r).coerceAtLeast(0f)
    val side = (h - 2 * r).coerceAtLeast(0f)
    fun box(left: Float, up: Float, width: Float, height: Float) = Path().apply { addRect(Rect(left, up, left + width, up + height)) }
    val pieces = mutableListOf<Pair<Path, Brush>>()
    // Above the panel only what shows over its top edge: the drop puts the rest under it.
    if (spread > drop) pieces += box(r, top - spread, run, spread - drop) to Brush.verticalGradient(*fade, startY = top, endY = top - spread)
    pieces += box(r, bottom, run, spread) to Brush.verticalGradient(*fade, startY = bottom, endY = bottom + spread)
    pieces += box(-spread, top + r, spread, side) to Brush.horizontalGradient(*fade, startX = 0f, endX = -spread)
    pieces += box(w, top + r, spread, side) to Brush.horizontalGradient(*fade, startX = w, endX = w + spread)
    // The strip the drop leaves under the panel, between the corners.
    if (drop > 0f) pieces += box(r, h, run, drop) to SolidColor(full)
    // Each corner: the square from its circle's centre out, less the panel, ringed round that centre.
    val panel = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r))) }
    val reach = r + spread
    fun corner(cx: Float, cy: Float, left: Float, up: Float) {
        val piece = Path.combine(PathOperation.Difference, box(left, up, reach, reach), panel)
        pieces += piece to Brush.radialGradient(*ring, center = Offset(cx, cy), radius = reach)
    }
    corner(r, top + r, r - reach, top + r - reach)
    corner(w - r, top + r, w - r, top + r - reach)
    corner(r, bottom - r, r - reach, bottom - r)
    corner(w - r, bottom - r, w - r, bottom - r)
    return pieces
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

/**
 * How the shadow fades, from the panel's edge (0) to the end of its spread (1): soft, a third left at
 * 40%. Three stops: gradients of five or more stops made the emulator of the Car preview go down at
 * the first frame with a panel, every time (the same shadow with three looks the same).
 */
private val SHADOW_FADE = listOf(0f to 1f, 0.4f to 0.35f, 1f to 0f)

/** The rim's light at the top, a third of the way down and at the foot: on a dark screen, and on a light one. */
private val RIM_DARK = Triple(0.3f, 0.08f, 0.05f)
private val RIM_LIGHT = Triple(0.6f, 0.2f, 0.1f)
