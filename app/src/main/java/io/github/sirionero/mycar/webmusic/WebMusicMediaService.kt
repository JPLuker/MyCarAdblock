package io.github.sirionero.mycar.webmusic

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.sirionero.mycar.MyCarActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A music service's web player as one of the car's music apps: the player that myCar's player
 * beside the map, the dock, the car's buttons and the voice assistant reach through Android's media
 * session, as for any music app - with the service's page in the car doing the playing
 * ([WebMusicPlayer]). No library to browse here (the page is that), and no notification of its own:
 * the page plays inside myCar, which the drive's own service keeps going.
 *
 * One class per service (below): the car tells its music apps apart by their component.
 */
@OptIn(UnstableApi::class)
abstract class WebMusicMediaService(private val serviceId: String) : MediaLibraryService() {
    private lateinit var player: WebPlayer
    private lateinit var session: MediaLibrarySession

    override fun onCreate() {
        super.onCreate()
        val service = checkNotNull(WebMusic.byId(serviceId))
        player = WebPlayer(mainLooper, service)
        val openMyCar = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MyCarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, Callback())
            // One media session per service, and Xonos inside myCar has one with the default id.
            .setId("web-music-$serviceId")
            .setSessionActivity(openMyCar)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    // No notification, and so never in the foreground on its own: the drive's service is.
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) = Unit

    override fun onDestroy() {
        session.release()
        player.release()
        super.onDestroy()
    }

    private inner class Callback : MediaLibrarySession.Callback {
        // Only myCar itself can connect (the service isn't exported): every button, not just looking.
        // No library search: the page searches ("play X" comes as a search to play, see WebPlayer);
        // offered, a library search would never answer, and myCar's voice "play X" waited on it.
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> = Futures.immediateFuture(
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
                        .remove(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)
                        .build(),
                )
                .build(),
        )

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(ROOT, params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))

        // The voice assistant's "play X" (a search, as Android Auto asks): to the player as asked,
        // where the page does the searching.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))

        // Play with nothing said by the page yet: nothing to bring back from here; the page plays
        // what it has (no warning in the log at each such Play).
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(ImmutableList.of(), C.INDEX_UNSET, C.TIME_UNSET))
    }

    private companion object {
        val ROOT: MediaItem = MediaItem.Builder()
            .setMediaId("root")
            .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
            .build()
    }
}

class SpotifyMusicService : WebMusicMediaService("spotify")
class YouTubeMusicService : WebMusicMediaService("youtube-music")
class AppleMusicService : WebMusicMediaService("apple-music")
class AmazonMusicService : WebMusicMediaService("amazon-music")
class DeezerMusicService : WebMusicMediaService("deezer")
class TidalMusicService : WebMusicMediaService("tidal")
class SoundCloudMusicService : WebMusicMediaService("soundcloud")
class QobuzMusicService : WebMusicMediaService("qobuz")

/** The player class of the service [id] (one of [WebMusic.CATALOG]'s). */
internal fun serviceClass(id: String): Class<out WebMusicMediaService> = when (id) {
    "spotify" -> SpotifyMusicService::class.java
    "youtube-music" -> YouTubeMusicService::class.java
    "apple-music" -> AppleMusicService::class.java
    "amazon-music" -> AmazonMusicService::class.java
    "deezer" -> DeezerMusicService::class.java
    "tidal" -> TidalMusicService::class.java
    "soundcloud" -> SoundCloudMusicService::class.java
    "qobuz" -> QobuzMusicService::class.java
    else -> error("no web music service $id")
}

/**
 * The song on a service's page as a Media3 player: what the page last said ([WebMusicPlayer]), and
 * every button sent back to the page. Previous and next always reach the service, which decides as
 * for a computer's media keys - the song from its start, or the one before.
 */
@OptIn(UnstableApi::class)
internal class WebPlayer(looper: Looper, private val service: WebMusicService) : SimpleBasePlayer(looper) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val page = WebMusicPlayer.state(service.id)

    /** The page's word the player shows, to tell a jump in its position (a seek on the page). */
    private var shown: WebPlayerState? = null

    init {
        scope.launch { page.collect { invalidateState() } }
    }

    override fun getState(): State {
        val s = page.value
        val now = SystemClock.elapsedRealtime()
        val before = shown
        shown = s
        val builder = State.Builder()
            .setAvailableCommands(commands(s))
            .setPlayWhenReady(s.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE)
        // Something on the page even without a name for it (a song playing, or its cover): shown,
        // under the service's name, so the dock and the car's buttons have it.
        if (!s.hasTrack && !s.playing && s.artUrl.isEmpty()) {
            return builder.setPlaylist(emptyList()).setPlaybackState(Player.STATE_IDLE).build()
        }
        val position = s.positionAt(now)
        builder
            .setPlaylist(listOf(item(s)))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(position)
            .setPlaybackState(Player.STATE_READY)
        if (before != null && before.key == s.key && abs(before.positionAt(now) - position) > JUMP_MS) {
            builder.setPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL, position)
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        if (playWhenReady) WebMusicPlayer.act(service, "play") { it.playing } else WebMusicPlayer.act(service, "pause") { !it.playing }

    override fun handleStop(): ListenableFuture<*> = WebMusicPlayer.act(service, "pause") { !it.playing }

    /** Only a search comes here ("play X"): the page searches and plays what it finds first. */
    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        val query = mediaItems.firstNotNullOfOrNull { it.requestMetadata.searchQuery?.toString()?.trim()?.ifEmpty { null } }
        if (query != null) WebMusicPlayer.search(service, query)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val before = page.value.key
        return when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                WebMusicPlayer.act(service, "nexttrack") { it.key != before }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ->
                WebMusicPlayer.act(service, "previoustrack") { it.key != before || it.positionAt(SystemClock.elapsedRealtime()) < RESTART_MS }
            else -> {
                val target = positionMs.coerceAtLeast(0)
                WebMusicPlayer.act(service, "seekto", target / 1000.0) { abs(it.positionAt(SystemClock.elapsedRealtime()) - target) < JUMP_MS }
            }
        }
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

    private fun commands(s: WebPlayerState): Player.Commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
        )
        .addIf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, s.canSeek && s.durationMs > 0)
        // "Play X" for the voice assistant, where the service has a search.
        .addIf(Player.COMMAND_SET_MEDIA_ITEM, service.search != null)
        .build()

    private fun item(s: WebPlayerState): MediaItemData {
        val metadata = MediaMetadata.Builder()
            .setTitle(s.title.ifEmpty { service.name })
            .setArtist(s.artist.ifEmpty { null })
            .setAlbumTitle(s.album.ifEmpty { null })
            .setArtworkUri(s.artUrl.ifEmpty { null }?.let(Uri::parse))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        return MediaItemData.Builder(s.key)
            .setMediaItem(MediaItem.Builder().setMediaId(s.key).setMediaMetadata(metadata).build())
            .setDurationUs(if (s.durationMs > 0) s.durationMs * 1000 else C.TIME_UNSET)
            .setIsSeekable(s.canSeek)
            .build()
    }

    private companion object {
        /** A position this far from where it should be has jumped. */
        const val JUMP_MS = 2_000L

        /** Before this far into a song, "previous" is taken as done once it's there. */
        const val RESTART_MS = 3_000L
    }
}
