package io.github.sirionero.mycar.car

import android.Manifest
import android.provider.ContactsContract
import androidx.car.app.CarToast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.PermissionActivity
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.GlassTile
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone
import io.github.sirionero.mycar.home.HaConnection
import io.github.sirionero.mycar.home.HomeAssistant
import io.github.sirionero.mycar.speech.VoiceLanguages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * What the Jolly key does, chosen in the car: the kinds of command as big tiles, then whom to call,
 * which assistant, what to run in Home Assistant, which command or which app, a tap each, as the
 * other settings. What needs writing - a place, a command of one's own - is written on the phone
 * (Settings > Car screen > Jolly): here it's only picked. Opened by holding the key, or by tapping it
 * while nothing is chosen; a choice closes it, and the car says what the key does now.
 */
@Composable
internal fun JollyScreen() {
    val navigator = LocalCarNavigator.current
    val context = LocalContext.current
    val current by JollySetting.action.collectAsState()
    var kind by remember { mutableStateOf<JollyKind?>(null) }
    // The contact whose numbers are listed, once picked from several.
    var person by remember { mutableStateOf<String?>(null) }
    // The car's Back, and the header's, go back a step first.
    BackCloses(kind != null) { if (person != null) person = null else kind = null }

    val choose: (JollyAction) -> Unit = { action ->
        JollySetting.set(action)
        MyCarLog.i("jolly: now ${action.kind.name.lowercase()}, chosen in the car")
        CarToasts.show(Jolly, Words.get(R.string.jolly_does, Words.get(R.string.jolly_name), Jolly.title(context, action)), CarToast.LENGTH_LONG)
        navigator.close()
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = MyCarSpace.XL, vertical = MyCarSpace.L),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.L),
    ) {
        ScreenHeader(person ?: kind?.let { kindLabel(it) } ?: stringResource(R.string.jolly_name))
        when (kind) {
            null -> Kinds(current) { kind = it }
            JollyKind.CALL -> ContactsPick(current, person, onPerson = { person = it }, onPick = choose)
            JollyKind.AI -> AssistantsPick(current, choose)
            JollyKind.HOME -> HomePick(current, choose)
            JollyKind.NAVIGATE -> PlacePick(current) { navigator.close() }
            JollyKind.SAY -> CommandsPick(current, choose)
            JollyKind.APP -> AppsPick(current, choose)
        }
    }
}

/** A kind of command's name, as its tile in the car and its choice on the phone say it. */
@Composable
internal fun kindLabel(kind: JollyKind): String = stringResource(
    when (kind) {
        JollyKind.CALL -> R.string.jolly_kind_call
        JollyKind.AI -> R.string.jolly_kind_ai
        JollyKind.HOME -> R.string.jolly_kind_home
        JollyKind.NAVIGATE -> R.string.jolly_kind_navigate
        JollyKind.SAY -> R.string.jolly_kind_say
        JollyKind.APP -> R.string.jolly_kind_app
    },
)

/** A kind of command's picture: myCar's own glass tiles, Plancia's icon for Home Assistant. */
@Composable
internal fun KindIcon(kind: JollyKind, size: androidx.compose.ui.unit.Dp) {
    when (kind) {
        JollyKind.CALL -> GlassTile(MyCarPalette.Positive, R.drawable.sym_call, size)
        JollyKind.AI -> GlassTile(MyCarPalette.Indigo, R.drawable.sym_record_voice_over, size)
        JollyKind.HOME -> AppIcon(null, BuiltInApps.Plancia.id, size)
        JollyKind.NAVIGATE -> GlassTile(MyCarPalette.Accent, R.drawable.sym_navigation, size)
        JollyKind.SAY -> GlassTile(MyCarPalette.Warning, R.drawable.sym_mic, size)
        JollyKind.APP -> GlassTile(MyCarPalette.Graphite, R.drawable.sym_grid_view, size)
    }
}

/**
 * The kinds, as tiles of the same width in rows (one row on a wide screen, two at 800x480), the one
 * the key runs now lit; under them what it does now, and that the phone can choose it too.
 */
