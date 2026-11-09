package io.github.sirionero.mycar.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.setup.ChargingNoCar
import io.github.sirionero.mycar.stats.UsageStats
import io.github.sirionero.mycar.wireless.WirelessLink
import kotlinx.coroutines.delay

/**
 * On Home, when the phone has been charging from a USB port for a while, no car has come to
 * myCar and no drive is under way: what keeps a car from seeing the phone on the cable. A
 * computer or a charger looks the same, so it's a quiet card that only says what to check; it
 * goes as soon as a car comes or the cable is out. Watched only while Home is in sight. See
 * [ChargingNoCar].
 */
@Composable
internal fun ChargingNoCarCard() {
    val context = LocalContext.current
    val status by SessionStatus.state.collectAsStateWithLifecycle()
    val live = status.phase == SessionStatus.Phase.CONNECTING || status.phase == SessionStatus.Phase.PROJECTING
    var usb by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                usb = chargesFromUsb(intent)
            }
        }
        // Sticky: registering hands over how the phone is plugged in right now.
        usb = chargesFromUsb(app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED))
        onStopOrDispose {
            runCatching { app.unregisterReceiver(receiver) }
            usb = false
        }
    }
    val hint = remember { ChargingNoCar() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(usb, live) {
        // Looked at every second while it charges: the wait runs out, or a car comes - on the
        // cable, read from the USB port, or wirelessly, with the cable there only to charge.
        while (true) {
            val car = live || WirelessLink.busy || carCameToMyCar(context)
            shown = hint.update(SystemClock.elapsedRealtime(), usb, car)
            if (!usb || live) break
            delay(LOOK_MS)
        }
    }
    val graphene = remember { runsGrapheneOs(context) }
    // Switched off, myCar doesn't take the car at all, and Home says so already (SetupLeftCard).
    if (!shown || !AppSettings.myCarEnabled(context)) return
    Spacer(Modifier.height(16.dp))
    SettingsCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_no_car_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.home_no_car_check),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Check(stringResource(R.string.home_no_car_cable))
            Check(stringResource(R.string.home_no_car_always))
            if (graphene) {
                Check(stringResource(R.string.usb_c_graphene))
                FilledTonalButton(onClick = { openSecuritySettings(context) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.common_open_settings))
                }
            }
        }
    }
}

/** One thing to check: a dot, then its line, which wraps under itself. */
@Composable
private fun Check(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Charging from a USB port: a car's, a computer's, or a charger that passes for one. */
private fun chargesFromUsb(battery: Intent?): Boolean =
    battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == BatteryManager.BATTERY_PLUGGED_USB

/**
 * A car on the cable that came to myCar: Android gave myCar the accessory attached, as it does
 * when it opens myCar for it. One it holds while it asks which app to open, or gave to another
 * app, doesn't count: that tells myCar nothing either, and the card has something to say about it.
 */
private fun carCameToMyCar(context: Context): Boolean {
    val usb = context.getSystemService(UsbManager::class.java) ?: return false
    return runCatching { usb.accessoryList?.any { usb.hasPermission(it) } == true }.getOrDefault(false)
}

/**
 * GrapheneOS, known as the usage notes know it. The debug build's CI shots ask for its lines on
 * the emulator (car-preview.sh: --ez graphene true).
 */
internal fun runsGrapheneOs(context: Context): Boolean =
    UsageStats.osName(context) == "GrapheneOS" ||
        (BuildConfig.DEBUG && (context as? Activity)?.intent?.getBooleanExtra("graphene", false) == true)

/** Android's Security & privacy: GrapheneOS keeps its USB-C port setting there, under Exploit protection. */
internal fun openSecuritySettings(context: Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** How often Home looks at the USB port while the phone charges from one. */
private const val LOOK_MS = 1_000L
