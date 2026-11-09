package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.AudioRoute
import io.github.sirionero.mycar.car.CarSettings

/**
 * The car audio step of the setup list: the three ways the phone's sound reaches the car, each
 * with what it means at every drive - Android's question, the car's radio - so the choice is made
 * knowing it. It never holds up Done, and Automatic stays the default. Anything but Bluetooth can
 * bring Android's question at each drive, so the command that stops it for good follows, as in
 * How to connect. The same setting as Settings > Connection > Car audio.
 */
@Composable
internal fun SetupAudioChoice() {
    val route by CarSettings.audioRoute.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(Modifier.selectableGroup()) {
            AudioWay(
                stringResource(R.string.setup_audio_automatic),
                stringResource(R.string.setup_audio_recommended),
                stringResource(R.string.setup_audio_auto_effect),
                selected = route == AudioRoute.AUTO,
            ) { CarSettings.setAudioRoute(AudioRoute.AUTO) }
            AudioWay(
                stringResource(R.string.common_bluetooth),
                null,
                stringResource(R.string.setup_audio_bluetooth_effect),
                selected = route == AudioRoute.BLUETOOTH,
            ) { CarSettings.setAudioRoute(AudioRoute.BLUETOOTH) }
            AudioWay(
                stringResource(R.string.setup_audio_through_mycar),
                null,
                stringResource(R.string.setup_audio_mycar_effect),
                selected = route == AudioRoute.CAR_LINK,
            ) { CarSettings.setAudioRoute(AudioRoute.CAR_LINK) }
        }
        if (route != AudioRoute.BLUETOOTH) {
            Text(
                stringResource(R.string.help_step_audio_prompt),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(R.string.help_step_audio_prompt_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CopyableCommand(PROJECT_MEDIA_COMMAND)
        }
        Text(
            stringResource(R.string.settings_audio_per_car),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** One way: a radio button, its name, and what it means at every drive. The whole row is the touch target. */
@Composable
private fun AudioWay(title: String, tag: String?, effect: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            tag?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Text(effect, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
