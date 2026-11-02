package io.github.sirionero.mycar.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarAudio

/**
 * On the player, while the music that has to go through myCar can't: Android's question for the
 * sound was turned down, and that one "no" left the car silent for the whole drive with nothing on
 * its screen to say so. A tap asks again - on the phone, at once if myCar is open there, or as soon
 * as it is (the car says so).
 */
@Composable
internal fun MusicNotReaching(playing: Boolean, modifier: Modifier = Modifier) {
    val capture by CarAudio.capture.collectAsState()
    val pipe by CarSettings.pipeAudio.collectAsState()
    val blocked by CarAudio.micBlocked.collectAsState()
    // Android's question still waiting (unanswered on the phone, or asked with myCar not in front):
    // said after a moment, not during the question answered at once.
    var waitedLong by remember { mutableStateOf(false) }
    LaunchedEffect(capture) {
        waitedLong = false
        if (capture == CarAudio.Capture.ASKING) {
            kotlinx.coroutines.delay(ASKING_SAID_AFTER_MS)
            waitedLong = true
        }
    }
    // myCar's own music (Xonos) needs no question: it reaches the car all the same.
    if (!playing || !pipe || CarAudio.consentWaits()) return
    val title = when {
        capture == CarAudio.Capture.DENIED && blocked -> R.string.settings_mic_blocked_title
        capture == CarAudio.Capture.DENIED -> R.string.audio_not_reaching_title
        capture == CarAudio.Capture.ASKING && waitedLong -> R.string.activity_toast_open_for_music
        else -> return
    }
    // Only a "no" can be asked again from here: the microphone blocked is the phone's settings, and
    // a question waiting is answered on the phone.
    val canAsk = capture == CarAudio.Capture.DENIED && !blocked
    Row(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .then(if (canAsk) Modifier.clickable { CarAudio.askAgain() } else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Warning, null, Modifier.size(22.dp), tint = Color(0xFFFFB020))
        Column {
            Text(
                stringResource(title),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (canAsk) {
                Text(
                    stringResource(R.string.audio_ask_again),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
    }
}

/** How long Android's question may wait before the player says the music isn't reaching the car yet. */
private const val ASKING_SAID_AFTER_MS = 5_000L
