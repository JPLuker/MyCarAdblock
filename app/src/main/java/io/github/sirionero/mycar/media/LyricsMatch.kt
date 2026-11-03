package io.github.sirionero.mycar.media

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Which lyrics on LRCLIB belong to the song playing. No Android here: LyricsMatchTest runs it on
 * the JVM.
 *
 * Music apps write a title for people ("Bohemian Rhapsody - Remastered 2011", "Levitating (feat.
 * DaBaby)", "Let It Go - From "Frozen"/Soundtrack Version", a video's "Artist - Title (Official
 * Video)"), while LRCLIB keeps the song's own name and its /api/get compares names exactly (lower
 * case, no accents, punctuation as spaces: its prepare_input) and the album too when one is given.
 * So the title is cleaned before asking, and every answer is checked here before it's shown: the
 * lyrics of another song are worse than none.
 */
object LyricsMatch {

    /** Seconds of difference within which a recording is the same one, and its times fit. LRCLIB's own /api/get allows 2. */
    const val SAME_LENGTH_S = 3.0

    /** Longer than this it's a podcast, an audiobook or a mix: no lyrics to look for. */
    const val LONGEST_SONG_S = 20 * 60

    /** Requests to LRCLIB for one song, at most (a busy server's one retry aside). */
    const val MAX_ASKS = 4

    // --- names --------------------------------------------------------------------------------

