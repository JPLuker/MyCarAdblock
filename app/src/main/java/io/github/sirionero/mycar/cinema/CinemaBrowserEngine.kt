package io.github.sirionero.mycar.cinema

/** The two Cinema website engines; unrelated to music WebViews and native media playback. */
enum class CinemaBrowserEngine(val storageValue: String) {
    ANDROID_WEBVIEW("webview"),
    FIREFOX_GECKOVIEW("gecko");

    companion object {
        fun fromStored(value: String): CinemaBrowserEngine =
            entries.firstOrNull { it.storageValue == value } ?: ANDROID_WEBVIEW
    }
}

/** Unavailable optional code must never prevent the built-in browser from opening. */
internal object CinemaBrowserPolicy {
    fun active(preferred: CinemaBrowserEngine, geckoReady: Boolean): CinemaBrowserEngine =
        if (preferred == CinemaBrowserEngine.FIREFOX_GECKOVIEW && geckoReady) {
            CinemaBrowserEngine.FIREFOX_GECKOVIEW
        } else {
            CinemaBrowserEngine.ANDROID_WEBVIEW
        }
}

/** Gecko native startup can crash even when extensions are bypassed. */
internal object CinemaGeckoAttemptPolicy {
    fun shouldStart(interrupted: Boolean): Boolean = !interrupted

    // Explicit re-selection from the safe engine is a user-directed retry; merely
    // reapplying the stored Gecko preference may never unlock a crash loop.
    fun shouldResetOnEngineSelection(
        previous: CinemaBrowserEngine,
        next: CinemaBrowserEngine,
    ): Boolean = previous == CinemaBrowserEngine.ANDROID_WEBVIEW &&
        next == CinemaBrowserEngine.FIREFOX_GECKOVIEW
}
