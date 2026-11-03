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

    @Test fun startsBesideAMapsCardAndUnderItsButtonsAtLarge() {
        // 800x480 at Large: 544x384.
        val (w, h) = 544f to 384f
        val (x, y) = topLeft(startOn(w, h), w, h)
        // Right of the card a map shows at its left (Vela's places: 244 dp, MAP_PANE_MIN_EDGE).
        assertTrue("$x", x >= 244f + 8f - 0.5f)
        // Below the map's buttons: an app's row and three map buttons, as Vela has (54 dp, 8 apart).
        assertTrue("$y", y >= 12f + 54f + 3 * 62f + 8f - 0.5f)
        // Above the page dots (44 dp, 8 from the bottom), inside the map.
        assertTrue(y + MINI_HEIGHT <= h - 8f - 44f)
        assertTrue(x + MINI_WIDTH <= w - MINI_EDGE + 0.5f)
    }

    @Test fun startsLeftOfTheButtonsAndAboveTheTripAtExtraLarge() {
        // 800x480 at Extra large: 437x320, where nothing leaves room for it between the card and the
        // buttons, drawn smaller as there (fitScale).
        val (w, h) = 437.33f to 320f
        val areaW = w - 2 * MINI_EDGE
        val areaH = h - MINI_EDGE - MINI_DOTS_STRIP
        val fit = MiniPlace.fitScale(areaW)
        val ownW = MINI_WIDTH * fit
        val ownH = MINI_HEIGHT * fit
        val place = MiniPlace.start(areaW, areaH, ownW, ownH)
        val x = MINI_EDGE + place.x * (areaW - ownW)
        val y = MINI_EDGE + place.y * (areaH - ownH)
        // Left of the map's buttons (54 dp, 12 from the edge, 8 apart): under them its first touch only
        // woke them, and its X sat under Vela's zoom out.
        assertTrue("$x", x + ownW <= w - 12f - 54f - 8f + 0.5f)
        assertTrue("$x", x >= MINI_EDGE)
        // Above the trip's time card (about 80 dp, 12 from the bottom) and the page dots.
        assertTrue("$y", y + ownH <= h - 12f - 80f)
    }

    @Test fun startsLeftOfTheButtonsOnAShortWideMap() {
        // 1920x720 at 320 dpi: 864x360, the corner under the buttons; the card and the trip's time are far.
        val (x, y) = topLeft(startOn(864f, 360f), 864f, 360f)
        assertTrue(x + MINI_WIDTH <= 864f - 12f - 54f)
        assertTrue(x >= 0.42f * 864f)
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

    @Test fun fitsBesideTheCardOnASmallMap() {
        // 1920x720: room to spare.
        assertEquals(1f, MiniPlace.fitScale(1752f), 0.001f)
        // 800x480 at Extra large: 413 dp of area, the card to 240 dp, 173 dp of room for 276.
        assertEquals(173f / 276f, MiniPlace.fitScale(413f), 0.01f)
        // Never smaller than about its Normal size at Extra large.
        assertEquals(0.6f, MiniPlace.fitScale(300f), 0.001f)
    }

    /** [place] let go on a map [mapW] x [mapH] dp, kept clear of the map's buttons: its top-left corner there. */
    private fun letGoAt(place: MiniPlace, mapW: Float, mapH: Float) = topLeft(
        MiniPlace.clearOfButtons(place, mapW - 2 * MINI_EDGE, mapH - MINI_EDGE - MINI_DOTS_STRIP, MINI_WIDTH, MINI_HEIGHT),
        mapW, mapH,
    )

    @Test fun letGoUnderTheAppsRowItMovesBelowIt() {
        // 800x480 at Normal, dragged up to the top right as in the preview (0.79, 0.047): under Vela's Search.
        val (x, y) = letGoAt(MiniPlace(0.79f, 0.047f), 704f, 480f)
        // Below the row: 12 dp from the top, 54 dp buttons, 8 dp apart.
        assertTrue("$y", y >= 12f + 54f + 8f - 0.5f)
        // And clear of the map's buttons down the right (54 dp, 12 from the edge, 8 apart).
        assertTrue("$x", x + MINI_WIDTH <= 704f - 12f - 54f - 8f + 0.5f)
    }

    @Test fun letGoInTheTopRightCornerItMovesLeftOfTheButtons() {
        // 1920x720 at 240 dpi: room left of the buttons, right of the card.
        val (x, y) = letGoAt(MiniPlace(1f, 0f), 1184f, 480f)
        assertTrue("$x", x + MINI_WIDTH <= 1184f - 12f - 54f - 8f + 0.5f)
        assertTrue("$x", x >= 0.42f * 1184f + 8f - 0.5f)
        assertTrue("$y", y >= 12f + 54f + 8f - 0.5f)
    }

    @Test fun letGoElsewhereItStaysPut() {
        assertEquals(MiniPlace.CORNER, MiniPlace.clearOfButtons(MiniPlace.CORNER, 680f, 408f, MINI_WIDTH, MINI_HEIGHT))
        val middle = MiniPlace(0.5f, 0.8f)
        assertEquals(middle, MiniPlace.clearOfButtons(middle, 680f, 408f, MINI_WIDTH, MINI_HEIGHT))
        // Over the card at the left, low down: the driver's choice.
        val left = MiniPlace(0f, 0.6f)
        assertEquals(left, MiniPlace.clearOfButtons(left, 680f, 408f, MINI_WIDTH, MINI_HEIGHT))
    }

    @Test fun letGoOnATinyMapItStaysInside() {
        // 800x480 at Extra large (437x320), no room anywhere: still a place inside the map.
        val place = MiniPlace.clearOfButtons(MiniPlace(1f, 0f), 413f, 248f, MINI_WIDTH, MINI_HEIGHT)
        assertTrue(place.x in 0f..1f && place.y in 0f..1f)
        // No room for it at all: as it was.
        assertEquals(MiniPlace(1f, 0f), MiniPlace.clearOfButtons(MiniPlace(1f, 0f), 200f, 50f, MINI_WIDTH, MINI_HEIGHT))
    }
}