    /**
     * A name as LRCLIB compares it: lower case, no accents, no apostrophes, anything else that isn't
     * a letter or a digit a space. "Perche'" and "Perché" are both "perche", "Tití" is "titi".
     */
    fun key(text: String): String {
        val out = StringBuilder(text.length)
        for (c in Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)) {
            when {
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> Unit
                c in APOSTROPHES -> Unit
                c == 'ø' -> out.append('o')
                c == 'æ' -> out.append("ae")
                c == 'œ' -> out.append("oe")
                c == 'ß' -> out.append("ss")
                c == 'ł' -> out.append('l')
                c == 'đ' -> out.append('d')
                c.isLetterOrDigit() -> out.append(c)
                else -> out.append(' ')
            }
        }
        return out.toString().trim().replace(SPACES, " ")
    }

    /** A title without what the app added to it, and what that said. */
    class Title(
        /** Without the decorations (remaster, radio edit, official video, from "Film", feat.): what to ask first. */
        val strict: String,
        /** Also without any other (...), [...] or " - " tail: live, acoustic, remix, a second name. */
        val loose: String,
        /** Artists named in the title: "(feat. DaBaby)", "(with Dua Lipa)". */
        val featured: List<String>,
        /** A live or acoustic take: the same words, other times. */
        val live: Boolean,
        /** An instrumental or karaoke version: its own entry, never the sung song's words. */
        val instrumental: Boolean,
    )

    fun cleanTitle(raw: String): Title {
        var rest = raw.trim()
        // A video's "Title | Official Video": what follows the bar, when it's a decoration, goes.
        PIPE.find(rest)?.let { bar ->
            if (bar.range.first > 0 && decorationAfterDash(rest.substring(bar.range.last + 1))) rest = rest.substring(0, bar.range.first).trimEnd()
        }
        val featured = ArrayList<String>()
        val kept = ArrayDeque<String>()
        var live = false
        var instrumental = false
        while (true) {
            val tail = lastTail(rest) ?: break
            val inner = tail.inner.trim()
            when (if (tail.dash && !decorationAfterDash(inner)) Kind.OTHER else kindOf(inner)) {
                Kind.FEAT -> featured += splitArtists(FEAT_START.replaceFirst(inner, ""))
                Kind.DROP -> Unit
                Kind.LIVE -> live = true
                Kind.INSTRUMENTAL -> { instrumental = true; kept.addFirst(tail.text) }
                Kind.VERSION -> kept.addFirst(tail.text)
                // A dash before something that isn't a version ("Artist - Title" of a radio) is the title's own.
                Kind.OTHER -> if (tail.dash) break else kept.addFirst(tail.text)
            }
            rest = rest.substring(0, tail.start).trimEnd()
        }
        INLINE_FEAT.find(rest)?.let {
            featured += splitArtists(it.groupValues[1])
            rest = rest.substring(0, it.range.first).trimEnd()
        }
        val strict = (listOf(rest) + kept).joinToString(" ").trim()
        return Title(strict.ifEmpty { raw.trim() }, rest.ifEmpty { raw.trim() }, featured, live, instrumental)
    }

    /** The artists in an artist field or a feat.: "A, B", "A & B", "A x B", "A feat. B", "A / B". */
    fun splitArtists(field: String): List<String> =
        field.split(ARTIST_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }

    /** The artist field as the app writes it, without a video channel's " - Topic" or "VEVO". */
    fun artistOf(field: String): String {
        var a = TOPIC.replace(field.trim(), "")
        val vevo = VEVO.find(a)
        if (vevo != null) a = CAMEL.replace(a.substring(0, vevo.range.first), "$1 $2")
        return a.trim()
    }

    // --- the song asked for ---------------------------------------------------------------------

    class SongQuery(
        /** The cleaned title, what LRCLIB most likely has. */
        val title: String,
        /** The title without any version tail. */
        val loose: String,
        /** The artist field as given (cleaned of channel names): LRCLIB's /api/get wants it whole. */
        val artist: String,
        /** Its first artist: a phrase LRCLIB's search finds inside "A, B" and "A & B". */
        val main: String,
        /** Every artist, the title's featured ones too, as [key]s. */
        val names: Set<String>,
        val album: String,
        /** Whole seconds; 0 when the app doesn't say. */
        val durationSec: Int,
        val live: Boolean,
        val instrumental: Boolean,
        /** "Artist - Title" read off the title, for a radio's stream title or a video: (artist, title). */
        val alt: Pair<String, String>?,
    )

    /** What to look for, or null for nothing: no title, or too long to be a song. */
    fun query(title: String, artist: String, album: String, durationMs: Long): SongQuery? {
        if (title.isBlank()) return null
        val durationSec = if (durationMs > 0) ((durationMs + 500) / 1000).toInt() else 0
        if (durationSec > LONGEST_SONG_S) return null
        var raw = title.trim()
        val cleanArtist = artistOf(artist)
        var alt: Pair<String, String>? = null
        DASH.find(raw)?.let { dash ->
            val left = raw.substring(0, dash.range.first).trim()
            val right = raw.substring(dash.range.last + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) {
                val leftNames = splitArtists(left).map(::key).toSet()
                val fieldNames = splitArtists(cleanArtist).map(::key).toSet()
                if (cleanArtist.isNotEmpty() && (key(left) == key(cleanArtist) || leftNames.any { it in fieldNames })) {
                    // A video's "Artist - Title (Official Video)": the artist is the app's already.
                    raw = right
                } else if (!decorationAfterDash(right)) {
                    // A radio's "ARTIST - TITLE", the station as artist: worth one more question.
                    alt = left to cleanTitle(right).strict
                }
            }
        }
        val clean = cleanTitle(raw)
        val artists = splitArtists(cleanArtist)
        val main = artists.firstOrNull() ?: cleanArtist
        val names = (artists + clean.featured).map(::key).filter { it.isNotEmpty() }.toSet()
        return SongQuery(
            title = clean.strict, loose = clean.loose, artist = cleanArtist, main = main, names = names,
            album = album.trim(), durationSec = durationSec, live = clean.live, instrumental = clean.instrumental,
            alt = alt,
        )
    }

    // --- the questions to LRCLIB ------------------------------------------------------------------

    sealed interface Ask {
        /** /api/get: exact names (no album: the apps' album names differ most), the length within 2 s. */
        data class Get(val track: String, val artist: String, val durationSec: Int) : Ask

        /** /api/search with track_name and artist_name: each a phrase inside LRCLIB's name. */
        data class Search(val track: String, val artist: String) : Ask

        /** /api/search?q=: every word somewhere in title, artist or album. Only if nothing else answered. */
        data class Words(val q: String) : Ask
    }

    /**
     * At most [MAX_ASKS], in order, no question twice: the exact one, then LRCLIB's search by title
     * and main artist, by the loose title, and its search by words last. A radio's "ARTIST - TITLE"
     * is asked first by the artist it names: the station's name would find nothing. No artist at all
     * (an app that gives only the title): nothing, as [fit] couldn't take any entry.
     */
    fun plan(q: SongQuery): List<Ask> {
        if (q.artist.isEmpty() && q.alt == null && q.names.isEmpty()) return emptyList()
        val asks = LinkedHashSet<Ask>()
        q.alt?.let { (artist, title) -> asks += Ask.Search(title, artist) }
        if (q.artist.isNotEmpty()) asks += Ask.Get(q.title, q.artist, q.durationSec)
        if (q.main.isNotEmpty()) asks += Ask.Search(q.title, q.main)
        if (q.main.isNotEmpty() && key(q.loose) != key(q.title) && !q.instrumental) asks += Ask.Search(q.loose, q.main)
        val words = q.alt?.let { (artist, title) -> "$artist $title" } ?: "${q.main} ${q.loose}"
        val plan = asks.take(MAX_ASKS - 1).toMutableList()
        plan += Ask.Words(words.trim())
        return plan
    }

    // --- the answers -----------------------------------------------------------------------------

    /** One of LRCLIB's entries, as /api/get and /api/search give them. */
    class Hit(
        val id: Long,
        val track: String,
        val artist: String,
        val album: String,
        /** Seconds; 0 when unknown. */
        val durationSec: Double,
        val instrumental: Boolean,
        val synced: String?,
        val plain: String?,
    ) {
        val hasWords: Boolean get() = !synced.isNullOrBlank() || !plain.isNullOrBlank()
    }

    /** An entry that is this song. [timed]: its times can be followed; otherwise its words only. */
    class Fit(val hit: Hit, val timed: Boolean, val sameLength: Boolean, val score: Int) {
        val instrumental: Boolean get() = hit.instrumental && !hit.hasWords
    }

    /** Whether [hit] is the song asked for, and how well; null when it's another song, or can't be told apart from one. */
    fun fit(q: SongQuery, hit: Hit): Fit? {
        if (!hit.hasWords && !hit.instrumental) return null
        val theirs = cleanTitle(hit.track)
        val sameTitle = key(theirs.strict) == key(q.title) ||
            q.alt?.let { key(theirs.strict) == key(it.second) } == true
        val sameLoose = sameTitle || near(key(theirs.loose), key(q.loose))
        if (!sameLoose) return null
        val artist = artistFit(q, hit.artist) ?: return null
        val d = if (q.durationSec > 0 && hit.durationSec > 0) abs(hit.durationSec - q.durationSec) else null
        val sameLength = d != null && d <= SAME_LENGTH_S
        // A word in many names ("Mahmood" of "Mahmood & BLANCO", "Anna" of "Anna Tatangelo") or a
        // title like "Intro" is only this song at this song's length.
        if ((artist == ArtistFit.PART || generic(q.loose)) && !sameLength) return null
        if (hit.instrumental && !hit.hasWords) {
            // "Instrumental" only for the very song: never from a loose name or another length.
            return if (sameTitle && artist == ArtistFit.SAME && (sameLength || d == null)) Fit(hit, false, sameLength, if (sameLength) 1000 else 0) else null
        }
        // A version of the song by its loose name (a remix, a second title) can have other words:
        // only at the same length. A live or acoustic take keeps the words, not the times. A live take is
        // already the same title (strict leaves "Live" out): one by the loose name only is a sister song's
        // (a Reprise, a Part 2, a version in another language), never this one's at another length.
        val liveTake = q.live || theirs.live
        if (!sameTitle && !sameLength) return null
        if (q.instrumental != theirs.instrumental && !sameLength) return null
        val farther = d != null && !sameLength
        if (farther && !liveTake && d > max(15.0, q.durationSec * 0.25)) return null
        // Times only for the same recording: without the song's length (a radio's stream) they can't be trusted.
        val timed = !hit.synced.isNullOrBlank() && sameLength
        var score = 0
        if (sameLength) score += 1000 - (d * 50).toInt()
        if (timed) score += 300
        if (artist == ArtistFit.SAME) score += 100
        if (sameTitle) score += 50
        if (q.album.isNotEmpty() && key(cleanTitle(hit.album).loose) == key(cleanTitle(q.album).loose)) score += 20
        return Fit(hit, timed, sameLength, score)
    }

    /**
     * The best of [hits] for [q], the highest score; words before an "instrumental", unless only the
     * instrumental entry has this very length.
     */
    fun best(q: SongQuery, hits: List<Hit>): Fit? {
        val fits = hits.mapNotNull { fit(q, it) }
        val words = fits.filter { !it.instrumental }.maxByOrNull { it.score }
        val bare = fits.filter { it.instrumental }.maxByOrNull { it.score }
        return if (words == null || (bare != null && bare.sameLength && !words.sameLength)) bare else words
    }

    /** The title itself says there are no words ("(Instrumental)", not "(Karaoke Version)"): nothing to ask. */
    fun instrumentalByTitle(q: SongQuery): Boolean = q.instrumental && !KARAOKE.containsMatchIn(q.title)

    /** /api/search?q= only when no other question found any entry at all: it finds the same ones, less precisely. */
    fun worthAsking(ask: Ask, entriesSoFar: Int): Boolean = ask !is Ask.Words || entriesSoFar == 0

    /** No need to ask further: lyrics that follow the song, or the song itself marked instrumental. */
    fun settled(q: SongQuery, fit: Fit): Boolean =
        fit.timed || fit.instrumental || (q.durationSec == 0 && fit.hit.hasWords && fit.score >= 150)

    private enum class ArtistFit { SAME, PART }

    private fun artistFit(q: SongQuery, field: String): ArtistFit? {
        val theirs = key(artistOf(field))
        if (theirs.isEmpty()) return null
        val altArtist = q.alt?.first?.let(::key)
        val theirNames = names(field)
        val theirFirst = splitArtists(artistOf(field)).firstOrNull()?.let(::key)
        // The song's own artist: the whole field (LRCLIB's "A & B" and the app's "A, B" are one key), the
        // same names in another order, the same first artist, or the artist a radio's title names.
        if (theirs == key(q.artist) || theirs == altArtist || theirNames == names(q.artist) ||
            (theirFirst != null && (theirFirst == key(q.main) || theirFirst == altArtist))
        ) return ArtistFit.SAME
        // Only through someone else on the bill (a featured artist, the second of "A & B", a band name cut
        // at its comma) or a name inside the other: this song only at this song's length. "Stay" by Justin
        // Bieber is another song than "Stay (feat. Justin Bieber)" by The Kid LAROI.
        val myNames = q.names + names(q.artist) + listOfNotNull(altArtist)
        if (theirNames.any { it in myNames }) return ArtistFit.PART
        if ((myNames + key(q.artist)).any { it.length >= 4 && (containsWords(theirs, it) || containsWords(it, theirs)) }) return ArtistFit.PART
        return null
    }

    /** Artist names for comparing, also split on "and", "e", "y", "et", "und": both sides alike. */
    private fun names(field: String): Set<String> =
        splitArtists(artistOf(field)).flatMap { it.split(AND_WORDS) }.map(::key).filter { it.isNotEmpty() }.toSet()

    private fun containsWords(text: String, words: String) = " $text ".contains(" $words ")

    /** The same title but for one typo, in a title long enough for that to mean nothing. */
    private fun near(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.isEmpty() || b.isEmpty() || minOf(a.length, b.length) < 10 || abs(a.length - b.length) > 1) return false
        return editsAtMostOne(a, b)
    }

    private fun editsAtMostOne(a: String, b: String): Boolean {
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) { i++; j++; continue }
            if (++edits > 1) return false
            when {
                a.length > b.length -> i++
                a.length < b.length -> j++
                else -> { i++; j++ }
            }
        }
        return edits + (a.length - i) + (b.length - j) <= 1
    }

    private fun generic(title: String) = key(title).let { it in GENERIC || TRACK_N.matches(it) }

    // --- the tails of a title -------------------------------------------------------------------

    private enum class Kind { FEAT, DROP, LIVE, INSTRUMENTAL, VERSION, OTHER }

    private class Tail(val start: Int, val inner: String, val text: String, val dash: Boolean)

    /** The last (...) or [...] at the end of [t], or what follows its last " - ". */
    private fun lastTail(t: String): Tail? {
        if (t.endsWith(')') || t.endsWith(']')) {
            val close = t.last()
            val open = if (close == ')') '(' else '['
            var depth = 0
            for (i in t.indices.reversed()) {
                if (t[i] == close) depth++
                if (t[i] == open && --depth == 0) {
                    return if (i == 0) null else Tail(i, t.substring(i + 1, t.length - 1), t.substring(i), dash = false)
                }
            }
            return null
        }
        val dash = DASH.findAll(t).lastOrNull() ?: return null
        if (dash.range.first == 0) return null
        return Tail(dash.range.first, t.substring(dash.range.last + 1), t.substring(dash.range.first).trim(), dash = true)
    }

    private fun kindOf(inner: String): Kind = when {
        FEAT_START.containsMatchIn(inner) -> Kind.FEAT
        INSTRUMENTAL_WORDS.containsMatchIn(inner) -> Kind.INSTRUMENTAL
        DROP_WORDS.containsMatchIn(inner) || DROP_WHOLE.matches(inner.trim()) -> Kind.DROP
        LIVE_WORDS.containsMatchIn(inner) -> Kind.LIVE
        VERSION_WORDS.containsMatchIn(inner) -> Kind.VERSION
        else -> Kind.OTHER
    }

    private val IC = setOf(RegexOption.IGNORE_CASE)
    private val PIPE = Regex("""\s[|｜]\s""")
    private val KARAOKE = Regex("""karaoke""", IC)
    private val FROM_FILM = Regex("""^\s*(?:from\s+(?:["“”«]|the\s)|dal\s+film|tratto\s+da|bande\s+originale|b\.?s\.?o\.?\b)""", IC)

    /**
     * After " - " (or " | ") a decoration is short and says so at its start or end: "Remastered 2011",
     * "2013 Remaster", "Radio Edit", "Live at Wembley", "From "Frozen"", "Official Video". After a radio's
     * "ARTIST - ", "Video Killed the Radio Star", "Live and Let Die" or "Mix Tape" are the title.
     */
    internal fun decorationAfterDash(tail: String): Boolean {
        if (FROM_FILM.containsMatchIn(tail)) return true
        val words = key(tail).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 5) return false
        val first = words.first()
        val last = words.last()
        if (words.joinToString(" ") in LIVE_PHRASES) return true
        // "Live", "Live at Wembley", "Live Aid", "Live 1986": not "Live and Let Die".
        if (first == "live") return words.size == 1 || words[1] in LIVE_NEXT || words[1].all(Char::isDigit)
        // "Official Video", "Video Ufficiale", "Lyrics", "HD": every word a decoration.
        if (words.all { it in PLAIN_DECOR }) return true
        // "2011 Remaster" or "Sanremo 2024": a year after decorations.
        if (last.length == 4 && last.all(Char::isDigit) && words.size > 1) return words.dropLast(1).all { it in PLAIN_DECOR || it in FIRST_DECOR || it in LAST_DECOR || it == "sanremo" }
        return first in FIRST_DECOR || last in LAST_DECOR
    }
    private val LIVE_PHRASES = setOf("dal vivo", "en vivo", "en directo", "ao vivo", "en concierto", "in concerto")
    private val LIVE_NEXT = setOf("at", "from", "in", "on", "aid", "version", "session", "sessions", "acoustic", "edit", "dal", "da", "en", "au", "ao")
    private val FIRST_DECOR = setOf(
        "remaster", "remastered", "remasterizado", "remasterise", "remasterisee", "rimasterizzato", "rimasterizzata",
        "radio", "single", "album", "extended", "original", "bonus", "deluxe", "unplugged", "acoustic", "sped", "slowed",
    )
    private val LAST_DECOR = setOf(
        "remaster", "remastered", "rimasterizzato", "rimasterizzata", "edit", "mix", "remix", "version", "versione",
        "acoustic", "acustico", "acustica", "unplugged", "instrumental", "strumentale", "karaoke", "explicit",
        "demo", "soundtrack", "ost",
    )
    private val PLAIN_DECOR = setOf(
        "official", "music", "video", "audio", "lyric", "lyrics", "ufficiale", "videoclip", "visualizer", "visualiser",
        "hd", "4k", "hq", "testo", "oficial", "mv", "clean", "explicit", "mono", "stereo",
    )
    private val SPACES = Regex("\\s+")
    private val APOSTROPHES = setOf('\'', '’', 'ʼ', '`', '´', '‘')

    /** " - ", " – ", " — " between words. */
    private val DASH = Regex("""\s[-–—]\s""")
    private val FEAT_START = Regex("""^\s*(?:(?:feat\.?|ft\.?|featuring)\s+|(?:with|con)\s+(?=(?-i:\p{Lu})))""", IC)
    private val INLINE_FEAT = Regex("""\s(?:feat\.|ft\.|featuring)\s+(.+)$""", IC)
    private val INSTRUMENTAL_WORDS = Regex("""\b(?:instrumental|karaoke|strumentale|instrumentale?)\b""", IC)

    /** Words that never change what's sung: how the recording was released, filmed or sold. */
    private val DROP_WORDS = Regex(
        """remaster|rimasterizzat|remasteris|remasteriz|radio\s+edit|single\s+version|album\s+version|""" +
            """original\s+(?:version|mix)|\bmono\b|\bstereo\b|official|ufficiale|oficial|lyric\s+video|""" +
            """\blyrics?\b|\btesto\b|visuali[sz]er|videoclip|\bvideo\b|\baudio\b|\bexplicit\b|\bclean\b|""" +
            """bonus\s+track|\bdeluxe\b|from\s+["“”«]|from\s+the\s+|\bdal\s+film\b|tratto\s+da|\bost\b|""" +
            """soundtrack|film\s+version|movie\s+version|\bb\.?s\.?o\b|bande\s+originale""",
        IC,
    )
    private val DROP_WHOLE = Regex("""(?:\d{4}|hd|hq|4k|mv|m/v|sanremo\s+\d{4})""", IC)
    private val LIVE_WORDS = Regex("""\blive\b|dal\s+vivo|en\s+vivo|en\s+directo|unplugged|acoustic|acustic|acústic|akustik""", IC)
    private val VERSION_WORDS = Regex("""remix|\bmix\b|\bedit\b|version|versione|rework|\bcover\b|\bdemo\b|sped\s+up|slowed|reprise""", IC)

    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|;|\s/\s|\s&\s|\s\+\s|(?-i:\sx\s)|\s×\s|\sfeat\.?\s|\sft\.?\s|\sfeaturing\s|\swith\s|\svs\.?\s)\s*""",
        IC,
    )
    private val AND_WORDS = Regex("""\s(?:and|e|y|et|und)\s""", IC)
    private val TOPIC = Regex("""\s+-\s+Topic$""", IC)
    private val VEVO = Regex("""\s*VEVO$""")
    private val CAMEL = Regex("""(\p{Ll})(\p{Lu})""")
    private val GENERIC = setOf(
        "intro", "outro", "interlude", "skit", "untitled", "prelude", "reprise", "bonus track", "theme",
        "senza titolo", "intermezzo", "instrumental", "hidden track", "jingle",
    )
    private val TRACK_N = Regex("""(?:track|traccia)\s*\d+""")
}
