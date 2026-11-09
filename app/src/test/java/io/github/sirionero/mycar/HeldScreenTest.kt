package io.github.sirionero.mycar

import io.github.sirionero.mycar.HeldScreen.Seen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A car that keeps its own screen while the music channel is held closed (music over Bluetooth). */
class HeldScreenTest {

    @Test fun `a car that gives its screen at once changes nothing`() {
        val held = HeldScreen(known = false)
        assertFalse(held.openAtOnce())
        // The screen came within the 5 s: nothing opened, and the car showed it doesn't need the channel.
        assertFalse(held.openNow(sessionOn = true, screenCame = true, channelHeld = true, now = 5_000))
        assertEquals(Seen.NOT_NEEDED, held.screenCame(now = 400, held = true))
    }

    @Test fun `no screen after the wait opens the channel once, and the screen soon after counts`() {
        val held = HeldScreen(known = false)
        assertTrue(held.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_000))
        // Once a session.
        assertFalse(held.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_001))
        // The screen came right after the second ask (1 s after the opening).
        assertEquals(Seen.NEEDED, held.screenCame(now = 6_300, held = false))
    }

    @Test fun `a screen that comes much later is the driver's choice, not the channel's`() {
        // The car was on its own menus when the phone came; the driver picked the phone's screen 30 s later.
        val held = HeldScreen(known = false)
        assertTrue(held.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_000))
        assertEquals(Seen.NOTHING, held.screenCame(now = 35_000, held = false))
        // The limit itself still counts.
        val soon = HeldScreen(known = false)
        assertTrue(soon.openNow(sessionOn = true, screenCame = false, channelHeld = true, now = 5_000))
        assertEquals(Seen.NEEDED, soon.screenCame(now = 5_000 + HeldScreen.REMEMBER_WITHIN_MS, held = false))
    }

    @Test fun `nothing to open when the channel is open already or the session ended`() {
        // Through myCar, or Automatic chose it meanwhile: the channel is open already.
        assertFalse(HeldScreen(known = false).openNow(sessionOn = true, screenCame = false, channelHeld = false, now = 5_000))
        // The car went, or the session was stopped.
        assertFalse(HeldScreen(known = false).openNow(sessionOn = false, screenCame = false, channelHeld = true, now = 5_000))
        // A screen with the channel opened by Automatic says nothing of the car.
        assertEquals(Seen.NOTHING, HeldScreen(known = false).screenCame(now = 6_000, held = false))
    }

    @Test fun `a car known to need it gets the channel at once and isn't counted again`() {
        val held = HeldScreen(known = true)
        assertTrue(held.openAtOnce())
        assertEquals(Seen.NOTHING, held.screenCame(now = 400, held = false))
    }

    @Test fun `known only after two drives in a row - one slow screen is not for good`() {
        // One drive the car was slow (on its own menus, its system starting): not yet.
        assertFalse(HeldScreen.knownAfter(0))
        assertFalse(HeldScreen.knownAfter(1))
        // The second drive in a row, with no drive between that gave the screen with the channel closed.
        assertTrue(HeldScreen.knownAfter(HeldScreen.TIMES))
        assertTrue(HeldScreen.knownAfter(HeldScreen.TIMES + 1))
    }
}
