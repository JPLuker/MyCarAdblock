package io.github.sirionero.mycar.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarCarTheme

/**
 * Debug only, for CarPreviewActivity's `--es jolly <kind>`: the Jolly key set to a made-up command of
 * that kind - call, ai, home, unlock, navigate, say, app - or to nothing (none, or no extra), so its
 * first tap opens the chooser. `--es jollySay <phrase>` (with `--es jollyLang <code>`, English by
 * default) is the command "say" runs; `--ez jollyKey false` takes the key off the dock. Set every run.
 */
internal object JollyPreview {
    fun set(kind: String?, phrase: String?, lang: String?, shown: Boolean) {
        JollySetting.setShown(shown)
        JollySetting.set(
            when (kind) {
                "call" -> JollyAction.Call("Anna", "+39 02 1234 5678", "Mobile")
                "ai" -> JollyAction.Assistant(PhoneAssistants.PHONE)
                "home" -> JollyAction.Home("script.jolly_test", HomeAct.RUN, "Jolly test")
                "unlock" -> JollyAction.Home("lock.front_door", HomeAct.UNLOCK, "Front door")
                // Not the Duomo: the emulator's position is there (car-preview.sh).
                "navigate" -> JollyAction.Navigate("Castello Sforzesco, Milano", "Castello")
                "say" -> JollyAction.Say(phrase ?: "what time is it", lang ?: "en")
                "app" -> JollyAction.OpenApp(BUILT_IN_PREFIX + BuiltInApps.Settings.id, "Settings")
                else -> null
            },
        )
    }
}

/**
 * Debug only, for CarPreviewActivity's `--ez jollySettings true`: the car settings' word on the Jolly
 * key on its own, as the settings screen scrolls it out of a screenshot.
 */
@Composable
internal fun JollySettingsPreview() {
    val navigator = remember { CarNavigator(CarSettings.HOME) }
    CompositionLocalProvider(LocalCarNavigator provides navigator) {
        MyCarCarTheme(dark = true) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
                JollyCarSettings()
            }
        }
    }
}
