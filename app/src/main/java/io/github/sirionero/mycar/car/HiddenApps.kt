package io.github.sirionero.mycar.car

import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone
import io.github.sirionero.mycar.webmusic.WebMusic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The apps the driver took off the car's app grid: hidden from its menu (a long press on the app)
 * or from the phone's settings, and brought back from either's settings. Only off the grid: a
 * hidden app pinned to the dock stays there, and the voice assistant still opens it by name.
 * Kept as the ids pins use ([Entry.componentId]).
 */
internal object HiddenApps {
    private const val KEY = "car_hidden_apps"
    private var app: Context? = null
    private val flow = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = flow.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        flow.value = AppSettings.getString(app, KEY, "").split(',').filter { it.isNotEmpty() }.toSet()
    }

    fun hide(id: String) = save(flow.value + id)

    /** Every app back on the grid: the car preview, before each shot. */
    fun reset() = save(emptySet())

    private fun save(ids: Set<String>) {
        flow.value = ids
        app?.let { AppSettings.putString(it, KEY, ids.joinToString(",")) }
    }

    /** Back on the grid: [id] as the car shows it now, and its Plancia or Xonos twin if that's what was hidden. */
    fun show(id: String, entries: List<Entry>) = save(flow.value.filterNot { it == id || twinOf(it, entries) == id }.toSet())

    /** Never off the grid: the car's Settings, where hidden apps come back from. */
    fun canHide(entry: Entry): Boolean = entry.componentId != null && entry.builtIn !== BuiltInApps.Settings
}

/** A hidden id as the car shows that app now: Xonos hidden is the Xonos app while it stands in, and back. */
private fun twinOf(id: String, entries: List<Entry>): String? = withXonosInside(withPlanciaInside(id, entries), entries)

/**
 * The ids of the apps on the grid that [hidden] keeps off it: as saved, and as their Plancia or
 * Xonos twin. Not a music service's web player for its app hidden: those are two different apps.
 */
internal fun hiddenAsShown(hidden: Set<String>, entries: List<Entry>): Set<String> =
    hidden + hidden.mapNotNull { twinOf(it, entries) }

/**
 * A music service's web player in place of its own app on the phone, once it's on and ticked to
 * hide that app (see [WebMusic.replacing]): a pin or a choice beside the map saved with the
 * Spotify app shows Spotify inside myCar.
 */
internal fun withWebMusicInside(id: String?, entries: List<Entry>): String? {
    if (id == null) return null
    val service = WebMusic.replacing(ComponentName.unflattenFromString(id)?.packageName) ?: return id
    return entries.firstOrNull { it.webMusic === service }?.componentId ?: id
}

/**
 * Whether an app is the phone's own app of a music service that takes its place in the car (see
 * [WebMusic.replacing]): off the grid and the lists of apps, so there's one Spotify.
 */
@Composable
internal fun rememberReplacedByWeb(): (Entry) -> Boolean {
    val on by WebMusic.on.collectAsState()
    val hideApp by WebMusic.hideApp.collectAsState()
    return remember(on, hideApp) {
        val replaced: (Entry) -> Boolean = { entry ->
            entry.webMusic == null && entry.messaging == null && WebMusic.replacing(entry.packageName, on, hideApp) != null
        }
        replaced
    }
}

/**
 * Whether an app stays off the car's app grid: hidden by the driver (also as its Plancia or Xonos
 * twin), or the phone's own app of a music service that takes its place.
 */
@Composable
internal fun rememberOffTheGrid(entries: List<Entry>): (Entry) -> Boolean {
    val hidden by HiddenApps.ids.collectAsState()
    val replaced = rememberReplacedByWeb()
    return remember(hidden, replaced, entries) {
        val shownAs = hiddenAsShown(hidden, entries)
        val off: (Entry) -> Boolean = { entry -> entry.componentId in shownAs || replaced(entry) }
        off
    }
}

/** The apps hidden from [ids], as the car shows them now; one no longer on the phone isn't listed. */
internal fun hiddenEntries(ids: Set<String>, entries: List<Entry>): List<Entry> =
    ids.mapNotNull { id -> entries.find { it.componentId == id } ?: entries.find { it.componentId == twinOf(id, entries) } }
        .distinctBy { it.componentId }

/**
 * The car settings' hidden apps: each with Show, which puts it back on the app grid. With none, how
 * to hide one, so the driver knows it can be done.
 */
@Composable
internal fun HiddenAppsSettings() {
    val ids by HiddenApps.ids.collectAsState()
    val entries = rememberAllEntries()
    // Not the phone's app of a music service in its place: Show couldn't bring it back, the Music
    // tick on the phone keeps it off. Untick that, and it's here again with a Show that works.
    val replaced = rememberReplacedByWeb()
    val hidden = remember(ids, entries, replaced) { hiddenEntries(ids, entries).filterNot(replaced) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.car_settings_hidden_apps), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        hidden.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(entry.icon, entry.builtIn?.id, 48.dp)
                Text(
                    entry.shownLabel,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                GsButton(
                    onClick = { entry.componentId?.let { HiddenApps.show(it, entries) } },
                    tone = GsTone.Tonal,
                    modifier = Modifier.height(56.dp),
                ) { Text(stringResource(R.string.car_settings_hidden_apps_show), style = MaterialTheme.typography.titleMedium) }
            }
        }
        if (hidden.isEmpty()) {
            Text(
                stringResource(R.string.car_settings_hidden_apps_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
