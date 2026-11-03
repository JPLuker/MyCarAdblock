package io.github.sirionero.mycar.speech

import android.content.Context
import java.util.Locale

object VoiceLanguages {

    /** The debug build's car preview: the language, whatever the phone and its models (none runs there). */
    @Volatile var override: VoiceLanguage? = null

    /** The phone's language, for the assistant, whether or not a model on the phone hears it yet. */
    fun phone(): VoiceLanguage = VoiceLanguage.of(Locale.getDefault())

    /**
     * The language the assistant listens and answers in: the phone's, once a model on the phone
     * hears it (or the phone's own recognizer listens instead, [systemRecognizer]). English until
     * then, as it always was: a phone in Italian without the model for it keeps English commands.
     */
    fun effective(context: Context, systemRecognizer: Boolean): VoiceLanguage {
        override?.let { return it }
        val phone = phone()
        if (phone == VoiceLanguage.EN || systemRecognizer) return phone
        return if (SpeechModels.finalFor(context, phone) != null) phone else VoiceLanguage.EN
    }

    /**
     * The voice to answer in: the phone's own kind of [language] (Brazilian or European Portuguese).
     * With pirate speak myCar's language has the private-use region XP, no country: the phone's country
     * instead, or the recognizer and the voice got "pt-XP" and a Portuguese phone lost its voice.
     */
    fun speakingLocale(language: VoiceLanguage): Locale {
        val default = Locale.getDefault().takeIf { it.language == language.code } ?: return language.locale
        if (default.country != io.github.sirionero.mycar.AppLanguage.PIRATE) return default
        return Locale.Builder().setLanguage(default.language).setRegion(io.github.sirionero.mycar.AppLanguage.country()).build()
    }
}
