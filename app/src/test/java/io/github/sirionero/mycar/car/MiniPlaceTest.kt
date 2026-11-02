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
