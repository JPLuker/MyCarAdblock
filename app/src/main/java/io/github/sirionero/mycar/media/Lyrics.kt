package io.github.sirionero.mycar.media

import android.content.Context
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.media.LyricsMatch.Ask
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.data.MaException
import io.github.sirionero.mycar.xonos.data.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

class LyricLine(val timeMs: Long, val text: String)

/** [synced] means every line carries a time, so the current one can be followed along. */
class LyricsResult(val lines: List<LyricLine>, val synced: Boolean)

/** The song as the car's player has it: what a lookup needs. */
class SongInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Music Assistant's address of the track while Xonos inside myCar plays it; "" otherwise. */
    val maUri: String = "",
) {
    /** One song, one answer: the cache's key. */
    val key: String = listOf(
        LyricsMatch.key(title), LyricsMatch.key(artist), LyricsMatch.key(album),
        ((durationMs.coerceAtLeast(0) + 500) / 1000).toString(), maUri,
    ).joinToString("|")
}

/** What the lyrics view shows for a song. */
sealed interface LyricsAnswer {
    class Found(val result: LyricsResult, val fromMusicAssistant: Boolean = false) : LyricsAnswer

    /** A song without words: its title says so, or LRCLIB does of this very recording. */
    data object Instrumental : LyricsAnswer

    data object None : LyricsAnswer
}

/** The song for the lyrics, named as the app tags it; with its Music Assistant address when it's Xonos inside myCar. */
fun NowPlaying.lyricsSong(xonosInside: Boolean) = SongInfo(
    title = trackTitle.ifEmpty { title },
    artist = trackArtist.ifEmpty { artist },
    album = album,
    durationMs = durationMs,
    maUri = if (xonosInside && mediaId.contains("://")) mediaId else "",
)

/**
 * Lyrics for the song playing. With Xonos inside myCar, Music Assistant first: the track's own tags,
 * the .lrc beside it, the lyrics services set up there. Then LRCLIB (lrclib.net), a free community
 * archive that needs no key, open source (MIT): media apps almost never publish lyrics in their
 * session.
 *
 * LRCLIB is asked at most [LyricsMatch.MAX_ASKS] questions per song (see LyricsMatch.plan), and
 * every entry it gives is checked against the song (LyricsMatch.fit) before it's shown: the lyrics
 * of another song, or times off by a different recording, are worse than none.
 *
 * A lookup sends the song's title, artist and length to lrclib.net: only while the lyrics are on
 * screen, or kept beside the map (CarNavigator.lyricsWanted), and the next song in the queue while
 * they're on screen (LyricsAhead). Never in the background otherwise.
 *
 * Answers are kept in memory for the process and on disk under the cache directory: lyrics and
 * "instrumental" for good, "none" for [NONE_KEPT_MS] (LRCLIB grows). A lookup that couldn't reach
 * LRCLIB (no signal, the server busy) is a failure, never "none", and is asked again next time.
 */
object Lyrics {

    private const val API = "https://lrclib.net/api/"
    private val AGENT = "myCar/${BuildConfig.VERSION_NAME} (https://github.com/SirioNero/MyCar)"

    /** Songs skipped in a row aren't looked up: a song not known yet waits this long on screen before its lookup. */
    const val SETTLE_MS = 700L

    /** Music Assistant has this long to answer before LRCLIB is asked: without a network its own services can take tens of seconds. */
    private const val MA_WAIT_MS = 8_000L

    /** A lookup stops asking LRCLIB after this long: a slow network, the server busy. */
    private const val LOOKUP_BUDGET_MS = 15_000L

    /** A song LRCLIB had nothing for is asked again after a week. */
    private const val NONE_KEPT_MS = 7L * 24 * 60 * 60 * 1000

    private val answers = lru<LyricsAnswer>(200)

    /** LRCLIB's entries for a question, read already: the next song's search (warm) costs nothing when it starts. Small: they carry the lyrics. */
    private val asked = lru<List<LyricsMatch.Hit>>(16)

    /** One lookup per song at a time, the panel's and the one ahead alike; it goes on if its asker leaves, into the cache. */
    private val running = HashMap<String, Deferred<Result<LyricsAnswer>>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Success with [LyricsAnswer.None]: there are none for this song. A failure: LRCLIB couldn't be asked. */
    suspend fun find(context: Context, song: SongInfo): Result<LyricsAnswer> {
        val query = LyricsMatch.query(song.title, song.artist, song.album, song.durationMs)
        if (query == null && song.maUri.isEmpty()) return Result.success(LyricsAnswer.None)
        val key = song.key
        synchronized(answers) { answers[key] }?.let { return Result.success(it) }
        val job = synchronized(running) {
            running[key] ?: scope.async(start = CoroutineStart.LAZY) { lookUp(context.applicationContext, song, query, key) }
                .also { job ->
                    running[key] = job
                    job.invokeOnCompletion { synchronized(running) { running.remove(key, job) } }
                }
        }
        job.start()
        return job.await()
    }

