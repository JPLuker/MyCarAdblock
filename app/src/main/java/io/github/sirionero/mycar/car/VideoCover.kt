package io.github.sirionero.mycar.car

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.car.app.CarToast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconBox
import io.github.sirionero.mycar.webmusic.WebMusic
import io.github.sirionero.mycar.webmusic.WebMusicPlayer
import io.github.sirionero.mycar.webmusic.WebMusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/*
 * The video cover: the short looping video some songs have in place of their cover (Spotify's
 * Canvas), in the player beside the map, where the car's music can have one and the driver turned it
 * on with the button on the cover (or in the settings). The still cover stays everywhere else: the
 * mini player over the map, the dock, the glass under the player's buttons.
 */

/**
 * The cover of the player beside the map: the song's video where its music has one, or the still
 * picture. Still until the driver turns video on - the button on the cover, the car's settings or the
 * phone's (key car_video_cover) - and as chosen from then on, the next drives too.
 */
object VideoCoverSetting {
    private const val KEY = "car_video_cover"
    private var app: Context? = null
    private val onFlow = MutableStateFlow(false)
    val on: StateFlow<Boolean> = onFlow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        onFlow.value = AppSettings.getString(app, KEY, "false") == "true"
    }

    fun setOn(on: Boolean) {
        onFlow.value = on
        app?.let { AppSettings.putString(it, KEY, on.toString()) }
    }
}

/** For the car preview (debug builds): a video cover for whatever plays, as if its music had them. Null for the real ones. */
internal object VideoCoverPreview {
    @Volatile var url: String? = null
}

/** The car's music, when it can have video covers (Spotify Web): its service; null otherwise. */
@Composable
internal fun rememberVideoCoverService(): WebMusicService? {
    val app by CarServices.mediaApp.collectAsState()
    return app?.component?.let(WebMusic::serviceFor)?.takeIf { it.videoCovers.isNotEmpty() }
}

/** The video cover of what [service]'s page plays now, if it has one; the preview's stand-in in debug builds. */
@Composable
internal fun rememberVideoCover(service: WebMusicService?): String? {
    VideoCoverPreview.url?.let { return it }
    if (service == null) return null
    val state by remember(service.id) { WebMusicPlayer.state(service.id) }.collectAsState()
    return state.videoUrl.ifEmpty { null }
}

/** A song's video cover on the car's screen: whether a picture of it is up yet, when the still cover gives way. */
@Stable
internal class VideoCover(val url: String) {
    var drawn by mutableStateOf(false)
        internal set
}

/**
 * How much of the still cover shows over [video]: all of it until the video's first picture is
 * drawn, then it fades away; all of it again the moment the video goes. Read while drawing (in a
 * graphicsLayer), so the fade doesn't recompose the player.
 */
@Composable
internal fun rememberStillCoverAlpha(video: VideoCover?): () -> Float {
    val fade = remember(video) { Animatable(1f) }
    val drawn = video?.drawn == true
    LaunchedEffect(fade, drawn) {
        if (drawn) fade.animateTo(0f, tween(MyCarMotion.ScreenMs, easing = MyCarMotion.Decelerate)) else fade.snapTo(1f)
    }
    return remember(fade, drawn) { { if (drawn) fade.value else 1f } }
}

/**
 * On the cover of the player beside the map, where the car's music can have video covers: lit,
 * the songs that have one show their video in place of the cover; off, the still picture. It
 * stays as chosen, the next drives too.
 */
@Composable
internal fun VideoCoverButton(modifier: Modifier = Modifier) {
    val on by VideoCoverSetting.on.collectAsState()
    GsIconBox(
        onClick = {
            val video = !VideoCoverSetting.on.value
            VideoCoverSetting.setOn(video)
            // Most songs have no video: said, so a tap that changes nothing in sight still says what it did.
            CarToasts.clear(VideoCoverToast)
            CarToasts.show(
                VideoCoverToast,
                Words.get(if (video) R.string.car_player_video_cover_now_on else R.string.car_player_video_cover_now_off),
                CarToast.LENGTH_SHORT,
            )
        },
        modifier = modifier.size(BUTTON),
        // Lit: white, as a switch that's on. Off: the dark glass of the player's X beside it.
        colors = if (on) GsColors(Color.White.copy(alpha = 0.92f), Color.Black) else GsColors(Color.Black.copy(alpha = 0.4f), Color.White),
        shape = CircleShape,
        latched = on,
    ) {
        Icon(
            painterResource(R.drawable.sym_motion_play),
            stringResource(if (on) R.string.car_player_video_cover_off else R.string.car_player_video_cover_on),
            Modifier.size(ICON),
        )
    }
}

