package io.github.sirionero.mycar.car

/**
 * Fits an HTML fullscreen media surface inside the current Cinema pane.
 *
 * GeckoView has a documented fullscreen geometry defect when its surface is
 * smaller than the Android window (Mozilla Bug 1750416). Its media delegate
 * provides the actual video dimensions; resizing the view to the media aspect
 * preserves native controls and captions inside the same bounded surface.
 *
 * Values use the same unit (Compose dp in the caller); no screen pixels are
 * inferred from the phone's display or the car's physical resolution.
 */
internal object CinemaFullscreenSizing {
    /**
     * Firefox can immediately abandon document fullscreen when its Android rendering
     * surface is remeasured during the fullscreen transition. In a map split, preserve
     * the same full-height GeckoView bounds across entry and exit. Only the already
     * proven single-pane fullscreen path uses aspect-fitted AndroidView bounds.
     */
    fun resizeGeckoSurface(fullscreen: Boolean, split: Boolean): Boolean =
        fullscreen && !split

    data class Size(val width: Float, val height: Float)

    fun fit(paneWidth: Float, paneHeight: Float, videoAspectRatio: Float): Size {
        if (!paneWidth.isFinite() || !paneHeight.isFinite() ||
            paneWidth <= 0f || paneHeight <= 0f ||
            !videoAspectRatio.isFinite() || videoAspectRatio <= 0f
        ) {
            return Size(paneWidth, paneHeight)
        }

        val aspectBounded = videoAspectRatio.coerceIn(0.25f, 4f)
        val heightAtFullWidth = paneWidth / aspectBounded
        return if (heightAtFullWidth <= paneHeight) {
            Size(paneWidth, heightAtFullWidth)
        } else {
            Size(paneHeight * aspectBounded, paneHeight)
        }
    }
}
