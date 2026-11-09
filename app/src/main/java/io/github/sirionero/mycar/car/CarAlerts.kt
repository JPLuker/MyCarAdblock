package io.github.sirionero.mycar.car

import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.car.app.model.Action
import androidx.car.app.model.Alert
import androidx.car.app.model.AlertCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconButton
import io.github.sirionero.mycar.car.theme.GsProgressBar
import io.github.sirionero.mycar.host.cancel
import io.github.sirionero.mycar.host.click
import io.github.sirionero.mycar.host.color
import io.github.sirionero.mycar.host.dismissed
import io.github.sirionero.mycar.host.isDrawable
import io.github.sirionero.mycar.host.text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one alert on the car's screen, from whichever app for cars sent it: Waze asking whether a
 * hazard it reported is still there, say (it used to be dropped). As in Android Auto, a newer alert
 * takes the place of the one up, and it shows over any screen: a question about the road matters
 * with the music up too. Main thread only.
 */
@MainThread
internal object CarAlerts {
    /** [alert] from the app [owner] (its host's toast owner), put up at [at] on the uptime clock. */
    class Shown(val owner: Any, val alert: Alert, val at: Long) {
        /** When its time runs out; null for one shown until it's answered or closed. */
        val until: Long? = alert.durationMillis.takeIf { it > 0 && it < Alert.DURATION_SHOW_INDEFINITELY }?.let { at + it }
    }

    private val current = MutableStateFlow<Shown?>(null)
    val shown: StateFlow<Shown?> = current.asStateFlow()

    fun show(owner: Any, alert: Alert) {
        val old = current.value
        current.value = Shown(owner, alert, SystemClock.uptimeMillis())
        // Its words only in the debug build: an app's alert may name a person or a place.
        MyCarLog.i(
            "host: alert ${alert.id} with ${alert.actions.size} buttons, " +
                (if (alert.durationMillis < Alert.DURATION_SHOW_INDEFINITELY) "${alert.durationMillis} ms" else "until answered") +
                if (BuildConfig.DEBUG) " \"${alert.title.text()}\"" else "",
        )
        // The same alert again only has its words or time changed: the app isn't told it went.
        if (old != null && !(old.owner === owner && old.alert.id == alert.id)) old.alert.callbackDelegate.dismissed()
    }

    /** The app took its alert down: nothing to do unless it's the one up. */
    fun dismiss(owner: Any, id: Int) {
        val up = current.value ?: return
        if (up.owner !== owner || up.alert.id != id) return
        current.value = null
        up.alert.callbackDelegate.dismissed()
    }

    /** The driver tapped one of its buttons: the app gets the tap, and the alert goes. */
    fun tapped(shown: Shown, action: Action) {
        action.onClickDelegate.click("alert button")
        if (current.value !== shown) return
        current.value = null
        shown.alert.callbackDelegate.dismissed()
    }

    /** Its time ran out, or the driver closed it: [reason] is AlertCallback's. */
    fun cancelled(shown: Shown, reason: Int) {
        if (current.value !== shown) return
        current.value = null
        MyCarLog.i("host: alert ${shown.alert.id} ${if (reason == AlertCallback.REASON_TIMEOUT) "timed out" else "closed"}")
        shown.alert.callbackDelegate.cancel(reason)
    }

    /** The app went: its alert goes with it, with no one left to tell. */
    fun clear(owner: Any) {
        if (current.value?.owner === owner) current.value = null
    }
}

/**
 * The alert up, as a card across the top: the app's icon, title and subtitle, up to two of its
 * buttons, and a bar for the time left. A close button when nothing else would take it away soon:
 * no time limit, or no buttons.
 */
@Composable
internal fun CarAlertOverlay(modifier: Modifier = Modifier) {
    val current by CarAlerts.shown.collectAsState()
    val shown = current ?: return
    val alert = shown.alert
    val until = shown.until
    // On the clock it was put up with: a car screen made again (screen sharing, a new encoder)
    // doesn't give it its whole time over.
    if (until != null) {
        LaunchedEffect(shown) {
            delay((until - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            CarAlerts.cancelled(shown, AlertCallback.REASON_TIMEOUT)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val actions = alert.actions.filter { it.isDrawable() }.take(MAX_BUTTONS)
    Surface(
        modifier
            .padding(horizontal = MyCarSpace.XL, vertical = MyCarSpace.L)
            .widthIn(max = ALERT_MAX_WIDTH)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(MyCarRadius.Sheet),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(MyCarSpace.L), verticalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyCarSpace.L)) {
                if (alert.icon != null) CarGlyph(alert.icon, Modifier.size(ALERT_ICON))
                Column(Modifier.weight(1f)) {
                    Text(
                        alert.title.text(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    alert.subtitle.text().takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (until == null || actions.isEmpty()) {
                    GsIconButton(
                        Icons.Filled.Close, stringResource(R.string.common_close),
                        { CarAlerts.cancelled(shown, AlertCallback.REASON_USER_ACTION) },
                        size = MyCarSize.TouchTarget,
                    )
                }
            }
            if (actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M)) {
                    actions.forEach { action -> AlertButton(shown, action, Modifier.weight(1f)) }
                }
            }
            if (until != null) {
                val left by rememberTimeLeft(until, alert.durationMillis)
                GsProgressBar(left, Modifier.fillMaxWidth(), height = 14.dp)
            }
        }
    }
}

/** One of the alert's buttons, in the colour the app gave it (a red "Still there"), else the neutral one. */
@Composable
private fun AlertButton(shown: CarAlerts.Shown, action: Action, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val neutral = scheme.surfaceContainerHighest
    val background = action.backgroundColor.color(scheme.surface.luminanceIsDark(), neutral)
    val content = when {
        background == neutral -> scheme.onSurface
        background.luminanceIsDark() -> Color.White
        else -> Color.Black
    }
    GsButton(
        onClick = { CarAlerts.tapped(shown, action) },
        modifier = modifier.height(MyCarSize.TouchTarget),
        colors = GsColors(background, content),
        enabled = action.isEnabled,
    ) {
        if (action.icon != null) {
            CarGlyph(action.icon, Modifier.size(24.dp))
            Spacer(Modifier.width(MyCarSpace.S))
        }
        Text(action.title.text(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An alert has at most two buttons (the Car App Library's own limit). */
private const val MAX_BUTTONS = 2
private val ALERT_MAX_WIDTH = 640.dp
private val ALERT_ICON = 44.dp
