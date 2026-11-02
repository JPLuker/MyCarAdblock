package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R

/**
 * Asks once per car whether to pair it over Bluetooth, then waits while the phone shows its
 * pairing dialog. See [CarBluetooth]. With no pairing to do, says when the phone isn't connected
 * to the car over Bluetooth (see [BluetoothAudioCheck]), or else when the phone's location is off (see
 * [LocationCheck]). Unlike the call prompt, a tap outside the card does nothing:
 * the driver picks Pair or Not now.
 */
@Composable
internal fun BluetoothPairOverlay() {
    val prompt by CarBluetooth.prompt.collectAsState()
    val bluetoothAudio = !CarSettings.pipeAudio.collectAsState().value
    when (prompt) {
        CarBluetooth.Prompt.NONE -> {
            val missing by BluetoothAudioCheck.missing.collectAsState()
            val dismissed by BluetoothAudioCheck.carDismissed.collectAsState()
            val warn by CarSettings.bluetoothWarning.collectAsState()
            val locationOff by LocationCheck.off.collectAsState()
            val locationDismissed by LocationCheck.carDismissed.collectAsState()
            val bluetoothDue = missing && warn && !dismissed
            val locationDue = locationOff && !locationDismissed
            // The map running with no position to borrow: Android refused myCar's location service
            // (the phone locked as the map connected), or myCar has no location permission.
            val noPosition by io.github.sirionero.mycar.host.LocationKeepAlive.noPosition.collectAsState()
            val navStatus by CarServices.nav.status.collectAsState()
            val positionDue = noPosition != io.github.sirionero.mycar.host.LocationKeepAlive.NoPosition.NONE &&
                navStatus.phase == io.github.sirionero.mycar.host.CarAppConnection.Phase.RUNNING
            // These cover the map with a veil until OK: they wait for the car to stop, as
            // What's new does, instead of coming up mid-drive when Bluetooth drops. The car's own
            // driving status says so, else the phone's GPS (see CarDriving).
            val moving = if (bluetoothDue || locationDue || positionDue) !rememberVideoAllowed() else false
            if (moving) return
            if (bluetoothDue) PairCard(
                title = stringResource(R.string.bluetooth_not_connected_title),
                body = stringResource(
                    if (bluetoothAudio) R.string.bluetooth_not_connected_calls_music else R.string.bluetooth_not_connected_calls,
                ),
                secondary = stringResource(R.string.bluetooth_dont_show_again) to BluetoothAudioCheck::neverAgain,
                primary = stringResource(R.string.common_ok) to BluetoothAudioCheck::dismissOnCar,
                onBack = BluetoothAudioCheck::dismissOnCar,
            ) else if (locationDue) PairCard(
                title = stringResource(R.string.bluetooth_location_off_title),
                body = stringResource(R.string.bluetooth_location_off_body),
                secondary = stringResource(R.string.bluetooth_dont_show_again) to LocationCheck::neverAgain,
                primary = stringResource(R.string.common_ok) to LocationCheck::dismissOnCar,
                onBack = LocationCheck::dismissOnCar,
            ) else if (positionDue) PairCard(
                title = stringResource(R.string.car_map_no_position_title),
                body = stringResource(
                    if (noPosition == io.github.sirionero.mycar.host.LocationKeepAlive.NoPosition.NO_PERMISSION) {
                        R.string.car_map_no_position_permission
                    } else {
                        R.string.car_map_no_position_locked
                    },
                ),
                secondary = null,
                primary = stringResource(R.string.common_ok) to { io.github.sirionero.mycar.host.LocationKeepAlive.dismissNoPosition() },
                onBack = { io.github.sirionero.mycar.host.LocationKeepAlive.dismissNoPosition() },
            )
        }
        CarBluetooth.Prompt.ASK -> PairCard(
            title = stringResource(R.string.bluetooth_pair_title),
            body = stringResource(if (bluetoothAudio) R.string.bluetooth_pair_body_calls_music else R.string.bluetooth_pair_body_calls),
            secondary = stringResource(R.string.common_not_now) to CarBluetooth::notNow,
            primary = stringResource(R.string.bluetooth_pair) to CarBluetooth::pair,
            // Not now would turn this car down for good: Back leaves the choice to the driver.
            onBack = {},
        )
        CarBluetooth.Prompt.PAIRING -> PairCard(
            title = stringResource(R.string.bluetooth_confirm_title),
            body = stringResource(R.string.bluetooth_confirm_body),
            secondary = null,
            primary = stringResource(R.string.bluetooth_hide) to CarBluetooth::hide,
            onBack = CarBluetooth::hide,
        )
    }
}

@Composable
private fun PairCard(title: String, body: String, secondary: Pair<String, () -> Unit>?, primary: Pair<String, () -> Unit>, onBack: () -> Unit) {
    // The car's Back answers the card (OK for this trip, Hide), never the screen under it.
    CardBackKey(CardBack::pair, onBack)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = {},
        ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(0.85f).padding(16.dp),
        ) {
            Column(
                Modifier.padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    secondary?.let { (label, onClick) ->
                        GsButton(onClick = onClick, tone = GsTone.Outline, modifier = Modifier.weight(1f).height(56.dp)) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    GsButton(onClick = primary.second, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(primary.first, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
