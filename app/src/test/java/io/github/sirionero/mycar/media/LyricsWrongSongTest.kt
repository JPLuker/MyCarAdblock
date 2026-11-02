package io.github.sirionero.mycar.media

import io.github.sirionero.mycar.media.LyricsMatch.Ask
import io.github.sirionero.mycar.media.LyricsMatch.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cases a first draft got wrong: another song's words, or a radio title read as a decoration. */
class LyricsWrongSongTest {
    private fun q(title: String, artist: String, seconds: Int = 0) = requireNotNull(LyricsMatch.query(title, artist, "", seconds * 1000L))
    private fun hit(track: String, artist: String, seconds: Double) = Hit(1, track, artist, "", seconds, false, "[00:01.00] w", "w")

    @Test
    fun `a featured artist's own song of the same name is another song`() {
        assertNull(LyricsMatch.fit(q("Stay (feat. Justin Bieber)", "The Kid LAROI", 141), hit("Stay", "Justin Bieber", 150.0)))
        assertNull(LyricsMatch.fit(q("Hold On (feat. Someone)", "Main Artist", 200), hit("Hold On", "Someone", 205.0)))
        // At the very length it's the same recording, credited to the guest.
        assertNotNull(LyricsMatch.fit(q("Stay (feat. Justin Bieber)", "The Kid LAROI", 141), hit("Stay", "Justin Bieber", 141.5)))
    }

    @Test
    fun `a band name cut at its comma is not the band`() {
        assertNull(LyricsMatch.fit(q("September", "Earth, Wind & Fire", 215), hit("September", "Fire", 220.0)))
        assertNotNull(LyricsMatch.fit(q("September", "Earth, Wind & Fire", 215), hit("September", "Earth, Wind & Fire", 230.0)))
        assertNull(LyricsMatch.fit(q("Felicità", "Al Bano & Romina Power", 191), hit("Felicità", "Romina Power", 200.0)))
    }

    @Test
    fun `a radio title that only looks like a decoration keeps its words`() {
        val buggles = q("BUGGLES - VIDEO KILLED THE RADIO STAR", "Radio Deejay")
        assertEquals("BUGGLES" to "VIDEO KILLED THE RADIO STAR", buggles.alt)
        assertEquals(Ask.Search("VIDEO KILLED THE RADIO STAR", "BUGGLES"), LyricsMatch.plan(buggles).first())
        assertEquals("WINGS" to "LIVE AND LET DIE", q("WINGS - LIVE AND LET DIE", "Radio 105").alt)
        assertNull(q("Hey Jude - Remastered 2015", "The Beatles").alt)
        assertTrue(LyricsMatch.cleanTitle("Bohemian Rhapsody - Live Aid").live)
        assertTrue(LyricsMatch.cleanTitle("Song 2 - Live at Glastonbury 1998").live)
        assertEquals("Mix Tape", LyricsMatch.cleanTitle("Mix Tape").strict)
        for (title in listOf("WAKE ME UP", "1999", "DAL PROFONDO", "LOVE IN STEREO", "LIVE AND LET DIE"))
            assertFalse(title, LyricsMatch.decorationAfterDash(title))
        for (tail in listOf("Remastered 2011", "2013 Remaster", "Radio Edit", "Live at Wembley", "Live Aid", "Dal vivo",
            "Official Video", "Video Ufficiale", "Mono", "Mono Version", "Sped Up", "Sanremo 2024", "From \"Frozen\"/Soundtrack Version"))
            assertTrue(tail, LyricsMatch.decorationAfterDash(tail))
    }

    @Test
    fun `an Italian subtitle in brackets is not a featured artist`() {
        val t = LyricsMatch.cleanTitle("Time to Say Goodbye (Con te partirò)")
        assertTrue(t.featured.isEmpty())
        assertEquals(listOf("Fedez"), LyricsMatch.cleanTitle("Bella storia (con Fedez)").featured)
        assertTrue(LyricsMatch.cleanTitle("Tu (With or Without You)").featured.isEmpty())
    }

    @Test
    fun `a video's bar goes with what follows it`() {
        assertEquals("Beggin'", q("Måneskin - Beggin' | Official Video", "Måneskin", 211).title)
    }

    @Test
    fun `a live take of a sister song is not this song`() {
        assertNull(LyricsMatch.fit(q("Sgt. Pepper's Lonely Hearts Club Band (Reprise)", "The Beatles", 79), hit("Sgt. Pepper's Lonely Hearts Club Band (Live)", "The Beatles", 150.0)))
        assertNull(LyricsMatch.fit(q("Song (Part 1)", "Band", 200), hit("Song (Part 2) [Live]", "Band", 320.0)))
        assertNull(LyricsMatch.fit(q("Bella ciao (Live)", "Banda", 180), hit("Bella ciao (Version française)", "Banda", 185.0)))
        assertNull(LyricsMatch.fit(q("Despacito - Remix", "Luis Fonsi", 229), hit("Despacito (Live)", "Luis Fonsi", 282.0)))
        // The same song live keeps its words at another length, without the times.
        val live = LyricsMatch.fit(q("Song", "Band", 200), hit("Song (Live)", "Band", 260.0))
        assertNotNull(live)
        assertFalse(live!!.timed)
        assertNotNull(LyricsMatch.fit(q("Bohemian Rhapsody - Live Aid", "Queen", 300), hit("Bohemian Rhapsody", "Queen", 354.0)))
    }

    @Test
    fun `instrumental by the title asks nobody, karaoke still gets the words`() {
        assertTrue(LyricsMatch.instrumentalByTitle(q("Brividi (Instrumental)", "Mahmood", 240)))
        assertFalse(LyricsMatch.instrumentalByTitle(q("Brividi (Karaoke Version)", "Mahmood", 240)))
    }
}
