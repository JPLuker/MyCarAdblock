package io.github.sirionero.mycar.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the drive's own Autoplay waits for the sound to have a way to the car. */
class AutoplayWaitTest {

    @Test
    fun `Automatic still deciding, it waits`() {
        assertTrue(autoplayWaits(deciding = true, throughMyCar = false, captureOn = false, straightIn = false))
        assertTrue(autoplayWaits(deciding = true, throughMyCar = true, captureOn = true, straightIn = true))
    }

    @Test
    fun `over Bluetooth it plays at once`() {
        assertFalse(autoplayWaits(deciding = false, throughMyCar = false, captureOn = false, straightIn = false))
    }

    @Test
    fun `through myCar, it waits for Android's capture`() {
        assertTrue(autoplayWaits(deciding = false, throughMyCar = true, captureOn = false, straightIn = false))
        assertFalse(autoplayWaits(deciding = false, throughMyCar = true, captureOn = true, straightIn = false))
    }

    @Test
    fun `Xonos inside myCar plays straight into the car, no capture needed`() {
        assertFalse(autoplayWaits(deciding = false, throughMyCar = true, captureOn = false, straightIn = true))
    }

    @Test
    fun `on the Bluetooth way with no Bluetooth output, it waits rather than play on the phone's speaker`() {
        assertTrue(autoplayWaits(deciding = false, throughMyCar = false, captureOn = false, straightIn = false, bluetoothOut = false))
        assertTrue(autoplayWaits(deciding = false, throughMyCar = false, captureOn = false, straightIn = true, bluetoothOut = false))
        assertFalse(autoplayWaits(deciding = false, throughMyCar = false, captureOn = false, straightIn = false, bluetoothOut = true))
    }

    @Test
    fun `through myCar, a Bluetooth output doesn't matter`() {
        assertFalse(autoplayWaits(deciding = false, throughMyCar = true, captureOn = true, straightIn = false, bluetoothOut = false))
        assertFalse(autoplayWaits(deciding = false, throughMyCar = true, captureOn = false, straightIn = true, bluetoothOut = false))
        assertTrue(autoplayWaits(deciding = false, throughMyCar = true, captureOn = false, straightIn = false, bluetoothOut = true))
    }
}
