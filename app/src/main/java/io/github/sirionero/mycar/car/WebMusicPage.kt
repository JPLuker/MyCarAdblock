package io.github.sirionero.mycar.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.cinema.SiteIcons
import io.github.sirionero.mycar.webmusic.WebMusic
import io.github.sirionero.mycar.webmusic.WebMusicPlayer
import io.github.sirionero.mycar.webmusic.WebMusicService
import io.github.sirionero.mycar.webmusic.WebPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

/** The drive's pages of the music services' web players: the car's screen makes it, each page only when wanted. */
internal val LocalWebMusicPages = staticCompositionLocalOf<WebMusicPages?> { null }

/**
 * The music services' web players in the car (see webmusic/WebMusic), one page per service for
 * the whole drive: made the first time the service is opened, or its player is asked to play, and
 * then kept until the car's screen goes - out of sight too, so the music goes on with the map or
 * another app in front, and the service comes back where it was. Never more pages than services
 * opened this drive.
 */
internal class WebMusicPages(private val context: Context) : WebMusicPlayer.Pages {
    private val made = SystemClock.uptimeMillis()
    private val pages = mutableMapOf<String, WebMusicPage>()

    init {
        WebMusicPlayer.pages = this
    }

    fun page(service: WebMusicService): WebMusicPage = pages.getOrPut(service.id) { WebMusicPage(context, service, made) }

    override fun open(service: WebMusicService): WebMusicPlayer.Page = page(service).also { it.make() }

    fun destroy() {
        if (WebMusicPlayer.pages === this) WebMusicPlayer.pages = null
        pages.values.forEach { it.destroy() }
        pages.clear()
    }
}

/**
 * One music service's web player in the car: its own page, as on a computer (a computer's user
 * agent, as Cinema's computer layout), laid out at the width the car's screen gives it at myCar's
 * size, so the service's own layout - library, main view, player bar - fits the car. Never paused
 * while the drive lasts; signed in for good, as the web engine keeps its cookies, written out
 * whenever the page is left. What it plays reaches myCar's player, the dock and the car's buttons
 * through [WebMusicPlayer], and the car's buttons reach it back.
 */
