package io.github.sirionero.mycar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.Insets
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.VehicleProfiles
import io.github.sirionero.mycar.car.AppTheme
import io.github.sirionero.mycar.car.CarApps
import io.github.sirionero.mycar.car.CarEnvironment
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.car.MiniPlayerSetting
import io.github.sirionero.mycar.car.NightMode
import io.github.sirionero.mycar.car.SidePaneChoice
import io.github.sirionero.mycar.car.Units
import io.github.sirionero.mycar.car.design.MyCarCarTheme
import io.github.sirionero.mycar.car.design.WallpaperBackground
import io.github.sirionero.mycar.car.design.WallpaperSetting
import io.github.sirionero.mycar.car.design.Wallpapers
import io.github.sirionero.mycar.car.fitsBesideMap
import io.github.sirionero.mycar.car.rememberAllEntries
import io.github.sirionero.mycar.car.theme.GsThemes
import io.github.sirionero.mycar.car.pinShown
import io.github.sirionero.mycar.car.rememberReplacedByWeb

/**
 * The car screen's settings on the phone: whatever the Settings app on the car changes, here too,
 * on the same keys (CarSettings and the choices kept beside it), so a change on one side shows on
 * the other at once, during a drive or not. The voice assistant's are with the voice ones
 * ([AssistantSection]), the car audio with the connection.
 */
@Composable
internal fun CarScreenSettings() {
    CarHomeSection()
    CarPagesSection()
    CarGridSection()
    JollySection()
    CarLookSection()
    CarWallpaperSection()
    CarDriveSection()
    CarWebPageSection()
    CarScreenFitSection()
    CarMoreSection()
}

/** What the car opens on, and what shares Home with the map. */
@Composable
private fun CarHomeSection() {
    val open by CarSettings.openOnConnect.collectAsState()
    val side by SidePaneChoice.current.collectAsState()
    val playerSize by CarSettings.playerSize.collectAsState()
    val miniPlayer by MiniPlayerSetting.on.collectAsState()
    val entries = rememberAllEntries()
    // As in the car: not the phone's app of a music service that takes its place.
    val replaced = rememberReplacedByWeb()
    val apps = entries.filter { it.fitsBesideMap && it.componentId != null && !replaced(it) }.sortedBy { it.label.lowercase() }
    // As in the car: a choice saved with the Plancia or Xonos app is the one inside myCar now.
    // An app since gone (uninstalled, a service turned off) shows as the music, as in the car.
    val shownSide = pinShown(side, entries).takeIf { id -> apps.any { it.componentId == id } } ?: SidePaneChoice.MUSIC
    val appNames = apps.map { it.shownLabel }
    val carApps = CarApps.all.filter { it.id != "settings" }
    val carAppNames = carApps.map { it.shownLabel }
    SettingsSection(stringResource(R.string.car_settings_open_home), footer = stringResource(R.string.car_side_explained)) {
        PickRow(
            stringResource(R.string.car_settings_open_on_connect),
            listOf(Pick(CarSettings.HOME, stringResource(R.string.car_settings_open_home))) +
                carApps.mapIndexed { i, app -> Pick(app.id, carAppNames[i]) },
            open,
        ) { CarSettings.setOpenOnConnect(it) }
        SettingsDivider()
        PickRow(
            stringResource(R.string.car_side_title),
            listOf(Pick(SidePaneChoice.MUSIC, stringResource(R.string.car_side_music))) +
                apps.mapIndexedNotNull { i, app -> app.componentId?.let { Pick(it, appNames[i]) } },
            shownSide,
        ) { SidePaneChoice.set(it) }
        SettingsDivider()
        PickRow(
            stringResource(R.string.car_settings_player_size),
            listOf(
                Pick(CarSettings.PLAYER_SMALL, stringResource(R.string.car_settings_size_small)),
                Pick(CarSettings.PLAYER_MEDIUM, stringResource(R.string.car_settings_size_medium)),
                Pick(CarSettings.PLAYER_LARGE, stringResource(R.string.car_settings_size_large)),
                Pick(CarSettings.PLAYER_EXTRA, stringResource(R.string.car_settings_size_extra_large)),
            ),
            playerSize,
        ) { CarSettings.setPlayerSize(it) }
        SettingsDivider()
        SwitchRow(
            stringResource(R.string.car_settings_mini_player),
            miniPlayer,
            subtitle = stringResource(R.string.car_settings_mini_player_detail),
        ) { MiniPlayerSetting.setOn(it) }
    }
}

