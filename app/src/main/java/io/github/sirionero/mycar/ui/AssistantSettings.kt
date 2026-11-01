package io.github.sirionero.mycar.ui

import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.AssistantChoice
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.car.PhoneAssistants

/**
 * Which voice assistant the car's microphone and the steering wheel's voice button start, and how
 * myCar's own listens: the car Settings' choices, on the phone too, on the same keys.
 */
@Composable
internal fun AssistantSection() {
    val context = LocalContext.current
    val current by AssistantChoice.current.collectAsState()
    val systemSpeech by CarSettings.systemSpeech.collectAsState()
    val apps = remember { PhoneAssistants.installed(context) }
    SettingsSection(stringResource(R.string.assistant_picker_title), footer = stringResource(R.string.assistant_picker_footer)) {
        AssistantRow(PhoneAssistants.MYCAR, "myCar", current)
        SettingsDivider()
        AssistantRow(PhoneAssistants.PHONE, stringResource(R.string.assistant_choice_phone), current)
        apps.forEach { app ->
            SettingsDivider()
            AssistantRow(app.packageName, app.label, current)
        }
    }
    SettingsSection {
        SwitchRow(
            stringResource(R.string.car_settings_system_speech),
            systemSpeech,
            stringResource(R.string.car_settings_system_speech_note),
        ) { CarSettings.setSystemSpeech(it) }
    }
}

@Composable
private fun AssistantRow(id: String, label: String, current: String) {
    SettingsRow(
        label,
        Modifier.selectable(id == current, role = Role.RadioButton) { AssistantChoice.set(id) },
        trailing = { RadioButton(selected = id == current, onClick = null) },
    )
}
