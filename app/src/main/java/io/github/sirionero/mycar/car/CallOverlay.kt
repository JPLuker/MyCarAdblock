package io.github.sirionero.mycar.car

import androidx.compose.foundation.shape.CircleShape
import io.github.sirionero.mycar.car.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import io.github.sirionero.mycar.R
import android.telephony.PhoneNumberUtils
import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.Caller
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.call.CarCalls

/**
 * Sits above whatever the car is showing while a call rings: it takes the whole screen, because
 * nothing else matters more right now. Once under way, the call is a pill over every screen
 * instead, with who it is and a button to end it (see CallPill).
 */
@Composable
fun CallOverlay() {
    val state by CarCalls.state.collectAsState()
    val context = LocalContext.current.applicationContext
    val ringing = state as? CallUiState.Ringing ?: return
    IncomingCall(ringing, onAnswer = { CarCalls.answer(context) }, onDecline = CarCalls::decline)
}

@Composable
private fun IncomingCall(s: CallUiState.Ringing, onAnswer: () -> Unit, onDecline: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.sym_call),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MyCarPalette.Positive,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.call_incoming),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                s.caller.shown(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            if (rememberCallOnPhone(active = false)) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.call_no_bluetooth_ringing),
                    style = MaterialTheme.typography.titleSmall,
                    color = MyCarPalette.Warning,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(56.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                CallButton(ImageVector.vectorResource(R.drawable.sym_call_end), stringResource(R.string.call_decline), MyCarPalette.Negative, enabled = s.canDecline, onClick = onDecline)
                CallButton(ImageVector.vectorResource(R.drawable.sym_call), stringResource(R.string.call_answer), MyCarPalette.Positive, enabled = true, onClick = onAnswer)
            }
            if (!s.canDecline) {
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.call_ringing_aloud),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Who's calling: the name, or the number as the phone writes it ("333 123 4567", not "3331234567"). */
internal fun Caller.shown(): String =
    name ?: number.takeIf { it.isNotBlank() }?.let { PhoneNumberUtils.formatNumber(it, io.github.sirionero.mycar.AppLanguage.country()) ?: it } ?: label

@Composable
private fun CallButton(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GsIconButton(
            icon, label, onClick,
            colors = GsColors(color, Color.White),
            enabled = enabled,
            size = 72.dp,
            iconSize = 30.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
