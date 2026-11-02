package io.github.sirionero.mycar

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import io.github.sirionero.mycar.media.Lyrics
import io.github.sirionero.mycar.media.LyricsAnswer
import io.github.sirionero.mycar.media.LyricsMatch
import io.github.sirionero.mycar.media.SongInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Debug-only: songs as the music apps write them, through the lyrics lookup of 0.6.8 (kept below,
 * [LyricsLegacy]) and the new one (media/Lyrics, LRCLIB only, no cache), against the real lrclib.net,
 * which the cloud session can't reach and the Car preview workflow's emulator can. Writes a
 * "lyrics check: …" line per song, a summary, then "lyrics check: done". The song names are in the
 * log: a debug build's.
 *
 *   adb shell am start -n io.github.sirionero.mycar.dev/io.github.sirionero.mycar.LyricsCheckActivity
 *
 * Shot "lyrics-check" of .github/car-preview.sh, only when asked for by name: some 250 requests to a
 * free service, a few minutes.
 */
class LyricsCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyCarLog.init(this)
        thread(name = "lyrics-check") {
            runCatching { check() }.onFailure { MyCarLog.e("lyrics check: stopped", it) }
            MyCarLog.i("lyrics check: done")
        }
    }

    private fun check() {
        var oldRight = 0; var oldWrong = 0; var oldOffTime = 0; var newRight = 0; var newWrong = 0; var newErrors = 0
        var requests = 0; var most = 0; var timed = 0
        val songs = LyricsCases.all
        for ((n, c) in songs.withIndex()) {
            val old = runCatching { LyricsLegacy.find(c.title, c.artist, c.album, c.durationMs) }
            val started = SystemClock.elapsedRealtime()
            val new = runCatching { Lyrics.traceFresh(SongInfo(c.title, c.artist, c.album, c.durationMs)) }
            val ms = SystemClock.elapsedRealtime() - started

            val oldHit = old.getOrNull()
            // 0.6.8 showed an entry's times whatever its length: a live take's or another edit's times run off.
            val offTime = oldHit != null && oldHit.timed && c.durationMs > 0 &&
                kotlin.math.abs(oldHit.durationSec - c.durationMs / 1000.0) > LyricsMatch.SAME_LENGTH_S
            val oldSays = old.fold({ hit -> c.judge(hit?.track, hit?.artist, instrumental = false) }, { "error" }) +
                (if (offTime) "/off-time" else "")
            if (offTime) oldOffTime++
            val trace = new.getOrNull()
            val newSays = new.fold(
                { t ->
                    val hit = t?.fit?.hit
                    c.judge(hit?.track, hit?.artist, instrumental = t?.answer == LyricsAnswer.Instrumental)
                },
                { "error" },
            )
            if (oldSays.startsWith("ok")) oldRight++
            if (oldSays.startsWith("WRONG")) oldWrong++
            if (newSays.startsWith("ok")) newRight++
            if (newSays.startsWith("WRONG")) newWrong++
            if (newSays == "error") newErrors++
            if (trace?.fit?.timed == true) timed++
            requests += trace?.requests ?: 0
            most = maxOf(most, trace?.requests ?: 0)

            val how = trace?.let { t ->
                val kind = when {
                    t.answer == LyricsAnswer.Instrumental -> "instrumental"
                    t.fit?.timed == true -> "timed"
                    t.fit != null -> "words only"
                    else -> "none"
                }
                "question ${t.askIndex + 1}, ${t.requests} requests, $ms ms, $kind" +
                    (t.fit?.hit?.let { h -> " → \"${h.track}\" · ${h.artist} · ${h.durationSec.toInt()} s" } ?: "")
            } ?: new.exceptionOrNull()?.message.orEmpty()
            MyCarLog.i("lyrics check: ${n + 1} old=$oldSays new=$newSays ($how) | ${c.title} · ${c.artist} · ${c.durationMs / 1000} s")
            // A miss: what LRCLIB had, to correct the song's length here or to see what the match refused.
            if (!newSays.startsWith("ok") && trace != null) {
                trace.seen.distinctBy { it.id }.take(4).forEach { h ->
                    MyCarLog.i("lyrics check:     seen \"${h.track}\" · ${h.artist} · ${h.durationSec.toInt()} s" +
                        (if (h.instrumental) " · instrumental" else "") + (if (h.synced.isNullOrBlank()) "" else " · timed"))
                }
            }
            Thread.sleep(300)  // gently: LRCLIB is free
        }
        val total = songs.size
        MyCarLog.i(
            "lyrics check: old $oldRight/$total right, $oldWrong wrong, $oldOffTime timed off · new $newRight/$total right, $newWrong wrong, " +
                "$newErrors errors, $timed timed, ${"%.1f".format(requests / total.toFloat())} requests a song (at most $most)",
        )
    }
}

