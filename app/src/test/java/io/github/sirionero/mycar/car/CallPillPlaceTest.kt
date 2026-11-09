package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallPillPlaceTest {

    /** Where the pill starts on a screen [w] dp wide beside the dock, drawn [fit] times its size: its left and right edges there. */
    private fun startOn(w: Float, fit: Float = 1f): Pair<Float, Float> {
        val areaW = w - 2 * PILL_EDGE
        val ownW = PILL_WIDTH * fit
        val place = CallPillPlace.start(areaW, ownW)
        assertEquals("along the top", 0f, place.y, 0f)
        val left = PILL_EDGE + place.x * (areaW - ownW)
        return left to left + ownW
    }

    @Test fun startsInTheMiddleOfTheTopOnAWideScreen() {
        // 1920x720 at 240 dpi, and a 1920x1080 screen with margins at Extra small: clear of a map's card and buttons.
        assertEquals(MiniPlace(0.5f, 0f), CallPillPlace.start(1160f, PILL_WIDTH))
        assertEquals(MiniPlace(0.5f, 0f), CallPillPlace.start(1617f, PILL_WIDTH))
    }

    @Test fun startsRightOfTheTurnCardAndLeftOfTheButtons() {
        // An 800x480 screen at Extra small (867 dp beside the dock), 1920x720 at 320 dpi, 800x480 at Normal.
        for (w in listOf(867f, 864f, 704f)) {
            val (left, right) = startOn(w)
            // Right of the card of the next turn: 12 dp in, 320 wide at most.
            assertTrue("$w: $left", left >= 12f + minOf(320f, 0.6f * w) - 0.5f)
            // Left of the map's buttons: 54 dp, 12 from the edge, 8 apart.
            assertTrue("$w: $right", right <= w - 12f - 54f - 8f + 0.5f)
        }
        // With room to spare, the whole gap after the card.
        assertEquals(12f + 320f + 8f, startOn(867f).first, 0.5f)
    }

    @Test fun startsLeftOfTheButtonsWhereNothingFits() {
        // 800x480 at Large (544 dp) and Extra large (437 dp), drawn smaller as there: over the end of the card.
        for (w in listOf(544f, 437.33f)) {
            val fit = CallPillPlace.fitScale(w - 2 * PILL_EDGE)
            val (left, right) = startOn(w, fit)
            assertTrue("$w: $right", right <= w - 12f - 54f - 8f + 0.5f)
            assertTrue("$w: $left", left >= PILL_EDGE)
        }
    }

    @Test fun drawnSmallerOnlyWhereItDoesNotFit() {
        // 1920x720 and 800x480 at Normal: its size.
        assertEquals(1f, CallPillPlace.fitScale(1160f), 0.001f)
        assertEquals(1f, CallPillPlace.fitScale(680f), 0.001f)
        // 800x480 at Large: a little smaller; at Extra large about its size at Normal there.
        assertEquals(280f / 296f, CallPillPlace.fitScale(520f), 0.01f)
        assertEquals(0.6f, CallPillPlace.fitScale(413.33f), 0.001f)
    }

    // The mini player on 1920x720 at 240 dpi: a map 1184x480, its area inside 12 dp and above the page dots.
    private val areaW = 1184f - 2 * MINI_EDGE
    private val areaH = 480f - MINI_EDGE - MINI_DOTS_STRIP

    /** A pill whose top-left corner is at [left], [top] dp of the screen beside the dock, seen from the mini player's area. */
    private fun pillAt(left: Float, top: Float) = ScreenRect(left, top, left + PILL_WIDTH, top + 64f).moved(-MINI_EDGE, -MINI_EDGE)

    /** The mini player at [place] in its area, as a rectangle there. */
    private fun mini(place: MiniPlace): ScreenRect {
        val x = place.x * (areaW - MINI_WIDTH)
        val y = place.y * (areaH - MINI_HEIGHT)
        return ScreenRect(x, y, x + MINI_WIDTH, y + MINI_HEIGHT)
    }

    private fun apart(rest: MiniPlace, pill: ScreenRect) = CallPillPlace.miniApart(rest, pill, areaW, areaH, MINI_WIDTH, MINI_HEIGHT)

    @Test fun miniStaysWhereItRestsAwayFromThePill() {
        // The pill where it starts, along the top; the mini player in its corner, bottom right.
        assertEquals(MiniPlace.CORNER, apart(MiniPlace.CORNER, pillAt(444f, 12f)))
    }

    @Test fun miniMovesAboveAPillLetGoInItsCorner() {
        val pill = pillAt(1184f - 12f - PILL_WIDTH, 480f - 12f - 64f)
        val moved = apart(MiniPlace.CORNER, pill)
        val box = mini(moved)
        assertFalse(box.near(pill, 8f))
        // Straight up, just above it.
        assertEquals(mini(MiniPlace.CORNER).left, box.left, 0.5f)
        assertEquals(pill.top - 8f, box.bottom, 0.5f)
    }

    @Test fun miniMovesBelowThePillAtTheTop() {
        // Let go at the top middle on an earlier drive: the pill starts there.
        val pill = pillAt(444f, 12f)
        val box = mini(apart(MiniPlace(0.5f, 0f), pill))
        assertFalse(box.near(pill, 8f))
        assertEquals(pill.bottom + 8f, box.top, 0.5f)
    }

    @Test fun miniKeepsClearOfTheMapsButtons() {
        // The pill at the right edge, a little above the mini player's corner: just above the pill is under
        // the map's buttons (54 dp at 12 from the edge, three of them and an app's row reach 240 dp down).
        val pill = pillAt(1184f - 12f - PILL_WIDTH, 300f)
        val box = mini(apart(MiniPlace.CORNER, pill))
        assertFalse(box.near(pill, 8f))
        val roomW = areaW - MINI_WIDTH
        assertTrue("$box", box.left <= roomW - 62f + 0.5f || box.top >= 240f - 0.5f)
    }

    @Test fun miniStaysUnderThePillWhereNothingIsClear() {
        val rest = MiniPlace(0.3f, 0.7f)
        assertEquals(rest, CallPillPlace.miniApart(rest, ScreenRect(0f, 0f, 300f, 100f), 300f, 100f, 200f, 64f))
        // No room for it at all: as it rests.
        assertEquals(rest, CallPillPlace.miniApart(rest, ScreenRect(0f, 0f, 10f, 10f), 100f, 50f, 200f, 64f))
    }

    @Test fun timeAsAPhoneShowsIt() {
        assertEquals("0:00", callClock(0))
        assertEquals("0:07", callClock(7))
        assertEquals("1:23", callClock(83))
        assertEquals("12:34", callClock(754))
        assertEquals("59:59", callClock(3_599))
        assertEquals("1:00:00", callClock(3_600))
        assertEquals("1:02:03", callClock(3_723))
        assertEquals("0:00", callClock(-5))
    }
}
