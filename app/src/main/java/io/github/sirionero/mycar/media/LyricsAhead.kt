package io.github.sirionero.mycar.media

import kotlinx.coroutines.delay

/**
 * While the lyrics are on screen: the next song in the app's queue, looked up by title and artist
 * (no length), so its lyrics show at once when it starts. Nothing ahead with the lyrics closed: a
 * lookup sends the song's title and artist to lrclib.net (see Lyrics).
 */
object LyricsAhead {

    /** The next song is looked up this long after the lyrics show one: not for songs skipped in a row. */
    private const val NEXT_AFTER_MS = 5_000L

    suspend fun warmNext(media: CarMedia) {
        delay(NEXT_AFTER_MS)
        val now = media.now.value
        val queue = media.queue.value
        val at = queue.indexOfFirst { it.queueId == now.activeQueueItemId }
        if (at < 0) return
        val next = queue.getOrNull(at + 1) ?: return
        Lyrics.warm(next.title, next.subtitle)
    }
}
