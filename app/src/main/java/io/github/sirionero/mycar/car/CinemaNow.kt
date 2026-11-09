package io.github.sirionero.mycar.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView

/**
 * The film Cinema is playing now, for the car taking its speakers back for good (the driver picks
 * the radio): it has no media session for [CarServices.pausePhoneMedia] to reach, and it ran on
 * unseen and unheard, losing minutes, and kept the car from being asked for the speakers again.
 */
internal object CinemaNow {
    @Volatile var pause: (() -> Unit)? = null

    /**
     * Whether Cinema's own player plays a film now, with no media session to say so: a player, whose
     * sound takes the car's speakers from its radio (see CarAudio.playerPlaying). Null with none open.
     */
    @Volatile var playing: (() -> Boolean)? = null

    /** When the car last took its speakers back for good ([pauseForCar]), on elapsedRealtime: for [CinemaMusic]. */
    @Volatile var takenBackAt = 0L
        private set

    /**
     * A [pause] for a web page - a site's video in Cinema, a radio in the browser - which has no media
     * session either: its videos and sounds paused.
     */
    fun pausePage(web: WebView): () -> Unit = { runCatching { web.evaluateJavascript(PAUSE_PAGE_JS, null) } }

    private const val PAUSE_PAGE_JS = "document.querySelectorAll('video, audio').forEach(function (m) { m.pause(); });"

    /** From the car's thread: the player is the main thread's. */
    fun pauseForCar() {
        takenBackAt = SystemClock.elapsedRealtime()
        Handler(Looper.getMainLooper()).post { pause?.invoke() }
    }
}
