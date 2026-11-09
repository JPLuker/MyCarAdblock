package io.github.sirionero.mycar.car

/**
 * How a video fills its place from the middle, as a cover's picture does (ContentScale.Crop): what
 * spills over is cut, nothing is squashed. A TextureView first squeezes its video to its own shape;
 * this undoes that, across and down. Pure, for the tests.
 */
internal object CoverCrop {

    /**
     * The stretch across and down, about the middle of a [viewWidth] x [viewHeight] view, for a
     * [videoWidth] x [videoHeight] video in it; 1 and 1 while a size isn't known.
     */
    fun scale(viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int): Pair<Float, Float> {
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return 1f to 1f
        val fill = maxOf(viewWidth.toFloat() / videoWidth, viewHeight.toFloat() / videoHeight)
        return videoWidth * fill / viewWidth to videoHeight * fill / viewHeight
    }
}
