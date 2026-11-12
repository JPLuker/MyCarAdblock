package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import java.util.concurrent.atomic.AtomicBoolean
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.KeyEvent
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.cinema.CinemaBrowserFactory
import io.github.sirionero.mycar.cinema.CinemaBrowserSession
import io.github.sirionero.mycar.cinema.CinemaGeckoAttemptGuard
import io.github.sirionero.mycar.cinema.CinemaAdBlockAction
import io.github.sirionero.mycar.cinema.CinemaAdBlockTransitionPolicy
import io.github.sirionero.mycar.cinema.CinemaSponsorTransitionPolicy
import io.github.sirionero.mycar.cinema.Cinema
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession

/**
 * A browser implementation compiled only into :cinema_gecko, instantiated reflectively by
 * the base app after Android confirms the matching optional split is installed.
 */
class GeckoBrowserFactory : CinemaBrowserFactory {
    override fun create(
        context: Context,
        url: String,
        desktop: Boolean,
        adBlock: Boolean,
        sponsorBlock: Boolean,
        onVisited: (String) -> Unit,
        onKeyboard: (String?) -> Unit,
        onFullScreen: (Boolean) -> Unit,
        onFullScreenAspect: (Float?) -> Unit,
        onFailure: (String) -> Unit,
    ): CinemaBrowserSession {
        MyCarLog.i("gecko: starting optional runtime; packaged GeckoView=${BuildConfig.GECKOVIEW_VERSION}", "CINEMA")
        val runtime = CinemaGeckoRuntime.get(context.applicationContext)
        MyCarLog.i("gecko: runtime attached; creating browser session", "CINEMA")
        val options = GeckoSessionSettings.Builder()
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(options)
        // Compose's AndroidView can change bounds as the car toggles split/fullscreen.
        // Match its measured viewport exactly, not GeckoView's default WRAP_CONTENT.
        val view = GeckoView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        val result = RunningSession(context.applicationContext, runtime, session, view, url, onKeyboard, onFailure)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            // Cinema has one visible browser surface. Links that request a separate tab
            // (including some login links) should not open an invisible Gecko session.
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny>? {
                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW &&
                    (request.uri.startsWith("https://") || request.uri.startsWith("http://"))) {
                    session.loadUri(request.uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return null
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!result.isClosed && url != null &&
                    (url.startsWith("http://") || url.startsWith("https://"))) onVisited(url)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (!result.isClosed) result.hasBack = canGoBack
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFirstContentfulPaint(session: GeckoSession) {
                // The Gecko thread has survived native startup and rendered real content.
                // This is the earliest safe point to clear a *native* startup checkpoint.
                // Extension readiness alone proved too early in physical crash logs.
                if (result.markFirstPaint()) {
                    MyCarLog.i("gecko: first contentful paint; native startup checkpoint cleared", "CINEMA")
                    CinemaGeckoAttemptGuard.finish(context)
                }
            }
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                if (!result.isClosed) {
                    // Distinguish a real Gecko document-fullscreen exit from a host
                    // layout glitch. A TRUE -> FALSE pair immediately after a
                    // split-mode resize is the regression we are investigating.
                    MyCarLog.i("gecko: document fullscreen=$fullScreen", "CINEMA")
                    if (!fullScreen) onFullScreenAspect(null)
                    onFullScreen(fullScreen)
                }
            }
            override fun onCrash(session: GeckoSession) {
                if (!result.isClosed) {
                    MyCarLog.w("gecko: content process crashed; Java host is still running", "CINEMA")
                    onFailure("Firefox's page process stopped. Switch to Android WebView or try reopening this site.")
                }
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                if (!result.isClosed) result.activeMedia = mediaSession
            }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (result.activeMedia === mediaSession) result.activeMedia = null
            }

