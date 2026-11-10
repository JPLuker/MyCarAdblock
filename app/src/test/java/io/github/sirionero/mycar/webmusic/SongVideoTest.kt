package io.github.sirionero.mycar.webmusic

import org.junit.Assert.assertEquals
import org.junit.Test

class SongVideoTest {

    @Test
    fun aSongWithItsVideo() {
        val rule = SongVideo()
        assertEquals("", rule.next("A", "", 0))
        assertEquals("va", rule.next("A", "va", 1_000))
        assertEquals("va", rule.next("A", "va", 60_000))
    }

    @Test
    fun theSongBeforesVideoStillShownIsNotTheNewSongs() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "va", 180_000))
        assertEquals("", rule.next("B", "va", 181_000))
        assertEquals("vb", rule.next("B", "vb", 182_000))
    }

    @Test
    fun theNewSongsVideoWithTheNewSong() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("vb", rule.next("B", "vb", 180_000))
    }

    @Test
    fun aVideoAMomentAheadOfTheNewSongsWordsIsTheNewSongs() {
        // The page's picture changed a moment before its song: that video is the next song's.
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("vb", rule.next("A", "vb", 180_000))
        assertEquals("vb", rule.next("B", "vb", 180_000 + SongVideo.FRESH_MS - 1))
        assertEquals("vb", rule.next("B", "vb", 200_000))
    }

    @Test
    fun rapidSkipsDontCarryTheOldVideo() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "va", 5_000))
        assertEquals("", rule.next("C", "va", 6_000))
        assertEquals("", rule.next("D", "va", 7_000))
        assertEquals("vd", rule.next("D", "vd", 8_000))
    }

    @Test
    fun aMomentAheadOnlyOnce() {
        // Taken for the song after as a moment ahead, it isn't new again for the songs after that.
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("va", rule.next("B", "va", 1_000))
        assertEquals("", rule.next("C", "va", 2_500))
        assertEquals("", rule.next("D", "va", 3_000))
    }

    @Test
    fun nextThenBackGivesTheVideoBack() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "va", 60_000))
        assertEquals("va", rule.next("A", "va", 63_000))
    }

    @Test
    fun theOldVideoCountsAgainAfterAWhile() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "va", 60_000))
        assertEquals("", rule.next("B", "va", 60_000 + SongVideo.STALE_MS - 1))
        assertEquals("va", rule.next("B", "va", 60_000 + SongVideo.STALE_MS))
    }

    @Test
    fun letGoThenShownAgainIsThisSongs() {
        // The same video for two songs in a row (one uploaded for both): once the page has shown none
        // in between, it's this song's.
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "va", 180_000))
        assertEquals("", rule.next("B", "", 181_000))
        assertEquals("va", rule.next("B", "va", 182_000))
    }

    @Test
    fun songsInARowWhileTheOldVideoStays() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "va", 180_000))
        assertEquals("", rule.next("C", "va", 181_000))
        assertEquals("vc", rule.next("C", "vc", 182_000))
        assertEquals("", rule.next("D", "vc", 400_000))
    }

    @Test
    fun aSongWithoutVideoInBetween() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("", rule.next("B", "", 180_000))
        assertEquals("va", rule.next("C", "va", 400_000))
    }

    @Test
    fun theSameSongAgainKeepsItsVideo() {
        val rule = SongVideo()
        rule.next("A", "va", 0)
        assertEquals("va2", rule.next("A", "va2", 1_000))
        assertEquals("va2", rule.next("A", "va2", 2_000))
    }
}
