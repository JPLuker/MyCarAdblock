package io.github.sirionero.mycar.media

import io.github.sirionero.mycar.media.LyricsMatch.Ask
import io.github.sirionero.mycar.media.LyricsMatch.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Titles as the music apps write them, and LRCLIB's entries that are (or aren't) that song. */
class LyricsMatchTest {

    private fun q(title: String, artist: String, album: String = "", seconds: Int = 0) =
        requireNotNull(LyricsMatch.query(title, artist, album, seconds * 1000L))

    private fun hit(
        track: String, artist: String, seconds: Double, album: String = "",
        synced: String? = "[00:01.00] words", plain: String? = "words", instrumental: Boolean = false,
    ) = Hit(1, track, artist, album, seconds, instrumental, synced, plain)

    // --- names --------------------------------------------------------------------------------

    @Test
    fun `names compare as LRCLIB does`() {
        assertEquals("perche", LyricsMatch.key("Perche'"))
        assertEquals("perche", LyricsMatch.key("Perché"))
        assertEquals("titi me pregunto", LyricsMatch.key("Tití Me Preguntó"))
        assertEquals("maneskin", LyricsMatch.key("Måneskin"))
        assertEquals("i p me tu p te", LyricsMatch.key("I p' me, tu p' te"))
        assertEquals("simon garfunkel", LyricsMatch.key("Simon & Garfunkel"))
    }

