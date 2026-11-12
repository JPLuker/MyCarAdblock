package io.github.sirionero.mycar.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaBrowserEngineTest {
    @Test fun interruptedNativeStartupDoesNotCrashLoopEvenWithoutAdBlock() {
        // The extension toggle is deliberately irrelevant: Gecko itself crashes.
        assertFalse(CinemaGeckoAttemptPolicy.shouldStart(interrupted = true))
        assertTrue(CinemaGeckoAttemptPolicy.shouldStart(interrupted = false))
    }

    @Test fun crashGuardResetsOnlyForExplicitWebViewToFirefoxChange() {
        assertTrue(CinemaGeckoAttemptPolicy.shouldResetOnEngineSelection(
            CinemaBrowserEngine.ANDROID_WEBVIEW, CinemaBrowserEngine.FIREFOX_GECKOVIEW,
        ))
        assertFalse(CinemaGeckoAttemptPolicy.shouldResetOnEngineSelection(
            CinemaBrowserEngine.FIREFOX_GECKOVIEW, CinemaBrowserEngine.FIREFOX_GECKOVIEW,
        ))
        assertFalse(CinemaGeckoAttemptPolicy.shouldResetOnEngineSelection(
            CinemaBrowserEngine.FIREFOX_GECKOVIEW, CinemaBrowserEngine.ANDROID_WEBVIEW,
        ))
        assertFalse(CinemaGeckoAttemptPolicy.shouldResetOnEngineSelection(
            CinemaBrowserEngine.ANDROID_WEBVIEW, CinemaBrowserEngine.ANDROID_WEBVIEW,
        ))
    }

    @Test fun absentSettingKeepsLightweightBrowser() {
        assertEquals(CinemaBrowserEngine.ANDROID_WEBVIEW, CinemaBrowserEngine.fromStored(""))
        assertEquals(CinemaBrowserEngine.ANDROID_WEBVIEW, CinemaBrowserEngine.fromStored("other"))
    }

    @Test fun choiceRoundTrips() {
        CinemaBrowserEngine.entries.forEach {
            assertEquals(it, CinemaBrowserEngine.fromStored(it.storageValue))
        }
    }

    @Test fun missingOptionalEngineNeverPreventsBrowsing() {
        assertEquals(
            CinemaBrowserEngine.ANDROID_WEBVIEW,
            CinemaBrowserPolicy.active(CinemaBrowserEngine.FIREFOX_GECKOVIEW, false),
        )
        assertEquals(
            CinemaBrowserEngine.FIREFOX_GECKOVIEW,
            CinemaBrowserPolicy.active(CinemaBrowserEngine.FIREFOX_GECKOVIEW, true),
        )
        assertEquals(
            CinemaBrowserEngine.ANDROID_WEBVIEW,
            CinemaBrowserPolicy.active(CinemaBrowserEngine.ANDROID_WEBVIEW, true),
        )
    }
}
