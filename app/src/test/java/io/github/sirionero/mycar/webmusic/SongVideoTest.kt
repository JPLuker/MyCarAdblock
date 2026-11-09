package io.github.sirionero.mycar.webmusic

import org.junit.Assert.assertEquals
import org.junit.Test

class SongVideoTest {

    @Test
    fun aSongWithItsVideo() {
        val rule = SongVideo()
        assertEquals("", rule.next("A", ""))
        assertEquals("va", rule.next("A", "va"))
        assertEquals("va", rule.next("A", "va"))
    }

    @Test
    fun theSongBeforesVideoStillShownIsNotTheNewSongs() {
        val rule = SongVideo()
        rule.next("A", "va")
        assertEquals("", rule.next("B", "va"))
        assertEquals("", rule.next("B", "va"))
        assertEquals("vb", rule.next("B", "vb"))
    }

    @Test
    fun theNewSongsVideoWithTheNewSong() {
        val rule = SongVideo()
        rule.next("A", "va")
        assertEquals("vb", rule.next("B", "vb"))
    }

    @Test
    fun letGoThenShownAgainIsThisSongs() {
        // The same video for two songs in a row (one uploaded for both): once the page has shown none
        // in between, it's this song's.
        val rule = SongVideo()
        rule.next("A", "va")
        assertEquals("", rule.next("B", "va"))
        assertEquals("", rule.next("B", ""))
        assertEquals("va", rule.next("B", "va"))
    }

    @Test
    fun songsInARowWhileTheOldVideoStays() {
        val rule = SongVideo()
        rule.next("A", "va")
        assertEquals("", rule.next("B", "va"))
        assertEquals("", rule.next("C", "va"))
        assertEquals("vc", rule.next("C", "vc"))
        assertEquals("", rule.next("D", "vc"))
    }

    @Test
    fun aSongWithoutVideoInBetween() {
        val rule = SongVideo()
        rule.next("A", "va")
        assertEquals("", rule.next("B", ""))
        assertEquals("va", rule.next("C", "va"))
    }

    @Test
    fun theSameSongAgainKeepsItsVideo() {
        val rule = SongVideo()
        rule.next("A", "va")
        assertEquals("va2", rule.next("A", "va2"))
        assertEquals("va2", rule.next("A", "va2"))
    }
}