internal class WebMusicPage(
    private val context: Context,
    val service: WebMusicService,
    /** When the car's screen came up: a page is made no sooner than [AFTER_MS] after. */
    private val screenUp: Long,
) : WebMusicPlayer.Page {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // A page's dropdown can't close myCar on the car's screen (see carWebContext). Dark, as the car's
    // screen is: a service that follows the system's colours draws dark.
    private val themed = ContextThemeWrapper(carWebContext(context), android.R.style.Theme_Material_NoActionBar)
    private val density = context.resources.displayMetrics.density
    private val ring = String.format("#%06X", MyCarPalette.Accent.toArgb() and 0xFFFFFF)

    private var web: WebView? = null
    private var making = false
    private var released = false
    private val hosts = mutableListOf<FrameLayout>()
    private var loaded = false
    private var viewport = 0
    private var widthScript: ScriptHandler? = null
    private var probed = false
    private var pressedPlay = false

    /** The page has spoken since it loaded: its own buttons and handlers are there for the car's. */
    private var heard = false

    /** A button that came before the page could take it: sent once it can. */
    private var pending: Pair<String, Double?>? = null
    private var wasPlaying = false

    /** The text of the page's field the car's keyboard types in; null with none focused. */
    val field = MutableStateFlow<String?>(null)

    /** A video the page put full screen (a video podcast), shown over it. */
    val fullScreen = MutableStateFlow<Pair<View, WebChromeClient.CustomViewCallback>?>(null)

    /** The page couldn't be reached (no signal): it loads again when a network comes back, or at a tap. */
    val unreachable = MutableStateFlow(false)

    init {
        // The car's size setting: the page lays out wider or narrower.
        scope.launch { CarSettings.scale.drop(1).collect { web?.let { w -> if (w.width > 0) fit(w.width) } } }
    }

    /** Puts the page in [frame], the place on screen it shows in now; the last asked wins. */
    fun show(frame: FrameLayout) {
        hosts.remove(frame)
        hosts.add(frame)
        make()
        place()
    }

    /** [frame] is leaving the screen: the page goes back to the place shown before, or plays on unseen. */
    fun hide(frame: FrameLayout) {
        hosts.remove(frame)
        place()
        if (hosts.isEmpty()) leftScreen()
    }

    /**
     * The page, made now, or a moment after the car's screen came up: making the first web page
     * of a drive holds the car's screen for a moment, never while the car connects.
     */
    fun make() {
        if (web != null || making || released) return
        making = true
        val wait = (screenUp + AFTER_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        main.postDelayed({
            making = false
            if (released || web != null) return@postDelayed
            web = createWebView()
            WebMusicPlayer.attach(service.id, this)
            place()
            loadSoon()
        }, wait)
    }

    fun goBack(): Boolean {
        val w = web ?: return false
        if (!w.canGoBack()) return false
        w.goBack()
        return true
    }

    fun exitFullScreen(): Boolean {
        val video = fullScreen.value ?: return false
        fullScreen.value = null
        runCatching { video.second.onCustomViewHidden() }
        return true
    }

    fun reload() {
        unreachable.value = false
        web?.reload()
    }

    // The car's keyboard, typing in the page's field as in the browser's.
    fun type(text: String) {
        field.value = text
        web?.evaluateJavascript(setFieldValueJs(text), null)
    }

    fun submit() {
        web?.evaluateJavascript(SUBMIT_FIELD_JS, null)
    }

    fun dismissKeyboard() {
        field.value = null
        web?.evaluateJavascript(BLUR_FIELD_JS, null)
    }

    fun copy() {
        web?.let(CarClipboard::copyFromPage)
    }

    override fun act(action: String, seconds: Double?) = send(action, seconds, retries = if (heard) 0 else RETRIES)

    /** Signed out on the phone: the page starts again, with what it kept in its storage gone too. */
    override fun signedOut() {
        val w = web ?: return
        w.evaluateJavascript(CLEAR_STORAGE_JS) { if (web === w) w.loadUrl(service.url) }
    }

    /**
     * A button for the page. Before the page can take it, it waits; just loaded, the service's own
     * player may not be there yet, so it's tried again for a while.
     */
    private fun send(action: String, seconds: Double?, retries: Int) {
        val w = web
        if (w == null || !heard) {
            pending = action to seconds
            make()
            return
        }
        if (!ours(w.url)) return
        val arg = seconds?.let { ", $it" } ?: ""
        w.evaluateJavascript("window.__mycarMusicAct ? window.__mycarMusicAct('$action'$arg) : 'no script'") { how ->
            if ((how == "\"none\"" || how == "\"no script\"") && retries > 0) {
                main.postDelayed({ send(action, seconds, retries - 1) }, RETRY_MS)
            } else {
                MyCarLog.i("music: ${service.id} $action ($how)")
            }
        }
    }

    fun destroy() {
        released = true
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        hosts.clear()
        exitFullScreen()
        WebMusicPlayer.detach(service.id, this)
        web?.let { w ->
            WebSelect.cancel(w)
            (w.parent as? ViewGroup)?.removeView(w)
            w.stopLoading()
            w.destroy()
        }
        web = null
        CookieManager.getInstance().flush()
    }

    private fun place() {
        val w = web ?: return
        val target = hosts.lastOrNull()
        if (w.parent === target) return
        (w.parent as? ViewGroup)?.removeView(w)
        target?.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** Left: no video stays full screen, no keyboard open, and the sign-in is written out. */
    private fun leftScreen() {
        exitFullScreen()
        if (field.value != null) dismissKeyboard()
        CookieManager.getInstance().flush()
    }

    /** Loaded once it has a width: its place's, or, out of sight, the car's screen beside the dock. */
    private fun loadSoon() {
        main.postDelayed({
            val w = web ?: return@postDelayed
            if (!loaded) fit(w.width.takeIf { it > 0 } ?: outOfSightWidth())
        }, LAYOUT_WAIT_MS)
    }

    private fun outOfSightWidth(): Int {
        val dock = MyCarSize.DockWidth.value * density * CarSettings.scale.value
        return (CarEnvironment.frame.value.width - dock).roundToInt().coerceAtLeast(1)
    }

    /**
     * The page laid out [widthPx] wide at myCar's size, as a computer's window that wide - never
     * narrower than [MIN_WIDTH], where these players fold away their parts - then fitted to its place.
     */
    private fun fit(widthPx: Int) {
        val w = web ?: return
        val css = maxOf((widthPx / density / CarSettings.scale.value).roundToInt(), MIN_WIDTH)
        if (css != viewport) {
            viewport = css
            // For the pages to come, from their start; for this one, now.
            runCatching {
                widthScript?.remove()
                widthScript = WebViewCompat.addDocumentStartJavaScript(w, "window.__mycarMusicWidth = $css;", service.origins.toSet())
            }
            w.evaluateJavascript("window.__mycarMusicViewport && window.__mycarMusicViewport($css);", null)
        }
        if (!loaded) {
            loaded = true
            MyCarLog.i("music: opening ${service.name}, laid out ${css}px wide")
            w.loadUrl(service.url)
        }
    }

    private fun ours(url: String?): Boolean = url != null && service.origins.any { url == it || url.startsWith("$it/") }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(themed)
        // Black before the service paints, never white.
        webView.setBackgroundColor(Color.BLACK)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        // The car's buttons start it, with nobody touching the page.
        s.mediaPlaybackRequiresUserGesture = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        s.userAgentString = userAgentFor(webView, desktop = true)
        CookieManager.getInstance().setAcceptCookie(true)
        webView.webViewClient = Client()
        // Full screen when its player asks (camera and microphone never; protected audio, which most
        // of these need, yes), and the site's own icon kept for its tile: as Cinema's sites.
        webView.webChromeClient = CinemaChrome(
            onShow = { view, callback -> fullScreen.value = view to callback },
            onHide = { fullScreen.value = null },
            onIcon = { SiteIcons.offer(context, service.host, it) },
        )
        webView.addJavascriptInterface(FieldFocusBridge(onFocus = { field.value = it }, onBlur = { field.value = null }), "MyCarField")
        WebSelect.install(webView)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        ) {
            runCatching {
                WebViewCompat.addWebMessageListener(webView, "MyCarMusic", service.origins.toSet()) { view, message, _, isMainFrame, _ ->
                    if (isMainFrame && view === web) onMessage(message.data)
                }
                WebViewCompat.addDocumentStartJavaScript(webView, WebMusicScripts.setup(service), service.origins.toSet())
            }.onFailure { MyCarLog.w("music: ${service.id}: the page's script wasn't added: ${it.message}") }
        } else {
            MyCarLog.w("music: this web engine can't run the page's script: myCar's player won't follow ${service.name}")
        }
        // Moved to a wider or narrower place: laid out again at that width.
        webView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left > 0 && right - left != oldRight - oldLeft) fit(right - left)
        }
        return webView
    }

    private fun onMessage(raw: String?) {
        if (raw == null || raw.length > MAX_MESSAGE) return
        val o = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (o.optString("type")) {
            "state" -> report(o)
            "probe" -> MyCarLog.i("music: ${service.id} in the car's web engine: " + o.toString().take(MAX_LOG))
            "layout" -> MyCarLog.i("music: ${service.id} page: " + o.toString().take(MAX_LOG))
        }
    }

    private fun report(o: JSONObject) {
        val state = WebPlayerState(
            title = o.optString("title").take(MAX_TEXT),
            artist = o.optString("artist").take(MAX_TEXT),
            album = o.optString("album").take(MAX_TEXT),
            artUrl = o.optString("art").takeIf { it.startsWith("https://") && it.length < MAX_URL }.orEmpty(),
            playing = o.optBoolean("playing"),
            positionMs = o.optLong("position").coerceAtLeast(0),
            durationMs = o.optLong("duration").coerceAtLeast(0),
            at = SystemClock.elapsedRealtime(),
            canSeek = o.optBoolean("seek"),
        )
        WebMusicPlayer.report(service.id, state)
        if (!heard) {
            heard = true
            sendPending()
        }
        if (state.playing && !wasPlaying) becomeTheMusic()
        wasPlaying = state.playing
    }

    private fun sendPending() {
        val (action, seconds) = pending ?: return
        pending = null
        send(action, seconds, RETRIES)
    }

    /** Playing, it's the car's music: myCar's player, the dock and the car's buttons follow it. */
    private fun becomeTheMusic() {
        val app = WebMusic.mediaApp(context, service)
        if (CarServices.mediaApp.value?.component == app.component) return
        MyCarLog.i("music: ${service.name} plays - now the car's music")
        CarServices.openMedia(app, remember = false)
    }

    private inner class Client : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (view !== web) return
            unreachable.value = false
            field.value = null
            heard = false
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (view !== web) return
            view.evaluateJavascript(FOCUS_TRACKING_JS, null)
            view.evaluateJavascript(focusRingJs(ring), null)
            CookieManager.getInstance().flush()
            MyCarLog.i("music: ${service.id} page loaded: $url (${view.title})")
            if (!probed && ours(url)) {
                probed = true
                view.evaluateJavascript(WebMusicScripts.probe(BuildConfig.DEBUG), null)
            }
            if (WebMusicPreview.play && !pressedPlay && ours(url)) {
                pressedPlay = true
                main.postDelayed({
                    web?.evaluateJavascript(PLAY_SOMETHING_JS) { MyCarLog.i("music: preview pressed ${service.id}'s first Play: $it") }
                }, PREVIEW_PLAY_MS)
            }
            // A page that never speaks (another address, a web engine without the script): the
            // buttons go to it all the same after a moment.
            main.postDelayed({
                if (web === view && !heard) {
                    heard = true
                    sendPending()
                }
            }, HEARD_WAIT_MS)
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = view.handledAppLink(request)

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view !== web || !request.isForMainFrame) return
            MyCarLog.w("music: ${request.url} didn't load - ${error.errorCode} ${error.description}")
            if (error.errorCode in NO_NETWORK) unreachable.value = true
        }

        // The web engine's page process is one for the whole app: when it dies, every web view on it
        // must say it handled that, or Android ends myCar - and the drive. This one opens again.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            MyCarLog.w("music: ${service.id}: the page closed (crash: ${detail.didCrash()}) - opening it again")
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
            if (view === web && !released) {
                loaded = false
                viewport = 0
                widthScript = null
                heard = false
                fullScreen.value = null
                field.value = null
                WebMusicPlayer.report(service.id, WebPlayerState())
                web = createWebView()
                place()
                loadSoon()
            }
            return true
        }
    }

    private companion object {
        /** After the car's screen comes up, as Plancia's page: the map and the first frames go first. */
        const val AFTER_MS = 2_500L
        const val LAYOUT_WAIT_MS = 400L
        const val HEARD_WAIT_MS = 5_000L
        const val RETRIES = 20
        const val RETRY_MS = 1_000L

        /** Narrower than this, these players fold away their parts: the page is drawn smaller instead. */
        const val MIN_WIDTH = 800
        const val MAX_MESSAGE = 30_000
        const val MAX_LOG = 3_500
        const val MAX_TEXT = 300
        const val MAX_URL = 2_000

        const val CLEAR_STORAGE_JS = "try { localStorage.clear(); sessionStorage.clear(); } catch (e) {}"

        const val PREVIEW_PLAY_MS = 8_000L

        /** The page's first "Play" button in sight, pressed: for the car preview ([WebMusicPreview]). */
        const val PLAY_SOMETHING_JS = """
(function () {
  var all = Array.prototype.slice.call(document.querySelectorAll('button[aria-label^="Play"], a[aria-label^="Play"], [role="button"][aria-label^="Play"]'));
  var shown = all.filter(function (e) { var r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; });
  if (!shown.length) return 'nothing to play';
  shown[0].click();
  return shown[0].getAttribute('aria-label');
})()
"""
    }
}

/** For the car preview (debug builds), where no account plays: a page presses its first "Play" a few seconds in. */
internal object WebMusicPreview {
    @Volatile var play = false
}
