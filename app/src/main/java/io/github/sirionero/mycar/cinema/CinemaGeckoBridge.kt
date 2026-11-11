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
        return runCatching {
            val clazz = Class.forName(FACTORY, true, context.classLoader)
            val factory = clazz.getDeclaredConstructor().newInstance() as CinemaBrowserFactory
            factory.create(context, url, desktop, adBlock, onVisited, onKeyboard, onFullScreen, onFailure)
        }.onFailure {
            // Never bring down the whole car connection because a split failed to load.
            MyCarLog.w("cinema: optional browser unavailable: " + it.javaClass.simpleName)
            onFailure("Firefox browser could not start. Use Android WebView instead.")
        }.getOrNull()
    }
}
