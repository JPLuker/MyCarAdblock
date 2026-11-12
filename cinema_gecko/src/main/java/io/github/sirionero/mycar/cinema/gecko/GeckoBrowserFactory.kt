package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.KeyEvent
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.cinema.CinemaBrowserFactory
import io.github.sirionero.mycar.cinema.CinemaBrowserSession
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
        onVisited: (String) -> Unit,
        onKeyboard: (String?) -> Unit,
        onFullScreen: (Boolean) -> Unit,
        onFailure: (String) -> Unit,
    ): CinemaBrowserSession {
        MyCarLog.i("gecko: starting optional runtime", "CINEMA")
        val runtime = CinemaGeckoRuntime.get(context.applicationContext)
        MyCarLog.i("gecko: runtime attached; creating browser session", "CINEMA")
        val options = GeckoSessionSettings.Builder()
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(options)
        val view = GeckoView(context)
        val result = RunningSession(runtime, session, view, onKeyboard)
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
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) onVisited(url)
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                result.hasBack = canGoBack
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) = onFullScreen(fullScreen)
            override fun onCrash(session: GeckoSession) {
                MyCarLog.w("gecko: content process crashed; Java host is still running", "CINEMA")
                onFailure("Firefox's page process stopped. Switch to Android WebView or try reopening this site.")
            }
        }
        session.mediaSessionDelegate = object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                result.activeMedia = mediaSession
            }
            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                if (result.activeMedia === mediaSession) result.activeMedia = null
            }
        }
        session.textInput.setDelegate(object : GeckoSession.TextInputDelegate {
            override fun showSoftInput(session: GeckoSession) {
                onKeyboard(result.readInput())
            }
            override fun hideSoftInput(session: GeckoSession) = onKeyboard(null)
            override fun restartInput(session: GeckoSession, reason: Int) {
                if (reason == GeckoSession.TextInputDelegate.RESTART_REASON_BLUR) {
                    onKeyboard(null)
                } else {
                    onKeyboard(result.readInput())
                }
            }
        })
        session.open(runtime)
        MyCarLog.i("gecko: session opened; attaching display", "CINEMA")
        view.setSession(session)
        MyCarLog.i("gecko: view attached; initializing signed extension", "CINEMA")

        // Do not load the page without blocking when the driver selected uBO. A signed add-on
        // installation may be necessary on the first launch, and is asynchronous.
        try {
            CinemaUBlock.ensure(context, runtime, adBlock, onReady = {
                MyCarLog.i("gecko: add-on ready; loading website", "CINEMA")
                session.loadUri(url)
            }, onError = {
                MyCarLog.w("gecko: add-on initialization failed: $it", "CINEMA")
                onFailure(it)
            })
        } catch (failure: Exception) {
            MyCarLog.e("gecko: add-on initialization threw", failure, "CINEMA")
            onFailure("Firefox add-on initialization failed. Open the myCar log for details.")
        }
        return result
    }
}

private class RunningSession(
    private val runtime: GeckoRuntime,
    private val session: GeckoSession,
    override val view: GeckoView,
    private val onKeyboard: (String?) -> Unit,
) : CinemaBrowserSession {
    var hasBack = false
    var activeMedia: MediaSession? = null
    override val canGoBack: Boolean get() = hasBack

    private fun connection() = session.textInput.onCreateInputConnection(EditorInfo())

    fun readInput(): String = connection()
        ?.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString().orEmpty()

    override fun setText(text: String) {
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

    override fun goBack() { if (hasBack) session.goBack() }
    override fun reload() = session.reload()
    // Inactive GeckoSession is a render-visibility hint, not an HTML video pause.
    override fun pause() { activeMedia?.pause() }

    override fun setVisible(visible: Boolean) {
        if (!visible) pause()
        // GeckoSession#setActive is explicitly about rendering visibility, not navigation.
        // Keeping the GeckoSession and its GeckoView attached preserves the page/seek state.
        session.setActive(visible)
        view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    override fun exitFullScreen() { session.exitFullScreen() }
    override fun close() {
        onKeyboard(null)
        activeMedia = null
        view.releaseSession()
        session.close()
    }
}

/**
 * Exactly one GeckoRuntime per car-app process. Mozilla specifies a process-lifetime runtime for
 * apps that own sessions; re-creating it whenever Cinema opens can fail or tear down pages.
 */
private object CinemaGeckoRuntime {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun get(context: Context): GeckoRuntime {
        runtime?.let { return it }
        val created = GeckoRuntime.create(context.applicationContext)
        runtime = created
        return created
    }
}
