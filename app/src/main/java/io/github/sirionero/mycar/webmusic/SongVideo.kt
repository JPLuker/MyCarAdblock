package io.github.sirionero.mycar.webmusic

/**
 * The video cover of the song in play (Spotify's Canvas), from what its page says each time
 * ([WebPlayerState.videoUrl]). A page's Now playing view can go on showing the song before's video
 * for a moment after the next song has started, while it looks the new one up: that video isn't the
 * new song's. So a video seen with the song before counts for this one only once the page has let
 * it go (shown none) in between. Pure, for the tests.
 */
internal class SongVideo {
    private var song: String? = null

    /** This song's video, as last taken ("" for none). */
    private var current = ""

    /** The song before's video, while the page may still show it: not this song's. */
    private var stale = ""

    /** The video cover of [song] (its [WebPlayerState.key]), from the [video] its page shows now ("" for none). */
    fun next(song: String, video: String): String {
        if (song != this.song) {
            if (current.isNotEmpty()) stale = current
            this.song = song
            current = ""
        }
        if (video.isEmpty()) {
            stale = ""
            current = ""
            return ""
        }
        if (video == stale) return ""
        current = video
        return video
    }
}
