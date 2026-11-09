package io.github.sirionero.mycar.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.Caller
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Where the driver last let a call's pill go (see [CallPill]), for every call after: null until then. */
object CallPillSetting {
    private const val KEY_AT = "car_call_pill_at"
    private var app: Context? = null
    private val placeFlow = MutableStateFlow<MiniPlace?>(null)
    internal val place: StateFlow<MiniPlace?> = placeFlow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        placeFlow.value = MiniPlace.parse(AppSettings.getString(app, KEY_AT, ""))
    }

    internal fun moveTo(place: MiniPlace?) {
        placeFlow.value = place
        app?.let { AppSettings.putString(it, KEY_AT, place?.saved().orEmpty()) }
    }
}

/** Where a call's pill rests, in dp of the screen beside the dock; null with no call under way. The mini player over the map keeps clear of it. */
internal object CallPillSpot {
    var rest by mutableStateOf<ScreenRect?>(null)
}

/**
 * A call under way - placed, picked up, on hold - as a pill over whatever the car shows, from the map to
 * Cinema, until it ends: who it's with (their photo from the contacts, or their initial), how long it has
 * gone on, whether its voice stays on the phone, and a red button that ends it as the phone's own would. A
 * tap on the rest opens the Phone. Held, it lifts; dragged, it goes anywhere beside the dock and stays where
 * it's let go, for the calls after too. In place of the band the call had along the top, which pushed every
 * screen down under it: the pill covers only itself, and every touch outside it reaches what's under it.
 * While a call rings, the card over the whole screen has it (CallOverlay).
 */
@Composable
internal fun CallPill(modifier: Modifier = Modifier) {
    val call = CarCalls.state.collectAsState().value as? CallUiState.Active ?: return
    OngoingCall(call, modifier)
}

