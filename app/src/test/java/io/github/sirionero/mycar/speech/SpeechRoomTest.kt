package io.github.sirionero.mycar.speech

import io.github.sirionero.mycar.speech.SpeechRoom.Press
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the model that rereads the phrase loads, from drive logs of a Pixel 10 Pro (see SpeechRoom). */
class SpeechRoomTest {

    private fun mb(n: Long) = n shl 20

    /** Parakeet multilingual: 640 MB on disk, 5/4 of it in memory. */
    private val parakeet = mb(799)
    private val line = mb(216)

    @Test
    fun `ahead of a command only with plenty of room`() {
        assertTrue(SpeechRoom.ahead(mb(4_116), line, low = false, need = parakeet))
        // 6 November, 18:03: 2546 MB free - it waited for the first command.
        assertFalse(SpeechRoom.ahead(mb(2_546), line, low = false, need = parakeet))
        assertFalse(SpeechRoom.ahead(mb(9_000), line, low = true, need = parakeet))
    }

    @Test
    fun `at a press with plenty of room, as always`() {
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(4_000), line, low = false, need = parakeet, english = true))
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(4_000), line, low = false, need = parakeet, english = false))
    }

    @Test
    fun `Italian at a press still loads under the margin - the only model that hears it`() {
        // 6 November, 18:04: 2497 MB free, Parakeet loaded at the press and heard the command.
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(2_497), line, low = false, need = parakeet, english = false))
        // An evening with 1.5 GB free: still above Android's own line after it.
        assertEquals(Press.LOAD, SpeechRoom.atPress(mb(1_506), line, low = false, need = parakeet, english = false))
    }

    @Test
    fun `English under the margin keeps the live words`() {
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(2_497), line, low = false, need = parakeet, english = true))
        assertEquals(Press.LIVE_WORDS, SpeechRoom.atPress(mb(600), line, low = true, need = parakeet, english = true))
    }

    @Test
    fun `another language with the phone short of memory - nothing loads`() {
        assertEquals(Press.NONE, SpeechRoom.atPress(mb(900), line, low = false, need = parakeet, english = false))
        assertEquals(Press.NONE, SpeechRoom.atPress(mb(3_000), line, low = true, need = parakeet, english = false))
    }
}
