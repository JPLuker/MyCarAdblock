package io.github.sirionero.mycar.car

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
                "navigate" -> JollyAction.Navigate("Piazza del Duomo, Milano", "Duomo")
                "say" -> JollyAction.Say(phrase ?: "what time is it", lang ?: "en")
                "app" -> JollyAction.OpenApp(BUILT_IN_PREFIX + BuiltInApps.Settings.id, "Settings")
                else -> null
            },
        )
    }
}
