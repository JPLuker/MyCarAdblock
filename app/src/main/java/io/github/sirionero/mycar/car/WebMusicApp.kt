package io.github.sirionero.mycar.car

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.SiteIcons
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.webmusic.WebMusic
import io.github.sirionero.mycar.webmusic.WebMusicService
import kotlin.math.absoluteValue

/*
 * The music services' web players on the car's screen: each one on, a music app like any other -
 * a tile on the grid, the car's music once opened, a choice beside the map - whose full player is
 * the service's own page, edge to edge.
 */

/**
 * A service's own page full screen, from its tile or as the car's music (the dock's song held,
 * the car's media key). Nothing of myCar's over it but a small Back: it looks as the service's
 * own app does. The car's keyboard types in its fields, with the phone's clipboard.
 */
@Composable
internal fun WebMusicScreen(service: WebMusicService) {
    val pages = LocalWebMusicPages.current ?: return
    val navigator = LocalCarNavigator.current
    // Turned off on the phone: out of the car, this screen too.
    val on by WebMusic.on.collectAsState()
    if (service.id !in on) {
        LaunchedEffect(Unit) { navigator.back() }
        return
    }
    val page = remember(pages, service.id) { pages.page(service) }
    val field by page.field.collectAsState()
    val video by page.fullScreen.collectAsState()
    val unreachable by page.unreachable.collectAsState()
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    val videoAllowed = !parkedOnly || rememberVideoAllowed()

    // Its sound has to reach the car: the capture starts now, if it waited for Xonos (as Cinema's).
    LaunchedEffect(Unit) { CarAudio.captureNow() }
    // The car's Back: a video full screen first, then a page back in the service, then out.
    DisposableEffect(page) {
        navigator.backInterceptor = { page.exitFullScreen() || page.goBack() }
        onDispose { navigator.backInterceptor = null }
    }
    val shown = video
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(page) {
                AndroidView(
                    factory = { context -> FrameLayout(context).also(page::show) },
                    onRelease = page::hide,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (shown != null) {
                key(shown.first) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        factory = { context ->
                            FrameLayout(context).apply {
                                (shown.first.parent as? ViewGroup)?.removeView(shown.first)
                                addView(shown.first, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                        },
                        onRelease = { it.removeAllViews() },
                    )
                }
                // A video full screen (a video podcast) shows only with the car parked, as Cinema's.
                // The music's own page is never covered.
                if (!videoAllowed) ParkedCover()
            } else {
                BackButton(Modifier.align(Alignment.TopStart), onClick = navigator::back)
            }
            if (unreachable) {
                PageCover(stringResource(R.string.cinema_page_failed_title), stringResource(R.string.cinema_page_failed_text), page::reload)
                ReloadWhenOnline(page::reload)
            }
        }
        val text = field
        if (text != null && shown == null) {
            CarKeyboard(
                text = text,
                onTextChange = page::type,
                onSubmit = page::submit,
                onDismiss = page::dismissKeyboard,
                // The phone's clipboard: a password copied on the phone, to sign in.
                clipboard = true,
                onCopy = page::copy,
            )
        }
    }
}

/** myCar's only mark on a service's page: Back, small and see-through, in its top corner. */
@Composable
private fun BackButton(modifier: Modifier, onClick: () -> Unit) {
    val back = stringResource(R.string.common_back)
    Box(
        modifier
            .padding(4.dp)
            .size(BACK_TOUCH)
            .clip(CircleShape)
            .clickable(onClickLabel = back, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(BACK_DISC).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = back, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

private val BACK_TOUCH = 52.dp
private val BACK_DISC = 40.dp

/**
 * The services on in the car, as music apps for the grid, the dock and the place beside the map,
 * each with the icon its site gives itself - an initial on a colour until there's one.
 */
@Composable
internal fun rememberWebMusicEntries(): List<Entry> {
    val context = LocalContext.current
    val on by WebMusic.on.collectAsState()
    val icons by SiteIcons.icons.collectAsState()
    val services = remember(on) { WebMusic.enabled() }
    LaunchedEffect(services) { SiteIcons.load(context, services.map { it.site }) }
    return remember(services, icons) {
        services.map { webMusicEntry(WebMusic.mediaApp(context, it).component, it, icons[it.host]) }
    }
}

/** The services on in the car as its apps, away from its screen (the voice assistant's "open Spotify"). */
internal fun webMusicEntries(context: android.content.Context): List<Entry> =
    WebMusic.enabled().map { webMusicEntry(WebMusic.mediaApp(context, it).component, it, SiteIcons.icons.value[it.host]) }

/** The service whose player [id] (a component, as a pin or the running list knows it) is, as an app of the car's; null otherwise. */
internal fun webMusicEntry(id: String): Entry? {
    val component = ComponentName.unflattenFromString(id) ?: return null
    val service = WebMusic.serviceFor(component)?.takeIf { it.id in WebMusic.on.value } ?: return null
    return webMusicEntry(component, service, SiteIcons.icons.value[service.host])
}

private fun webMusicEntry(component: ComponentName, service: WebMusicService, icon: ImageBitmap?): Entry =
    Entry(
        service.name,
        icon ?: initialIcon(service),
        media = MediaApp(component, service.name, android.os.Process.myUid()),
        webMusic = service,
    )

private val initials = mutableMapOf<String, ImageBitmap>()

/** A service's initial on a colour of its own, as a Cinema site's tile has until its icon comes. */
private fun initialIcon(service: WebMusicService): ImageBitmap = initials.getOrPut(service.id) {
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(InitialColors[(service.host.hashCode() % InitialColors.size).absoluteValue].toArgb())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = ICON_PX * 0.42f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(service.name.take(1).uppercase(), ICON_PX / 2f, ICON_PX / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    bitmap.asImageBitmap()
}

private const val ICON_PX = 192