/** Who the button's toast belongs to, for [CarToasts]. */
private object VideoCoverToast

private val BUTTON = 52.dp
private val ICON = 26.dp

/**
 * [video] in this place, cut from its middle to fill it as the cover does, muted and looping; paused
 * while not [running] (its picture stays). Drawn under the still cover, which gives way once
 * [VideoCover.drawn]. Played by Android's own player on a TextureView, as Cinema's videos, from a copy
 * on the phone ([VideoCoverFiles]): nothing until the copy is there, and nothing at all if it won't
 * come or won't play - the still cover stays. Gone with this composable; one at a time.
 */
@Composable
internal fun VideoCoverView(video: VideoCover, running: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var file by remember(video) { mutableStateOf<File?>(null) }
    LaunchedEffect(video) { file = VideoCoverFiles.get(context, video.url) }
    val ready = file ?: return
    val loop = remember(ready) { VideoLoop(ready, video) }
    DisposableEffect(loop) {
        loop.open()
        onDispose { loop.release() }
    }
    LaunchedEffect(loop, running) { loop.run(running) }
    AndroidView(
        factory = { ctx -> TextureView(ctx).also(loop::show) },
        // Again when the video says its size.
        update = { view -> loop.fit(view) },
        modifier = modifier,
    )
}

/** The video cover playing now, if any: one at a time on the car's screen. Main thread only. */
private var playingLoop: VideoLoop? = null

/** Android's player for one video cover: muted, looping, on a TextureView's surface. Main thread only. */
private class VideoLoop(private val file: File, private val video: VideoCover) {
    private val player = MediaPlayer()
    private var surface: Surface? = null
    private var prepared = false
    private var wanted = false
    private var released = false

    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set

    fun open() {
        // One at a time: one still going on another car screen (a screen made again) ends.
        playingLoop?.takeIf { it !== this }?.release()
        playingLoop = this
        player.setOnPreparedListener {
            prepared = true
            start()
        }
        player.setOnVideoSizeChangedListener { _, width, height ->
            videoWidth = width
            videoHeight = height
        }
        player.setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && !released && !video.drawn) {
                video.drawn = true
                MyCarLog.i("video cover: playing, ${videoWidth}x$videoHeight")
            }
            false
        }
        player.setOnErrorListener { _, what, extra ->
            MyCarLog.w("video cover: didn't play - error $what/$extra")
            video.drawn = false
            VideoCoverFiles.bad(video.url, file)
            true
        }
        // A cover: no sound of its own, round and round.
        player.setVolume(0f, 0f)
        player.isLooping = true
        try {
            player.setDataSource(file.path)
            player.prepareAsync()
        } catch (e: Exception) {
            MyCarLog.w("video cover: didn't open - ${e.javaClass.simpleName}")
            VideoCoverFiles.bad(video.url, file)
        }
    }

    /** Plays while [on]; paused while not, its picture staying. */
    fun run(on: Boolean) {
        wanted = on
        if (on) start() else pause()
    }

    private fun start() {
        if (released || !prepared || !wanted || surface == null) return
        runCatching { if (!player.isPlaying) player.start() }
    }

    private fun pause() {
        if (released || !prepared) return
        runCatching { if (player.isPlaying) player.pause() }
    }

    fun show(view: TextureView) {
        // See-through until its first picture: nothing dark at the still cover's edges.
        view.isOpaque = false
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                attach(texture)
                fit(view)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fit(view)

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                detach()
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
    }

    /** Cut from its middle to fill [view], as the cover is: the TextureView first squeezes it to its own shape. */
    fun fit(view: TextureView) {
        val width = view.width
        val height = view.height
        val (across, down) = CoverCrop.scale(width, height, videoWidth, videoHeight)
        if (width > 0 && height > 0) view.setTransform(Matrix().apply { setScale(across, down, width / 2f, height / 2f) })
    }

    private fun attach(texture: SurfaceTexture) {
        if (released) return
        surface?.release()
        surface = Surface(texture).also { s -> runCatching { player.setSurface(s) } }
        start()
    }

    private fun detach() {
        pause()
        if (!released) runCatching { player.setSurface(null) }
        surface?.release()
        surface = null
    }

    fun release() {
        if (released) return
        released = true
        video.drawn = false
        if (playingLoop === this) playingLoop = null
        runCatching { player.release() }
        surface?.release()
        surface = null
    }
}

