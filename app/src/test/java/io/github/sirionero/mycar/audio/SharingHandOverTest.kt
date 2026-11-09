package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.audio.SharingHandOver.End
import io.github.sirionero.mycar.audio.SharingHandOver.Over
import io.github.sirionero.mycar.audio.SharingHandOver.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The capture of the phone's sound moving onto the screen sharing's projection, and ending with it. */
class SharingHandOverTest {

    /** The sharing's Start tapped with a capture running, the own projection taken, the sharing's picture up. */
    private fun shared() = SharingHandOver().apply {
        assertTrue(sharingStarts(ownAlive = true))
        assertTrue(ownStopped())
        assertTrue(sharingUp())
    }

    @Test
    fun `no capture on its own projection, nothing moves`() {
        val h = SharingHandOver()
        assertFalse(h.sharingStarts(ownAlive = false))
        assertEquals(Stage.OWN, h.stage)
        assertFalse(h.ownStopped())
        assertFalse(h.sharingUp())
        assertEquals(Over.NOTHING, h.sharingOver(ownAlive = false))
    }

    @Test
    fun `with no sharing, a capture's end and its projection's stop are as before`() {
        val h = SharingHandOver()
        assertFalse(h.ownStopped())
        assertEquals(End.AS_BEFORE, h.captureEnded(onSharing = false, stoppedHere = false))
        assertEquals(End.AS_BEFORE, h.captureEnded(onSharing = false, stoppedHere = true))
    }

    @Test
    fun `the own capture ending as its projection gives way leaves the phone muted for the next`() {
        val h = SharingHandOver()
        assertTrue(h.sharingStarts(ownAlive = true))
        assertEquals(End.NOTHING, h.captureEnded(onSharing = false, stoppedHere = false))
        assertTrue(h.ownStopped())
        assertTrue(h.sharingUp())
        // Its reading may end only once the capture on the sharing's projection has begun.
        assertEquals(End.NOTHING, h.captureEnded(onSharing = false, stoppedHere = false))
        assertEquals(Stage.SHARED, h.stage)
    }

    @Test
    fun `the sharing's picture up before the own projection's stop arrives, the capture moves all the same`() {
        val h = SharingHandOver()
        assertTrue(h.sharingStarts(ownAlive = true))
        assertTrue(h.sharingUp())
        assertEquals(Stage.SHARED, h.stage)
    }

    @Test
    fun `the sharing over, the capture ends with it, once`() {
        val h = shared()
        assertEquals(Over.END, h.sharingOver(ownAlive = false))
        assertEquals(Stage.OWN, h.stage)
        assertEquals(Over.NOTHING, h.sharingOver(ownAlive = false))
        // Its reading, stopped from here or by the projection going, has nothing more to do.
        assertEquals(End.NOTHING, h.captureEnded(onSharing = true, stoppedHere = true))
        assertEquals(End.NOTHING, h.captureEnded(onSharing = true, stoppedHere = false))
    }

    @Test
    fun `the capture on the sharing's projection ending by itself ends with the sharing`() {
        val h = shared()
        assertEquals(End.WITH_SHARING, h.captureEnded(onSharing = true, stoppedHere = false))
        assertEquals(Over.END, h.sharingOver(ownAlive = false))
    }

    @Test
    fun `the driver's Stop during the sharing is as before`() {
        val h = shared()
        assertEquals(End.AS_BEFORE, h.captureEnded(onSharing = true, stoppedHere = true))
    }

    @Test
    fun `a sharing that never took the own projection leaves the capture on it`() {
        val h = SharingHandOver()
        assertTrue(h.sharingStarts(ownAlive = true))
        assertEquals(Over.STAY, h.sharingOver(ownAlive = true))
        assertEquals(Stage.OWN, h.stage)
        assertEquals(End.AS_BEFORE, h.captureEnded(onSharing = false, stoppedHere = false))
    }

    @Test
    fun `a sharing that took the own projection and never came up ends the capture`() {
        val h = SharingHandOver()
        assertTrue(h.sharingStarts(ownAlive = true))
        assertTrue(h.ownStopped())
        assertEquals(Over.END, h.sharingOver(ownAlive = false))
        // Its picture coming up too late finds nothing waiting.
        assertFalse(h.sharingUp())
    }

    @Test
    fun `a second Start while moving or shared moves nothing more`() {
        val h = SharingHandOver()
        assertTrue(h.sharingStarts(ownAlive = true))
        assertFalse(h.sharingStarts(ownAlive = true))
        assertTrue(h.sharingUp())
        assertFalse(h.sharingStarts(ownAlive = true))
        assertFalse(h.sharingUp())
    }
}
