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
        assertFalse(held.openNow(sessionOn = true, screenCame = true, channelHeld = true, now = 5_000))
        assertFalse(held.rememberCar(now = 400))
    }

    @Test fun `no screen after the wait opens the channel once, and the car is remembered`() {
        val held = HeldScreen(known = false)
        assertTrue(held.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_000))
        // Once a session.
        assertFalse(held.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_001))
        // The screen came right after the second ask (1 s after the opening).
        assertTrue(held.rememberCar(now = 6_300))
    }

    @Test fun `a screen that comes much later is the driver's choice, not the channel's`() {
        // The car was on its own menus when the phone came; the driver picked the phone's screen 30 s later.
        val held = HeldScreen(known = false)
        assertTrue(held.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_000))
        assertFalse(held.rememberCar(now = 35_000))
        // The limit itself still counts.
        val soon = HeldScreen(known = false)
        assertTrue(soon.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_000))
        assertTrue(soon.rememberCar(now = 5_000 + HeldScreen.REMEMBER_WITHIN_MS))
    }

    @Test fun `nothing to open when the channel is open already or the session ended`() {
        // Through myCar, or Automatic chose it meanwhile: the channel is open already.
        assertFalse(HeldScreen(known = false).openNow(sessionOn = true, screenCame = false, channelHeld = false, now = 5_000))
        // The car went, or the session was stopped.
        assertFalse(HeldScreen(known = false).openNow(sessionOn = false, screenCame = false, channelHeld = true, now = 5_000))
        assertFalse(HeldScreen(known = false).rememberCar(now = 6_000))
    }

    @Test fun `a car known to need it gets the channel at once and isn't remembered again`() {
        val held = HeldScreen(known = true)
        assertTrue(held.openAtOnce())
        assertFalse(held.rememberCar(now = 400))
    }
}
