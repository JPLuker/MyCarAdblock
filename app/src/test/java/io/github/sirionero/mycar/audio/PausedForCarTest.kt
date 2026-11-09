package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.audio.PausedForCar.Then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /** The memory as CarServices keeps it, with players named by a string. */
    private fun memory() = PausedForCar.Memory<String> { it }

    @Test fun `what a moment paused plays again once, at its GAIN`() {
        val memory = memory()
        memory.paused(listOf("spotify"), own = false, askMark = 4, app = "web")
        val paused = present(memory.given())
        assertEquals(listOf("spotify"), paused.players)
        assertEquals(4, paused.askMark)
        assertTrue(memory.waits(paused))
        assertTrue(memory.done(paused))
        // Done: a second GAIN plays nothing.
        assertFalse(memory.waits(paused))
        assertNull(memory.given())
    }

    @Test fun `nothing playing at the car's moment leaves nothing to play again`() {
        val memory = memory()
        memory.paused(emptyList(), own = false, askMark = 4, app = "web")
        assertNull(memory.given())
        // Without notification access, the app myCar drives counts.
        memory.paused(emptyList(), own = true, askMark = 4, app = "web")
        assertTrue(present(memory.given()).own)
    }

    @Test fun `the car taking its speakers again during the call keeps what its first moment paused`() {
        // Ringing: the car takes the speakers, the music is paused. Its GAIN comes while the call goes
        // on (the music waits for the call's end), then the car takes them again for the call itself:
        // nothing plays now, the first moment's music is still paused.
        val memory = memory()
        memory.paused(listOf("spotify"), own = false, askMark = 4, app = "web")
        val first = present(memory.given())
        memory.paused(emptyList(), own = false, askMark = 6, app = "web")
        // The wait for the call's end gives up: it would play with the car's speakers taken.
        assertFalse(memory.waits(first))
        assertFalse(memory.done(first))
        // The car's GAIN at the end of the call plays it, counting the driver's asks from the first moment.
        val again = present(memory.given())
        assertEquals(listOf("spotify"), again.players)
        assertEquals(4, again.askMark)
        assertTrue(memory.done(again))
    }

    @Test fun `a GAIN and a new moment before the main thread plays again - both moments wait for the next GAIN`() {
        val memory = memory()
        memory.paused(listOf("spotify"), own = false, askMark = 1, app = "web")
        val first = present(memory.given())
        // The car's next moment, on the session's thread, before the main thread got to play the first.
        memory.paused(listOf("podcast", "spotify"), own = false, askMark = 2, app = "web")
        assertFalse(memory.waits(first))
        val both = present(memory.given())
        assertEquals(listOf("spotify", "podcast"), both.players)
        assertEquals(1, both.askMark)
    }

    @Test fun `a moment without its GAIN - the stream stopped since - plays nothing again`() {
        // Paused, then the stream stopped (AudioLink.stop): no GAIN came for it. The car's next moment
        // finds nothing playing: nothing to play again at its GAIN, as before.
        val memory = memory()
        memory.paused(listOf("spotify"), own = false, askMark = 1, app = "web")
        memory.paused(emptyList(), own = false, askMark = 3, app = "web")
        assertNull(memory.given())
        // Or something else playing: only that.
        memory.paused(listOf("spotify"), own = false, askMark = 1, app = "web")
        memory.paused(listOf("podcast"), own = false, askMark = 3, app = "web")
        assertEquals(listOf("podcast"), present(memory.given()).players)
    }

    @Test fun `forgotten at the drive's end, the radio or the car leaving, even while waiting for a call's end`() {
        val memory = memory()
        memory.paused(listOf("spotify"), own = false, askMark = 1, app = "web")
        val paused = present(memory.given())
        memory.forget()
        assertFalse(memory.waits(paused))
        assertFalse(memory.done(paused))
        assertNull(memory.given())
    }

    /** [value], which must be there. */
    private fun <T> present(value: T?): T {
        org.junit.Assert.assertNotNull(value)
        return value!!
    }
}
