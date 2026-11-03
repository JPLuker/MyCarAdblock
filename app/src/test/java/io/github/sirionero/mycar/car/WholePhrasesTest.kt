package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguage.DE
import io.github.sirionero.mycar.speech.VoiceLanguage.EL
import io.github.sirionero.mycar.speech.VoiceLanguage.ES
import io.github.sirionero.mycar.speech.VoiceLanguage.FR
import io.github.sirionero.mycar.speech.VoiceLanguage.IT
import io.github.sirionero.mycar.speech.VoiceLanguage.PT
import io.github.sirionero.mycar.speech.VoiceLanguage.RU
import io.github.sirionero.mycar.speech.VoiceLanguage.ZH
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The time, the weather, the arrival, Home and Settings: said whole, or on to Home Assistant. */
class WholePhrasesTest {

    private val all = listOf(
        "time" to WholePhrases::time,
        "weather" to WholePhrases::weather,
        "arrival" to WholePhrases::arrival,
        "home" to WholePhrases::home,
        "settings" to WholePhrases::settings,
    )

    private fun only(which: String, vararg heard: String) {
        for (h in heard) for ((name, test) in all) {
            if (name == which) assertTrue("$h is $name", test(h)) else assertFalse("$h isn't $name", test(h))
        }
    }

    private fun none(vararg heard: String) {
        for (h in heard) for ((name, test) in all) assertFalse("$h isn't $name", test(h))
    }

    @Test fun theTime() = only(
        "time",
        "what time is it", "what's the time", "what is the time", "whats the time", "what time", "time", "the time",
        "tell me the time", "do you know the time", "do you know what time it is", "tell me what time it is",
        "what's the current time", "current time", "check the time",
    )

    @Test fun theWeather() = only(
        "weather",
        "what's the weather", "weather", "what's the weather like", "how's the weather", "how hot is it", "how cold is it outside",
        "how hot", "how cold is it going to be today", "what's the temperature outside", "is it going to rain",
        "is it going to rain today", "is it gonna rain later",
    )

    @Test fun theArrival() = only(
        "arrival",
        "when do i arrive", "when will we get there", "what time do we arrive", "when do we get to the hotel", "eta", "what's my eta",
        "arrival time", "how much longer", "how long left", "how long is left", "how long until we get there", "how long till we arrive",
        "how long before we get home", "how long until we're there", "how long to go", "how long until arrival", "how long until",
    )

    @Test fun home() = only("home", "go home", "home", "home screen", "go to the home screen", "back to the home screen")

    @Test fun settings() = only(
        "settings",
        "settings", "open settings", "go to settings", "open the settings", "show me the settings", "open my car settings",
        "open mycar settings", "car settings", "settings menu",
    )

    /** For Home Assistant: they only have a word of myCar's in them. */
    @Test fun questionsForHomeAssistant() = none(
        "what time is sunset", "what time does the pharmacy close", "what time is it in tokyo", "what's the time in new york",
        "how long until christmas", "how long to boil an egg", "how long until i can charge the car", "how long is the drive to milan",
        "what are the thermostat settings", "change the thermostat settings", "how hot is the living room", "how cold is the bedroom",
        "is it going to rain in rome tomorrow", "turn on the heating when i go home", "when i go home turn on the lights",
        "put on the timer", "play il timer di 10 minuti", "set a timer for ten minutes",
    )

    private fun native(l: VoiceLanguage, vararg said: Pair<String, String>) {
        for ((s, which) in said) only(which, AssistantLanguage.toEnglish(s, l).lowercase().replace(Regex("[.,!?]"), " ").trim())
    }

    /** What the phrasebooks make of the driver's language still gets there (the phrases of their own tests). */
    @Test fun thePhrasebooksEnglish() {
        native(IT, "Che ore sono?" to "time", "Che tempo fa?" to "weather", "Quando arriviamo?" to "arrival",
            "Torna alla home" to "home", "Apri le impostazioni." to "settings")
        native(ES, "¿Qué hora es?" to "time", "¿Qué tiempo hace?" to "weather", "Cuando llegamos." to "arrival",
            "Abre los ajustes." to "settings")
        native(FR, "Quelle heure est-il?" to "time", "Quel temps fait-il?" to "weather", "Quand est-ce que j'arrive?" to "arrival",
            "Ouvre les paramètres." to "settings")
        native(DE, "Wie spät ist es?" to "time", "Wie ist das Wetter?" to "weather", "Wann komme ich an?" to "arrival",
            "Öffne die Einstellungen." to "settings")
        native(PT, "Que horas são?" to "time", "Como está o tempo?" to "weather", "Quando eu chego." to "arrival",
            "Abra as configurações." to "settings")
        native(EL, "Τι ώρα είναι;" to "time", "Θα βρέξει σήμερα;" to "weather", "Πότε φτάνουμε;" to "arrival",
            "Πήγαινε στην αρχική οθόνη." to "home", "Άνοιξε τις ρυθμίσεις." to "settings")
        native(RU, "Который час?" to "time", "Будет дождь?" to "weather", "Когда приедем?" to "arrival",
            "На главную." to "home", "Открой настройки." to "settings", "Перейди в настройки." to "settings")
        native(ZH, "现在几点了？" to "time", "外面多少度？" to "weather", "还要多久？" to "arrival", "回到主页。" to "home",
            "打开设置。" to "settings", "去设置。" to "settings")
    }
}
