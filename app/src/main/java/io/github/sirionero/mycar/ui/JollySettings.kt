package io.github.sirionero.mycar.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.BuiltInApps
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.car.Contact
import io.github.sirionero.mycar.car.HomeActs
import io.github.sirionero.mycar.car.Jolly
import io.github.sirionero.mycar.car.JollyAction
import io.github.sirionero.mycar.car.JollyKind
import io.github.sirionero.mycar.car.JollySetting
import io.github.sirionero.mycar.car.KindIcon
import io.github.sirionero.mycar.car.PhoneAssistants
import io.github.sirionero.mycar.car.READY_COMMANDS
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.kindLabel
import io.github.sirionero.mycar.car.launcherOrder
import io.github.sirionero.mycar.car.loadContacts
import io.github.sirionero.mycar.car.numbersOf
import io.github.sirionero.mycar.car.pinShown
import io.github.sirionero.mycar.car.rememberAllEntries
import io.github.sirionero.mycar.car.rememberReplacedByWeb
import io.github.sirionero.mycar.car.rememberVehicleDataShown
import io.github.sirionero.mycar.car.shownNumber
import io.github.sirionero.mycar.home.HomeAssistant
import io.github.sirionero.mycar.speech.VoiceLanguages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Jolly key from the phone, as the car's chooser has it (car/JollyScreen.kt), on the same key:
 * whether the dock shows it, and what it does - a kind, then whom to call, which assistant, what to run
 * in Home Assistant, a place or a command written here with the phone's keyboard, or an app.
 */
@Composable
internal fun JollySection() {
    val context = LocalContext.current
    val action by JollySetting.action.collectAsState()
    val shown by JollySetting.shown.collectAsState()
    var kinds by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf<JollyKind?>(null) }
    val name = stringResource(R.string.jolly_name)
    SettingsSection(name, footer = stringResource(R.string.settings_jolly_footer)) {
        SwitchRow(
            stringResource(R.string.car_settings_jolly_key, name),
            shown,
            stringResource(R.string.settings_jolly_key_detail),
        ) { JollySetting.setShown(it) }
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_jolly_action),
            Modifier.clickable { kinds = true },
            subtitle = action?.let { Jolly.title(context, it) } ?: stringResource(R.string.settings_jolly_none),
        )
    }
    if (kinds) {
        val labels = JollyKind.entries.map { kindLabel(it) }
        PickDialog(
            stringResource(R.string.settings_jolly_kind),
            JollyKind.entries.mapIndexed { i, k -> Pick<JollyKind?>(k, labels[i]) },
            action?.kind,
            onPick = {
                kinds = false
                kind = it
            },
            onDismiss = { kinds = false },
            leading = { k -> k?.let { KindIcon(it, 32.dp) } },
        )
    }
    val close = { kind = null }
    val pick = { chosen: JollyAction ->
        JollySetting.set(chosen)
        kind = null
    }
    when (kind) {
        null -> Unit
        JollyKind.CALL -> ContactDialog(action, pick, close)
        JollyKind.AI -> AssistantDialog(action, pick, close)
        JollyKind.HOME -> HomeDialog(action, pick, close)
        JollyKind.NAVIGATE -> PlaceDialog(action, pick, close)
        JollyKind.SAY -> CommandDialog(action, pick, close)
        JollyKind.APP -> AppDialog(action, pick, close)
    }
}

