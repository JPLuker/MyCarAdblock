package io.github.sirionero.mycar.car

import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.CinemaBrowserEngine
import io.github.sirionero.mycar.cinema.CinemaBrowserSession
import io.github.sirionero.mycar.cinema.CinemaGeckoBridge
import io.github.sirionero.mycar.cinema.CinemaSite

/**
 * GeckoView's alternative to CinemaSiteView. It shares the car UI, safety overlay and on-car
 * keyboard, but its browser is constructed reflectively from an optional signed APK split.
 * An absent/crashed split can never prevent the normal WebView from opening the same site.
 */
@Composable
internal fun CinemaGeckoSiteView(site: CinemaSite, startAt: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    val allowed = !parkedOnly || rememberVideoAllowed()
    var browser by remember { mutableStateOf<CinemaBrowserSession?>(null) }
    var fieldText by remember { mutableStateOf<String?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { CarAudio.askAgainOnce(); CarAudio.captureNow() }

    DisposableEffect(browser) {
        val pause = browser?.let { active -> { active.pause() } }
        if (pause != null) CinemaNow.pause = pause
        navigator.backInterceptor = {
            val current = browser
            when {
                fullScreen -> { browser?.exitFullScreen(); fullScreen = false; true }
                current != null && current.canGoBack -> { current.goBack(); true }
                else -> { onClose(); true }
            }
        }
        onDispose {
            navigator.backInterceptor = null
            if (pause != null && CinemaNow.pause === pause) CinemaNow.pause = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!fullScreen) {
            CinemaBar(site.name,
                onBack = { if (browser?.canGoBack == true) browser?.goBack() else onClose() },
                onClose = onClose,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val instance = CinemaGeckoBridge.open(
                        context = ctx,
                        url = startAt ?: site.url,
                        desktop = site.desktop,
                        adBlock = Cinema.adBlockEnabled.value,
                        onVisited = { url -> CinemaContinue.site(context, site, url) },
                        onKeyboard = { fieldText = it },
                        onFullScreen = { fullScreen = it },
                        onFailure = { failure = it },
                    )
                    if (instance == null) {
                        failure = "Optional Firefox engine is not installed or could not start."
                        FrameLayout(ctx)
                    } else {
                        browser = instance
                        instance.view
                    }
                },
                onRelease = { browser?.close(); browser = null },
            )
            if (failure != null) {
                PageCover(
                    "Firefox engine unavailable",
                    (failure ?: "") + "\n\nTap to use the built-in Android WebView.",
                ) {
                    Cinema.setBrowserEngine(CinemaBrowserEngine.ANDROID_WEBVIEW)
                    failure = null
                }
            }
            if (!allowed) ParkedCover()
        }
        val text = fieldText
        if (text != null && !fullScreen) {
            CarKeyboard(
                text = text,
                onTextChange = { updated ->
                    fieldText = updated
                    browser?.setText(updated)
                },
                onSubmit = { browser?.submitText() },
                onDismiss = { fieldText = null },
                clipboard = true,
            )
        }
    }
}
