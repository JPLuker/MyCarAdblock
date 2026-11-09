package io.github.sirionero.mycar.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the phone's speech engine is made again (DirectionsVoice, CarAssistant). */
class SpeechRestartTest {

    private val start = 1_000_000L
    private val wait = SpeechRestart.AFTER_MS

    @Test fun `an engine that is ready is never made again`() {
        assertFalse(SpeechRestart.due(ready = true, failed = false, startedAt = start, now = start))
        assertFalse(SpeechRestart.due(ready = true, failed = false, startedAt = start, now = start + 10 * wait))
    }

    @Test fun `a start that never answers gets its time, then another try`() {
        assertFalse(SpeechRestart.due(ready = false, failed = false, startedAt = start, now = start + 5_000))
        assertFalse(SpeechRestart.due(ready = false, failed = false, startedAt = start, now = start + wait - 1))
        assertTrue(SpeechRestart.due(ready = false, failed = false, startedAt = start, now = start + wait))
        assertTrue(SpeechRestart.due(ready = false, failed = false, startedAt = start, now = start + 23 * 60_000L))
    }

    @Test fun `a start that said no, or an engine that refused, waits as long since its start`() {
        assertFalse(SpeechRestart.due(ready = false, failed = true, startedAt = start, now = start + 1_000))
        assertTrue(SpeechRestart.due(ready = false, failed = true, startedAt = start, now = start + wait))
        // Ready at first, then it refused to speak (updated in the background).
        assertFalse(SpeechRestart.due(ready = true, failed = true, startedAt = start, now = start + wait / 2))
        assertTrue(SpeechRestart.due(ready = true, failed = true, startedAt = start, now = start + 2 * wait))
    }

    @Test fun `a try just made is not made again at once`() {
        val again = start + 2 * wait
        assertTrue(SpeechRestart.due(ready = false, failed = false, startedAt = start, now = again))
        // The try at `again` is the last start now: the next turns wait for it.
        assertFalse(SpeechRestart.due(ready = false, failed = false, startedAt = again, now = again + 500))
        assertTrue(SpeechRestart.due(ready = false, failed = false, startedAt = again, now = again + wait))
    }
}
