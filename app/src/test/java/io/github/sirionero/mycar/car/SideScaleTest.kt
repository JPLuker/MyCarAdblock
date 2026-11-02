package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SideScaleTest {
    /** MAP_PANE_MIN_EDGE and a margin. */
    private val mapMin = 268f

    /** Home's map page [w] x [h] dp, as CarHome passes it: the gap off the width. */
    private fun scale(wanted: Float, w: Float, h: Float) = sideScaleFor(wanted, w - 8f, h, mapMin)

    @Test fun theSizeChosenWhereItFits() {
        // 800x480 and 1920x720 at Normal, 800x480 at Large: Extra large as chosen.
        assertEquals(1.4f, scale(1.4f, 704f, 480f), 0f)
        assertEquals(1.4f, scale(1.4f, 1184f, 480f), 0f)
        assertEquals(1.4f, scale(1.4f, 544f, 384f), 0f)
    }

    @Test fun smallerWhereTheMapWouldBeTooNarrow() {
        // 800x480 at Extra large: Extra large and Large both leave the map under an app's card.
        val w = 437.33f - 8f
        for (wanted in listOf(1.2f, 1.4f)) {
            val s = sideScaleFor(wanted, w, 320f, mapMin)
            assertTrue("$wanted: $s", s < wanted)
            assertTrue(w * (1f - s / MAP_SHARE) >= mapMin - 0.01f)
            assertTrue(320f / s >= columnNeeds(w / MAP_SHARE))
        }
    }

    @Test fun smallerWhereTheColumnWouldBeTooShort() {
        // A wide, short screen: the map has room, the column's height doesn't.
        val s = scale(1.4f, 1100f, 360f)
        assertTrue(s < 1.4f)
        assertEquals(columnNeeds((1100f - 8f) / MAP_SHARE), 360f / s, 0.5f)
    }

    @Test fun neverUnderSmall() {
        assertEquals(1f, scale(1f, 437f, 320f), 0f)
        assertEquals(1f, scale(1.4f, 300f, 200f), 0f)
    }
}
