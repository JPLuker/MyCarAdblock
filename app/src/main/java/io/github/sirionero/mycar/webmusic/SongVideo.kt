package io.github.sirionero.mycar.webmusic

/**
 * The video cover of the song in play (Spotify's Canvas), from what its page says each time
 * ([WebPlayerState.videoUrl]). A page's Now playing view can go on showing the song before's video
 * for a moment after the next song has started, while it looks the new one up: that video isn't the
 * new song's. So a video the song before had counts for this one only once the page has let it go
 * (shown none) in between - unless it came just before the song changed ([FRESH_MS]): then the
 * page's picture was a moment ahead of its words, and it's this song's. Pure, for the tests.
 */
internal class SongVideo {
    private var song: String? = null

    /** This song's video, as last taken ("" for none), and when it was first taken (elapsed clock). */
    private var current = ""
    private var takenAt = 0L

    /** The song before's video, while the page may still show it: not this song's. */
    private var stale = ""

    /** The video cover of [song] (its [WebPlayerState.key]), from the [video] its page shows [now] ("" for none). */
    fun next(song: String, video: String, now: Long): String {
        if (song != this.song) {
            if (current.isNotEmpty()) stale = if (now - takenAt >= FRESH_MS) current else ""
            this.song = song
            current = ""
        }
        if (video.isEmpty()) {
            stale = ""
            current = ""
            return ""
        }
        if (video == stale) return ""
        if (video != current) {
            current = video
            takenAt = now
        }
        return video
    }

    companion object {
        /** A video first shown this shortly before the song changed is taken for the new song. */
        const val FRESH_MS = 2_000L
    }
}
