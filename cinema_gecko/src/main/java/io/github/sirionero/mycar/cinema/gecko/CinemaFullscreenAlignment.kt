package io.github.sirionero.mycar.cinema.gecko

import io.github.sirionero.mycar.MyCarLog
import org.mozilla.geckoview.GeckoRuntime

/**
 * Bundled first-party Gecko content script, NOT a sideloaded/unsigned user extension.
 *
 * Fullscreen in a 44/56 Maps + Cinema layout must preserve native GeckoView bounds;
 * resizing the Android surface during fullscreen causes Firefox to exit fullscreen.
 * The script instead repositions just YouTube's video and caption overlay using
 * individual CSS translation in narrow fullscreen viewports. It never reads URLs,
 * sends data, changes playback or interacts with unrelated sites.
 *
 * Gecko's resource://android/assets/ points to the installed APK archive;
 * the optional feature carries this directory alongside Gecko's omnijar.
 */
internal object CinemaFullscreenAlignment {
    private const val ID = "cinema-fullscreen@mycar.internal"
    private const val LOCATION = "resource://android/assets/cinema_fullscreen/"

    /** Failure is non-fatal; keep Firefox usable with its existing fullscreen behavior. */
    fun ensure(runtime: GeckoRuntime, finished: () -> Unit) {
        try {
            runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ extension ->
                if (extension?.id == ID) {
                    MyCarLog.i("gecko: Cinema fullscreen alignment script installed", "CINEMA")
                } else {
                    MyCarLog.w("gecko: Cinema fullscreen alignment did not confirm its ID", "CINEMA")
                }
                finished()
            }, { error ->
                MyCarLog.w(
                    "gecko: Cinema fullscreen alignment unavailable: " +
                        (error?.message ?: "unknown error"),
                    "CINEMA",
                )
                finished()
            })
        } catch (failure: Exception) {
            MyCarLog.w(
                "gecko: Cinema fullscreen alignment initialization failed: " +
                    (failure.message ?: failure.javaClass.simpleName),
                "CINEMA",
            )
            finished()
        }
    }
}