            override fun onFullscreen(
                session: GeckoSession,
                mediaSession: MediaSession,
                enabled: Boolean,
                meta: MediaSession.ElementMetadata?,
            ) {
                if (result.isClosed) return
                // Mozilla Bug 1750416: a GeckoView confined to a sub-window can
                // render video at the wrong fullscreen size. Gecko provides the
                // actual video dimensions here, so the host can aspect-fit its
                // *existing* GeckoView surface without restarting the webpage.
                val aspect = if (enabled && meta != null &&
                    meta.width > 0 && meta.height > 0
                ) {
                    (meta.width.toDouble() / meta.height.toDouble())
                        .takeIf { it.isFinite() && it in 0.25..4.0 }
                        ?.toFloat()
                } else null
                if (enabled) {
                    MyCarLog.i(
                        "gecko: fullscreen video metadata available=${aspect != null}; " +
                            "recomputing Cinema pane viewport",
                        "CINEMA",
                    )
                }
                onFullScreenAspect(aspect)
            }
        }
        session.textInput.setDelegate(object : GeckoSession.TextInputDelegate {
            override fun showSoftInput(session: GeckoSession) {
                if (!result.isClosed) onKeyboard(result.readInput())
            }
            override fun hideSoftInput(session: GeckoSession) {
                if (!result.isClosed) onKeyboard(null)
            }
            override fun restartInput(session: GeckoSession, reason: Int) {
                if (reason == GeckoSession.TextInputDelegate.RESTART_REASON_BLUR) {
                    if (!result.isClosed) onKeyboard(null)
                } else {
                    if (!result.isClosed) onKeyboard(result.readInput())
                }
            }
        })
        session.open(runtime)
        MyCarLog.i("gecko: session opened; attaching display", "CINEMA")
        view.setSession(session)
        MyCarLog.i("gecko: view attached; synchronizing the selected ad-block setting", "CINEMA")
        // This must run for BOTH settings. Simply bypassing WebExtensionController when OFF
        // leaves an already-installed uBlock Origin active in the persistent Gecko profile.
        // Navigation waits for the extension to be confirmed enabled or disabled, and later
        // settings changes are applied to the same GeckoSession without a reload.
        result.setSponsorBlockEnabled(sponsorBlock)
        result.setAdBlockEnabled(adBlock)
        return result
    }
}