/**
 * The video covers' files on the phone, in its cache: each fetched once and the last few kept - a
 * loop plays again and again (from the network it would be fetched again each time round), and the
 * same songs come back. One that won't come, or won't play, isn't asked for again for a while.
 */
internal object VideoCoverFiles {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /** One download at a time: a song skipped while it waits isn't fetched at all. */
    private val one = Mutex()

    /** The addresses that didn't come or didn't play, and when (on the elapsed clock). */
    private val failed = mutableMapOf<String, Long>()

    /** [url]'s file on the phone, fetched now if need be; null if it won't come, or didn't a moment ago. */
    suspend fun get(context: Context, url: String): File? {
        if (failedLately(url)) return null
        val dir = File(context.cacheDir, DIR)
        return one.withLock {
            withContext(Dispatchers.IO) {
                try {
                    fetch(dir, url)
                } catch (e: Exception) {
                    // Not its address in the log, but in debug builds: it says which song it is.
                    MyCarLog.w("video cover: not fetched - ${e.javaClass.simpleName}" + if (BuildConfig.DEBUG) " ($url)" else "")
                    null
                }
            }
        }.also { if (it == null) markFailed(url) }
    }

    /** [file], fetched from [url], didn't play: it goes, and isn't fetched again for a while. */
    fun bad(url: String, file: File) {
        file.delete()
        markFailed(url)
    }

    private fun fetch(dir: File, url: String): File? {
        val file = File(dir, name(url))
        if (file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        dir.mkdirs()
        val part = File(dir, file.name + PART)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    MyCarLog.w("video cover: not fetched - HTTP ${response.code}")
                    return null
                }
                val body = response.body
                if (body.contentLength() > MAX_BYTES) return null
                part.outputStream().use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(BUFFER)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return null
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (!part.renameTo(file)) return null
        } finally {
            part.delete()
        }
        prune(dir)
        return file.takeIf { it.length() > 0 }
    }

    /** The last [KEPT] kept; pieces of downloads cut short go too. */
    private fun prune(dir: File) {
        dir.listFiles { f -> f.name.endsWith(PART) && f.lastModified() < System.currentTimeMillis() - STALE_PART_MS }?.forEach { it.delete() }
        val files = dir.listFiles { f -> f.name.endsWith(EXT) } ?: return
        if (files.size <= KEPT) return
        files.sortedBy { it.lastModified() }.take(files.size - KEPT).forEach { it.delete() }
    }

    private fun name(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(16).joinToString("") { "%02x".format(it) } + EXT

    private fun failedLately(url: String): Boolean = synchronized(failed) {
        val at = failed[url] ?: return false
        SystemClock.elapsedRealtime() - at < RETRY_AFTER_MS
    }

    private fun markFailed(url: String) {
        synchronized(failed) {
            if (failed.size > MAX_FAILED) failed.clear()
            failed[url] = SystemClock.elapsedRealtime()
        }
    }

    private const val DIR = "video-covers"
    private const val EXT = ".mp4"
    private const val PART = ".part"
    private const val KEPT = 12
    private const val BUFFER = 64 * 1024

    /** Spotify's loops are a few megabytes; anything much bigger isn't a cover. */
    private const val MAX_BYTES = 20L * 1024 * 1024
    private const val RETRY_AFTER_MS = 10 * 60_000L
    private const val STALE_PART_MS = 60 * 60_000L
    private const val MAX_FAILED = 100
}
