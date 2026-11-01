package io.github.sirionero.mycar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.CarServices
import io.github.sirionero.mycar.webmusic.WebMusic
import io.github.sirionero.mycar.webmusic.WebMusicAccounts
import io.github.sirionero.mycar.webmusic.WebMusicService
import io.github.sirionero.mycar.webmusic.WebMusicSignInActivity
import io.github.sirionero.mycar.xonos.Xonos

/**
 * Music, on the phone: which music is in the car. Xonos and Spotify, each with a switch, and the
 * other music services' web players, added from a list; all off to start with. On, a service's
 * own settings show under its switch, and it's in the car - a tile on the app grid, a music app for
 * myCar's player beside the map, the dock and the car's buttons. Off, it's nowhere in the car.
 */
@Composable
internal fun MusicSettings() {
    XonosPart()
    WebMusicPart(WebMusic.SPOTIFY)
    val added by WebMusic.added.collectAsState()
    WebMusic.CATALOG.filter { it !== WebMusic.SPOTIFY && it.id in added }.forEach { WebMusicPart(it) }
    AddServiceSection()
}

/** Xonos's switch; on, its own settings (Music Assistant's address and account) right under it. */
@Composable
private fun XonosPart() {
    val context = LocalContext.current
    remember { Xonos.init(context) }
    val on by Xonos.enabled.collectAsState()
    val switch: @Composable () -> Unit = {
        SwitchRow(
            stringResource(R.string.music_in_the_car),
            on,
            stringResource(if (on) R.string.music_xonos_on else R.string.music_xonos_off),
        ) {
            Xonos.setEnabled(context, it)
            CarServices.xonosSwitched(it)
        }
    }
    if (on) {
        XonosSection(top = switch)
    } else {
        SettingsSection("Xonos") { switch() }
    }
}

/** A service's switch; on, its easy way in: sign in on the phone, sign out, and what it needs. */
@Composable
private fun WebMusicPart(service: WebMusicService) {
    val context = LocalContext.current
    val on by WebMusic.on.collectAsState()
    val isOn = service.id in on
    var signOutAsk by remember { mutableStateOf(false) }
    SettingsSection(
        service.name,
        footer = if (isOn && service.needs != 0) stringResource(service.needs) else null,
    ) {
        SwitchRow(
            stringResource(R.string.music_in_the_car),
            isOn,
            stringResource(if (isOn) R.string.music_service_on else R.string.music_service_off),
        ) { WebMusic.setOn(service.id, it) }
        if (isOn) {
            SettingsDivider()
            SettingsRow(
                stringResource(R.string.music_sign_in),
                Modifier.clickable { WebMusicSignInActivity.open(context, service) },
                subtitle = stringResource(R.string.music_sign_in_detail),
                trailing = { Arrow() },
            )
            SettingsDivider()
            SettingsRow(
                stringResource(R.string.music_sign_out),
                Modifier.clickable { signOutAsk = true },
                subtitle = stringResource(R.string.music_sign_out_detail),
            )
        }
    }
    if (signOutAsk) {
        ConfirmDialog(
            title = stringResource(R.string.music_sign_out_title, service.name),
            text = stringResource(R.string.music_sign_out_text, service.name),
            confirm = stringResource(R.string.music_sign_out),
            onConfirm = { WebMusicAccounts.signOut(context, service) },
            onDismiss = { signOutAsk = false },
        )
    }
}

/** "Add a service": the other services' web players, ticked to put each in the car. */
@Composable
private fun AddServiceSection() {
    var open by remember { mutableStateOf(false) }
    SettingsSection(footer = stringResource(R.string.music_add_footer)) {
        SettingsRow(
            stringResource(R.string.music_add),
            Modifier.clickable { open = true },
            icon = Icons.Filled.Add,
            trailing = { Arrow() },
        )
    }
    if (open) {
        val added by WebMusic.added.collectAsState()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.music_add)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    WebMusic.CATALOG.filter { it !== WebMusic.SPOTIFY }.forEach { service ->
                        val checked = service.id in added
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(checked, role = Role.Checkbox) { if (it) WebMusic.add(service.id) else WebMusic.remove(service.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(service.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        stringResource(R.string.music_add_note),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_done)) } },
        )
    }
}

@Composable
private fun Arrow() = Icon(
    Icons.AutoMirrored.Filled.KeyboardArrowRight,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)
