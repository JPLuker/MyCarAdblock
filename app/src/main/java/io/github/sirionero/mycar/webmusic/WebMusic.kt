package io.github.sirionero.mycar.webmusic

import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.annotation.StringRes
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.cinema.CinemaSite
import io.github.sirionero.mycar.media.MediaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A music service's own web player, as one of myCar's music apps: Spotify's open.spotify.com, as
 * on a computer, and the like. It plays inside myCar, so its sound reaches the car through myCar as
 * Cinema's does - which a music app's often doesn't (Spotify's keeps other apps from recording what
 * it plays) - and it drives myCar's player, the dock and the car's buttons like any music app (see
 * [WebMusicMediaService]).
 */
class WebMusicService(
    val id: String,
    /** The service's own name, the same in every language. */
    val name: String,
    /** Where its player starts. */
    val url: String,
    /** The addresses its player lives on: myCar's scripts and messages are for these only. */
    val origins: List<String>,
    /** Where its sign-in lives: signing out takes every cookie of these, and their pages' storage. */
    val domains: List<String>,
    /** What it needs, in a line on the phone; 0 when there's nothing to say. */
    @StringRes val needs: Int = 0,
    /**
     * Its own play, next and previous buttons and its player bar, by data-testid: what plays and
     * the car's buttons when the page says nothing through its media session (Spotify's, for now).
     */
    val bar: Bar? = null,
    /** A little CSS of its own, for what the car's screen needs hidden or changed. */
    val css: String = "",
) {
    class Bar(
        val playPause: String,
        val next: String,
        val previous: String,
        val position: String,
        val duration: String,
        val widget: String,
        val title: String,
        val artist: String,
    )

    /** As one of Cinema's sites, as on a computer: for its icon, fetched from the site (cinema/SiteIcons). */
    val site: CinemaSite get() = CinemaSite(name, url, desktop = true)

    /** The host its icon is kept by. */
    val host: String get() = site.host
}

/**
 * The music services' web players myCar knows, and which of them the driver uses: none until one
 * is turned on in the phone's settings (Music). Spotify is always listed there; the others once
 * added. Only the ones on exist in the car: a tile on the app grid, a music app for the player, a
 * choice beside the map. One page per service, made the first time it's opened in a drive.
 */
object WebMusic {

    val SPOTIFY = WebMusicService(
        "spotify", "Spotify", "https://open.spotify.com/",
        origins = listOf("https://open.spotify.com"),
        domains = listOf("spotify.com"),
        bar = WebMusicService.Bar(
            playPause = "control-button-playpause",
            next = "control-button-skip-forward",
            previous = "control-button-skip-back",
            position = "playback-position",
            duration = "playback-duration",
            widget = "now-playing-widget",
            title = "context-item-info-title",
            artist = "context-item-info-artist",
        ),
    )

    private val AMAZON = listOf("com", "de", "co.uk", "it", "fr", "es", "ca", "com.br", "com.mx", "co.jp", "in", "com.au")

    /** The services offered, in this order on the phone. */
    val CATALOG = listOf(
        SPOTIFY,
        WebMusicService(
            "youtube-music", "YouTube Music", "https://music.youtube.com/",
            origins = listOf("https://music.youtube.com"),
            domains = listOf("youtube.com", "google.com"),
            needs = R.string.music_needs_google,
        ),
        WebMusicService(
            "apple-music", "Apple Music", "https://music.apple.com/",
            origins = listOf("https://music.apple.com"),
            domains = listOf("apple.com"),
            needs = R.string.music_needs_apple,
        ),
        WebMusicService(
            "amazon-music", "Amazon Music", "https://music.amazon.com/",
            origins = AMAZON.map { "https://music.amazon.$it" },
            domains = AMAZON.map { "amazon.$it" },
            needs = R.string.music_needs_amazon,
        ),
        WebMusicService(
            "deezer", "Deezer", "https://www.deezer.com/",
            origins = listOf("https://www.deezer.com"),
            domains = listOf("deezer.com"),
        ),
        WebMusicService(
            "tidal", "TIDAL", "https://listen.tidal.com/",
            origins = listOf("https://listen.tidal.com"),
            domains = listOf("tidal.com"),
            needs = R.string.music_needs_tidal,
        ),
        WebMusicService(
            "soundcloud", "SoundCloud", "https://soundcloud.com/",
            origins = listOf("https://soundcloud.com"),
            domains = listOf("soundcloud.com"),
        ),
        WebMusicService(
            "qobuz", "Qobuz", "https://play.qobuz.com/",
            origins = listOf("https://play.qobuz.com"),
            domains = listOf("qobuz.com"),
            needs = R.string.music_needs_qobuz,
        ),
    )

    fun byId(id: String): WebMusicService? = CATALOG.firstOrNull { it.id == id }

    private const val KEY_ADDED = "web_music_added"
    private const val KEY_ON = "web_music_on"
    private var app: Context? = null
    private val addedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val onFlow = MutableStateFlow<Set<String>>(emptySet())

    /** Listed in the phone's Music settings, besides Spotify, which always is. */
    val added: StateFlow<Set<String>> = addedFlow.asStateFlow()

    /** On: in the car. None to start with. */
    val on: StateFlow<Set<String>> = onFlow.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        addedFlow.value = read(app, KEY_ADDED)
        onFlow.value = read(app, KEY_ON)
    }

    /** The services in the car now, in the catalog's order. */
    fun enabled(): List<WebMusicService> = CATALOG.filter { it.id in onFlow.value }

    fun setOn(id: String, on: Boolean) {
        onFlow.value = if (on) onFlow.value + id else onFlow.value - id
        app?.let { write(it, KEY_ON, onFlow.value) }
    }

    /** Listed on the phone, and on in the car. */
    fun add(id: String) {
        addedFlow.value = addedFlow.value + id
        app?.let { write(it, KEY_ADDED, addedFlow.value) }
        setOn(id, true)
    }

    /** Off the phone's list, and out of the car. */
    fun remove(id: String) {
        addedFlow.value = addedFlow.value - id
        app?.let { write(it, KEY_ADDED, addedFlow.value) }
        setOn(id, false)
    }

    /** For the car preview: only [id] on, or none. */
    fun only(id: String?) {
        onFlow.value = setOfNotNull(id?.takeIf { byId(it) != null })
        app?.let { write(it, KEY_ON, onFlow.value) }
    }

    /** [service] as one of the car's music apps. */
    fun mediaApp(context: Context, service: WebMusicService): MediaApp =
        MediaApp(ComponentName(context, serviceClass(service.id)), service.name, Process.myUid())

    /** The car's music apps among these: the ones on. */
    fun mediaApps(context: Context): List<MediaApp> = enabled().map { mediaApp(context, it) }

    /** The service whose player [component] is; null for any other app. */
    fun serviceFor(component: ComponentName): WebMusicService? =
        CATALOG.firstOrNull { serviceClass(it.id).name == component.className }

    fun isInside(component: ComponentName): Boolean = serviceFor(component) != null

    private fun read(context: Context, key: String): Set<String> =
        AppSettings.getString(context, key, "").split(',').filter { id -> byId(id) != null }.toSet()

    private fun write(context: Context, key: String, ids: Set<String>) =
        AppSettings.putString(context, key, ids.joinToString(","))
}
