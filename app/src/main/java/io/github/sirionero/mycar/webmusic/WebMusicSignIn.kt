package io.github.sirionero.mycar.webmusic

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.AppLanguage
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.userAgentFor
import io.github.sirionero.mycar.ui.MyCarTheme

/**
 * A music service's sign-in on the phone: its own page, in myCar's web engine - the one the car's
 * pages use, cookies and all - so signed in here, the service is signed in in the car too. Typed with
 * the phone's own keyboard and its password manager, rather than the car's keys. The page is as the
 * car's (a computer's), to zoom with two fingers; Back steps back in it first.
 */
class WebMusicSignInActivity : ComponentActivity() {
    private var web: WebView? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = intent.getStringExtra(EXTRA_SERVICE)?.let(WebMusic::byId)
        if (service == null) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = web
                if (w != null && w.canGoBack()) w.goBack() else finish()
            }
        })
        setContent {
            MyCarTheme {
                SignInScreen(service, onWeb = { web = it }, onClose = ::finish)
            }
        }
        MyCarLog.i("music: signing in to ${service.id} on the phone")
    }

    // What the page kept (the sign-in) written out at once: the car's page may open any moment.
    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_SERVICE = "service"

        fun open(context: Context, service: WebMusicService) {
            context.startActivity(Intent(context, WebMusicSignInActivity::class.java).putExtra(EXTRA_SERVICE, service.id))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignInScreen(service: WebMusicService, onWeb: (WebView) -> Unit, onClose: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_sign_in_title, service.name)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            factory = { context -> signInWebView(context, service).also(onWeb) },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun signInWebView(context: Context, service: WebMusicService): WebView = WebView(context).apply {
    // As tall as its place, said outright. In an AndroidView a WebView gets WRAP_CONTENT, and then
    // WebView lays the page out 0 px tall (setForceZeroLayoutHeight): a page that fills the window
    // from its edges - Spotify's sign-in - came out black, nothing on it to see or touch.
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
    // As the car's page asks, so the service hands out the same sign-in.
    settings.userAgentString = userAgentFor(this, desktop = true)
    CookieManager.getInstance().setAcceptCookie(true)
    webViewClient = object : WebViewClient() {
        // Pages only: a link into an app (the service's own) stays here.
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.url.scheme?.lowercase() !in setOf("http", "https", "about", "data", "blob", "javascript")

        override fun onPageFinished(view: WebView, url: String?) {
            CookieManager.getInstance().flush()
        }
    }
    loadUrl(service.signIn ?: service.url)
}

/** A service signed out of myCar's web engine, on the phone and in the car alike. */
internal object WebMusicAccounts {

    /** Every cookie of [service]'s sites, and what its pages kept in their storage. On the main thread. */
    fun signOut(context: Context, service: WebMusicService) {
        val cookies = CookieManager.getInstance()
        var gone = 0
        for (domain in service.domains) {
            val hosts = listOf(domain, "www.$domain", "accounts.$domain", "login.$domain") +
                service.origins.mapNotNull { Uri.parse(it).host }.filter { it.endsWith(domain) }
            for (host in hosts.distinct()) {
                val url = "https://$host"
                val names = cookies.getCookie(url).orEmpty().split(';').mapNotNull { it.substringBefore('=').trim().ifEmpty { null } }
                for (name in names.distinct()) {
                    // Set only for this host, or for the whole site: either goes.
                    cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure")
                    cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure; Domain=$domain")
                    gone++
                }
            }
        }
        service.origins.forEach { WebStorage.getInstance().deleteOrigin(it) }
        cookies.flush()
        clearStorage(context.applicationContext, service.origins)
        MyCarLog.i("music: ${service.id} signed out ($gone cookies)")
        WebMusicPlayer.signedOut(service.id)
    }

    /**
     * What each address kept in its pages' storage, where some services keep the sign-in: a page of
     * nothing loaded as that address, which empties it, one address after the other.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun clearStorage(context: Context, origins: List<String>) {
        if (origins.isEmpty()) return
        val web = WebView(context)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        var next = 0
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript("try { localStorage.clear(); sessionStorage.clear(); } catch (e) {}") {
                    if (++next < origins.size) view.loadDataWithBaseURL("${origins[next]}/", BLANK, "text/html", "utf-8", null)
                    else view.destroy()
                }
            }
        }
        web.loadDataWithBaseURL("${origins[0]}/", BLANK, "text/html", "utf-8", null)
    }

    private const val BLANK = "<html><body></body></html>"
}