/** One song as an app writes it, and what the lyrics must be: [title] and [artist] words of LRCLIB's entry, or no lyrics. */
private class LyricsCase(
    val title: String, val artist: String, val album: String, val durationMs: Long,
    val expect: Expect,
) {
    sealed interface Expect
    class Song(val title: String, val artist: String) : Expect
    data object NoLyrics : Expect
    data object Instrumental : Expect

    /** "ok…", "MISS" (nothing where there should be), "WRONG…" (another song, or words for an instrumental). */
    fun judge(track: String?, artist: String?, instrumental: Boolean): String = when (val e = expect) {
        is Song -> when {
            instrumental -> "WRONG-instrumental"
            track == null -> "MISS"
            LyricsMatch.key(track).contains(LyricsMatch.key(e.title)) &&
                LyricsMatch.key(artist.orEmpty()).contains(LyricsMatch.key(e.artist)) -> "ok"
            else -> "WRONG"
        }
        NoLyrics -> if (track == null && !instrumental) "ok-none" else "WRONG"
        Instrumental -> when {
            instrumental -> "ok-instrumental"
            track == null -> "MISS"
            else -> "WRONG-words"
        }
    }
}

/**
 * The songs: Italian and international hits as Spotify, YouTube Music, YouTube and a radio's stream
 * write them. Lengths are Spotify's where known; the first run's "seen" lines correct the others.
 */
private object LyricsCases {
    private fun song(t: String, a: String, al: String, ms: Long, title: String, artist: String) =
        LyricsCase(t, a, al, ms, LyricsCase.Song(title, artist))

    val all = listOf(
        // Remasters, soundtracks, feat., with, several artists (Spotify).
        song("Bohemian Rhapsody - Remastered 2011", "Queen", "A Night At The Opera (2011 Remaster)", 354_947, "bohemian rhapsody", "queen"),
        song("Here Comes The Sun - Remastered 2009", "The Beatles", "Abbey Road (Remastered)", 185_733, "here comes the sun", "beatles"),
        song("Let It Be - Remastered 2009", "The Beatles", "Let It Be (Remastered)", 243_026, "let it be", "beatles"),
        song("Hotel California - 2013 Remaster", "Eagles", "Hotel California (2013 Remaster)", 391_376, "hotel california", "eagles"),
        song("Wonderwall - Remastered", "Oasis", "(What's The Story) Morning Glory? (Remastered)", 258_773, "wonderwall", "oasis"),
        song("Shape of You", "Ed Sheeran", "÷ (Deluxe)", 233_712, "shape of you", "ed sheeran"),
        song("Blinding Lights", "The Weeknd", "After Hours", 200_040, "blinding lights", "weeknd"),
        song("Levitating (feat. DaBaby)", "Dua Lipa", "Future Nostalgia", 203_064, "levitating", "dua lipa"),
        song("One Kiss (with Dua Lipa)", "Calvin Harris, Dua Lipa", "One Kiss", 214_846, "one kiss", "calvin harris"),
        song("Shallow", "Lady Gaga, Bradley Cooper", "A Star Is Born Soundtrack", 215_733, "shallow", "lady gaga"),
        song("Let It Go - From \"Frozen\"/Soundtrack Version", "Idina Menzel", "Frozen (Original Motion Picture Soundtrack)", 223_840, "let it go", "idina menzel"),
        song("Lose Yourself - From \"8 Mile\" Soundtrack", "Eminem", "8 Mile", 326_466, "lose yourself", "eminem"),
        song("Despacito - Remix", "Luis Fonsi, Daddy Yankee, Justin Bieber", "Despacito (Remix)", 228_827, "despacito", "fonsi"),
        song("Viva La Vida", "Coldplay", "Viva La Vida or Death and All His Friends", 242_373, "viva la vida", "coldplay"),
        song("Rolling in the Deep", "Adele", "21", 228_093, "rolling in the deep", "adele"),
        song("Smells Like Teen Spirit", "Nirvana", "Nevermind (Remastered)", 301_920, "smells like teen spirit", "nirvana"),
        song("Tití Me Preguntó", "Bad Bunny", "Un Verano Sin Ti", 243_717, "titi me pregunto", "bad bunny"),
        song("Alors on danse - Radio Edit", "Stromae", "Cheese", 206_067, "alors on danse", "stromae"),
        song("Du hast", "Rammstein", "Sehnsucht", 234_627, "du hast", "rammstein"),
        song("Gangnam Style (강남스타일)", "PSY", "PSY 6甲 Part 1", 219_493, "gangnam style", "psy"),
        song("(I Can't Get No) Satisfaction - Mono Version", "The Rolling Stones", "Out Of Our Heads", 222_813, "satisfaction", "rolling stones"),
        song("Shakira: Bzrp Music Sessions, Vol. 53", "Bizarrap, Shakira", "Shakira: Bzrp Music Sessions, Vol. 53", 218_000, "bzrp music sessions", "bizarrap"),
        song("Bohemian Rhapsody - Live Aid", "Queen", "Live Aid", 151_000, "bohemian rhapsody", "queen"),
        // Italian (lengths to be corrected by the first run where needed).
        song("ZITTI E BUONI", "Måneskin", "Teatro d'ira - Vol. I", 194_000, "zitti e buoni", "maneskin"),
        song("Beggin'", "Måneskin", "Chosen", 211_560, "beggin", "maneskin"),
        song("Brividi", "Mahmood, BLANCO", "Brividi", 240_000, "brividi", "mahmood"),
        song("La solitudine", "Laura Pausini", "Laura Pausini", 238_000, "la solitudine", "laura pausini"),
        song("Albachiara - Remastered", "Vasco Rossi", "Non siamo mica gli americani! (Remastered)", 256_000, "albachiara", "vasco rossi"),
        song("Emozioni", "Lucio Battisti", "Emozioni", 293_000, "emozioni", "battisti"),
        song("Nel blu dipinto di blu (Volare)", "Domenico Modugno", "Nel blu dipinto di blu", 216_000, "nel blu dipinto di blu", "modugno"),
        song("Più bella cosa", "Eros Ramazzotti", "Dove c'è musica", 267_000, "piu bella cosa", "ramazzotti"),
        song("I p' me, tu p' te", "Geolier", "Dio lo sa", 186_000, "i p me tu p te", "geolier"),
        song("Due vite", "Marco Mengoni", "Materia (Prisma)", 232_000, "due vite", "mengoni"),
        song("La noia", "Angelina Mango", "voglia di vivere", 171_000, "la noia", "angelina mango"),
        song("Con te partirò", "Andrea Bocelli", "Bocelli (Remastered)", 249_000, "con te partiro", "bocelli"),
        // Videos (YouTube, YouTube Music): the artist in the title, a channel as artist.
        song("Rick Astley - Never Gonna Give You Up (Official Music Video)", "Rick Astley", "", 213_000, "never gonna give you up", "rick astley"),
        song("Måneskin - Beggin' (Official Video)", "MåneskinVEVO", "", 212_000, "beggin", "maneskin"),
        song("Luis Fonsi - Despacito ft. Daddy Yankee", "LuisFonsiVEVO", "", 281_000, "despacito", "fonsi"),
        // A radio's stream title (Xonos), no length.
        song("QUEEN - DON'T STOP ME NOW", "Radio Deejay", "", 0, "dont stop me now", "queen"),
        song("VASCO ROSSI - VITA SPERICOLATA", "RTL 102.5", "", 0, "vita spericolata", "vasco"),
        // Instrumentals: say so, never words.
        LyricsCase("Nuvole Bianche", "Ludovico Einaudi", "Una Mattina", 357_000, LyricsCase.Instrumental),
        LyricsCase("Sandstorm - Radio Edit", "Darude", "Before the Storm", 225_000, LyricsCase.Instrumental),
        LyricsCase("Intro", "The xx", "xx", 127_000, LyricsCase.Instrumental),
        // Never another song's lyrics: famous titles by unknown artists, at the famous song's length too.
        LyricsCase("Yesterday", "Gruppo Prova Locale", "Demo", 125_000, LyricsCase.NoLyrics),
        LyricsCase("Imagine", "Coro Parrocchiale San Marco", "Concerto di Natale", 183_000, LyricsCase.NoLyrics),
        LyricsCase("Hello", "Band di Prova", "", 295_000, LyricsCase.NoLyrics),
        LyricsCase("Zzqx Nessuna Canzone", "Nessuno Conosciuto", "", 200_000, LyricsCase.NoLyrics),
    )
}

