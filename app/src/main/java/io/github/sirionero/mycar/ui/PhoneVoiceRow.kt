package io.github.sirionero.mycar.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.speech.PhoneVoice
import io.github.sirionero.mycar.speech.VoiceLanguages

/** The phone's text-to-speech as this screen finds it: the engine (its package and name), and whether it has a voice for the language. */
private class VoiceCheck(val engine: String?, val label: String?, val available: Boolean)

/**
 * A row when the phone's text-to-speech can't say the assistant's answers in its language: it has no
 * voice for it (asked here, each time the screen comes back: the driver may have just added one), or
 * in the car it didn't start speaking (PhoneVoice). In the car the answers are then only shown. With
 * the engine's name and Android's text-to-speech settings, where a voice or another engine is chosen.
 * Nothing when all is well.
 */
@Composable
internal fun PhoneVoiceSection() {
    val context = LocalContext.current
    remember { PhoneVoice.load(context) }
    val silence by PhoneVoice.silence.collectAsState()
    val systemSpeech by CarSettings.systemSpeech.collectAsState()
    // Asked again each time the screen comes back: from Android's settings, a voice may be there now.
    val round = rememberResumeCount()
    // The voice the car's assistant answers with (CarAssistant.syncLanguage).
    val locale = remember(round, systemSpeech) {
        VoiceLanguages.speakingLocale(VoiceLanguages.effective(context, systemSpeech && SpeechRecognizer.isRecognitionAvailable(context)))
    }
    var check by remember { mutableStateOf<VoiceCheck?>(null) }
    DisposableEffect(round, locale) {
        // The first round comes with the screen's first resume.
        if (round == 0) return@DisposableEffect onDispose {}
        val main = Handler(Looper.getMainLooper())
        var engine: TextToSpeech? = null
        var over = false
        // The answer comes on the main thread, or at once from inside the constructor when there's no engine.
        engine = TextToSpeech(context.applicationContext) { status ->
            main.post {
                val tts = engine
                if (over || tts == null) return@post
                check = if (status != TextToSpeech.SUCCESS) {
                    VoiceCheck(null, null, available = false)
                } else {
                    val pkg = runCatching { tts.defaultEngine }.getOrNull()
                    val label = runCatching { tts.engines }.getOrNull()?.firstOrNull { it.name == pkg }?.label
                    val result = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                    VoiceCheck(pkg, label, result >= TextToSpeech.LANG_AVAILABLE)
                }
                over = true
                runCatching { tts.shutdown() }
            }
        }
        onDispose {
            if (!over) {
                over = true
                runCatching { engine?.shutdown() }
            }
        }
    }
    val found = check ?: return
    val s = silence
    // Silent in the car with this engine and language - unless it had no voice then and has one now: added since.
    val silentHere = s != null && s.engine == found.engine && s.language == locale.toLanguageTag() && !(s.noVoice && found.available)
    if (found.available && !silentHere) return

    val language = locale.getDisplayLanguage(context.resources.configuration.locales[0]).ifEmpty { locale.toLanguageTag() }
    val engineName = found.label ?: found.engine
    SettingsSection {
        SettingsRow(
            stringResource(R.string.phone_voice_missing_title, language),
            subtitle = when {
                engineName == null -> stringResource(R.string.phone_voice_no_engine_detail, language)
                !found.available -> stringResource(R.string.phone_voice_missing_detail, engineName, language)
                else -> stringResource(R.string.phone_voice_silent_detail, engineName, language)
            },
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.error,
        )
        Button(
            onClick = { openSpeechSettings(context) },
            modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(stringResource(R.string.phone_voice_open_settings))
        }
    }
}

/** Android's text-to-speech settings, where a voice or another engine is chosen; its Settings if a phone has no such page. */
private fun openSpeechSettings(context: Context) {
    val opened = runCatching { context.startActivity(Intent(TTS_SETTINGS)) }.isSuccess ||
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }.isSuccess
    if (!opened) MyCarLog.w("voice: neither the text-to-speech settings nor Android's settings opened")
}

/** The page of Android's Settings for text-to-speech (no constant for it in the SDK). */
private const val TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