private class RunningSession(
    private val context: Context,
    private val runtime: GeckoRuntime,
    private val session: GeckoSession,
    override val view: GeckoView,
    private val initialUrl: String,
    private val onKeyboard: (String?) -> Unit,
    private val onFailure: (String) -> Unit,
) : CinemaBrowserSession {
    @Volatile var isClosed: Boolean = false
        private set
    private var firstPaintReported = false

    fun markFirstPaint(): Boolean {
        if (isClosed || firstPaintReported) return false
        firstPaintReported = true
        return true
    }

    var hasBack = false
    var activeMedia: MediaSession? = null
    override val canGoBack: Boolean get() = hasBack

    private val handler = Handler(Looper.getMainLooper())
    private var desiredBlocking: Boolean? = null
    private var appliedBlocking: Boolean? = null
    private var desiredSponsor: Boolean? = null
    private var appliedSponsor: Boolean? = null
    private var sponsorFailureFor: Boolean? = null
    private var applyingBlocking = false
    private var navigationStarted = false
    private var fullscreenAlignmentRequested = false
    private var fullscreenAlignmentReady = false
    private var fullscreenAlignmentTimeout: Runnable? = null
    private var pendingTimeout: Runnable? = null

    override fun setAdBlockEnabled(enabled: Boolean) {
        if (isClosed) return
        desiredBlocking = enabled
        applyBlockingIfNeeded()
    }

    override fun setSponsorBlockEnabled(enabled: Boolean) {
        if (isClosed) return
        if (desiredSponsor != enabled) {
            desiredSponsor = enabled
            sponsorFailureFor = null
            Cinema.reportSponsorBlockError(null)
        }
        applyBlockingIfNeeded()
    }

    /**
     * Serializes extension changes: the user can flip the switch during an install or
     * update, but only the latest desired value may authorize initial navigation.
     * The WebExtensionController is shared by the runtime, so no parallel requests.
     */
    private fun applyBlockingIfNeeded() {
        if (isClosed) return
        when (CinemaAdBlockTransitionPolicy.next(
            desiredBlocking, appliedBlocking, applyingBlocking, navigationStarted,
        )) {
            CinemaAdBlockAction.WAIT -> return
            CinemaAdBlockAction.IDLE, CinemaAdBlockAction.NAVIGATE -> {
                // SponsorBlock is optional but its persistent state must also be checked.
                // Only one Gecko extension operation may run at a time.
                val sponsor = desiredSponsor
                if (sponsor != null &&
                    CinemaSponsorTransitionPolicy.needsSync(sponsor, appliedSponsor, sponsorFailureFor)) {
                    applySponsorBlockIfNeeded(sponsor)
                    return
                }
                startNavigationOnce()
                return
            }
            CinemaAdBlockAction.SYNCHRONIZE -> Unit
        }
        val requested = desiredBlocking ?: return
        applyingBlocking = true
        val settled = AtomicBoolean(false)
        val timeout = Runnable {
            if (settled.compareAndSet(false, true)) {
                pendingTimeout = null
                applyingBlocking = false
                failBlocking("Firefox could not confirm the ad-block setting. Use Android WebView or retry.")
            }
        }
        pendingTimeout = timeout
        if (!handler.postDelayed(timeout, 30_000L)) {
            pendingTimeout = null
            applyingBlocking = false
            failBlocking("Firefox could not schedule extension initialization.")
            return
        }
        MyCarLog.i("gecko: synchronizing signed uBlock Origin (enabled=$requested)", "CINEMA")
        try {
            CinemaUBlock.ensure(context, runtime, requested, onReady = {
                if (settled.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    pendingTimeout = null
                    applyingBlocking = false
                    if (!isClosed) {
                        appliedBlocking = requested
                        MyCarLog.i("gecko: uBlock Origin state applied (enabled=$requested)", "CINEMA")
                        // If a later settings change arrived while the operation was running,
                        // apply the newer state BEFORE navigating to the first page.
                        applyBlockingIfNeeded()
                    }
                }
            }, onError = { reason ->
                if (settled.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    pendingTimeout = null
                    applyingBlocking = false
                    if (!isClosed) failBlocking(reason)
                }
            })
        } catch (failure: Exception) {
            if (settled.compareAndSet(false, true)) {
                handler.removeCallbacks(timeout)
                pendingTimeout = null
                applyingBlocking = false
                MyCarLog.e("gecko: extension state synchronization threw", failure, "CINEMA")
                if (!isClosed) failBlocking("Firefox could not apply its ad-block setting.")
            }
        }
    }

    /** SponsorBlock failures are visible in Settings, but never prevent YouTube loading. */
    private fun applySponsorBlockIfNeeded(requested: Boolean) {
        if (isClosed || applyingBlocking) return
        applyingBlocking = true
        val settled = AtomicBoolean(false)
        val timeout = Runnable {
            if (settled.compareAndSet(false, true)) {
                pendingTimeout = null
                applyingBlocking = false
                failSponsorBlock(requested, "SponsorBlock initialization timed out.")
            }
        }
        pendingTimeout = timeout
        if (!handler.postDelayed(timeout, 30_000L)) {
            pendingTimeout = null
            applyingBlocking = false
            failSponsorBlock(requested, "SponsorBlock initialization could not be scheduled.")
            return
        }
        try {
            CinemaSponsorBlock.ensure(context, runtime, requested, onReady = {
                if (settled.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    pendingTimeout = null
                    applyingBlocking = false
                    if (!isClosed) {
                        appliedSponsor = requested
                        Cinema.reportSponsorBlockError(null)
                        applyBlockingIfNeeded()
                    }
                }
            }, onError = { message ->
                if (settled.compareAndSet(false, true)) {
                    handler.removeCallbacks(timeout)
                    pendingTimeout = null
                    applyingBlocking = false
                    if (!isClosed) failSponsorBlock(requested, message)
                }
            })
        } catch (failure: Exception) {
            if (settled.compareAndSet(false, true)) {
                handler.removeCallbacks(timeout)
                pendingTimeout = null
                applyingBlocking = false
                MyCarLog.e("gecko: SponsorBlock synchronization threw", failure, "CINEMA")
                if (!isClosed) failSponsorBlock(requested, "SponsorBlock could not initialize.")
            }
        }
    }

    private fun failSponsorBlock(requested: Boolean, reason: String) {
        sponsorFailureFor = requested
        MyCarLog.w("gecko: SponsorBlock unavailable: $reason", "CINEMA")
        Cinema.reportSponsorBlockError(reason)
        if (!requested) {
            // A failed opt-out is not optional: SponsorBlock may still be running
            // against YouTube. Never claim it was disabled or continue browsing.
            pause()
            CinemaGeckoAttemptGuard.finish(context)
            onFailure("Firefox could not disable SponsorBlock. Use Android WebView or retry after restarting Firefox.")
            return
        }
        // Failed opt-in is harmless to the existing page, so preserve browser access.
        applyBlockingIfNeeded()
    }

    private fun failBlocking(reason: String) {
        // Stop active media before showing the failure overlay. In particular, never
        // leave video playing invisibly if the user requested uBlock to switch OFF.
        pause()
        // This is a managed extension error rather than a native crash. Do not leave the
        // native-startup checkpoint set after reporting a recoverable Java/Kotlin failure.
        CinemaGeckoAttemptGuard.finish(context)
        MyCarLog.w("gecko: signed uBlock Origin synchronization failed: $reason", "CINEMA")
        onFailure(reason)
    }

    private fun startNavigationOnce() {
        if (isClosed || navigationStarted) return
        // No navigation is allowed before the selected ad-block state has been verified.
        if (desiredBlocking == null || appliedBlocking != desiredBlocking) return
        if (!CinemaSponsorTransitionPolicy.allowsNavigation(
                desiredSponsor, appliedSponsor, sponsorFailureFor,
            )) return

        // A tiny first-party content script aligns YouTube's VIDEO and CAPTIONS
        // without resizing Gecko's Android surface. Install it before loading the
        // first webpage so its content script catches the fullscreen transition.
        // A missing/unsupported asset must never keep Firefox from navigating.
        if (!fullscreenAlignmentReady) {
            if (!fullscreenAlignmentRequested) {
                fullscreenAlignmentRequested = true
                val settled = AtomicBoolean(false)
                val timeout = Runnable {
                    if (settled.compareAndSet(false, true)) {
                        fullscreenAlignmentTimeout = null
                        MyCarLog.w(
                            "gecko: fullscreen alignment script timed out; browsing without it",
                            "CINEMA",
                        )
                        fullscreenAlignmentReady = true
                        startNavigationOnce()
                    }
                }
                fullscreenAlignmentTimeout = timeout
                handler.postDelayed(timeout, 8_000L)
                CinemaFullscreenAlignment.ensure(runtime) {
                    if (settled.compareAndSet(false, true)) {
                        handler.removeCallbacks(timeout)
                        fullscreenAlignmentTimeout = null
                        fullscreenAlignmentReady = true
                        if (!isClosed) startNavigationOnce()
                    }
                }
            }
            return
        }
        navigationStarted = true
        MyCarLog.i("gecko: extension state confirmed; navigating without a session reload", "CINEMA")
        runCatching { session.loadUri(initialUrl) }
            .onFailure {
                MyCarLog.e("gecko: navigation failed", it, "CINEMA")
                onFailure("Firefox could not navigate to this website.")
            }
    }

    private fun connection() = if (isClosed) null else session.textInput.onCreateInputConnection(EditorInfo())

    fun readInput(): String = connection()
        ?.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString().orEmpty()

    override fun setText(text: String) {
        if (isClosed) return
        // The on-car keyboard supplies whole strings. Replace the current selection atomically
        // through Gecko's real input connection, not by injecting JavaScript into page content.
        connection()?.apply {
            beginBatchEdit()
            performContextMenuAction(android.R.id.selectAll)
            commitText(text, 1)
            endBatchEdit()
        }
    }

    override fun submitText() {
        if (isClosed) return
        val input = connection()
        if (input?.performEditorAction(EditorInfo.IME_ACTION_GO) != true &&
            input?.performEditorAction(EditorInfo.IME_ACTION_DONE) != true) {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            session.textInput.onKeyDown(KeyEvent.KEYCODE_ENTER, down)
            session.textInput.onKeyUp(KeyEvent.KEYCODE_ENTER, up)
        }
        onKeyboard(null)
    }

    override fun goBack() { if (!isClosed && hasBack) session.goBack() }
    override fun reload() { if (!isClosed) session.reload() }
    // Inactive GeckoSession is a render-visibility hint, not an HTML video pause.
    override fun pause() {
        if (!isClosed) runCatching { activeMedia?.pause() }
            .onFailure { MyCarLog.w("gecko: media pause failed (${it.javaClass.simpleName})", "CINEMA") }
    }

    override fun setVisible(visible: Boolean) {
        if (isClosed) return
        if (!visible) pause()
        // GeckoSession#setActive is explicitly about rendering visibility, not navigation.
        // Keeping the GeckoSession and its GeckoView attached preserves the page/seek state.
        session.setActive(visible)
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun exitFullScreen() { if (!isClosed) session.exitFullScreen() }
    override fun close() {
        if (isClosed) return
        isClosed = true
        pendingTimeout?.let(handler::removeCallbacks)
        pendingTimeout = null
        fullscreenAlignmentTimeout?.let(handler::removeCallbacks)
        fullscreenAlignmentTimeout = null
        activeMedia = null
        onKeyboard(null)
        runCatching { view.releaseSession() }
            .onFailure { MyCarLog.w("gecko: releaseSession failed (${it.javaClass.simpleName})", "CINEMA") }
        runCatching { session.close() }
            .onFailure { MyCarLog.w("gecko: session close failed (${it.javaClass.simpleName})", "CINEMA") }
    }
}

/**
 * Exactly one GeckoRuntime per car-app process. Mozilla specifies a process-lifetime runtime for
 * apps that own sessions; re-creating it whenever Cinema opens can fail or tear down pages.
 */
internal object CinemaGeckoRuntime {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun get(context: Context): GeckoRuntime {
        runtime?.let { return it }
        val app = context.applicationContext
        val split = findGeckoResourceSplit(app)
        // GeckoThread builds its native -greomni argument from
        // GeckoAppShell.getApplicationContext().getPackageResourcePath().
        // The ordinary application context returns BASE.APK, but assets/omni.ja
        // is delivered in the optional cinema_gecko MASTER split. That leaves
        // Gecko with a successfully loaded libxul.so and no valid omnijar.
        // Point only Gecko's resource-path query at the Android-installed,
        // same-signature split while delegating all other Context operations.
        val geckoContext = GeckoResourceContext(app, split)
        MyCarLog.i("gecko: native omnijar source=${split.name}; present in installed feature", "CINEMA")
        val created = GeckoRuntime.create(geckoContext)
        runtime = created
        return created
    }
}

/** Mozilla's -greomni path must name the APK *containing* assets/omni.ja. */
private class GeckoResourceContext(base: Context, private val featureApk: java.io.File) :
    ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getPackageResourcePath(): String = featureApk.absolutePath
}

/** Use only a split path already installed by Android's package manager. */
private fun findGeckoResourceSplit(context: Context): java.io.File {
    val info = context.applicationInfo
    val splits = info.splitSourceDirs.orEmpty()
    val names = info.splitNames.orEmpty()
    val apk = splits.indices
        .asSequence()
        .filter { names.getOrNull(it) == "cinema_gecko" }
        .map { java.io.File(splits[it]) }
        .firstOrNull { candidate ->
            candidate.isFile && runCatching {
                java.util.zip.ZipFile(candidate).use { zip ->
                    zip.getEntry("assets/omni.ja") != null
                }
            }.getOrDefault(false)
        }
        ?: throw IllegalStateException(
            "Firefox's installed feature is missing assets/omni.ja. Import the matching Gecko ZIP again.",
        )
    return apk
}
