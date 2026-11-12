package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Splitting Cinema must resize one existing screen, never navigate away or reset its stack. */
class CinemaSplitNavigatorTest {
    @Test fun mapSplitDoesNotCloseCinemaOrLoseItsNavigationPosition() {
        val navigator = CarNavigator("")
        navigator.open("cinema")
        val screen = navigator.current
        val trail = navigator.trail.toList()
        assertFalse(navigator.cinemaSplit)
        navigator.toggleCinemaSplit()
        assertTrue(navigator.cinemaSplit)
        assertEquals(screen, navigator.current)
        assertEquals(trail, navigator.trail)
        navigator.cinemaVideoFullscreen = true
        // A video takes the full Cinema pane until it exits fullscreen.
        assertTrue(navigator.cinemaSplit)
        navigator.cinemaVideoFullscreen = false
        navigator.toggleCinemaSplit()
        assertFalse(navigator.cinemaSplit)
        assertEquals(screen, navigator.current)
        assertEquals(trail, navigator.trail)
    }
}
