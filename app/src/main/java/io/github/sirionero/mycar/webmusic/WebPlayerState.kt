package io.github.sirionero.mycar.webmusic

/** What a music service's web player is doing, as its page last said (see car/WebMusicScripts.kt). */
data class WebPlayerState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    /** The cover, an https address; empty without one. */
    val artUrl: String = "",
    /**
     * The song's video cover (Spotify's Canvas: a short muted loop), the one its page shows, from the
     * service's own video addresses ([WebMusicService.videoCovers]); empty without one.
     */
    val videoUrl: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    /** 0 while unknown. */
    val durationMs: Long = 0,
    /** When [positionMs] held, on SystemClock.elapsedRealtime(). */
    val at: Long = 0,
    /** Whether the page takes a position to play from. */
    val canSeek: Boolean = false,
) {
    /** A song to show: its name is known. */
    val hasTrack: Boolean get() = title.isNotEmpty()

    /** Which song it is: another one is another item for myCar's player. */
    val key: String get() = listOf(title, artist, album).joinToString("\u0001")

    /** The position at [now] (on the same clock as [at]), moved on while it plays, within the song. */
    fun positionAt(now: Long): Long {
        val moved = if (playing && at > 0) positionMs + (now - at).coerceAtLeast(0) else positionMs
        return if (durationMs > 0) moved.coerceIn(0, durationMs) else moved.coerceAtLeast(0)
    }
}