/** The lookup of 0.6.8, kept for comparison: /api/get with the album, then the first search entry with any lyrics, unchecked. */
private object LyricsLegacy {

    class Hit(val track: String, val artist: String, val durationSec: Double, val timed: Boolean)

    fun find(title: String, artist: String, album: String, durationMs: Long): Hit? {
        if (title.isBlank()) return null
        return exact(title, artist, album, durationMs) ?: search(title, artist)
    }

    private fun exact(title: String, artist: String, album: String, durationMs: Long): Hit? {
        if (artist.isBlank()) return null
        val query = buildString {
            append("track_name=").append(enc(title)).append("&artist_name=").append(enc(artist))
            if (album.isNotBlank()) append("&album_name=").append(enc(album))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000)
        }
        return get("https://lrclib.net/api/get?$query")?.let { JSONObject(it) }?.takeIf(::hasLyrics)?.let(::hitOf)
    }

    private fun search(title: String, artist: String): Hit? {
        val body = get("https://lrclib.net/api/search?q=" + enc("$artist $title".trim())) ?: return null
        val hits = JSONArray(body)
        for (i in 0 until hits.length()) hits.getJSONObject(i).takeIf(::hasLyrics)?.let { return hitOf(it) }
        return null
    }

    private fun hasLyrics(j: JSONObject) =
        listOf("syncedLyrics", "plainLyrics").any { !j.isNull(it) && j.optString(it).isNotBlank() }

    private fun hitOf(j: JSONObject) = Hit(
        j.optString("trackName"), j.optString("artistName"), j.optDouble("duration", 0.0),
        timed = !j.isNull("syncedLyrics") && j.optString("syncedLyrics").isNotBlank(),
    )

    private fun get(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "myCar (car media host)")
        }
        return try {
            if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
