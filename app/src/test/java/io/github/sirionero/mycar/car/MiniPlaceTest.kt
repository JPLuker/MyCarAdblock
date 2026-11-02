package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlaceTest {
    @Test fun savedComesBack() {
        val place = MiniPlace(0.25f, 0.75f)
        assertEquals(place, MiniPlace.parse(place.saved()))
    }

    @Test fun nothingOrRubbishIsNoPlace() {
        assertNull(MiniPlace.parse(""))
        assertNull(MiniPlace.parse("0.5"))
        assertNull(MiniPlace.parse("a,b"))
        assertNull(MiniPlace.parse("NaN,1"))
        assertNull(MiniPlace.parse("0.1,0.2,0.3"))
    }

    @Test fun outsideIsKeptInside() = assertEquals(MiniPlace(1f, 0f), MiniPlace.parse("2,-1"))

    @Test fun pixelsBecomeFractions() {
        assertEquals(MiniPlace(0.5f, 0.25f), MiniPlace.of(100f, 50f, 200, 200))
        assertEquals(MiniPlace(1f, 0f), MiniPlace.of(500f, -20f, 200, 200))
        assertEquals(MiniPlace(1f, 1f), MiniPlace.of(0f, 0f, 0, 0))
    }

    /** Its area on a map [mapW] x [mapH] dp: inside its margins and above the page dots. */
    private fun startOn(mapW: Float, mapH: Float) =
        MiniPlace.start(mapW - 2 * MINI_EDGE, mapH - MINI_EDGE - MINI_DOTS_STRIP, MINI_WIDTH, MINI_HEIGHT)

    /** Its top-left corner on that map, in dp, at [place]. */
    private fun topLeft(place: MiniPlace, mapW: Float, mapH: Float): Pair<Float, Float> {
        val roomW = mapW - 2 * MINI_EDGE - MINI_WIDTH
        val roomH = mapH - MINI_EDGE - MINI_DOTS_STRIP - MINI_HEIGHT
        return (MINI_EDGE + place.x * roomW) to (MINI_EDGE + place.y * roomH)
    }

    @Test fun startsBottomRightOnAWideMap() {
        // 800x480 at Normal, 1920x720 at 240 dpi: the corner is clear of everything.
        assertEquals(MiniPlace.CORNER, startOn(704f, 480f))
        assertEquals(MiniPlace.CORNER, startOn(1184f, 480f))
    }

    @Test fun startsClearOfTheButtonsAndTheTripOnASmallMap() {
        // 800x480 at Large (544x384) and Extra large (437x320).
        for ((w, h) in listOf(544f to 384f, 437.33f to 320f)) {
            val (x, y) = topLeft(startOn(w, h), w, h)
            // Left of the map's buttons (54 dp, 12 from the right edge).
            assertTrue("$w: ${x + MINI_WIDTH}", x + MINI_WIDTH <= w - 12f - 54f)
            // Above the trip's time card (about 80 dp, 12 from the bottom).
            assertTrue("$w: ${y + MINI_HEIGHT}", y + MINI_HEIGHT <= h - 12f - 80f)
            // Above the page dots (44 dp, 8 from the bottom).
            assertTrue(y + MINI_HEIGHT <= h - 8f - 44f)
            assertTrue(x >= MINI_EDGE && y >= MINI_EDGE)
        }
    }

    @Test fun startsLeftOfTheButtonsOnAShortWideMap() {
        // 1920x720 at 320 dpi: 864x360, the corner under the buttons; the trip's time is far.
        val (x, y) = topLeft(startOn(864f, 360f), 864f, 360f)
        assertTrue(x + MINI_WIDTH <= 864f - 12f - 54f)
        assertEquals(360f - MINI_DOTS_STRIP - MINI_HEIGHT, y, 0.5f)
    }

    @Test fun showsOnlyOverTheWholeMapWithMusic() {
        assertTrue(miniPlayerShows(on = true, hidden = false, music = true, besideMap = false, inView = true, panning = false))
        assertFalse(miniPlayerShows(on = true, hidden = false, music = true, besideMap = true, inView = true, panning = false))
        assertFalse(miniPlayerShows(on = true, hidden = false, music = false, besideMap = false, inView = true, panning = false))
        assertFalse(miniPlayerShows(on = true, hidden = true, music = true, besideMap = false, inView = true, panning = false))
        assertFalse(miniPlayerShows(on = false, hidden = false, music = true, besideMap = false, inView = true, panning = false))
        assertFalse(miniPlayerShows(on = true, hidden = false, music = true, besideMap = false, inView = false, panning = false))
        assertFalse(miniPlayerShows(on = true, hidden = false, music = true, besideMap = false, inView = true, panning = true))
    }
}