/** How big the car screen draws, its colours, its controls, the map's day and night, the units. */
@Composable
private fun CarLookSection() {
    val scale by CarSettings.scale.collectAsState()
    val theme by CarSettings.appTheme.collectAsState()
    val controls by CarSettings.uiTheme.collectAsState()
    val night by CarSettings.nightMode.collectAsState()
    val units by CarSettings.units.collectAsState()
    // An id no longer known draws as the default, so that's the one lit.
    val shownControls = GsThemes.builtIn.find { it.id == controls }?.id ?: GsThemes.default.id
    val controlNames = GsThemes.builtIn.map { it.shownName }
    SettingsSection {
        PickRow(
            stringResource(R.string.car_settings_size),
            listOf(
                Pick(0.83f, stringResource(R.string.car_settings_size_extra_small)),
                Pick(0.9f, stringResource(R.string.car_settings_size_small)),
                Pick(1f, stringResource(R.string.car_settings_size_normal)),
                Pick(1.25f, stringResource(R.string.car_settings_size_large)),
                Pick(1.5f, stringResource(R.string.car_settings_size_extra_large)),
            ),
            scale,
        ) { CarSettings.setScale(it) }
        SettingsDivider()
        PickRow(
            stringResource(R.string.car_settings_app_theme),
            listOf(
                Pick(AppTheme.PHONE, stringResource(R.string.car_settings_app_theme_phone)),
                Pick(AppTheme.LIGHT, stringResource(R.string.car_settings_app_theme_light)),
                Pick(AppTheme.DARK, stringResource(R.string.car_settings_app_theme_dark)),
            ),
            theme,
        ) { CarSettings.setAppTheme(it) }
        SettingsDivider()
        PickRow(
            stringResource(R.string.car_settings_controls_theme),
            GsThemes.builtIn.mapIndexed { i, look -> Pick(look.id, controlNames[i]) },
            shownControls,
        ) { CarSettings.setUiTheme(it) }
        SettingsDivider()
        PickRow(
            stringResource(R.string.car_settings_map_day_night),
            listOf(
                Pick(NightMode.AUTO, stringResource(R.string.car_settings_map_auto)),
                Pick(NightMode.DAY, stringResource(R.string.car_settings_map_day)),
                Pick(NightMode.NIGHT, stringResource(R.string.car_settings_map_night)),
            ),
            night,
        ) { CarSettings.setNightMode(it) }
        SettingsDivider()
        PickRow(
            stringResource(R.string.car_settings_units),
            listOf(
                Pick(Units.AUTO, stringResource(R.string.car_settings_units_auto)),
                Pick(Units.IMPERIAL, stringResource(R.string.car_settings_units_imperial)),
                Pick(Units.METRIC, stringResource(R.string.car_settings_units_metric)),
            ),
            units,
        ) { CarSettings.setUnits(it) }
    }
}

/** The car's wallpapers as swatches, still then moving, drawn as the car draws them. */
@Composable
private fun CarWallpaperSection() {
    val current by WallpaperSetting.current.collectAsState()
    val dark = rememberCarDark()
    SettingsSection(stringResource(R.string.car_wallpaper_title)) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwatchLabel(stringResource(R.string.car_wallpaper_still))
            SwatchStrip {
                Swatch(Wallpapers.NONE, stringResource(R.string.car_wallpaper_none), current, dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
                Wallpapers.all.filter { it.motion == null }.forEach { wallpaper ->
                    Swatch(wallpaper.id, stringResource(wallpaper.name), current, dark) {
                        WallpaperBackground(wallpaper, Modifier.fillMaxSize(), wholeScreen = false)
                    }
                }
            }
            SwatchLabel(stringResource(R.string.car_wallpaper_moving))
            SwatchStrip {
                Wallpapers.all.filter { it.motion != null }.forEach { wallpaper ->
                    Swatch(wallpaper.id, stringResource(wallpaper.name), current, dark) {
                        WallpaperBackground(wallpaper, Modifier.fillMaxSize(), wholeScreen = false)
                    }
                }
            }
        }
    }
}

