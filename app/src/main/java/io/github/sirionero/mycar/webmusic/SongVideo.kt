package io.github.sirionero.mycar.webmusic

/**
 * The video cover of the song in play (Spotify's Canvas), from what its page says each time
 * ([WebPlayerState.videoUrl]). A page's Now playing view can go on showing the song before's video
 * for a while after the next song has started, while it looks the new one up: that video isn't the
 * new song's. So the video a song had doesn't count for the songs after it until the page has let it
 * go (shown none), or [STALE_MS] have passed - and always counts again for its own song (next, then
 * back). Only a video the page started showing just before the song changed ([FRESH_MS]) is the new
 * song's: its picture a moment ahead of its words. Pure, for the tests.
 */
internal class SongVideo {
    private var song: String? = null

    /** This song's video, as last taken ("" for none). */
    private var current = ""

    /** The video the page shows, and since when (elapsed clock). */
    private var shown = ""
    private var shownSince = 0L

    /** The video of the song [staleSong], while the page may still show it: no other song's until [staleUntil]. */
    private var stale = ""
    private var staleSong: String? = null
    private var staleUntil = 0L

    /** The video cover of [song] (its [WebPlayerState.key]), from the [video] its page shows [now] ("" for none). */
    fun next(song: String, video: String, now: Long): String {
        if (song != this.song) {
            if (current.isNotEmpty()) {
                if (current != shown || now - shownSince >= FRESH_MS) {
                    stale = current
                    staleSong = this.song
                    staleUntil = now + STALE_MS
                } else if (stale == current) {
                    stale = ""
                }
            }
            this.song = song
            current = ""
        }
        if (video != shown) {
            shown = video
            shownSince = now
        }
        if (video.isEmpty()) {
            stale = ""
            current = ""
            return ""
        }
        if (video == stale && song != staleSong && now < staleUntil) return ""
        current = video
        return video
    }

    companion object {
        /** A video the page started showing this shortly before the song changed is taken for the new song. */
        const val FRESH_MS = 2_000L

        /** The song before's video still shown this long after the change is taken for the new song after all. */
        const val STALE_MS = 10_000L
    }
}
