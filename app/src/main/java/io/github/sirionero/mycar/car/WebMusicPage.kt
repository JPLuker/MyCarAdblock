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
import androidx.car.app.CarToast
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.cinema.SiteIcons
import io.github.sirionero.mycar.webmusic.SongVideo
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        WebMusicPlayer.pages = this
        // Music that was playing on the screen before this one, in the same drive: on again. Always
        // emptied: at the end of a drive the old screen still fills it.
        val again = WebMusicPlayer.resumeAfterRebuild
        WebMusicPlayer.resumeAfterRebuild = emptyList()
        if (CarServices.mediaDecided.value) again.forEach { service ->
            MyCarLog.i("music: ${service.id}: playing again on the new car screen")
            open(service)?.act("play")
        }
        // A service turned off on the phone leaves the car at once: its page goes, its music with it.
        scope.launch {
            WebMusic.on.collect { on ->
                pages.keys.filter { it !in on }.forEach { id -> pages.remove(id)?.destroy() }
                val music = CarServices.mediaApp.value?.component?.let(WebMusic::serviceFor)
                if (music != null && music.id !in on) CarServices.closeMedia()
            }
        }
    }

    fun page(service: WebMusicService): WebMusicPage = pages.getOrPut(service.id) { WebMusicPage(context, service, made) }

    /** A page for the car's buttons, out of sight if need be: none for a service that's off. */
    override fun open(service: WebMusicService): WebMusicPlayer.Page? =
        if (service.id in WebMusic.on.value) page(service).also { it.make() } else null

    fun destroy() {
        // Before the pages go (they report an empty state as they do).
        WebMusicPlayer.resumeAfterRebuild = pages.values.map { it.service }.filter { WebMusicPlayer.state(it.id).value.playing }
        scope.cancel()
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
    /** The room for myCar's Back last given to the page, in its CSS pixels (see [fit]). */
    private var backRoom = 0
    private var widthScript: ScriptHandler? = null
    private var probed = false
    private var pressedPlay = false

    /** The page has spoken since it loaded: its own buttons and handlers are there for the car's. */
    private var heard = false

    /** A button that came before the page could take it: sent once it can. */
    private var pending: Pair<String, Double?>? = null
    private var lastCrashResume = 0L
    private var playsElsewhere = false
    /** Since when the page has said it plays on another device (0: it doesn't say so). */
    private var elsewhereSince = 0L
    /** Said only once the page has kept saying it for [ELSEWHERE_SURE_MS]: a song that stops or changes here can look like it. */
    private val sayElsewhere = Runnable {
        if (elsewhereSince == 0L || playsElsewhere || released) return@Runnable
        if (WebMusicPlayer.state(service.id).value.playing) return@Runnable
        playsElsewhere = true
        MyCarLog.i("music: ${service.id} plays on another device (Spotify Connect), not in the car's page")
        if (isTheCarsMusic()) CarToasts.show(this@WebMusicPage, Words.get(R.string.car_web_music_elsewhere, service.name), CarToast.LENGTH_LONG)
    }
    /** The car's presses so far: a newer one ends the tries of one that waited for the page. */
    private var acts = 0
    /**
     * A Play still being tried ([send], patient): its press ([acts] then, [NO_TRIES] with none) and
     * when its tries last moved on. A Play pressed again meanwhile leaves them going (see [act]).
     */
    private var playTries = NO_TRIES
    private var playTriedAt = 0L
    /** When the page last started loading: a press that waited for it is tried until [BOOT_MS] after. */
    private var startedAt = 0L
    private var wasPlaying = false

    /** Which video cover the song in play has: not one the page still shows from the song before. */
    private val songVideo = SongVideo()

    /** A voice search under way, until its first Play is pressed or isn't found. */
    private var searching = false
    private var searchTries = 0
    private var searchedAt = 0L

    /** Where the page starts when it's made: a search asked before it was, or the service's start. */
    private var startAt: String? = null

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

    override fun act(action: String, seconds: Double?) {
        // A voice search plays what it finds: the Play sent with it isn't for the song before. A
        // search whose page never comes doesn't hold the car's Play for long.
        if (searching && action == "play" && SystemClock.uptimeMillis() - searchedAt < SEARCH_HOLD_MS) return
        // The same Play again while the one that waited for the page is still tried - the service's
        // player not there yet, or not playing yet - from the car or its Bluetooth, as the page loads.
        // It used to end those tries for one of its own, which the page took and then didn't play:
        // the drive started without music until the driver pressed Play. They go on instead. Not while
        // a new page loads (Spotify loads twice): the tries' last call may have gone with the old one,
        // never to answer, so this Play waits for the new page, patient, as it always did.
        if (action == "play" && heard && !unreachable.value && playStillTried()) {
            MyCarLog.i("music: ${service.id} play: the Play before is still being tried")
            return
        }
        // Made without network (a garage, off screen for the wheel's Play): nothing reloaded it, and
        // the buttons went to the error page. Loaded again now, and the button sent once it's there.
        acts++
        if (unreachable.value && web != null) {
            pending = action to seconds
            reload()
            return
        }
        send(action, seconds, patient = !heard)
    }

    /** "Play [query]": the service's search, loaded here, then the Play of what it found first. */
    override fun search(query: String) {
        val url = service.searchUrl(query) ?: return
        acts++
        pending = null
        searching = true
        searchedAt = SystemClock.uptimeMillis()
        searchTries = SEARCH_TRIES
        main.removeCallbacks(pressFirst)
        MyCarLog.i("music: ${service.id} searching, as the driver asked")
        val w = web
        if (w == null || !loaded) {
            startAt = url
            make()
        } else {
            w.loadUrl(url)
        }
    }

    private val pressFirst = Runnable { pressFirstPlay() }

    /** What the search found first, played with its own Play: tried again while the results come. */
    private fun pressFirstPlay() {
        val w = web ?: return
        if (!searching) return
        w.evaluateJavascript(WebMusicScripts.firstPlay(service)) { how ->
            if (!searching || web !== w) return@evaluateJavascript
            if (how == "\"nothing\"" && --searchTries > 0) {
                main.postDelayed(pressFirst, SEARCH_RETRY_MS)
            } else {
                searching = false
                MyCarLog.i("music: ${service.id} search: $how")
                if (BuildConfig.DEBUG) w.evaluateJavascript(WebMusicScripts.PLAY_BUTTONS) { MyCarLog.i("music: ${service.id} search page's play buttons: ${it.take(MAX_LOG)}") }
            }
        }
    }

    /** Signed out on the phone: the page starts again, with what it kept in its storage gone too. */
    override fun signedOut() {
        val w = web ?: return
        w.evaluateJavascript(CLEAR_STORAGE_JS) { if (web === w) w.loadUrl(service.url) }
    }

    /**
     * The sign-in on the phone closed, maybe signed in: the page in the car, made before, still
     * showed "Log in" until it was loaded again - and the wheel's Play landed on the sign-in form.
     * Not while it plays: a sign-in that changed nothing mustn't cut the song.
     */
    override fun signedIn() {
        val w = web ?: return
        if (WebMusicPlayer.state(service.id).value.playing) return
        unreachable.value = false
        w.loadUrl(service.url)
    }

    /**
     * A button for the page. Before the page can take it, it waits; just loaded ([patient]), the
     * service's own player may not be there yet, so it's tried again until [BOOT_MS] after the page
     * started - and a Play the page's own player took (its Media Session) but that plays nothing yet,
     * the service still starting, is sent again while this is the car's music. Never a second click
     * on the service's own button: it plays and pauses, and a song still loading would be paused.
     */
    private fun send(action: String, seconds: Double?, patient: Boolean, gen: Int = acts, waits: Int = WAITS) {
        val w = web
        if (w == null || !heard) {
            pending = action to seconds
            make()
            return
        }
        if (!ours(w.url)) return
        // A patient Play's tries, followed so that a Play pressed again meanwhile leaves them going (act).
        val tracked = patient && action == "play"
        if (tracked) {
            playTries = gen
            playTriedAt = SystemClock.uptimeMillis()
        }
        val arg = seconds?.let { ", $it" } ?: ""
        w.evaluateJavascript("window.__mycarMusicAct ? window.__mycarMusicAct('$action'$arg) : 'no script'") { how ->
            // Still this press, on this page, for the car's music: another press, another page or
            // another music app chosen meanwhile ends the tries.
            fun current() = gen == acts && web === w && (action != "play" || isTheCarsMusic())
            val trying = patient && current() && SystemClock.uptimeMillis() - startedAt < BOOT_MS
            if (tracked && trying) playTriedAt = SystemClock.uptimeMillis()
            // "wait": the service's bar has just come, and whether it plays on another device isn't
            // known yet - a click now could pause it there. A press on a page that has spoken waits too,
            // a little.
            val waiting = how == "\"wait\""
            when {
                trying && (how == "\"none\"" || how == "\"no script\"" || waiting) ->
                    main.postDelayed({ if (current()) send(action, seconds, patient, gen) else triesOver(gen) }, RETRY_MS)
                !patient && waiting && waits > 0 && current() ->
                    main.postDelayed({ if (current()) send(action, seconds, patient, gen, waits - 1) }, RETRY_MS)
                trying && action == "play" && how == "\"page\"" -> main.postDelayed({
                    if (gen == acts && web === w && !WebMusicPlayer.state(service.id).value.playing && isTheCarsMusic()) {
                        MyCarLog.i("music: ${service.id} play: not playing yet - again")
                        send(action, seconds, patient, gen)
                    } else {
                        triesOver(gen)
                    }
                }, PLAY_CHECK_MS)
                else -> {
                    triesOver(gen)
                    MyCarLog.i("music: ${service.id} $action ($how)")
                }
            }
        }
    }

    /**
     * A Play is still tried as the page loads ([send], patient), with nothing else pressed since, and
     * its tries moved on lately: never stuck on one whose page went without a word.
     */
    private fun playStillTried(): Boolean =
        playTries == acts && web != null && SystemClock.uptimeMillis() - playTriedAt < TRIES_GAP_MS

    /** The tries of the press [gen] are over: played, given up, or another press came. */
    private fun triesOver(gen: Int) {
        if (playTries == gen) playTries = NO_TRIES
    }

    private fun isTheCarsMusic(): Boolean =
        CarServices.mediaApp.value?.component?.let(WebMusic::serviceFor)?.id == service.id

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
        // The room for myCar's Back in the page's pixels (--mycar-back in the services' CSS).
        val back = webBackRoomCss(density * CarSettings.scale.value, css, widthPx)
        if (css != viewport || back != backRoom) {
            viewport = css
            backRoom = back
            val room = "document.documentElement && document.documentElement.style.setProperty('--mycar-back', '${back}px');"
            // For the pages to come, from their start; for this one, now.
            runCatching {
                widthScript?.remove()
                widthScript = WebViewCompat.addDocumentStartJavaScript(w, "window.__mycarMusicWidth = $css; $room", service.origins.toSet())
            }
            w.evaluateJavascript("window.__mycarMusicViewport && window.__mycarMusicViewport($css); $room", null)
        }
        if (!loaded) {
            loaded = true
            MyCarLog.i("music: opening ${service.name}, laid out ${css}px wide")
            w.loadUrl(startAt ?: service.url)
            startAt = null
        }
    }

    private fun ours(url: String?): Boolean = url != null && service.origins.any { url == it || url.startsWith("$it/") }

    private fun isSearch(url: String?): Boolean = url.orEmpty().contains("search", ignoreCase = true)

    /** An address for the log: a search's without the words the driver said. */
    private fun logged(url: String?): String =
        if (isSearch(url)) url.orEmpty().substringBefore("search") + "search (words left out)" else url.orEmpty()

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
        val said = WebPlayerState(
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
        // The song's video cover (Spotify's Canvas): only from the service's own video addresses, and
        // never in the log but in debug builds (its address says which song it is).
        val video = o.optString("video").takeIf { url -> url.length < MAX_URL && service.videoCovers.any { url.startsWith(it) } }.orEmpty()
        val state = said.copy(videoUrl = songVideo.next(said.key, if (said.hasTrack) video else ""))
        val before = WebMusicPlayer.state(service.id).value
        if (state.key != before.key || state.playing != before.playing || state.videoUrl.isEmpty() != before.videoUrl.isEmpty()) {
            // What the page says, without its words: whether there's a song, and if it plays.
            MyCarLog.i(
                "music: ${service.id} says: " + (if (state.hasTrack) "a song" else "no song") +
                    (if (state.playing) ", playing" else ", not playing") +
                    (if (state.artUrl.isNotEmpty()) ", with its cover" else "") +
                    (if (state.videoUrl.isNotEmpty()) ", with a video cover" + (if (BuildConfig.DEBUG) " (${state.videoUrl})" else "") else "") +
                    (if (state.durationMs > 0) ", ${state.durationMs / 1000} s long" else ""),
            )
        }
        WebMusicPlayer.report(service.id, state)
        if (!heard) {
            heard = true
            sendPending()
        }
        if (state.playing && !wasPlaying) becomeTheMusic()
        wasPlaying = state.playing
        // Playing on another device: said once to the driver whose music it is, and only once the page
        // has said it for ELSEWHERE_SURE_MS (sayElsewhere). Said again only after it played here.
        val remote = o.optBoolean("remote") && !state.playing
        if (remote && elsewhereSince == 0L) {
            elsewhereSince = SystemClock.uptimeMillis()
            main.postDelayed(sayElsewhere, ELSEWHERE_SURE_MS)
        } else if (!remote && elsewhereSince != 0L) {
            if (!playsElsewhere) MyCarLog.i("music: ${service.id} looked like another device for ${SystemClock.uptimeMillis() - elsewhereSince} ms - not said")
            elsewhereSince = 0L
            main.removeCallbacks(sayElsewhere)
        }
        if (state.playing) playsElsewhere = false
    }

    /** A new page (or a page made again): what the one before said about another device goes with it. */
    private fun forgetElsewhere() {
        elsewhereSince = 0L
        playsElsewhere = false
        main.removeCallbacks(sayElsewhere)
    }

    private fun sendPending() {
        val (action, seconds) = pending ?: return
        pending = null
        // Another music chosen while the page loaded (or the music closed): its Play is no one's now.
        // Sent, the page played and took the car's music back from the driver's pick.
        if (action == "play" && !isTheCarsMusic()) {
            MyCarLog.i("music: ${service.id} play dropped - no longer the car's music")
            return
        }
        send(action, seconds, patient = true)
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
            startedAt = SystemClock.uptimeMillis()
            forgetElsewhere()
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (view !== web) return
            view.evaluateJavascript(FOCUS_TRACKING_JS, null)
            view.evaluateJavascript(focusRingJs(ring), null)
            CookieManager.getInstance().flush()
            // The page's title only in debug builds: it can name the song, or the words searched for.
            MyCarLog.i("music: ${service.id} page loaded: ${logged(url)}" + (if (BuildConfig.DEBUG && !isSearch(url)) " (${view.title})" else ""))
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
            // A voice search's page: its results come in a moment after, then their first Play.
            if (searching && ours(url) && isSearch(url)) {
                main.removeCallbacks(pressFirst)
                main.postDelayed(pressFirst, SEARCH_FIRST_MS)
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
            MyCarLog.w("music: ${logged(request.url.toString())} didn't load - ${error.errorCode} ${error.description}")
            if (error.errorCode in NO_NETWORK) unreachable.value = true
        }

        // The web engine's page process is one for the whole app: when it dies, every web view on it
        // must say it handled that, or Android ends myCar - and the drive. This one opens again.
        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            MyCarLog.w("music: ${service.id}: the page closed (crash: ${detail.didCrash()}) - opening it again")
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
            if (view === web && !released) {
                val was = WebMusicPlayer.state(service.id).value.playing
                loaded = false
                viewport = 0
                widthScript = null
                heard = false
                searching = false
                // The old page's tries end with it, answered or not.
                playTries = NO_TRIES
                forgetElsewhere()
                fullScreen.value = null
                field.value = null
                WebMusicPlayer.report(service.id, WebPlayerState())
                web = createWebView()
                // Playing when it closed: the new page plays again at its first message. Once a minute
                // at most, in case the page itself is what ends the engine.
                val at = SystemClock.uptimeMillis()
                if (was && at - lastCrashResume > CRASH_RESUME_GAP_MS) {
                    lastCrashResume = at
                    pending = "play" to null
                }
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
        /** A page that closes again within this, playing, is opened again paused. */
        const val CRASH_RESUME_GAP_MS = 60_000L
        const val RETRY_MS = 1_000L
        /**
         * A press on a page that has spoken, while its bar can't tell yet where it plays (just shown, a new
         * song, or the music here just stopped with the clock running on: up to about 6 s): tried this many
         * times more, a second apart.
         */
        const val WAITS = 8
        /** The page saying it plays on another device this long before the driver is told. */
        const val ELSEWHERE_SURE_MS = 3_000L
        /** How long a press that waited for the page is tried, from the page's start: a slow network too. */
        const val BOOT_MS = 45_000L
        /** After the page's player took a Play: playing by then, or the Play goes again. */
        const val PLAY_CHECK_MS = 4_000L
        /** No Play being tried ([playTries]). */
        const val NO_TRIES = -1
        /**
         * The longest a Play's tries go without a step, [PLAY_CHECK_MS] and a page slow to answer: a
         * Play pressed again that much later is a press of its own.
         */
        const val TRIES_GAP_MS = PLAY_CHECK_MS + 4_000L

        /** Narrower than this, these players fold away their parts: the page is drawn smaller instead. */
        const val MIN_WIDTH = 800
        const val MAX_MESSAGE = 30_000
        const val MAX_LOG = 3_500
        const val MAX_TEXT = 300
        const val MAX_URL = 2_000

        const val CLEAR_STORAGE_JS = "try { localStorage.clear(); sessionStorage.clear(); } catch (e) {}"

        const val PREVIEW_PLAY_MS = 8_000L

        /** A search's results: looked for this long after its page loads, then once a second for a while. */
        const val SEARCH_FIRST_MS = 1_500L
        const val SEARCH_RETRY_MS = 1_000L
        const val SEARCH_TRIES = 12
        const val SEARCH_HOLD_MS = 20_000L

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
