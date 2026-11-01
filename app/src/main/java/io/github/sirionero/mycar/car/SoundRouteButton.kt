package io.github.sirionero.mycar.car

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.sirionero.mycar.R
import androidx.car.app.CarToast
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.audio.AudioRoute

/**
 * In the player: which way the sound goes to the car now, Bluetooth or through myCar, and a tap
 * to send it the other way. The choice is kept for this car (see [io.github.sirionero.mycar.audio.CarRoutes]).
 */
@Composable
internal fun SoundRouteButton(size: Dp) {
    val throughMyCar by CarSettings.pipeAudio.collectAsState()
    PlainButton(
        onClick = {
            val toMyCar = !throughMyCar
            CarSettings.setAudioRoute(if (toMyCar) AudioRoute.CAR_LINK else AudioRoute.BLUETOOTH)
            // One tap moves the sound, and it stays that way for this car: the car says where it goes now.
            CarToasts.show(
                SoundRoute,
                Words.get(if (toMyCar) R.string.car_player_sound_now_mycar else R.string.car_player_sound_now_bluetooth),
                CarToast.LENGTH_SHORT,
            )
        },
        description = stringResource(if (throughMyCar) R.string.car_player_sound_mycar else R.string.car_player_sound_bluetooth),
        size = size,
    ) {
        Icon(
            painterResource(if (throughMyCar) R.drawable.sym_directions_car else R.drawable.sym_bluetooth),
            null,
            Modifier.size(size * 0.52f),
        )
    }
}

/** Who the route's toast belongs to, for [CarToasts]. */
private object SoundRoute
