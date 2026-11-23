package io.github.sirionero.mycar.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
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
    fun sessions(context: Context): List<MediaController> = try {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MyCarNotificationListener::class.java))
            .filter { it.packageName !in blocked(context) }
    } catch (_: SecurityException) { emptyList() }
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
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        if (current == null) {
            Text("No phone media playing")
        } else {
            Text(title.ifBlank { "Phone media" })
            if (artist.isNotBlank()) Text(artist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { current.transportControls.skipToPrevious() }) { Text("Previous") }
                Button(onClick = {
                    if (state?.state == PlaybackState.STATE_PLAYING) current.transportControls.pause()
                    else current.transportControls.play()
                }) { Text(if (state?.state == PlaybackState.STATE_PLAYING) "Pause" else "Play") }
                Button(onClick = { current.transportControls.skipToNext() }) { Text("Next") }
            }
        }
    }
}