@Composable
private fun OngoingCall(call: CallUiState.Active, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val navigator = LocalCarNavigator.current
    BoxWithConstraints(modifier.fillMaxSize().padding(PILL_EDGE.dp)) {
        val density = LocalDensity.current
        // Smaller where it doesn't fit beside a map's card (800x480 at Extra large), as the mini player is.
        val fit = CallPillPlace.fitScale(maxWidth.value)
        val ownW = PILL_WIDTH * fit
        val ownH = MyCarSize.TouchTarget.value * fit
        // Where it was let go. Set here the moment it's let go, with the drag cleared in the same frame: the
        // saved place reaches a collector a moment later, and it sprang back for a frame meanwhile.
        var kept by remember { mutableStateOf(CallPillSetting.place.value) }
        val rest = kept ?: CallPillPlace.start(maxWidth.value, ownW)
        val restNow by rememberUpdatedState(rest)
        // Where it rests, for the mini player over the map to keep clear of: in dp of the screen beside the dock.
        val roomW = (maxWidth.value - ownW).coerceAtLeast(0f)
        val roomH = (maxHeight.value - ownH).coerceAtLeast(0f)
        val left = PILL_EDGE + rest.x * roomW
        val top = PILL_EDGE + rest.y * roomH
        val spot = ScreenRect(left, top, left + ownW, top + ownH)
        DisposableEffect(spot) {
            CallPillSpot.rest = spot
            onDispose { if (CallPillSpot.rest == spot) CallPillSpot.rest = null }
        }
        var held by remember { mutableStateOf(false) }
        // How far the finger has taken it from where it rests, in pixels.
        var moved by remember { mutableStateOf(Offset.Zero) }
        val room = with(density) {
            IntSize(
                (constraints.maxWidth - ownW.dp.roundToPx()).coerceAtLeast(0),
                (constraints.maxHeight - ownH.dp.roundToPx()).coerceAtLeast(0),
            )
        }
        val roomNow by rememberUpdatedState(room)
        val lift by animateFloatAsState(
            if (held) PILL_LIFTED else 1f,
            tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
            label = "callPillLift",
        )
        CompositionLocalProvider(LocalDensity provides if (fit < 1f) Density(density.density * fit, density.fontScale) else density) {
            Surface(
                modifier = Modifier
                    .align(BiasAbsoluteAlignment(rest.x * 2 - 1, rest.y * 2 - 1))
                    .offset { IntOffset(moved.x.roundToInt(), moved.y.roundToInt()) }
                    .pointerInput(Unit) {
                        dragAfterHold(
                            onHold = { held = true },
                            onDrag = { by ->
                                // Anywhere beside the dock, inside the car's margins: from where it rests to either edge.
                                val restX = restNow.x * roomNow.width
                                val restY = restNow.y * roomNow.height
                                val next = moved + by
                                moved = Offset(
                                    next.x.coerceIn(-restX, roomNow.width - restX),
                                    next.y.coerceIn(-restY, roomNow.height - restY),
                                )
                            },
                            onEnd = { dragged ->
                                if (dragged) {
                                    val to = MiniPlace.of(
                                        restNow.x * roomNow.width + moved.x, restNow.y * roomNow.height + moved.y,
                                        roomNow.width, roomNow.height,
                                    )
                                    kept = to
                                    CallPillSetting.moveTo(to)
                                    MyCarLog.i("call pill: let go at ${to.saved()}")
                                }
                                moved = Offset.Zero
                                held = false
                            },
                            // Held and let go where it was: put down, nothing else. A slow tap ending the call
                            // when the driver meant to move the pill would be worse than a second tap.
                            tapAfterHold = false,
                        )
                    }
                    // After the gesture in the chain: a lifted pill doesn't scale the finger's moves.
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .size(PILL_WIDTH.dp, MyCarSize.TouchTarget),
                shape = RoundedCornerShape(percent = 50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shadowElevation = if (held) 12.dp else 6.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val open = stringResource(R.string.call_pill_open_phone)
                    // Who and how long, the pill's whole height to hit: the Phone.
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(role = Role.Button) { BuiltInApps.Phone.open(navigator) }
                            .semantics { contentDescription = open }
                            .padding(start = MyCarSpace.S, end = MyCarSpace.XS),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CallerPicture(call.caller, PICTURE)
                        Spacer(Modifier.width(MyCarSpace.M))
                        Column(Modifier.weight(1f)) {
                            Text(
                                call.caller?.shown() ?: stringResource(R.string.call_on_call),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            CallTime(call.since)
                        }
                    }
                    // Ending a call takes ANSWER_PHONE_CALLS: without it, no button that does nothing.
                    if (CarCalls.canEndCalls(context)) {
                        PlainButton({ CarCalls.hangUp(context) }, stringResource(R.string.call_end), MyCarSize.TouchTarget) {
                            Box(Modifier.size(PICTURE).background(MyCarPalette.Negative, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(ImageVector.vectorResource(R.drawable.sym_call_end), null, Modifier.size(END_GLYPH), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * How long the call has gone on, a second at a time, in the call's green - and, while its voice stays on the
 * phone (no Bluetooth link to the car for calls: see rememberCallOnPhone), that too, in amber.
 */
@Composable
private fun CallTime(since: Long) {
    val onPhone = rememberCallOnPhone(active = true)
    val seconds by produceState((SystemClock.elapsedRealtime() - since) / 1_000, since) {
        if (since <= 0L) return@produceState
        while (true) {
            val ms = SystemClock.elapsedRealtime() - since
            value = ms / 1_000
            delay((1_000 - ms % 1_000).coerceAtLeast(1))
        }
    }
    val line = listOfNotNull(
        callClock(seconds).takeIf { since > 0L },
        stringResource(R.string.call_pill_voice_on_phone).takeIf { onPhone },
    ).joinToString(" · ")
    if (line.isEmpty()) return
    Text(
        line,
        // Figures of one width, so the time doesn't shift as it counts.
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = if (onPhone) MyCarPalette.Warning else MaterialTheme.colorScheme.tertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Who the call is with, as the Phone shows a contact: their photo from the contacts, or their initial on a colour (a figure, with no name). */
@Composable
private fun CallerPicture(caller: Caller?, size: Dp) {
    val context = LocalContext.current.applicationContext
    val number = caller?.number?.takeIf { it.isNotBlank() }
    val px = with(LocalDensity.current) { size.roundToPx() }
    val photo by produceState<ImageBitmap?>(null, number, px) {
        value = number?.let { n -> withContext(Dispatchers.IO) { callerPhoto(context, n, px) } }?.asImageBitmap()
    }
    val shown = photo
    if (shown != null) {
        Image(shown, contentDescription = null, Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        return
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(size).background(scheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        val initial = caller?.name?.firstOrNull { it.isLetterOrDigit() }?.uppercase()
        if (initial != null) {
            Text(initial, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, Modifier.size(size * 0.6f), tint = scheme.onPrimaryContainer)
        }
    }
}

/** The photo the contacts have for [number], about [px] across: null without one, or without access to the contacts. */
private fun callerPhoto(context: Context, number: String, px: Int): Bitmap? {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
    return runCatching {
        val lookup = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val columns = arrayOf(ContactsContract.PhoneLookup.PHOTO_URI, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
        val photo = context.contentResolver.query(lookup, columns, null, null, null)
            ?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: c.getString(1)) else null }
            ?: return null
        val bytes = context.contentResolver.openInputStream(Uri.parse(photo))?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        // The full photo can be 720 px across, for a picture of 48 dp: halved while that still covers it.
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px.coerceAtLeast(1)) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }.onFailure { MyCarLog.w("calls: the caller's photo couldn't be read: ${it.javaClass.simpleName}") }.getOrNull()
}

/** The caller's photo or initial, and the red button's circle. */
private val PICTURE = 48.dp
private val END_GLYPH = 26.dp
private const val PILL_LIFTED = 1.06f