    /** The answer already known for [song], without asking anybody; null when there's none yet. */
    fun known(song: SongInfo): LyricsAnswer? = synchronized(answers) { answers[song.key] }

    /**
     * The song after this one, looked up ahead with the one question that doesn't need its length
     * (LRCLIB's search by title and artist): when it starts, its lookup finds the entries here.
     */
    suspend fun warm(title: String, subtitle: String) {
        // A queue's subtitle is often "Artist • Album".
        val artist = subtitle.split(" • ", " · ", " — ").first()
        val q = LyricsMatch.query(title, artist, "", 0) ?: return
        if (q.main.isEmpty() || LyricsMatch.instrumentalByTitle(q)) return
        val ask = Ask.Search(q.title, q.main)
        val url = urlOf(ask)
        if (synchronized(asked) { asked[url] } != null) return
        withContext(Dispatchers.IO) {
            runCatching { hitsOf(ask, fetch(url)) }.onSuccess { hits -> synchronized(asked) { asked[url] = hits } }
        }
    }

    private suspend fun lookUp(context: Context, song: SongInfo, query: LyricsMatch.SongQuery?, key: String): Result<LyricsAnswer> {
        readDisk(context, key)?.let { remember(key, it); return Result.success(it) }
        // "(Instrumental)" in the title: no words, nobody to ask.
        if (query != null && LyricsMatch.instrumentalByTitle(query)) return Result.success(LyricsAnswer.Instrumental).also { remember(key, LyricsAnswer.Instrumental) }
        // Music Assistant that didn't answer (reconnecting, too slow) is asked again next time.
        var maAnswered = true
        if (song.maUri.isNotEmpty()) {
            when (val ma = fromMusicAssistant(song.maUri)) {
                is FromMa.Got -> {
                    keep(context, key, ma.answer)
                    log("Music Assistant", song)
                    return Result.success(ma.answer)
                }
                FromMa.Empty -> Unit
                FromMa.Unanswered -> maAnswered = false
            }
        }
        if (query == null) return Result.success(LyricsAnswer.None)
        return runCatching { lrclib(query) }
            .onSuccess { trace ->
                // Without Music Assistant's word LRCLIB's answer isn't kept: on disk it came before
                // Music Assistant for good (lyrics) or a week ("none"), the track's own tags unseen.
                // Lyrics it found stay in memory for this run.
                if (maAnswered) keep(context, key, trace.answer)
                else if (trace.answer is LyricsAnswer.Found) remember(key, trace.answer)
                log("LRCLIB, question ${trace.askIndex + 1}, ${trace.requests} requests", song, trace.answer)
            }
            .onFailure { MyCarLog.w("lyrics: LRCLIB couldn't be asked: ${it.message}") }
            .map { it.answer }
    }

    private fun log(where: String, song: SongInfo, answer: LyricsAnswer? = null) {
        val what = when (answer) {
            null, is LyricsAnswer.Found -> "found"
            LyricsAnswer.Instrumental -> "instrumental"
            LyricsAnswer.None -> "none"
        }
        // The song's name only in the debug build: the log is shared for help.
        val which = if (BuildConfig.DEBUG) " for \"${song.title}\" by \"${song.artist}\"" else ""
        MyCarLog.i("lyrics: $what ($where)$which")
    }

    // --- Music Assistant --------------------------------------------------------------------------

    /** What Music Assistant said of a track's lyrics. */
    private sealed interface FromMa {
        class Got(val answer: LyricsAnswer) : FromMa

        /** It answered: no lyrics, or it turned the question down. */
        data object Empty : FromMa

        /** No answer: not connected (reconnecting), the connection lost, or too slow. */
        data object Unanswered : FromMa
    }

    private suspend fun fromMusicAssistant(uri: String): FromMa = withTimeoutOrNull(MA_WAIT_MS) {
        try {
            val lyrics = Xonos.library.lyrics(uri, MA_WAIT_MS - 1_000)
            val lines = lyrics?.lines.orEmpty().map { LyricLine(it.timeMs, it.text) }
            if (lyrics == null || lines.isEmpty()) FromMa.Empty
            else FromMa.Got(LyricsAnswer.Found(LyricsResult(lines, lyrics.synced), fromMusicAssistant = true))
        } catch (e: Exception) {
            // The wait over, or the lookup's asker gone: not something Music Assistant said.
            if (e is CancellationException) throw e
            MyCarLog.w("lyrics: Music Assistant didn't answer: ${e.message}")
            // A refusal is its answer (an old Music Assistant, no permission); anything else is none.
            if (e is MaException && e.problem !in UNREACHED) FromMa.Empty else FromMa.Unanswered
        }
    } ?: FromMa.Unanswered

