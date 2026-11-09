package io.github.sirionero.mycar.speech

import android.content.Context
import io.github.sirionero.mycar.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The phone's voice that didn't speak in the car: the car's assistant gave its text-to-speech an
 * answer or a message, and the engine never started saying it (CarAssistant). Kept for the phone's
 * settings (ui/PhoneVoiceRow), which check by themselves whether the language has a voice, until a
 * reply is heard again. Safe from any thread.
 */
object PhoneVoice {

    /** Which engine (its package) didn't speak [language] (a locale's tag), and whether it said then it had no voice for it. */
    data class Silence(val engine: String, val language: String, val noVoice: Boolean)

    private val _silence = MutableStateFlow<Silence?>(null)
    val silence: StateFlow<Silence?> = _silence.asStateFlow()

    @Volatile private var loaded = false

    /** Reads what the last drives left, once. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val parts = AppSettings.getString(context, KEY, "").split('|')
        if (parts.size == 3 && parts[0].isNotEmpty()) _silence.value = Silence(parts[0], parts[1], parts[2] == "1")
    }

    /** The engine [engine] didn't start speaking [language]; [noVoice]: it had said it had no voice for it. */
    fun silent(context: Context, engine: String, language: String, noVoice: Boolean) {
        loaded = true
        val silence = Silence(engine, language, noVoice)
        if (_silence.value == silence) return
        _silence.value = silence
        AppSettings.putString(context, KEY, "$engine|$language|${if (noVoice) "1" else "0"}")
    }

    /** The phone's voice spoke: nothing to say about it any more. */
    fun spoke(context: Context) {
        load(context)
        if (_silence.value == null) return
        _silence.value = null
        AppSettings.putString(context, KEY, "")
    }

    private const val KEY = "phone_voice_silent"
}
