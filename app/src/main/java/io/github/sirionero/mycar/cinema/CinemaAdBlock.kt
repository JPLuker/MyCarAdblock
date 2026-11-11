package io.github.sirionero.mycar.cinema

import android.content.Context
import android.util.AtomicFile
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import io.github.sirionero.mycar.MyCarLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Network ad/tracker blocking for Cinema's WebView only. No VPN, DNS change or interference
 * with music WebViews, the general browser, Jellyfin's native player, or IPTV.
 *
 * Start with a few known ad hosts immediately, then use a cached, automatically refreshed hosts
 * list. The list is only fetched in Cinema, off the UI thread. Network failures retain the last
 * good rules. First-party video ads (notably YouTube/Twitch) may be indistinguishable from video
 * delivery at this layer and are deliberately not blocked by broad URL heuristics.
 */
internal object CinemaAdBlock {
    private const val HOSTS_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val CACHE_NAME = "cinema_adblock_hosts.txt"
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MIN_RULES = 5_000
    private const val UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

    private val bundled = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adnxs.com",
        "adsrvr.org", "pubmatic.com", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "criteo.com", "moatads.com",
    )
    private val updateLock = Mutex()
    private var cacheLoaded = false
    private var cacheUsable = false

    /** Published as a whole, never modified in place while WebView's IO threads read it. */
    @Volatile private var blockedDomains: Set<String> = bundled

    suspend fun warmUp(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        updateLock.withLock {
            val cache = AtomicFile(File(app.filesDir, CACHE_NAME))
            if (!cacheLoaded) {
                cacheLoaded = true
                runCatching {
                    if (cache.baseFile.length() > MAX_BYTES) throw IOException("Oversized cache")
                    cache.openRead().bufferedReader(Charsets.UTF_8).use { AdBlockRules.parseHosts(it.readText()) }
                }.getOrNull()?.takeIf { it.size >= MIN_RULES }?.let {
                    blockedDomains = bundled + it
                    cacheUsable = true
                }
            }
            if (!Cinema.adBlockEnabled.value) return@withLock
            val age = System.currentTimeMillis() - cache.baseFile.lastModified()
            if (cacheUsable && cache.baseFile.exists() && age in 0 until UPDATE_INTERVAL_MS) return@withLock

            val text = runCatching { downloadHosts() }.getOrElse {
                MyCarLog.w("cinema: ad-block rules refresh unavailable (" + it.javaClass.simpleName + ")")
                return@withLock
            }
            val rules = AdBlockRules.parseHosts(text)
            // Never replace a working cache with a response that is empty, truncated or not a
            // hosts list (for instance, a CDN's error page).
            if (rules.size < MIN_RULES) {
                MyCarLog.w("cinema: ad-block rules refresh contained too few valid entries")
                return@withLock
            }
            blockedDomains = bundled + rules
            runCatching {
                val out = cache.startWrite()
                try {
                    out.write(text.toByteArray(Charsets.UTF_8))
                    cache.finishWrite(out)
                    cacheUsable = true
                } catch (error: Exception) {
                    cache.failWrite(out)
                    throw error
                }
            }.onFailure {
                MyCarLog.w("cinema: ad-block rules could not be cached (" + it.javaClass.simpleName + ")")
            }
        }
    }

    /**
     * Android calls this on WebView's request threads, not the Compose thread. The switch is
     * consulted per request, so turning it off takes effect without recreating the video.
     * Never intercept a main-frame load: site navigation, sign-in and playback remain possible.
     */
    fun intercept(request: WebResourceRequest?): WebResourceResponse? {
        if (request == null || request.isForMainFrame || !Cinema.adBlockEnabled.value) return null
        val url = request.url
        if (url.scheme != "http" && url.scheme != "https") return null
        val host = url.host ?: return null
        if (!AdBlockRules.blocks(host, blockedDomains)) return null
        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    private fun downloadHosts(): String {
        val connection = URL(HOSTS_URL).openConnection() as HttpsURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "text/plain")
        try {
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                throw IOException("Hosts endpoint returned " + connection.responseCode)
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (bytes.size() + count > MAX_BYTES) throw IOException("Hosts response too large")
                    bytes.write(buffer, 0, count)
                }
            }
            return bytes.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }
}
