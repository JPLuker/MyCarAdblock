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
internal fun CinemaGeckoSiteView(site: CinemaSite, startAt: String?, active: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    val adBlockEnabled by Cinema.adBlockEnabled.collectAsState()
    val sponsorBlockEnabled by Cinema.sponsorBlockEnabled.collectAsState()
    val allowed = !parkedOnly || rememberVideoAllowed()
    var browser by remember { mutableStateOf<CinemaBrowserSession?>(null) }
    var fieldText by remember { mutableStateOf<String?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { CarAudio.askAgainOnce(); CarAudio.captureNow() }

    // Apply ON/OFF to the live Gecko profile without destroying the browser session.
    // Changing this setting while a video is playing must not navigate to YouTube anew.
    LaunchedEffect(browser, adBlockEnabled) {
        browser?.setAdBlockEnabled(adBlockEnabled)
    }
    LaunchedEffect(browser, sponsorBlockEnabled) {
        browser?.setSponsorBlockEnabled(sponsorBlockEnabled)
    }

    DisposableEffect(browser, active) {
        if (!active) return@DisposableEffect onDispose { }
        val pause = browser?.let { session -> { session.pause() } }
        if (pause != null) CinemaNow.pause = pause
        val back: () -> Boolean = {
            val current = browser
            when {
                fullScreen -> { browser?.exitFullScreen(); fullScreen = false; true }
                current != null && current.canGoBack -> { current.goBack(); true }
                else -> { onClose(); true }
            }
        }
        navigator.backInterceptor = back
        onDispose {
            if (navigator.backInterceptor === back) navigator.backInterceptor = null
            if (pause != null && CinemaNow.pause === pause) CinemaNow.pause = null
        }
    }

    // A hidden GeckoView keeps its open session and playback position; no navigation reload.
    LaunchedEffect(active, browser) {
        browser?.setVisible(active)
        if (!active) {
            browser?.pause()
            browser?.view?.clearFocus()
            if (fullScreen) {
                browser?.exitFullScreen()
                fullScreen = false
            }
            fieldText = null
            navigator.cinemaVideoFullscreen = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { navigator.cinemaVideoFullscreen = false }
    }

    Column(Modifier.fillMaxSize()) {
        if (!fullScreen) {
            CinemaBar(
                site.name,
                onBack = { if (browser?.canGoBack == true) browser?.goBack() else onClose() },
                onClose = onClose,
                split = navigator.cinemaSplit,
                onToggleSplit = navigator::toggleCinemaSplit,
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
                        sponsorBlock = Cinema.sponsorBlockEnabled.value,
                        onVisited = { url -> CinemaContinue.site(context, site, url) },
                        onKeyboard = { fieldText = it },
                        onFullScreen = {
                            fullScreen = it
                            navigator.cinemaVideoFullscreen = it
                        },
                        onFailure = { failure = it },
                    )
                    if (instance == null) {
                        // The bridge may already have provided the actionable crash-loop error.
                        // Do not overwrite it with a generic missing-split message.
                        if (failure == null) failure = "Optional Firefox engine is not installed or could not start."
                        FrameLayout(ctx)
                    } else {
                        browser = instance
                        instance.view
                    }
                },
                onRelease = { releasedView ->
                    // Only close the session that actually owns the released AndroidView.
                    // Never tear down a newer session after a rapid engine/site switch.
                    val released = browser?.takeIf { it.view === releasedView }
                    released?.close()
                    if (released != null) browser = null
                },
            )
            if (fullScreen) {
                Box(Modifier.align(androidx.compose.ui.Alignment.TopEnd)) {
                    CinemaExitFullscreen {
                        browser?.exitFullScreen()
                        fullScreen = false
                        navigator.cinemaVideoFullscreen = false
                    }
                }
            }
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
