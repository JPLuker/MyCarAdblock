package io.github.sirionero.mycar.cinema

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import io.github.sirionero.mycar.MyCarLog

/**
 * Minimal interface owned by the base APK. Implementations may live in an on-demand
 * feature, so nothing in this file has a direct dependency on org.mozilla.geckoview.
 *
 * Use only for Cinema sites, not the other WebViews or local video/IPTV player.
 */
interface CinemaBrowserSession {
    val view: View
    val canGoBack: Boolean

    fun goBack()
    fun reload()
    fun setText(text: String)
    fun submitText()
    fun pause()
    fun setVisible(visible: Boolean)
    fun exitFullScreen()
    fun close()
}

/** Called on the UI thread; implementations must dispatch callbacks on the UI thread. */
interface CinemaBrowserFactory {
    fun create(
        context: Context,
        url: String,
        desktop: Boolean,
        adBlock: Boolean,
        onVisited: (String) -> Unit,
        onKeyboard: (String?) -> Unit,
        onFullScreen: (Boolean) -> Unit,
        onFailure: (String) -> Unit,
    ): CinemaBrowserSession
}

/** No code from the optional module is referenced until the package manager confirms the split. */
object CinemaGeckoBridge {
    const val SPLIT_NAME = "cinema_gecko"
    private const val FACTORY = "io.github.sirionero.mycar.cinema.gecko.GeckoBrowserFactory"

    fun installed(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.splitNames?.contains(SPLIT_NAME) == true
    }.getOrDefault(false)

    fun open(
        context: Context,
        url: String,
        desktop: Boolean,
        adBlock: Boolean,
        onVisited: (String) -> Unit,
        onKeyboard: (String?) -> Unit,
        onFullScreen: (Boolean) -> Unit,
        onFailure: (String) -> Unit,
    ): CinemaBrowserSession? {
        if (!installed(context)) return null
        // A native Gecko crash kills the whole Android process, bypassing Kotlin exception
        // handlers. Do not repeat an interrupted signed-extension startup on the next drive.
        if (!CinemaGeckoAttemptPolicy.shouldStart(adBlock, CinemaGeckoAttemptGuard.interrupted(context))) {
            onFailure("uBlock Origin did not finish starting last time. Turn off Cinema ad blocking to test Firefox without it, or use Android WebView.")
            return null
        }
        if (adBlock && !CinemaGeckoAttemptGuard.begin(context)) {
            onFailure("Firefox could not save the add-on startup checkpoint. Use Android WebView.")
            return null
        }
        return runCatching {
            val clazz = Class.forName(FACTORY, true, context.classLoader)
            val factory = clazz.getDeclaredConstructor().newInstance() as CinemaBrowserFactory
            factory.create(context, url, desktop, adBlock, onVisited, onKeyboard, onFullScreen, onFailure)
        }.onFailure {
            // A normal Java/Kotlin failure did not kill the process; clear the checkpoint.
            // Only interrupted native/process exits must keep it for crash-loop protection.
            if (adBlock) CinemaGeckoAttemptGuard.finish(context)
            // Never bring down the whole car connection because a split failed to load.
            // Preserve the full Java cause chain in the existing on-device crash log.
            // A class-loader, missing split manifest component or GeckoRuntime startup error
            // cannot be diagnosed from the exception's short type name alone.
            MyCarLog.e("cinema: GeckoView initialization failed", it, tag = "CINEMA")
            onFailure("Firefox could not start. Choose Android WebView; see the myCar log for details.")
        }.getOrNull()
    }
}

/**
 * A durable checkpoint around *signed-extension startup*, not around page navigation.
 * commit() is intentional: an async preference write might be lost in the native SIGSEGV
 * that prompted this guard. The only stored value is a Boolean, never browsing data.
 *
 * Turning off Cinema ad blocking explicitly clears the checkpoint and allows unblocked
 * Firefox to be tested. Turning it on again is an explicit retry after that choice.
 */
object CinemaGeckoAttemptGuard {
    private const val PREFS = "cinema_gecko_startup"
    private const val PENDING = "signed_extension_pending"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun interrupted(context: Context): Boolean = prefs(context).getBoolean(PENDING, false)

    fun begin(context: Context): Boolean {
        val preferences = prefs(context)
        if (preferences.getBoolean(PENDING, false)) return false
        return preferences.edit().putBoolean(PENDING, true).commit()
    }

    fun finish(context: Context) {
        prefs(context).edit().remove(PENDING).commit()
    }
}
