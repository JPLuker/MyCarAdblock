package io.github.sirionero.mycar.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** When a capture of only zeros is said in the log (see SilentCapture). */
class SilentCaptureTest {

    /** Feeds a piece every 20 ms from [from] to [to]; the times it said so. */
    private fun SilentCapture.run(from: Long, to: Long, sound: Boolean, playing: Boolean): List<Long> =
        (from until to step 20).filter { piece(it, sound, playing) }

    @Test
    fun `the page still loading - silence before the music says it plays is nothing`() {
        // The Tayron's starts: capture at 0, zeros, the music says it plays at 6 s and is heard at 7 s.
        val c = SilentCapture(startedAt = 0)
        assertEquals(emptyList<Long>(), c.run(0, 6_000, sound = false, playing = false))
        assertEquals(emptyList<Long>(), c.run(6_000, 7_000, sound = false, playing = true))
        assertEquals(emptyList<Long>(), c.run(7_000, 60_000, sound = true, playing = true))
    }

    @Test
    fun `an app that plays and lets nothing be captured - said once, 5 s after it said it plays`() {
        val c = SilentCapture(startedAt = 0)
        assertEquals(emptyList<Long>(), c.run(0, 2_000, sound = false, playing = false))
        assertEquals(listOf(7_000L), c.run(2_000, 30_000, sound = false, playing = true))
    }

    @Test
    fun `music heard, then an app that lets nothing be captured`() {
        val c = SilentCapture(startedAt = 0)
        c.run(0, 10_000, sound = true, playing = true)
        // The driver switches to the phone's Spotify app: it says it plays, the capture has only zeros,
        // 5 s after the last sound (the piece at 9 980 ms).
        assertEquals(listOf(14_980L), c.run(10_000, 20_000, sound = false, playing = true))
    }

    @Test
    fun `a pause and a new stretch of silence say it again`() {
        val c = SilentCapture(startedAt = 0)
        assertEquals(listOf(5_000L), c.run(0, 8_000, sound = false, playing = true))
        c.run(8_000, 9_000, sound = false, playing = false)
        assertEquals(listOf(14_000L), c.run(9_000, 16_000, sound = false, playing = true))
    }

    @Test
    fun `short silences in the music are nothing`() {
        val c = SilentCapture(startedAt = 0)
        c.run(0, 10_000, sound = true, playing = true)
        assertEquals(emptyList<Long>(), c.run(10_000, 14_000, sound = false, playing = true))
        assertEquals(emptyList<Long>(), c.run(14_000, 30_000, sound = true, playing = true))
    }
}