    @Test
    fun `decorations come off the title, versions only off the loose one`() {
        fun strict(t: String) = LyricsMatch.cleanTitle(t).strict
        fun loose(t: String) = LyricsMatch.cleanTitle(t).loose
        assertEquals("Bohemian Rhapsody", strict("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("Hotel California", strict("Hotel California - 2013 Remaster"))
        assertEquals("Let It Go", strict("Let It Go - From \"Frozen\"/Soundtrack Version"))
        assertEquals("Lose Yourself", strict("Lose Yourself - From \"8 Mile\" Soundtrack"))
        assertEquals("Alors on danse", strict("Alors on danse - Radio Edit"))
        assertEquals("Never Gonna Give You Up", strict("Never Gonna Give You Up (Official Music Video)"))
        assertEquals("Albachiara", strict("Albachiara (Remastered) [HD]"))
        assertEquals("Despacito - Remix", strict("Despacito - Remix"))
        assertEquals("Despacito", loose("Despacito - Remix"))
        assertEquals("Gangnam Style (강남스타일)", strict("Gangnam Style (강남스타일)"))
        assertEquals("Gangnam Style", loose("Gangnam Style (강남스타일)"))
        assertEquals("Nel blu dipinto di blu", loose("Nel blu dipinto di blu (Volare)"))
        // Brackets that open the title are the title's own.
        assertEquals("(I Can't Get No) Satisfaction", strict("(I Can't Get No) Satisfaction - Mono Version"))
    }

    @Test
    fun `featured artists leave the title for the artists`() {
        val levitating = LyricsMatch.cleanTitle("Levitating (feat. DaBaby)")
        assertEquals("Levitating", levitating.strict)
        assertEquals(listOf("DaBaby"), levitating.featured)
        assertEquals(listOf("Dua Lipa"), LyricsMatch.cleanTitle("One Kiss (with Dua Lipa)").featured)
        assertEquals("Lean On", LyricsMatch.cleanTitle("Lean On feat. MØ").strict)
    }

    @Test
    fun `live and instrumental takes are told`() {
        assertTrue(LyricsMatch.cleanTitle("Bohemian Rhapsody - Live Aid").live)
        assertEquals("Bohemian Rhapsody", LyricsMatch.cleanTitle("Bohemian Rhapsody - Live Aid").strict)
        assertTrue(LyricsMatch.cleanTitle("Wonderwall (Acoustic)").live)
        assertTrue(LyricsMatch.cleanTitle("Brividi (Instrumental)").instrumental)
    }

    @Test
    fun `artist fields split, channels lose their suffix`() {
        assertEquals(listOf("Calvin Harris", "Dua Lipa"), LyricsMatch.splitArtists("Calvin Harris, Dua Lipa"))
        assertEquals(listOf("Mahmood", "BLANCO"), LyricsMatch.splitArtists("Mahmood & BLANCO"))
        assertEquals(listOf("Bizarrap", "Shakira"), LyricsMatch.splitArtists("Bizarrap x Shakira"))
        assertEquals(listOf("Malcolm X"), LyricsMatch.splitArtists("Malcolm X"))
        assertEquals(listOf("AC/DC"), LyricsMatch.splitArtists("AC/DC"))
        assertEquals("Måneskin", LyricsMatch.artistOf("Måneskin - Topic"))
        assertEquals("Luis Fonsi", LyricsMatch.artistOf("LuisFonsiVEVO"))
    }

    // --- the song asked for ---------------------------------------------------------------------

    @Test
    fun `a video's title loses the artist it repeats`() {
        val q = q("Rick Astley - Never Gonna Give You Up (Official Music Video)", "Rick Astley", seconds = 213)
        assertEquals("Never Gonna Give You Up", q.title)
        assertNull(q.alt)
    }

    @Test
    fun `a radio's stream title gives the artist to try`() {
        val q = q("QUEEN - DON'T STOP ME NOW", "Radio Deejay")
        assertEquals("QUEEN" to "DON'T STOP ME NOW", q.alt)
        assertEquals(Ask.Search("DON'T STOP ME NOW", "QUEEN"), LyricsMatch.plan(q).first())
        assertEquals(Ask.Words("QUEEN DON'T STOP ME NOW"), LyricsMatch.plan(q).last())
    }

    @Test
    fun `a video on a channel that isn't the artist's gives the artist to try`() {
        val q = q("Rick Astley - Never Gonna Give You Up (Official Music Video)", "Music Hits 80s", seconds = 213)
        assertEquals("Rick Astley" to "Never Gonna Give You Up", q.alt)
        // A version after the dash is no artist.
        assertNull(q("Hey Jude - Remastered 2015", "The Beatles").alt)
    }

    @Test
    fun `no lookup for no title or a podcast's length`() {
        assertNull(LyricsMatch.query("", "Queen", "", 200_000))
        assertNull(LyricsMatch.query("Episode 12", "A Podcast", "", 45 * 60_000L))
    }

    @Test
    fun `the length is rounded, not cut`() {
        assertEquals(355, LyricsMatch.query("Bohemian Rhapsody", "Queen", "", 354_947)!!.durationSec)
    }

    @Test
    fun `at most four questions, the exact one first and without the album`() {
        val q = q("Despacito - Remix", "Luis Fonsi, Daddy Yankee, Justin Bieber", "VIDA", 229)
        val plan = LyricsMatch.plan(q)
        assertTrue(plan.size <= LyricsMatch.MAX_ASKS)
        assertEquals(Ask.Get("Despacito - Remix", "Luis Fonsi, Daddy Yankee, Justin Bieber", 229), plan[0])
        assertEquals(Ask.Search("Despacito - Remix", "Luis Fonsi"), plan[1])
        assertEquals(Ask.Search("Despacito", "Luis Fonsi"), plan[2])
        assertTrue(plan.last() is Ask.Words)
        assertFalse(LyricsMatch.worthAsking(plan.last(), entriesSoFar = 3))
    }

    // --- the answers -----------------------------------------------------------------------------

    @Test
    fun `the same song at the same length is timed`() {
        val q = q("Bohemian Rhapsody - Remastered 2011", "Queen", "A Night At The Opera (2011 Remaster)", 355)
        val fit = LyricsMatch.fit(q, hit("Bohemian Rhapsody", "Queen", 354.0, "A Night at the Opera"))
        assertNotNull(fit)
        assertTrue(fit!!.timed)
        assertTrue(LyricsMatch.settled(q, fit))
    }

    @Test
    fun `a famous title by another artist is never shown`() {
        // The worst mistake: the Beatles' words for a local band's "Yesterday".
        val q = q("Yesterday", "Gruppo Prova Locale", seconds = 125)
        assertNull(LyricsMatch.fit(q, hit("Yesterday", "The Beatles", 125.0)))
        assertNull(LyricsMatch.fit(q("Imagine", "Coro San Marco", seconds = 183), hit("Imagine", "John Lennon", 183.0)))
    }

    @Test
    fun `another song by the same artist is never shown`() {
        val q = q("Zitti e buoni", "Måneskin", seconds = 194)
        assertNull(LyricsMatch.fit(q, hit("Beggin'", "Måneskin", 211.0)))
    }

    @Test
    fun `a different length keeps the words but not the times`() {
        val q = q("Shallow", "Lady Gaga, Bradley Cooper", seconds = 216)
        val fit = LyricsMatch.fit(q, hit("Shallow", "Lady Gaga", 225.0))
        assertNotNull(fit)
        assertFalse(fit!!.timed)
        // Too far apart to be the same recording.
        assertNull(LyricsMatch.fit(q, hit("Shallow", "Lady Gaga", 400.0)))
    }

    @Test
    fun `a live take shows the studio words, untimed`() {
        val q = q("Bohemian Rhapsody - Live Aid", "Queen", seconds = 360)
        val fit = LyricsMatch.fit(q, hit("Bohemian Rhapsody", "Queen", 354.0 + 60))
        assertNotNull(fit)
        assertFalse(fit!!.timed)
    }

    @Test
    fun `a remix by its loose name only at the same length`() {
        val q = q("Despacito - Remix", "Luis Fonsi, Daddy Yankee, Justin Bieber", seconds = 229)
        assertNotNull(LyricsMatch.fit(q, hit("Despacito", "Luis Fonsi", 229.5)))
        assertNull(LyricsMatch.fit(q, hit("Despacito", "Luis Fonsi", 282.0)))
    }

    @Test
    fun `a generic title needs the length`() {
        val q = q("Intro", "The xx", seconds = 127)
        assertNull(LyricsMatch.fit(q, hit("Intro", "The xx", 190.0)))
        assertNotNull(LyricsMatch.fit(q, hit("Intro", "The xx", 128.0, synced = null, plain = null, instrumental = true)))
    }

    @Test
    fun `a name inside the other needs the length`() {
        val q = q("Brividi", "Mahmood", seconds = 240)
        assertNotNull(LyricsMatch.fit(q, hit("Brividi", "Mahmood, BLANCO", 300.0)))  // the same name, split
        val anna = q("Desiderio", "Anna", seconds = 200)
        assertNull(LyricsMatch.fit(anna, hit("Desiderio", "Anna Tatangelo", 230.0)))
        assertNotNull(LyricsMatch.fit(anna, hit("Desiderio", "Anna Tatangelo", 201.0)))
    }

    @Test
    fun `featured artists count as the song's`() {
        val q = q("Levitating (feat. DaBaby)", "Dua Lipa", seconds = 203)
        assertNotNull(LyricsMatch.fit(q, hit("Levitating", "Dua Lipa feat. DaBaby", 203.0)))
        assertNotNull(LyricsMatch.fit(q, hit("Levitating (feat. DaBaby)", "Dua Lipa", 203.0)))
    }

    @Test
    fun `a radio's stream title matches through its own artist`() {
        val q = q("QUEEN - DON'T STOP ME NOW", "Radio Deejay")
        val fit = LyricsMatch.fit(q, hit("Don't Stop Me Now", "Queen", 209.0))
        assertNotNull(fit)
        assertFalse(fit!!.timed)  // no length from a radio: the words, not the times
        assertTrue(LyricsMatch.settled(q, fit))
        assertNull(LyricsMatch.fit(q, hit("Don't Stop Me Now", "McFly", 209.0)))
    }

    @Test
    fun `the closest length wins, words before instrumental`() {
        val q = q("Hotel California - 2013 Remaster", "Eagles", seconds = 391)
        val far = hit("Hotel California", "Eagles", 400.0)
        val near = hit("Hotel California", "Eagles", 390.6)
        assertEquals(390.6, LyricsMatch.best(q, listOf(far, near))!!.hit.durationSec, 0.0)
        val bare = hit("Hotel California", "Eagles", 391.0, synced = null, plain = null, instrumental = true)
        assertFalse(LyricsMatch.best(q, listOf(bare, near))!!.instrumental)
    }

    @Test
    fun `an instrumental at this length beats words at another`() {
        val q = q("Nuvole Bianche", "Ludovico Einaudi", seconds = 357)
        val bare = hit("Nuvole Bianche", "Ludovico Einaudi", 357.0, synced = null, plain = null, instrumental = true)
        val words = hit("Nuvole Bianche", "Ludovico Einaudi", 345.0, synced = null)
        assertTrue(LyricsMatch.best(q, listOf(words, bare))!!.instrumental)
    }

    @Test
    fun `instrumental only for the very song`() {
        val q = q("Despacito - Remix", "Luis Fonsi", seconds = 229)
        assertNull(LyricsMatch.fit(q, hit("Despacito", "Luis Fonsi", 229.0, synced = null, plain = null, instrumental = true)))
    }

    @Test
    fun `an entry without words or the instrumental mark is no answer`() {
        assertNull(LyricsMatch.fit(q("Blinding Lights", "The Weeknd", seconds = 200), hit("Blinding Lights", "The Weeknd", 200.0, synced = null, plain = null)))
    }
}
