package io.github.sirionero.mycar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A car that keeps its own screen while the music channel is held closed (music over Bluetooth). */
class HeldScreenTest {

    @Test fun `a car that gives its screen at once changes nothing`() {
        val held = HeldScreen(known = false)
        assertFalse(held.openAtOnce())
        // The screen came within the 5 s: nothing opened, nothing remembered.
        assertFalse(held.openNow(sessionOn = true, screenCame = true, channelHeld = true))
        assertFalse(held.rememberCar())
    }

    @Test fun `no screen after the wait opens the channel once, and the car is remembered`() {
        val held = HeldScreen(known = false)
        assertTrue(held.openNow(sessionOn = true, screenCame = false, channelHeld = true))
        // Once a session.
        assertFalse(held.openNow(sessionOn = true, screenCame = false, channelHeld = true))
        // The screen came after the opening.
        assertTrue(held.rememberCar())
    }

    @Test fun `nothing to open when the channel is open already or the session ended`() {
        // Through myCar, or Automatic chose it meanwhile: the channel is open already.
        assertFalse(HeldScreen(known = false).openNow(sessionOn = true, screenCame = false, channelHeld = false))
        // The car went, or the session was stopped.
        assertFalse(HeldScreen(known = false).openNow(sessionOn = false, screenCame = false, channelHeld = true))
        assertFalse(HeldScreen(known = false).rememberCar())
    }

    @Test fun `a car known to need it gets the channel at once and isn't remembered again`() {
        val held = HeldScreen(known = true)
        assertTrue(held.openAtOnce())
        assertFalse(held.rememberCar())
    }
}
