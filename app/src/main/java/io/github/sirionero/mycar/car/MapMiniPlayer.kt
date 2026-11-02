package io.github.sirionero.mycar.car

import android.content.Context
import androidx.car.app.CarToast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.media.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** The mini player over the map (see [MapMiniPlayer]): on unless turned off, in the car or on the phone, and where it was last let go. */
object MiniPlayerSetting {
    private const val KEY_ON = "car_mini_player"
    private const val KEY_AT = "car_mini_player_at"
    private var app: Context? = null
    private val onFlow = MutableStateFlow(true)
    private val placeFlow = MutableStateFlow(MiniPlace.CORNER)
    val on: StateFlow<Boolean> = onFlow
    internal val place: StateFlow<MiniPlace> = placeFlow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        onFlow.value = AppSettings.getString(app, KEY_ON, "true") == "true"
        placeFlow.value = MiniPlace.parse(AppSettings.getString(app, KEY_AT, "")) ?: MiniPlace.CORNER
    }

    fun setOn(on: Boolean) {
        onFlow.value = on
        app?.let { AppSettings.putString(it, KEY_ON, on.toString()) }
    }

    internal fun moveTo(place: MiniPlace) {
        placeFlow.value = place
        app?.let { AppSettings.putString(it, KEY_AT, place.saved()) }
    }
}

/**
 * The mini player over the map, for a driver who wants the whole map with music on: the cover (a
 * tap opens the full player), back, play or pause, next, and an X that puts it away for the drive.
 * Held, it lifts; dragged, it follows the finger anywhere over the map and stays where it's let go,
 * for the next drives too. Laid over the map's drawing and under the app's own cards and buttons
 * (CarAppStage's overMap): the map keeps its whole surface, and every touch outside it still
 * reaches the map. No progress line: a mini player that doesn't change sends the car no new frames.
 */
@Composable
internal fun MapMiniPlayer(now: NowPlaying, modifier: Modifier = Modifier) {
    val navigator = LocalCarNavigator.current
    val media = CarServices.media
    val mediaApp by CarServices.mediaApp.collectAsState()
    val art = rememberArt(now)
    val cover = remember(art) { art?.asImageBitmap() }
    val appIcon = remember(mediaApp) { mediaApp?.let { findEntry(it.component.flattenToString())?.icon } }
    BoxWithConstraints(modifier.padding(MyCarSpace.M)) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        // Where it rests. Set here the moment it's let go, with the drag cleared in the same frame:
        // the saved place reaches a collector a moment later, and it sprang back for a frame meanwhile.
        var rest by remember { mutableStateOf(MiniPlayerSetting.place.value) }
        var held by remember { mutableStateOf(false) }
        // How far the finger has taken it from where it rests, in pixels.
        var moved by remember { mutableStateOf(Offset.Zero) }
        val room = IntSize((constraints.maxWidth - size.width).coerceAtLeast(0), (constraints.maxHeight - size.height).coerceAtLeast(0))
        val roomNow by rememberUpdatedState(room)
        val lift by animateFloatAsState(
            if (held) LIFTED else 1f,
            tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
            label = "miniLift",
        )
        Surface(
            modifier = Modifier
                .align(BiasAbsoluteAlignment(rest.x * 2 - 1, rest.y * 2 - 1))
                .offset { IntOffset(moved.x.roundToInt(), moved.y.roundToInt()) }
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    dragAfterHold(
                        onHold = { held = true },
                        onDrag = { by ->
                            // Inside the map whichever way: from where it rests to either edge.
                            val restX = rest.x * roomNow.width
                            val restY = rest.y * roomNow.height
                            val next = moved + by
                            moved = Offset(
                                next.x.coerceIn(-restX, roomNow.width - restX),
                                next.y.coerceIn(-restY, roomNow.height - restY),
                            )
                        },
                        onEnd = { dragged ->
                            if (dragged) {
                                val to = MiniPlace.of(
                                    rest.x * roomNow.width + moved.x, rest.y * roomNow.height + moved.y,
                                    roomNow.width, roomNow.height,
                                )
                                rest = to
                                MiniPlayerSetting.moveTo(to)
                                MyCarLog.i("mini player: let go at ${to.saved()}")
                            }
                            moved = Offset.Zero
                            held = false
                        },
                    )
                }
                // After the gesture in the chain: a lifted mini player doesn't scale the finger's moves.
                .graphicsLayer { scaleX = lift; scaleY = lift }
                .height(MyCarSize.TouchTarget),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            shadowElevation = if (held) 12.dp else 6.dp,
        ) {
            Row(Modifier.padding(end = MyCarSpace.S), verticalAlignment = Alignment.CenterVertically) {
                val open = stringResource(R.string.car_mini_open_player)
                // The cover, the mini player's whole height to hit: the full player.
                Box(
                    Modifier
                        .size(MyCarSize.TouchTarget)
                        .clickable(role = Role.Button, onClick = navigator::media)
                        .semantics { contentDescription = open },
                    contentAlignment = Alignment.Center,
                ) {
                    if (cover != null) {
                        Image(cover, null, Modifier.size(COVER).clip(RoundedCornerShape(MyCarRadius.Small)), contentScale = ContentScale.Crop)
                    } else {
                        AppIcon(appIcon, null, COVER)
                    }
                }
                TransportRow(now, media, skip = SKIP)
                PlainButton(
                    {
                        navigator.hideMiniPlayer()
                        CarToasts.show(MiniPlayerSetting, Words.get(R.string.car_mini_hidden_toast), CarToast.LENGTH_LONG)
                    },
                    stringResource(R.string.car_mini_hide),
                    CLOSE,
                ) {
                    Icon(Icons.Filled.Close, null, Modifier.size(22.dp), tint = LocalContentColor.current.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/**
 * Hold, then drag. [onHold] once the finger has stayed down, and still, for a long press; [onDrag]
 * with each move once it has gone past the touch slop after that; [onEnd] when it lifts, with
 * whether it was dragged. Watched on the way in (the Initial pass), so a drag is taken before the
 * button under the finger and the pages see it: the button's tap is cancelled and no page turns.
 * A hold let go without moving is left to the button: a slow tap on a bumpy road is still a tap.
 * A swipe begun before the hold is taken and dropped: on the map's page nothing turns the pages.
 */
private suspend fun PointerInputScope.dragAfterHold(
    onHold: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (dragged: Boolean) -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val slop = viewConfiguration.touchSlop
    // True: lifted before the hold (a tap: the button's). False: moved off before it. Null: held.
    val early: Boolean? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        var lifted = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                lifted = true
                break
            }
            if ((change.position - down.position).getDistance() > slop) {
                change.consume()
                break
            }
        }
        lifted
    }
    if (early == true) return@awaitEachGesture
    if (early == false) {
        // A swipe: taken until the finger lifts, so neither the pages nor the button get it.
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            change.consume()
            if (!change.pressed) break
        }
        return@awaitEachGesture
    }
    onHold()
    var dragged = false
    try {
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!dragged) {
                // Let go without moving: the button under the finger keeps its tap.
                if (!change.pressed) break
                // Not moved yet, so its own coordinates still hold: the finger's way from where it went down.
                val travel = change.position - down.position
                if (travel.getDistance() <= slop) continue
                dragged = true
                change.consume()
                onDrag(travel)
                continue
            }
            val by = change.positionChange()
            change.consume()
            if (by != Offset.Zero) onDrag(by)
            if (!change.pressed) break
        }
    } finally {
        onEnd(dragged)
    }
}

private val COVER = 48.dp
private val SKIP = 48.dp
private val CLOSE = 48.dp
private const val LIFTED = 1.06f
