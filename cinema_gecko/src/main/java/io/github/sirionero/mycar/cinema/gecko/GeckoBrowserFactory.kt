package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.KeyEvent
import io.github.sirionero.mycar.cinema.CinemaBrowserFactory
import io.github.sirionero.mycar.cinema.CinemaBrowserSession
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

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
        val runtime = GeckoRuntime.getDefault(context.applicationContext)
        val options = GeckoSessionSettings.Builder()
            .userAgentMode(if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP else GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .viewportMode(if (desktop) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP else GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
            .build()
        val session = GeckoSession(options)
        val view = GeckoView(context)
        val result = RunningSession(runtime, session, view, onKeyboard)
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
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
                onFailure("Firefox's page process stopped. Switch to Android WebView or try reopening this site.")
            }
        }
        session.textInput.delegate = object : GeckoSession.TextInputDelegate {
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
        }
        session.open(runtime)
        view.setSession(session)

        // Do not load the page without blocking when the driver selected uBO. A signed add-on
        // installation may be necessary on the first launch, and is asynchronous.
        CinemaUBlock.ensure(runtime, adBlock, onReady = {
            session.loadUri(url)
        }, onError = {
            onFailure(it)
        })
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
    override fun pause() { session.setActive(false) }
    override fun close() {
        onKeyboard(null)
        view.releaseSession()
        session.close()
    }
}