/** Whether the car screen is dark, worked out as CarUi does: the wallpapers are drawn on its ground. */
@Composable
private fun rememberCarDark(): Boolean {
    val theme by CarSettings.appTheme.collectAsState()
    val phoneDark = isSystemInDarkTheme()
    return when (theme) {
        AppTheme.PHONE -> phoneDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
}

@Composable
private fun SwatchLabel(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwatchStrip(swatches: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { swatches() }
}

@Composable
private fun Swatch(id: String, name: String, current: String, dark: Boolean, picture: @Composable () -> Unit) {
    val chosen = id == current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(SWATCH_WIDTH)
            .clip(shape)
            .selectable(chosen, role = Role.RadioButton) { WallpaperSetting.set(id) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(SWATCH_WIDTH)
                .height(SWATCH_HEIGHT)
                .clip(shape)
                .border(
                    if (chosen) 3.dp else 1.dp,
                    if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                ),
        ) {
            // In the car's own colours, dark or light, not the phone's.
            MyCarCarTheme(dark) { picture() }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val SWATCH_WIDTH = 104.dp
private val SWATCH_HEIGHT = 64.dp

/** Music at the start of a drive, the spoken turns, and the warnings the car screen shows. */
@Composable
private fun CarDriveSection() {
    val autoplay by CarSettings.autoplay.collectAsState()
    val speak by CarSettings.speakDirections.collectAsState()
    val bluetooth by CarSettings.bluetoothWarning.collectAsState()
    val location by CarSettings.locationWarning.collectAsState()
    SettingsSection {
        SwitchRow(stringResource(R.string.car_settings_autoplay), autoplay) { CarSettings.setAutoplay(it) }
        SettingsDivider()
        SwitchRow(
            stringResource(R.string.car_settings_spoken_directions),
            speak,
            stringResource(R.string.car_settings_spoken_directions_note),
        ) { CarSettings.setSpeakDirections(it) }
        SettingsDivider()
        SwitchRow(stringResource(R.string.car_settings_bluetooth_warning), bluetooth) { CarSettings.setBluetoothWarning(it) }
        SettingsDivider()
        SwitchRow(stringResource(R.string.car_settings_location_warning), location) { CarSettings.setLocationWarning(it) }
    }
}

/** The page the car's browser opens on: typed here with the phone's own keyboard. */
@Composable
private fun CarWebPageSection() {
    val context = LocalContext.current
    var url by remember { mutableStateOf(AppSettings.startupUrl(context)) }
    SettingsSection(stringResource(R.string.car_settings_web_page), footer = stringResource(R.string.car_settings_web_page_note)) {
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                AppSettings.setStartupUrl(context, it)
            },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.car_settings_web_page_default)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/** The name MyCarRunner gives a car no vehicle profile matched: nothing to save to. */
private const val UNKNOWN_VEHICLE = "Unknown vehicle"

/**
 * How much of the picture the car screen keeps clear at the top and the bottom, for a car that
 * hides part of it. Only with a car's picture up, as the car shows each step while it's made; saved
 * to the car's vehicle profile, when it has one, as from the car.
 */
@Composable
private fun CarScreenFitSection() {
    val status by SessionStatus.state.collectAsState()
    val vehicle by CarEnvironment.vehicle.collectAsState()
    val insets by CarEnvironment.insets.collectAsState()
    val frame by CarEnvironment.frame.collectAsState()
    val context = LocalContext.current.applicationContext
    var saved by remember { mutableStateOf<String?>(null) }
    val live = status.phase == SessionStatus.Phase.PROJECTING && vehicle.isNotEmpty()
    val known = vehicle.isNotEmpty() && vehicle != UNKNOWN_VEHICLE
    val savedText = stringResource(R.string.car_settings_saved_to, vehicle)
    val notSavedText = stringResource(R.string.car_settings_save_failed)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    fun change(new: Insets) {
        saved = null
        CarEnvironment.setInsets(new)
    }

    SettingsSection(stringResource(R.string.car_settings_screen_fit), footer = if (live) saved else null) {
        if (!live) {
            SettingsRow(stringResource(R.string.settings_car_fit_offline), titleColor = dim)
        } else {
            FitStepper(stringResource(R.string.car_settings_fit_top), insets.top) {
                change(insets.copy(top = (insets.top + it).coerceAtLeast(0)))
            }
            SettingsDivider()
            FitStepper(stringResource(R.string.car_settings_fit_bottom), insets.bottom) {
                change(insets.copy(bottom = (insets.bottom + it).coerceAtLeast(0)))
            }
            SettingsDivider()
            SettingsRow(
                stringResource(R.string.car_settings_usable_height, frame.height - insets.top - insets.bottom, frame.height),
                titleColor = dim,
            )
            SettingsDivider()
            SettingsRow(
                stringResource(if (known) R.string.car_settings_save_for_vehicle else R.string.car_settings_no_vehicle_profile),
                if (known) {
                    Modifier.clickable {
                        saved = if (VehicleProfiles.saveInsets(context, vehicle, insets)) savedText else notSavedText
                    }
                } else {
                    Modifier
                },
                titleColor = if (known) MaterialTheme.colorScheme.primary else dim,
            )
        }
    }
}

/** A side of the picture: its pixels kept clear, and steps of five and of one either way. */
@Composable
private fun FitStepper(label: String, value: Int, onStep: (Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.car_settings_pixels, value),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-5, -1, 1, 5).forEach { step ->
                OutlinedButton(
                    onClick = { onStep(step) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp),
                ) { Text(if (step < 0) "−${-step}" else "+$step") }
            }
        }
    }
}

/** The notes after an update, and the experimental features with the one they hold now. */
@Composable
private fun CarMoreSection() {
    val whatsNew by CarSettings.whatsNewAfterUpdates.collectAsState()
    val experimental by CarSettings.experimentalFeaturesEnabled.collectAsState()
    val sensors by CarSettings.carSensorsEnabled.collectAsState()
    SettingsSection {
        SwitchRow(
            stringResource(R.string.car_settings_whats_new_after_updates),
            whatsNew,
            stringResource(R.string.car_settings_whats_new_after_updates_note),
        ) { CarSettings.setWhatsNewAfterUpdates(it) }
        SettingsDivider()
        SwitchRow(stringResource(R.string.car_settings_experimental), experimental) { CarSettings.setExperimentalFeaturesEnabled(it) }
        if (experimental) {
            SettingsDivider()
            SwitchRow(
                stringResource(R.string.car_vehicle_title),
                sensors,
                stringResource(R.string.car_settings_vehicle_data_note),
            ) { CarSettings.setCarSensorsEnabled(it) }
        }
    }
}

/** One choice of a [PickRow] or [PickDialog]: what it sets, its name, and whether it can be picked now. */
internal class Pick<out T>(val value: T, val label: String, val enabled: Boolean = true)

/**
 * A setting with a few choices, as on the phone's own settings: its name, the choice now under it,
 * and tapped, the choices in a dialog.
 */
@Composable
internal fun <T> PickRow(title: String, options: List<Pick<T>>, selected: T, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingsRow(
        title,
        Modifier.clickable { open = true },
        subtitle = options.firstOrNull { it.value == selected }?.label,
    )
    if (open) {
        PickDialog(
            title,
            options,
            selected,
            onPick = {
                open = false
                onPick(it)
            },
            onDismiss = { open = false },
        )
    }
}

/** The choices of a setting as a list of round buttons, with [note] under them and a picture of each from [leading]. */
@Composable
internal fun <T> PickDialog(
    title: String,
    options: List<Pick<T>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    leading: (@Composable (T) -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    val on = option.value == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(on, enabled = option.enabled, role = Role.RadioButton) { onPick(option.value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = on, onClick = null, enabled = option.enabled)
                        leading?.invoke(option.value)
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                note?.let {
                    Text(
                        it,
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** A setting that is on or off, the whole row the switch. */
@Composable
internal fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title,
        Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}
