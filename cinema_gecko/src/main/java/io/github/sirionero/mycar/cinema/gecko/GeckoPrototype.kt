package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Initial runtime proof-of-concept, physically located in the optional split.
 *
 * Do not expose this from Cinema until the split installer, in-car keyboard,
 * fullscreen behavior and real uBlock Origin permission prompts have been tested.
 * The base application contains no compile-time reference to GeckoView.
 */
class GeckoPrototype(private val context: Context) {

    // GeckoRuntime may only be created once per process; a new Cinema tab gets its own session.
    private val runtime: GeckoRuntime = RuntimeHolder.get(context)

    fun open(url: String): GeckoView {
        val session = GeckoSession()
        session.setContentDelegate(object : GeckoSession.ContentDelegate {})
        session.open(runtime)
        return GeckoView(context).apply {
            setSession(session)
            session.loadUri(url)
        }
    }

    fun close(view: GeckoView) {
        view.releaseSession()?.close()
    }
}

/** Process-wide runtime, initialized only on the UI thread when Gecko is requested. */
private object RuntimeHolder {
    private var runtime: GeckoRuntime? = null

    @Synchronized
    fun get(context: Context): GeckoRuntime =
        runtime ?: GeckoRuntime.create(context.applicationContext).also { runtime = it }
}
