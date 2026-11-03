package io.github.sirionero.mycar.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** The pause a spoken "pause" sends again after the voice lets go: only if nothing else was asked meanwhile. */
class MusicAsksTest {

    private val asks = MusicAsks()
    private var pauses = 0

    /** As VoiceFocus.afterRelease keeps it while myCar holds the music's focus. */
    private val afterRelease = mutableListOf<() -> Unit>()

    private fun saidPause() {
        pauses++ // the pause sent at once
        afterRelease += asks.unlessAskedSince { pauses++ }
    }

    private fun focusGivenBack() {
        afterRelease.forEach { it() }
        afterRelease.clear()
    }

    @Test fun pauseAloneIsSentAgain() {
        saidPause()
        focusGivenBack()
        assertEquals(2, pauses)
    }

    @Test fun resumeNextOrASongRightAfterDropsIt() {
        // "Pause", then "resume" / "next" / "play X" / ▶ before myCar gave the focus back.
        saidPause()
        asks.asked()
        focusGivenBack()
        assertEquals(1, pauses)
    }

    @Test fun twoPausesBothStand() {
        saidPause()
        saidPause()
        focusGivenBack()
        assertEquals(4, pauses)
    }

    @Test fun aPauseAfterTheResumeStands() {
        saidPause()
        asks.asked()
        saidPause()
        focusGivenBack()
        // The first one dropped, the second one sent again.
        assertEquals(3, pauses)
    }

    @Test fun whatWasAskedBeforeThePauseDoesNotCount() {
        asks.asked()
        asks.asked()
        saidPause()
        focusGivenBack()
        assertEquals(2, pauses)
    }
}
