package io.github.sirionero.mycar.car

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.audio.CarAudioRoute
import io.github.sirionero.mycar.media.CarMedia

/**
 * Autoplay on Home: once the music app is connected, and again as the sound's way to the car
 * changes, since the drive's own pick waits for it (see [CarServices.autoplayIfDue]). Apart from
 * CarHome, so those changes recompose only this.
 */
@Composable
internal fun AutoplayWhenReady(phase: CarMedia.Phase) {
    val deciding by CarAudioRoute.deciding.collectAsStateWithLifecycle()
    val throughMyCar by CarSettings.pipeAudio.collectAsStateWithLifecycle()
    val capture by CarAudio.capture.collectAsStateWithLifecycle()
    val direct by CarAudio.direct.collectAsStateWithLifecycle()
    val bluetoothOut by CarAudioRoute.bluetoothOut.collectAsStateWithLifecycle()
    LaunchedEffect(phase, deciding, throughMyCar, capture, direct, bluetoothOut) {
        if (phase == CarMedia.Phase.READY) CarServices.autoplayIfDue()
    }
}
