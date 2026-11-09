package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.audio.PausedForCar.Then
import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's music paused for the car's moment on its speakers, at the car's GAIN. */
class PausedForCarTest {

    @Test fun `played again when the car gives its speakers back`() {
        assertEquals(Then.PLAY, PausedForCar.atGain(paused = true, driverAsked = false, inCall = false, waitsLeft = PausedForCar.CALL_CHECKS))
    }

    @Test fun `not after the driver asked the music for anything`() {
        assertEquals(Then.DROP, PausedForCar.atGain(paused = true, driverAsked = true, inCall = false, waitsLeft = PausedForCar.CALL_CHECKS))
        assertEquals(Then.DROP, PausedForCar.atGain(paused = true, driverAsked = true, inCall = true, waitsLeft = PausedForCar.CALL_CHECKS))
    }

    @Test fun `nothing remembered, nothing played - the drive ended, the radio, nothing was playing`() {
        assertEquals(Then.DROP, PausedForCar.atGain(paused = false, driverAsked = false, inCall = false, waitsLeft = PausedForCar.CALL_CHECKS))
    }

    @Test fun `a call still on waits for its end, but not for ever`() {
        assertEquals(Then.WAIT, PausedForCar.atGain(paused = true, driverAsked = false, inCall = true, waitsLeft = 3))
        assertEquals(Then.PLAY, PausedForCar.atGain(paused = true, driverAsked = false, inCall = false, waitsLeft = 2))
        assertEquals(Then.DROP, PausedForCar.atGain(paused = true, driverAsked = false, inCall = true, waitsLeft = 0))
    }
}
