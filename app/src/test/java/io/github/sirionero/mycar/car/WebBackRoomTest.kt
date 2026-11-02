package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The room for myCar's Back on a music service's page, in its CSS pixels. */
class WebBackRoomTest {

    @Test
    fun `at the page's own width a CSS pixel is a dp`() {
        // 1920x720 at 2.0, Normal: 1776 px beside the dock, laid out 888 px wide.
        assertEquals(56, webBackRoomCss(dpPx = 2f, cssWidth = 888, widthPx = 1776))
    }

    @Test
    fun `laid out at 800 in a narrower place, the room grows`() {
        // 800x480 at 160 dpi, Extra large: dock 144 px, page 656 px laid out 800 px wide.
        val room = webBackRoomCss(dpPx = 1.5f, cssWidth = 800, widthPx = 656)
        assertEquals(103, room)
        // The Back's touch ends at 56 dp = 84 real px: in CSS pixels that's within the room.
        assertTrue(84f * 800 / 656 <= room)
    }

    @Test
    fun `Large at 800x480`() {
        // Dock 120 px, page 680 px laid out at 800 (680 / 1.25 = 544, under the minimum).
        assertEquals(83, webBackRoomCss(dpPx = 1.25f, cssWidth = 800, widthPx = 680))
    }

    @Test
    fun `nothing measured yet, the plain room`() {
        assertEquals(56, webBackRoomCss(dpPx = 1f, cssWidth = 800, widthPx = 0))
    }
}
