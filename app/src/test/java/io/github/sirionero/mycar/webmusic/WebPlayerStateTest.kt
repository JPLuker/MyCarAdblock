package io.github.sirionero.mycar.webmusic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page's word on what plays, as myCar's player reads it between two of them. */
class WebPlayerStateTest {

    @Test
    fun playingMovesOnFromWhenItWasSaid() {
        val state = WebPlayerState(title = "Song", playing = true, positionMs = 10_000, durationMs = 200_000, at = 1_000)
        assertEquals(10_000, state.positionAt(1_000))
        assertEquals(15_000, state.positionAt(6_000))
    }

    @Test
    fun pausedStaysWhereItWas() {
        val state = WebPlayerState(title = "Song", playing = false, positionMs = 10_000, durationMs = 200_000, at = 1_000)
        assertEquals(10_000, state.positionAt(60_000))
    }

    @Test
    fun neverPastTheEndNorBeforeTheStart() {
        val state = WebPlayerState(title = "Song", playing = true, positionMs = 199_000, durationMs = 200_000, at = 1_000)
        assertEquals(200_000, state.positionAt(10_000))
        // A clock read before the word came (a report racing a button) doesn't go back.
        assertEquals(199_000, state.positionAt(500))
    }

    @Test
    fun unknownLengthStillMovesOn() {
        val state = WebPlayerState(title = "Radio", playing = true, positionMs = 0, durationMs = 0, at = 1_000)
        assertEquals(3_000, state.positionAt(4_000))
    }

    @Test
    fun aSongIsKnownByItsNameArtistAndAlbum() {
        val one = WebPlayerState(title = "Song", artist = "A", album = "X")
        assertTrue(one.hasTrack)
        assertEquals(one.key, one.copy(playing = true, positionMs = 5_000).key)
        assertNotEquals(one.key, one.copy(title = "Other").key)
        assertNotEquals(one.key, one.copy(artist = "B").key)
        assertFalse(WebPlayerState().hasTrack)
    }
}