@Composable
private fun Kinds(current: JollyAction?, onKind: (JollyKind) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MyCarSpace.L)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = ((maxWidth + MyCarSpace.M) / (ChoiceWidth + MyCarSpace.M)).toInt().coerceIn(2, JollyKind.entries.size)
            Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
                JollyKind.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
                        row.forEach { kind ->
                            Box(Modifier.weight(1f)) {
                                Choice(kindLabel(kind), current?.kind == kind, onClick = { onKind(kind) }) { KindIcon(kind, KindIconSize) }
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        current?.let { action ->
            Text(
                stringResource(R.string.jolly_now, Jolly.title(context, action)),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.jolly_choose_intro, stringResource(R.string.jolly_name)),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** One line of a list to pick from: a car-sized row, lit when it's what the key does now. */
@Composable
private fun PickLine(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        modifier = Modifier.fillMaxWidth().heightIn(min = LineHeight),
    ) {
        Row(
            Modifier.padding(horizontal = MyCarSpace.L, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyCarSpace.L),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A line of explanation under a list, or in its place. */
@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The phone's contacts, favourites first, one line a person: one number is chosen at once, several
 * are listed for [person]. Without the permissions, the question goes to the phone, as the Phone's does.
 */
@Composable
private fun ContactsPick(current: JollyAction?, person: String?, onPerson: (String?) -> Unit, onPick: (JollyAction) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(hasCallPermissions(context)) }
    if (!allowed) {
        LaunchedEffect(Unit) {
            while (!hasCallPermissions(context)) delay(1_000)
            allowed = true
        }
        Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
            Note(stringResource(R.string.dialer_permissions))
            GsButton(tone = GsTone.Tonal, onClick = { PermissionActivity.ask(context, CALL_PERMISSIONS) }) {
                Text(stringResource(R.string.dialer_ask_on_phone))
            }
        }
        return
    }
    val loaded by produceState<List<Contact>?>(null, context) {
        value = withContext(Dispatchers.IO) { loadContacts(context) }
    }
    val contacts = loaded
    val call = current as? JollyAction.Call
    when {
        contacts == null -> Note(stringResource(R.string.dialer_loading_contacts))
        contacts.isEmpty() -> Note(stringResource(R.string.dialer_no_contacts))
        person != null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
            items(numbersOf(contacts, person)) { c ->
                PickLine(
                    c.typeLabel ?: shownNumber(c.number),
                    call != null && call.name == c.name && call.number == c.number,
                    subtitle = shownNumber(c.number).takeIf { c.typeLabel != null },
                ) { onPick(JollyAction.Call(c.name, c.number, c.typeLabel.orEmpty())) }
            }
        }
        else -> {
            // One line a person, in the contacts' own order (favourites, then by name).
            val people = remember(contacts) { contacts.groupBy { it.name }.toList() }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
                items(people, key = { it.first }) { (name, numbers) ->
                    val one = numbers.singleOrNull()
                    PickLine(
                        name,
                        call?.name == name,
                        subtitle = if (one != null) listOfNotNull(one.typeLabel, shownNumber(one.number)).joinToString("  ·  ")
                        else pluralStringResource(R.plurals.jolly_numbers, numbers.size, numbers.size),
                        leading = { Initial(name) },
                    ) {
                        if (one != null) onPick(JollyAction.Call(name, one.number, one.typeLabel.orEmpty())) else onPerson(name)
                    }
                }
            }
        }
    }
}

/**
 * The numbers of the contact called [name], the one the phone calls them on first (the default, then
 * the mobile), the way "call Anna" picks one.
 */
internal fun numbersOf(contacts: List<Contact>, name: String): List<Contact> =
    contacts.filter { it.name == name }
        .sortedWith(compareByDescending<Contact> { it.primary }.thenByDescending { it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE })

/** A contact's initial in a circle, as the Phone lists them. */
@Composable
private fun Initial(name: String) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The phone's assistant, then each assistant app on the phone; that they speak on the phone, under them. */
@Composable
private fun AssistantsPick(current: JollyAction?, onPick: (JollyAction) -> Unit) {
    val context = LocalContext.current
    val apps = remember { PhoneAssistants.installed(context) }
    val now = (current as? JollyAction.Assistant)?.app
    Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
            item {
                PickLine(stringResource(R.string.assistant_choice_phone), now == PhoneAssistants.PHONE) {
                    onPick(JollyAction.Assistant(PhoneAssistants.PHONE))
                }
            }
            items(apps, key = { it.packageName }) { app ->
                PickLine(app.label, now == app.packageName) { onPick(JollyAction.Assistant(app.packageName)) }
            }
        }
        Note(stringResource(R.string.jolly_ai_note))
    }
}

/**
 * What Home Assistant offers, read over the drive's connection: scripts, scenes, buttons, switches,
 * lights, gates and locks (see [HomeActs]). Not set up, or not connected right now: it says so.
 */
@Composable
private fun HomePick(current: JollyAction?, onPick: (JollyAction) -> Unit) {
    val config by HomeAssistant.config.collectAsState()
    val connection by HomeAssistant.connection.collectAsState()
    if (!config.isComplete) {
        Note(stringResource(R.string.jolly_home_not_set))
        return
    }
    if (connection !is HaConnection.Connected) {
        Note(stringResource(R.string.jolly_home_offline))
        return
    }
    // Null while asked; the list, or nothing when Home Assistant didn't answer.
    val choices by produceState<Result<List<HomeActs.Choice>>?>(null, connection) {
        value = HomeAssistant.live?.let { client -> askHome { Jolly.homeChoices(client) } } ?: Result.failure(IllegalStateException())
    }
    val now = current as? JollyAction.Home
    val list = choices
    when {
        list == null -> Note(stringResource(R.string.jolly_home_loading))
        list.isFailure -> Note(stringResource(R.string.jolly_home_offline))
        list.getOrNull().orEmpty().isEmpty() -> Note(stringResource(R.string.jolly_home_none))
        else -> Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
            val things = list.getOrNull().orEmpty()
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
                items(things, key = { "${it.entity}/${it.act.id}" }) { thing ->
                    PickLine(
                        stringResource(Jolly.homeTitle(thing.entity, thing.act), thing.name),
                        now != null && now.entity == thing.entity && now.act == thing.act,
                    ) { onPick(JollyAction.Home(thing.entity, thing.act, thing.name)) }
                }
            }
            if (things.any { HomeActs.confirms(it.act) }) Note(stringResource(R.string.jolly_home_unlock_note))
        }
    }
}