    /** Music Assistant's errors that mean it wasn't reached, not that it said no. */
    private val UNREACHED = setOf(Problem.NOT_CONNECTED, Problem.LOST, Problem.NO_ANSWER, Problem.NETWORK)

    // --- LRCLIB -----------------------------------------------------------------------------------

    /** What one LRCLIB lookup did: for the log and the debug build's LyricsCheckActivity. */
    internal class Trace(
        val answer: LyricsAnswer,
        val fit: LyricsMatch.Fit?,
        /** Which question of the plan found it; -1 for none. */
        val askIndex: Int,
        /** Requests actually made (answers kept from before aside). */
        val requests: Int,
        /** Every entry LRCLIB gave, for the check's log. */
        val seen: List<LyricsMatch.Hit>,
    )

    /** Debug check only (LyricsCheckActivity): LRCLIB asked afresh, no cache either way. */
    internal fun traceFresh(song: SongInfo): Trace? =
        LyricsMatch.query(song.title, song.artist, song.album, song.durationMs)?.let { lrclib(it, cached = false) }

    /** Blocking: on Dispatchers.IO. */
    internal fun lrclib(q: LyricsMatch.SongQuery, cached: Boolean = true): Trace {
        // Questions answered before (the song looked up ahead) first: they cost nothing.
        val plan = LyricsMatch.plan(q).let { asks ->
            if (!cached) asks else asks.sortedBy { ask -> if (synchronized(asked) { asked[urlOf(ask)] } != null) 0 else 1 }
        }
        val seen = ArrayList<LyricsMatch.Hit>()
        var best: LyricsMatch.Fit? = null
        var bestAt = -1
        var requests = 0
        var failure: IOException? = null
        val started = System.nanoTime()
        for ((i, ask) in plan.withIndex()) {
            if (!LyricsMatch.worthAsking(ask, seen.size)) continue
            if ((System.nanoTime() - started) / 1_000_000 > LOOKUP_BUDGET_MS) {
                failure = failure ?: IOException("LRCLIB too slow")
                break
            }
            val url = urlOf(ask)
            val hits = (if (cached) synchronized(asked) { asked[url] } else null) ?: try {
                requests++
                hitsOf(ask, fetch(url)).also { if (cached) synchronized(asked) { asked[url] = it } }
            } catch (e: Busy) {
                // It asked to slow down: no more questions this time.
                failure = e
                break
            } catch (e: IOException) {
                failure = e
                continue
            }
            seen += hits
            val now = LyricsMatch.best(q, seen)
            if (now != null && now.hit !== best?.hit) {
                best = now
                bestAt = i
            }
            if (now != null && LyricsMatch.settled(q, now)) break
        }
        val fit = best
        // A question that couldn't be asked might have had them: not "none", ask again later.
        if (fit == null && failure != null) throw failure
        val answer = when {
            fit == null -> LyricsAnswer.None
            fit.instrumental -> LyricsAnswer.Instrumental
            else -> resultOf(fit)?.let { LyricsAnswer.Found(it) } ?: LyricsAnswer.None
        }
        return Trace(answer, fit, bestAt, requests, seen)
    }

    private fun urlOf(ask: Ask): String = when (ask) {
        // No album: /api/get wants it exactly as LRCLIB has it, and the apps' album names differ most.
        is Ask.Get -> API + "get?track_name=" + enc(ask.track) + "&artist_name=" + enc(ask.artist) +
            (if (ask.durationSec in 1..3600) "&duration=" + ask.durationSec else "")
        is Ask.Search -> API + "search?track_name=" + enc(ask.track) + "&artist_name=" + enc(ask.artist)
        is Ask.Words -> API + "search?q=" + enc(ask.q)
    }

    /** LRCLIB asked to slow down (429, 503): the lookup stops asking. */
    private class Busy(message: String) : IOException(message)

