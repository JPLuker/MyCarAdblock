package io.github.sirionero.mycar.car

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCommandWordsTest {
    @Test fun theMusicsOwnCommandsAreForTheMusic() {
        for (said in listOf(
            "pause", "pause the music", "pause it", "stop", "stop playing", "stop the music", "quiet", "shut up",
            "next", "next song", "skip", "skip this song", "skip to the next song", "play the next song", "next episode",
            "change the song", "play something else", "another song", "a different track",
            "previous", "go back to the previous song", "go back a song", "last song", "the last one",
            "play it again", "play that song again", "replay", "resume", "resume my music", "resume playback", "unpause",
            "continue", "keep playing", "keep going", "start the music", "music on", "turn the music back on",
            "play", "play music", "play some music", "play my music",
        )) assertTrue(said, musicCommandOnly(said))
    }

    @Test fun theMusicAppsNameIsPartOfIt() {
        assertTrue(musicCommandOnly("pause spotify", "Spotify"))
        assertTrue(musicCommandOnly("next song on spotify", "Spotify · Web"))
        assertFalse(musicCommandOnly("pause spotify"))
    }

    @Test fun otherQuestionsAndThingsAreNot() {
        for (said in listOf(
            "what's the weather for the next few days", "will it rain next week", "when is my next appointment",
            "pause the tv", "pause the vacuum", "stop the vacuum", "resume the vacuum", "who was the last one to call",
            "turn off the lights",
        )) assertFalse(said, musicCommandOnly(said, "Spotify"))
    }
}