/** [ask] Home Assistant, its failure kept rather than thrown; a cancelled ask still ends. */
private suspend fun <T> askHome(ask: suspend () -> T): Result<T> = try {
    Result.success(ask())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    MyCarLog.w("jolly: Home Assistant's things didn't come (${e.javaClass.simpleName})")
    Result.failure(e)
}

/** A place is written on the phone: the one chosen there, if it's the key's now, and where to write one. */
@Composable
private fun PlacePick(current: JollyAction?, onKeep: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
        (current as? JollyAction.Navigate)?.let { place ->
            PickLine(
                place.name.ifBlank { place.place },
                true,
                subtitle = place.place.takeIf { place.name.isNotBlank() },
                onClick = onKeep,
            )
        }
        Note(stringResource(R.string.jolly_navigate_on_phone, stringResource(R.string.jolly_name)))
    }
}

/**
 * Ready-made commands in myCar's language, as the assistant takes them, and the key's own if it was
 * written on the phone; any other is written there.
 */
@Composable
private fun CommandsPick(current: JollyAction?, onPick: (JollyAction) -> Unit) {
    val language = VoiceLanguages.phone().code
    val ready = READY_COMMANDS.map { stringResource(it) }
    val now = current as? JollyAction.Say
    Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
        LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
            if (now != null && now.phrase !in ready) {
                item { PickLine(now.phrase, true) { onPick(now) } }
            }
            items(ready) { phrase ->
                PickLine(phrase, now?.phrase == phrase) { onPick(JollyAction.Say(phrase, language)) }
            }
        }
        Note(stringResource(R.string.jolly_say_on_phone, stringResource(R.string.jolly_name)))
    }
}

/** The ready-made commands, in every language a phrase its phrasebook takes (JollyActionTest). */
internal val READY_COMMANDS = listOf(
    R.string.jolly_say_messages,
    R.string.jolly_say_arrival,
    R.string.jolly_say_call_back,
    R.string.jolly_say_weather,
)

/** The car's apps as the grid shows them, hidden ones too: a tile each. */
@Composable
private fun AppsPick(current: JollyAction?, onPick: (JollyAction) -> Unit) {
    val entries = rememberAllEntries()
    val replaced = rememberReplacedByWeb()
    val vehicleData = rememberVehicleDataShown()
    val order by CarSettings.appOrder.collectAsState()
    val apps = launcherOrder(entries.filterNot(replaced), vehicleData, order)
        .filter { it.componentId != null && it.builtIn !== BuiltInApps.Exit }
        .distinctBy { it.componentId }
    val now = (current as? JollyAction.OpenApp)?.let { pinShown(it.id, entries) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ChoiceWidth),
        horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
    ) {
        items(apps, key = { it.componentId!! }) { app ->
            val id = app.componentId!!
            val name = app.shownLabel
            Choice(name, now == id, onClick = { onPick(JollyAction.OpenApp(id, name)) }) {
                AppIcon(app.icon, app.builtIn?.id, KindIconSize)
            }
        }
    }
}

/**
 * The car Settings' word on the Jolly key: in the dock or not, what it does now, and its chooser -
 * the same as on the phone, Settings > Car screen.
 */
@Composable
internal fun JollyCarSettings() {
    val navigator = LocalCarNavigator.current
    val context = LocalContext.current
    val shown by JollySetting.shown.collectAsState()
    val action by JollySetting.action.collectAsState()
    val name = stringResource(R.string.jolly_name)
    Column(verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
        ChoiceRow(
            stringResource(R.string.car_settings_jolly_key, name),
            listOf(false to stringResource(R.string.common_off), true to stringResource(R.string.common_on)),
            shown,
        ) { JollySetting.setShown(it) }
        Text(
            action?.let { stringResource(R.string.jolly_now, Jolly.title(context, it)) }
                ?: stringResource(R.string.car_settings_jolly_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GsButton(onClick = { navigator.jolly() }, tone = GsTone.Tonal, modifier = Modifier.height(MyCarSize.TouchTarget)) {
            Text(stringResource(R.string.car_settings_jolly_choose), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** What a call needs, as the Phone asks for it. */
private val CALL_PERMISSIONS = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)

/** Every list's line: a finger's height and a bit, with a name and a line under it. */
private val LineHeight = 72.dp

/** The pictures on the tiles, as on the pages' chooser. */
private val KindIconSize = 52.dp
