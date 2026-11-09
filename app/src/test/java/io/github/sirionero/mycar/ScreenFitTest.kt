package io.github.sirionero.mycar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which picture myCar takes from what a car offers, when no vehicle profile names one. */
class ScreenFitTest {

    // Resolutions as ServiceDiscovery numbers them.
    private val r800x480 = 1
    private val r1280x720 = 2
    private val r1920x1080 = 3
    private val r720x1280 = 6
    private val r1080x1920 = 7
    private val r1440x2560 = 8

    private fun config(resolution: Int, widthMargin: Int = 0, heightMargin: Int = 0) =
        ServiceDiscovery.VideoConfig(resolution, frameRate = 2, density = 160, widthMargin, heightMargin)

    private fun choose(configs: List<ServiceDiscovery.VideoConfig>, touchWidth: Int, touchHeight: Int) =
        ScreenFit.choose(configs, configs.indices.toList(), touchWidth, touchHeight)

    @Test
    fun oneConfigAndAMatchingTouchscreenTakesIt() {
        val choice = choose(listOf(config(r800x480)), 800, 480)
        assertEquals(0, choice.index)
        assertFalse(choice.upright)
    }

    @Test
    fun noTouchscreenSizeTakesTheLargestUpTo1080p() {
        // The 2024 VW: no touchscreen size, three pictures with margins.
        val configs = listOf(
            config(r1920x1080, heightMargin = 240),
            config(r1280x720, heightMargin = 160),
            config(r800x480, heightMargin = 130),
        )
        val choice = choose(configs, 0, 0)
        assertEquals(0, choice.index)
        assertFalse(choice.upright)
    }

    @Test
    fun noTouchscreenSizeStillTakesTheFirstOfTwoAsLarge() {
        // Unchanged: without a touchscreen size the order the car lists them in decides.
        assertEquals(0, choose(listOf(config(r1920x1080), config(r1080x1920)), 0, 0).index)
        assertEquals(0, choose(listOf(config(r1080x1920), config(r1920x1080)), 0, 0).index)
    }

    @Test
    fun aWideScreenTakesTheSmallestThatFillsItsWidth() {
        // The Audi: 1540x720.
        val configs = listOf(config(r800x480), config(r1280x720), config(r1920x1080))
        assertEquals(1, choose(configs, 1540, 720).index)
    }

    @Test
    fun aWideScreenKeepsItsChoiceWhenUprightPicturesAreOffered() {
        val configs = listOf(config(r800x480), config(r1280x720), config(r1080x1920))
        val choice = choose(configs, 1280, 720)
        assertEquals(1, choice.index)
        assertFalse(choice.upright)
    }

    @Test
    fun anUprightScreenTakesAnUprightPicture() {
        // By width alone 1280x720 won, for having fewer pixels.
        val choice = choose(listOf(config(r1280x720), config(r1080x1920)), 1080, 1920)
        assertEquals(1, choice.index)
        assertTrue(choice.upright)
    }

    @Test
    fun anUprightScreenTakesTheSmallestUprightPictureThatFillsItsHeight() {
        val configs = listOf(config(r1440x2560), config(r1080x1920), config(r720x1280), config(r1920x1080))
        assertEquals(2, choose(configs, 720, 1280).index)
        assertEquals(1, choose(configs, 1080, 1920).index)
    }

    @Test
    fun anUprightScreenTakesTheLargestUprightPictureWhenNoneFillsItsHeight() {
        val configs = listOf(config(r720x1280), config(r1080x1920), config(r1920x1080))
        val choice = choose(configs, 1440, 2560)
        assertEquals(1, choice.index)
        assertTrue(choice.upright)
    }

    @Test
    fun anUprightScreenWithOnlyWidePicturesKeepsTodaysRule() {
        val configs = listOf(config(r800x480), config(r1280x720), config(r1920x1080))
        val choice = choose(configs, 1080, 1920)
        assertEquals(1, choice.index)
        assertFalse(choice.upright)
    }

    @Test
    fun uprightMeansWhatTheCarShowsOnceItsMarginsAreCropped() {
        // 800x480 with 500 px of its width cropped away shows 300x480: upright, as the car shows it.
        val croppedWide = config(r800x480, widthMargin = 500)
        val configs = listOf(config(r1280x720), croppedWide)
        val choice = choose(configs, 300, 480)
        assertEquals(1, choice.index)
        assertTrue(choice.upright)
    }

    @Test
    fun onlyOfferedConfigsWithAKnownSizeCount() {
        val configs = listOf(config(99), config(r1080x1920), config(r1280x720))
        assertEquals(1, ScreenFit.choose(configs, listOf(0, 1, 2), 1080, 1920).index)
        assertEquals(2, ScreenFit.choose(configs, listOf(0, 2), 1080, 1920).index)
        assertNull(ScreenFit.choose(configs, listOf(0, 5), 1080, 1920).index)
    }
}
