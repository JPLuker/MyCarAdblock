package io.github.sirionero.mycar.car

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.ripple
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.PanelCornersNode
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * What every car button shows: the usual ripple when pressed, and a ring in the accent color
 * while the knob or D-pad is on it. Cars without a knob never see the ring, since nothing takes
 * focus while the screen is in touch mode.
 *
 * It replaces the theme's ripple (LocalIndication) for the whole car UI, so any `clickable` that
 * doesn't pass its own indication gets the ring for free.
 */
internal class FocusRingIndication(private val color: Color) : IndicationNodeFactory {
    private val ripple = ripple()

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(ripple.create(interactionSource), interactionSource, color)

    override fun equals(other: Any?) = other is FocusRingIndication && other.color == color
    override fun hashCode() = color.hashCode()
}

private class FocusRingNode(
    rippleNode: DelegatableNode,
    private val source: InteractionSource,
    private val color: Color,
) : DelegatingNode(), DrawModifierNode {
    private var focused = false

    init { delegate(rippleNode) }

    override fun onAttach() {
        coroutineScope.launch {
            var holding = 0
            source.interactions.collect {
                when (it) {
                    is FocusInteraction.Focus -> holding++
                    is FocusInteraction.Unfocus -> holding--
                }
                val now = holding > 0
                if (now != focused) {
                    focused = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (!focused) return
        // Inside the button's bounds, since most buttons clip to their shape: drawn outside, a
        // rounded tile would cut its own ring off.
        val width = RING_WIDTH.toPx()
        val radius = minOf(CORNER.toPx(), size.minDimension / 2)
        val corners = panelCorners(radius, width)
        if (corners == null) {
            drawRoundRect(
                color,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                cornerRadius = CornerRadius(radius),
                style = Stroke(width),
            )
        } else {
            val ring = RoundRect(
                left = width / 2, top = width / 2, right = size.width - width / 2, bottom = size.height - width / 2,
                topLeftCornerRadius = CornerRadius(corners[0]), topRightCornerRadius = CornerRadius(corners[1]),
                bottomRightCornerRadius = CornerRadius(corners[2]), bottomLeftCornerRadius = CornerRadius(corners[3]),
            )
            drawPath(Path().apply { addRoundRect(ring) }, color, style = Stroke(width))
        }
    }

    /**
     * A button in a corner of a raised panel (car/design/Panels.kt), as a widget filling one or a bar
     * across its top: the panel's corner is rounder than the ring's and would cut it off, so there the
     * ring takes the panel's, less half its width. Its corners top left, top right, bottom right and
     * bottom left, or null when none of them is a panel's.
     */
    private fun ContentDrawScope.panelCorners(radius: Float, width: Float): FloatArray? {
        val panel = findNearestAncestor(PanelCornersNode.Key) as? PanelCornersNode ?: return null
        val outer = panel.coordinates?.takeIf { it.isAttached } ?: return null
        val mine = requireLayoutCoordinates().takeIf { it.isAttached } ?: return null
        val at = outer.localPositionOf(mine, Offset.Zero)
        val left = abs(at.x) < EDGE_PX
        val top = abs(at.y) < EDGE_PX
        val right = abs(outer.size.width - (at.x + size.width)) < EDGE_PX
        val bottom = abs(outer.size.height - (at.y + size.height)) < EDGE_PX
        if (!(left || right) || !(top || bottom)) return null
        val round = maxOf(radius, minOf(panel.radiusPx - width / 2, size.minDimension / 2))
        return floatArrayOf(
            if (left && top) round else radius,
            if (right && top) round else radius,
            if (right && bottom) round else radius,
            if (left && bottom) round else radius,
        )
    }

    private companion object {
        val RING_WIDTH = 3.dp
        val CORNER = 16.dp
        /** How near a panel's edge a button's edge counts as on it, in pixels. */
        const val EDGE_PX = 1f
    }
}
