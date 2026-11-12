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
    /** Apply the setting to the existing Firefox session without reloading its page. */
    fun setAdBlockEnabled(enabled: Boolean)
    /** Toggle optional SponsorBlock without discarding playback. */
    fun setSponsorBlockEnabled(enabled: Boolean)
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
        sponsorBlock: Boolean,
        onVisited: (String) -> Unit,
        onKeyboard: (String?) -> Unit,
        onFullScreen: (Boolean) -> Unit,
        /** Actual fullscreen media aspect, derived from Gecko's media metadata when known. */
        onFullScreenAspect: (Float?) -> Unit,
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
        sponsorBlock: Boolean,
        onVisited: (String) -> Unit,
        onKeyboard: (String?) -> Unit,
        onFullScreen: (Boolean) -> Unit,
        /** Actual fullscreen media aspect, derived from Gecko's media metadata when known. */
        onFullScreenAspect: (Float?) -> Unit,
        onFailure: (String) -> Unit,
    ): CinemaBrowserSession? {
        if (!installed(context)) return null
        // A SIGSEGV kills the process even with ad blocking OFF. Guard the *entire* Gecko
        // startup, not just WebExtensionController. Never retry automatically on next drive.
        if (!CinemaGeckoAttemptPolicy.shouldStart(CinemaGeckoAttemptGuard.interrupted(context))) {
            onFailure("Firefox crashed while starting last time. Android WebView still works. To retry Firefox, select Android WebView in phone settings, then select Firefox again.")
            return null
        }
        if (!CinemaGeckoAttemptGuard.begin(context)) {
            onFailure("Firefox could not save its startup checkpoint. Use Android WebView.")
            return null
        }
        return runCatching {
            val clazz = Class.forName(FACTORY, true, context.classLoader)
            val factory = clazz.getDeclaredConstructor().newInstance() as CinemaBrowserFactory
            factory.create(
                context, url, desktop, adBlock, sponsorBlock,
                onVisited, onKeyboard, onFullScreen, onFullScreenAspect, onFailure,
            )
        }.onFailure {
            // A normal Java/Kotlin failure did not kill the process; clear the checkpoint.
            // Only interrupted native/process exits must keep it for crash-loop protection.
            CinemaGeckoAttemptGuard.finish(context)
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
 * Persist a checkpoint before GeckoRuntime is created. A native crash on the Gecko thread
 * cannot be caught by Kotlin and was reproduced with Cinema ad blocking ON and OFF.
 * Mark startup complete only after Gecko paints content, not when an add-on is ready.
 *
 * Keep the old key from initial experiments so an interrupted startup before this update
 * is not silently retried. No browsing data is stored. Synchronous commit is deliberate.
 */
object CinemaGeckoAttemptGuard {
    private const val PREFS = "cinema_gecko_startup"
    private const val PENDING = "native_startup_pending"
    private const val LEGACY_PENDING = "signed_extension_pending"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun interrupted(context: Context): Boolean = prefs(context).run {
        getBoolean(PENDING, false) || getBoolean(LEGACY_PENDING, false)
    }

    fun begin(context: Context): Boolean {
        if (interrupted(context)) return false
        return prefs(context).edit().putBoolean(PENDING, true).commit()
    }

    fun finish(context: Context) {
        prefs(context).edit().remove(PENDING).remove(LEGACY_PENDING).commit()
    }
}