    /**
     * LRCLIB's answer; "" when it has nothing (404) or won't take the question (400). Throws when it
     * can't be asked: no network, or still busy (503 with Retry-After) after one more try - at once
     * if it asks for more than a few seconds ([Busy]).
     */
    private fun fetch(url: String): String {
        for (attempt in 0..1) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4_000
                readTimeout = 6_000
                setRequestProperty("User-Agent", AGENT)
                // How LRCLIB tells its clients apart.
                setRequestProperty("Lrclib-Client", AGENT)
            }
            try {
                when (val code = connection.responseCode) {
                    200 -> return connection.inputStream.bufferedReader().readText()
                    400, 404 -> return ""
                    429, 503 -> {
                        val wait = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: 1
                        if (attempt == 1 || wait > 3) throw Busy("LRCLIB is busy ($code)")
                        Thread.sleep(wait.coerceAtLeast(1) * 1000)
                    }
                    else -> throw IOException("LRCLIB answered $code")
                }
            } finally {
                connection.disconnect()
            }
        }
        throw Busy("LRCLIB is busy")
    }

    private fun hitsOf(ask: Ask, body: String): List<LyricsMatch.Hit> = runCatching {
        when {
            body.isBlank() -> emptyList()
            ask is Ask.Get -> listOf(hitOf(JSONObject(body)))
            else -> JSONArray(body).let { a -> (0 until a.length()).map { hitOf(a.getJSONObject(it)) } }
        }
    }.getOrElse {
        MyCarLog.w("lyrics: LRCLIB's answer couldn't be read: ${it.message}")
        emptyList()
    }

    private fun hitOf(j: JSONObject) = LyricsMatch.Hit(
        id = j.optLong("id"),
        track = j.text("trackName") ?: j.text("name").orEmpty(),
        artist = j.text("artistName").orEmpty(),
        album = j.text("albumName").orEmpty(),
        durationSec = j.optDouble("duration", 0.0).takeUnless { it.isNaN() } ?: 0.0,
        instrumental = j.optBoolean("instrumental"),
        synced = j.text("syncedLyrics"),
        plain = j.text("plainLyrics"),
    )

    /** A JSON null is no text: Android's optString would give "null". */
    private fun JSONObject.text(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private fun resultOf(fit: LyricsMatch.Fit): LyricsResult? {
        val hit = fit.hit
        if (fit.timed) {
            hit.synced?.let(::parseLrc)?.takeIf { it.isNotEmpty() }?.let { return LyricsResult(it, synced = true) }
        }
        // The words only: the plain text, or the timed one without its times.
        val words = hit.plain ?: hit.synced?.let { s -> parseLrc(s).joinToString("\n") { it.text } } ?: return null
        return LyricsResult(words.lines().map { LyricLine(0, it) }, synced = false)
    }

    /** `[01:23.45] words` - a line may carry several stamps when a chorus repeats. */
    private fun parseLrc(text: String): List<LyricLine> {
        val stamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        return text.lines().flatMap { raw ->
            val stamps = stamp.findAll(raw).toList()
            val words = raw.replace(stamp, "").trim()
            stamps.map {
                val ms = ((it.groupValues[1].toLong() * 60 + it.groupValues[2].toDouble()) * 1000).toLong()
                LyricLine(ms, words)
            }
        }.sortedBy { it.timeMs }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    // --- the caches -------------------------------------------------------------------------------

    private fun remember(key: String, answer: LyricsAnswer) = synchronized(answers) { answers[key] = answer }

    private fun keep(context: Context, key: String, answer: LyricsAnswer) {
        remember(key, answer)
        writeDisk(context, key, answer)
    }

    private var oldCleared = false

    private fun cacheFile(context: Context, key: String): File {
        if (!oldCleared) {
            oldCleared = true
            // Until 0.6.8 a search's first entry was kept unchecked: it could be another song's.
            File(context.cacheDir, "lyrics").deleteRecursively()
        }
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(context.cacheDir, "lyrics-v2").apply { mkdirs() }.resolve("$name.json")
    }

    private fun readDisk(context: Context, key: String): LyricsAnswer? = runCatching {
        val file = cacheFile(context, key)
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        when (json.optString("kind")) {
            "found" -> {
                val arr = json.getJSONArray("lines")
                val lines = (0 until arr.length()).map { i ->
                    val line = arr.getJSONObject(i)
                    LyricLine(line.getLong("t"), line.getString("x"))
                }
                LyricsAnswer.Found(LyricsResult(lines, json.getBoolean("synced")), json.optBoolean("ma"))
            }
            "instrumental" -> LyricsAnswer.Instrumental
            "none" -> if (System.currentTimeMillis() - json.optLong("at") < NONE_KEPT_MS) LyricsAnswer.None else null
            else -> null
        }
    }.getOrNull()

    private fun writeDisk(context: Context, key: String, answer: LyricsAnswer) {
        runCatching {
            val json = JSONObject().put("at", System.currentTimeMillis())
            when (answer) {
                is LyricsAnswer.Found -> {
                    val lines = JSONArray()
                    answer.result.lines.forEach { lines.put(JSONObject().put("t", it.timeMs).put("x", it.text)) }
                    json.put("kind", "found").put("synced", answer.result.synced).put("lines", lines).put("ma", answer.fromMusicAssistant)
                }
                LyricsAnswer.Instrumental -> json.put("kind", "instrumental")
                LyricsAnswer.None -> json.put("kind", "none")
            }
            cacheFile(context, key).writeText(json.toString())
        }.onFailure { MyCarLog.w("lyrics: could not write disk cache: ${it.message}") }
    }

    private fun <T> lru(size: Int) = object : LinkedHashMap<String, T>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>?) = this.size > size
    }
}
