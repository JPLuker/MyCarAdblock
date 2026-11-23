package io.github.sirionero.mycar.media

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.media.MediaMetadata
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.notify.MyCarNotificationListener
import kotlinx.coroutines.delay

/** The phone's active playback session, independent of the car's selected media app. */
object PhoneMediaMirror {
    private const val PREF = "phone_media_mirror"
    private const val BLOCKED = "blocked_packages"
    fun blocked(context: Context): Set<String> =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getStringSet(BLOCKED, emptySet()).orEmpty().toSet()
    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        val updated = blocked(context).toMutableSet()
        if (blocked) updated.add(packageName) else updated.remove(packageName)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putStringSet(BLOCKED, updated).apply()
    }
    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
    }
    fun allSessions(context: Context): List<MediaController> = try {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MyCarNotificationListener::class.java))

    } catch (e: SecurityException) {
        io.github.sirionero.mycar.MyCarLog.w("phone media: notification listener access denied: ${e.message}")
        emptyList()
    }
    fun sessions(context: Context): List<MediaController> = allSessions(context).filter { it.packageName !in blocked(context) }
    fun current(context: Context): MediaController? {
        val all = sessions(context)
        return all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_BUFFERING }
            ?: all.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
    }
}

@Composable
fun PhoneMediaMirrorPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var revision by remember { mutableStateOf(0) }
    LaunchedEffect(context) {
        while (true) {
            controller = PhoneMediaMirror.current(context)
            revision++
            delay(1000)
        }
    }
    val current = controller
    val metadata = current?.metadata
    val state = current?.playbackState
    // revision intentionally triggers a refresh for track changes on the same session.
    val title = remember(revision) { metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty() }
    val artist = remember(revision) { metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty() }
    val artwork: Bitmap? = remember(revision) { metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART) }
    val appName = remember(revision) { current?.packageName?.let { pkg -> runCatching { val info = context.packageManager.getApplicationInfo(pkg, 0); context.packageManager.getApplicationLabel(info).toString() }.getOrDefault(pkg) }.orEmpty() }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (current == null) {
            Text("No phone media playing")
        } else {
            if (artwork != null) Image(artwork.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.size(120.dp), contentScale = ContentScale.Crop)
            Spacer(Modifier.size(8.dp))
            Text(appName, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            Text(title.ifBlank { "Phone media" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (artist.isNotBlank()) Text(artist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = state != null && (state.actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L, onClick = { current.transportControls.skipToPrevious() }) { Text("Previous") }
                Button(onClick = {
                    if (state?.state == PlaybackState.STATE_PLAYING) current.transportControls.pause()
                    else current.transportControls.play()
                }) { Text(if (state?.state == PlaybackState.STATE_PLAYING) "Pause" else "Play") }
                Button(enabled = state != null && (state.actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L, onClick = { current.transportControls.skipToNext() }) { Text("Next") }
            }
        }
    }
}