/** A choice in one of the Jolly's lists: a round button, the name, and a line under it. */
@Composable
private fun ChoiceLine(title: String, selected: Boolean, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DialogNote(text: String) {
    Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The phone's contacts, favourites first: a person with one number is chosen at once, one with
 * several lists them. Without the contacts permission, the one tap that asks for it.
 */
@Composable
private fun ContactDialog(current: JollyAction?, onPick: (JollyAction) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var person by remember { mutableStateOf<String?>(null) }
    val contacts by produceState<List<Contact>?>(null, allowed) {
        value = if (allowed) withContext(Dispatchers.IO) { loadContacts(context) } else emptyList()
    }
    val call = current as? JollyAction.Call
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(person ?: stringResource(R.string.jolly_kind_call)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val list = contacts
                when {
                    !allowed -> {
                        Text(stringResource(R.string.settings_jolly_contacts_permission), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { ask.launch(Manifest.permission.READ_CONTACTS) }) { Text(stringResource(R.string.common_allow)) }
                    }
                    list == null -> Text(stringResource(R.string.dialer_loading_contacts))
                    list.isEmpty() -> Text(stringResource(R.string.dialer_no_contacts))
                    person != null -> numbersOf(list, person!!).forEach { c ->
                        ChoiceLine(
                            c.typeLabel ?: shownNumber(c.number),
                            call != null && call.name == c.name && call.number == c.number,
                            subtitle = shownNumber(c.number).takeIf { c.typeLabel != null },
                        ) { onPick(JollyAction.Call(c.name, c.number, c.typeLabel.orEmpty())) }
                    }
                    else -> list.groupBy { it.name }.forEach { (name, numbers) ->
                        val one = numbers.singleOrNull()
                        ChoiceLine(
                            name,
                            call?.name == name,
                            subtitle = if (one != null) listOfNotNull(one.typeLabel, shownNumber(one.number)).joinToString("  ·  ")
                            else pluralStringResource(R.plurals.jolly_numbers, numbers.size, numbers.size),
                        ) {
                            if (one != null) onPick(JollyAction.Call(name, one.number, one.typeLabel.orEmpty())) else person = name
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (person != null) person = null else onDismiss() }) {
                Text(stringResource(if (person != null) R.string.common_back else R.string.common_cancel))
            }
        },
    )
}

/** The phone's assistant and each assistant app, with where their voice goes. */
@Composable
private fun AssistantDialog(current: JollyAction?, onPick: (JollyAction) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { PhoneAssistants.installed(context) }
    PickDialog(
        stringResource(R.string.jolly_kind_ai),
        listOf(Pick(PhoneAssistants.PHONE, stringResource(R.string.assistant_choice_phone))) + apps.map { Pick(it.packageName, it.label) },
        (current as? JollyAction.Assistant)?.app.orEmpty(),
        onPick = { onPick(JollyAction.Assistant(it)) },
        onDismiss = onDismiss,
        note = stringResource(R.string.jolly_ai_note),
    )
}

/**
 * What Home Assistant offers (see HomeActs), read on a connection of the phone's own, after Android
 * lets myCar onto the home network where it's needed (Android 17).
 */
@Composable
private fun HomeDialog(current: JollyAction?, onPick: (JollyAction) -> Unit, onDismiss: () -> Unit) {
    val config by HomeAssistant.config.collectAsState()
    val askLocalNetwork = rememberLocalNetworkAsk()
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(config) { if (config.isComplete) askLocalNetwork(config.urls) { asked = true } }
    val things by produceState<Result<List<HomeActs.Choice>>?>(null, config, asked) {
        if (!config.isComplete || !asked) return@produceState
        value = try {
            Result.success(Jolly.homeChoices(config))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    val now = current as? JollyAction.Home
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jolly_kind_home)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val result = things
                when {
                    !config.isComplete -> Text(stringResource(R.string.jolly_home_not_set))
                    result == null -> Text(stringResource(R.string.jolly_home_loading))
                    result.isFailure -> Text(
                        stringResource(R.string.settings_jolly_home_failed, result.exceptionOrNull()?.message.orEmpty()),
                    )
                    result.getOrNull().orEmpty().isEmpty() -> Text(stringResource(R.string.jolly_home_none))
                    else -> {
                        val list = result.getOrNull().orEmpty()
                        list.forEach { thing ->
                            ChoiceLine(
                                stringResource(Jolly.homeTitle(thing.entity, thing.act), thing.name),
                                now != null && now.entity == thing.entity && now.act == thing.act,
                                subtitle = thing.entity,
                            ) { onPick(JollyAction.Home(thing.entity, thing.act, thing.name)) }
                        }
                        if (list.any { HomeActs.confirms(it.act) }) DialogNote(stringResource(R.string.jolly_home_unlock_note))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** A place, as the driver would write it in a map app, and the name the car shows for it. */
@Composable
private fun PlaceDialog(current: JollyAction?, onPick: (JollyAction) -> Unit, onDismiss: () -> Unit) {
    val now = current as? JollyAction.Navigate
    var place by remember { mutableStateOf(now?.place.orEmpty()) }
    var placeName by remember { mutableStateOf(now?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jolly_kind_navigate)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    place,
                    { place = it.take(MAX_WRITTEN) },
                    label = { Text(stringResource(R.string.settings_jolly_place)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    placeName,
                    { placeName = it.take(MAX_NAME) },
                    label = { Text(stringResource(R.string.settings_jolly_place_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.settings_jolly_place_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(JollyAction.Navigate(place.trim(), placeName.trim())) }, enabled = place.isNotBlank()) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * A command for myCar's assistant, written as it would be said, in myCar's language (the one it's
 * read in, in the car); the ready-made ones a tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandDialog(current: JollyAction?, onPick: (JollyAction) -> Unit, onDismiss: () -> Unit) {
    var phrase by remember { mutableStateOf((current as? JollyAction.Say)?.phrase.orEmpty()) }
    val ready = READY_COMMANDS.map { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jolly_kind_say)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    phrase,
                    { phrase = it.take(MAX_WRITTEN) },
                    label = { Text(stringResource(R.string.settings_jolly_phrase)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ready.forEach { example -> AssistChip(onClick = { phrase = example }, label = { Text(example) }) }
                }
                Text(
                    stringResource(R.string.settings_jolly_phrase_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(JollyAction.Say(phrase.trim(), VoiceLanguages.phone().code)) },
                enabled = phrase.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The car's apps as its grid has them (hidden ones too), each with its icon. */
@Composable
private fun AppDialog(current: JollyAction?, onPick: (JollyAction) -> Unit, onDismiss: () -> Unit) {
    val entries = rememberAllEntries()
    val replaced = rememberReplacedByWeb()
    val vehicleData = rememberVehicleDataShown()
    val order by CarSettings.appOrder.collectAsState()
    val apps = launcherOrder(entries.filterNot(replaced), vehicleData, order)
        .filter { it.componentId != null && it.builtIn !== BuiltInApps.Exit }
        .distinctBy { it.componentId }
    val now = (current as? JollyAction.OpenApp)?.let { pinShown(it.id, entries) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.jolly_kind_app)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                apps.forEach { app ->
                    val id = app.componentId ?: return@forEach
                    val name = app.shownLabel
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(now == id, role = Role.RadioButton) { onPick(JollyAction.OpenApp(id, name)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = now == id, onClick = null)
                        AppIcon(app.icon, app.builtIn?.id, 32.dp)
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The longest a place or a command written here gets: a sentence, not a page. */
private const val MAX_WRITTEN = 200

/** The longest a place's name for the car gets: a word or two on a small screen. */
private const val MAX_NAME = 40
