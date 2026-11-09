package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverCropTest {

    @Test
    fun aTallVideoFillsTheColumnCutTopAndBottom() {
        // Spotify's Canvas (9:16) in the column beside the map at 1920x720 (569 x 720 px).
        val (across, down) = CoverCrop.scale(569, 720, 720, 1280)
        assertEquals(1f, across, 0.001f)
        // Its picture as wide as the column, taller than it: 569 / 720 * 1280 = 1011.6 px for 720.
        assertEquals(1011.6f / 720f, down, 0.002f)
    }

    @Test
    fun aWideVideoFillsTheColumnCutAtTheSides() {
        val (across, down) = CoverCrop.scale(569, 720, 1280, 720)
        assertEquals(1280f / 569f, across, 0.001f)
        assertEquals(1f, down, 0.001f)
    }

    @Test
    fun aSquareCoverPlaceAndATallVideo() {
        val (across, down) = CoverCrop.scale(400, 400, 720, 1280)
        assertEquals(1f, across, 0.001f)
        assertEquals(1280f / 720f, down, 0.001f)
    }

    @Test
    fun theSameShapeIsLeftAsItIs() {
        assertEquals(1f to 1f, CoverCrop.scale(300, 300, 640, 640))
        val (across, down) = CoverCrop.scale(360, 640, 720, 1280)
        assertEquals(1f, across, 0.001f)
        assertEquals(1f, down, 0.001f)
    }

    @Test
    fun nothingKnownYetNothingStretched() {
        assertEquals(1f to 1f, CoverCrop.scale(569, 720, 0, 0))
        assertEquals(1f to 1f, CoverCrop.scale(0, 0, 720, 1280))
    }
}
