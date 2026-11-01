package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.car.CarSettings

/**
 * On Home during a drive, while Android's question for the music through myCar has been turned
 * down: the car stays silent until it's asked again, which this does. The microphone blocked for
 * good has its own dialog (MicBlockedDialog), and myCar's own music (Xonos) needs no question.
 */
@Composable
internal fun AudioDeniedCard() {
    val capture by CarAudio.capture.collectAsStateWithLifecycle()
    val pipe by CarSettings.pipeAudio.collectAsStateWithLifecycle()
    val blocked by CarAudio.micBlocked.collectAsStateWithLifecycle()
    if (!pipe || blocked || capture != CarAudio.Capture.DENIED || CarAudio.consentWaits()) return
    // The yellow of the Bluetooth card: music is missing, nothing is broken.
    val dark = phoneDark()
    val container = if (dark) Color(0xFF4A3B00) else Color(0xFFFFE08A)
    val content = if (dark) Color(0xFFFFE08A) else Color(0xFF3A2E00)
    Spacer(Modifier.height(16.dp))
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.audio_not_reaching_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.audio_not_reaching_text), style = MaterialTheme.typography.bodyMedium)
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = content, contentColor = container),
                onClick = { CarAudio.askAgain() },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text(stringResource(R.string.audio_ask_again)) }
        }
    }
}
