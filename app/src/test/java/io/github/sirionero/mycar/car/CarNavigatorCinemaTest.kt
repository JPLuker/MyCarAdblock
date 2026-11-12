package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for map/Cinema navigation and fullscreen layout state. */
class CarNavigatorCinemaTest {
    @Test fun cinemaShortcutRemainsAvailableAfterReturningToMap() {
        val navigator = CarNavigator("")
        assertFalse(navigator.cinemaOpened)
        navigator.open("cinema")
        assertTrue(navigator.cinemaOpened)
        assertEquals(CarScreen.App("cinema"), navigator.current)

        navigator.home()
        assertEquals(CarScreen.Home, navigator.current)
        assertTrue(navigator.cinemaOpened)

        navigator.open("cinema")
        assertEquals(CarScreen.App("cinema"), navigator.current)
    }

    @Test fun fullscreenDoesNotUndoMapAndCinemaSplitPreference() {
        val navigator = CarNavigator("")
        navigator.open("cinema")
        navigator.toggleCinemaSplit()
        assertTrue(navigator.cinemaSplit)

        navigator.cinemaVideoFullscreen = true
        assertTrue(navigator.cinemaSplit)
        assertTrue(navigator.cinemaVideoFullscreen)

        navigator.toggleCinemaSplit()
        assertFalse(navigator.cinemaSplit)
        assertTrue(navigator.cinemaVideoFullscreen)

        navigator.toggleCinemaSplit()
        assertTrue(navigator.cinemaSplit)
    }

    @Test fun initiallyOpenedCinemaHasShortcutReady() {
        val navigator = CarNavigator("cinema")
        assertTrue(navigator.cinemaOpened)
        navigator.home()
        assertTrue(navigator.cinemaOpened)
    }
}
