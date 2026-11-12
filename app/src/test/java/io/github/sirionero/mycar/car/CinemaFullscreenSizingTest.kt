package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaFullscreenSizingTest {
    @Test fun splitFullscreenDoesNotResizeNativeGeckoRenderer() {
        assertFalse(CinemaFullscreenSizing.resizeGeckoSurface(fullscreen = false, split = true))
        assertFalse(CinemaFullscreenSizing.resizeGeckoSurface(fullscreen = true, split = true))
    }

    @Test fun workingSinglePaneFullscreenKeepsItsAspectFitPath() {
        assertFalse(CinemaFullscreenSizing.resizeGeckoSurface(fullscreen = false, split = false))
        assertTrue(CinemaFullscreenSizing.resizeGeckoSurface(fullscreen = true, split = false))
    }

    @Test fun widescreenVideoFitsAndCentersInsideNarrowMapSplit() {
        val stage = CinemaFullscreenSizing.fit(313f, 384f, 16f / 9f)
        assertEquals(313f, stage.width, 0.05f)
        assertEquals(176.0625f, stage.height, 0.1f)
        assertTrue(stage.height < 384f)
    }

    @Test fun fullscreenUsesNearlyAllAvailableWidthWithoutMap() {
        val stage = CinemaFullscreenSizing.fit(560f, 384f, 16f / 9f)
        assertEquals(560f, stage.width, 0.05f)
        assertEquals(315f, stage.height, 0.1f)
    }

    @Test fun portraitVideoRemainsInsidePaneInsteadOfCropping() {
        val stage = CinemaFullscreenSizing.fit(560f, 384f, 9f / 16f)
        assertEquals(216f, stage.width, 0.1f)
        assertEquals(384f, stage.height, 0.05f)
    }

    @Test fun unusuallyWideVideoIsAspectFitWithoutOverflow() {
        val stage = CinemaFullscreenSizing.fit(300f, 400f, 2.4f)
        assertEquals(300f, stage.width, 0.05f)
        assertEquals(125f, stage.height, 0.05f)
    }

    @Test fun invalidMetadataDoesNotProduceBadLayout() {
        val stage = CinemaFullscreenSizing.fit(300f, 400f, Float.NaN)
        assertEquals(300f, stage.width, 0f)
        assertEquals(400f, stage.height, 0f)
    }
}
