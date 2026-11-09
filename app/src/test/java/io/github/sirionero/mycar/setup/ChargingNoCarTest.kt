package io.github.sirionero.mycar.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's hint while the phone charges from a USB port and no car comes to myCar. */
class ChargingNoCarTest {

    @Test
    fun `only after the wait`() {
        val hint = ChargingNoCar(waitMs = 20_000)
        assertFalse(hint.update(1_000, usb = true, car = false))
        assertFalse(hint.update(20_999, usb = true, car = false))
        assertTrue(hint.update(21_000, usb = true, car = false))
    }

    @Test
    fun `never without a USB port charging the phone`() {
        val hint = ChargingNoCar(waitMs = 20_000)
        assertFalse(hint.update(0, usb = false, car = false))
        assertFalse(hint.update(60_000, usb = false, car = false))
    }

    @Test
    fun `unplugged, it goes, and plugged in again, the wait starts over`() {
        val hint = ChargingNoCar(waitMs = 20_000)
        hint.update(0, usb = true, car = false)
        assertTrue(hint.update(25_000, usb = true, car = false))
        assertFalse(hint.update(26_000, usb = false, car = false))
        assertFalse(hint.update(30_000, usb = true, car = false))
        assertFalse(hint.update(49_999, usb = true, car = false))
        assertTrue(hint.update(50_000, usb = true, car = false))
    }

    @Test
    fun `a car that comes takes it away`() {
        val hint = ChargingNoCar(waitMs = 20_000)
        hint.update(0, usb = true, car = false)
        assertTrue(hint.update(30_000, usb = true, car = false))
        assertFalse(hint.update(31_000, usb = true, car = true))
    }

    @Test
    fun `after a car on this cable, no hint until it's unplugged`() {
        val hint = ChargingNoCar(waitMs = 20_000)
        assertFalse(hint.update(0, usb = true, car = true))
        // The drive ended, or the car let the port go: still not a cable to check.
        assertFalse(hint.update(1_000, usb = true, car = false))
        assertFalse(hint.update(120_000, usb = true, car = false))
        // Another plug, another wait.
        assertFalse(hint.update(121_000, usb = false, car = false))
        assertFalse(hint.update(122_000, usb = true, car = false))
        assertTrue(hint.update(142_000, usb = true, car = false))
    }
}
