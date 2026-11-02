package io.github.sirionero.mycar.ui

import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.AssistantLanguage
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages

/**
 * The language the car's assistant listens and answers in: the phone's, once a model that knows
 * it is on the phone. Nothing to say for a phone in English, or in a language it doesn't speak.
 */
@Composable
internal fun VoiceLanguageNote() {
    val context = LocalContext.current
    val phone = VoiceLanguages.phone()
    if (phone == VoiceLanguage.EN) return
    // Named in the language of myCar's own words here, not always in English.
    val name = phone.locale.getDisplayLanguage(context.resources.configuration.locales[0])
    val needed = SpeechModels.recommended(phone).last()
    // Read for its changes: a download that finishes is the moment the language changes.
    val state by SpeechModels.state(needed).collectAsState()
    val final by SpeechModels.final.collectAsState()
    val model = remember(state, final) { SpeechModels.finalFor(context, phone) }
    // The assistant listens only with the live model on the phone too (SpeechEngine), or with the
    // phone's own recognizer: the language's model alone said "ready" while the car asked for a model.
    val live by SpeechModels.selected.collectAsState()
    val liveState by SpeechModels.state(live).collectAsState()
    val systemSpeech by CarSettings.systemSpeech.collectAsState()
    // The phone's own recognizer chosen, and there: it listens in the language without any model.
    val recognizer = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val phoneListens = systemSpeech && recognizer
    val liveReady = phoneListens || liveState == SpeechModels.State.Ready
    val example = AssistantLanguage.help(phone)?.firstOrNull()?.first
    SettingsSection(title = stringResource(R.string.voice_model_language_title, name)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                when {
                    phoneListens -> stringResource(R.string.voice_model_language_phone, name)
                    !liveReady -> stringResource(R.string.setup_guide_voice_body, live.downloadMb + (if (model == null) needed.downloadMb else 0))
                    model == null -> stringResource(R.string.voice_model_language_missing, name, stringResource(needed.title), needed.downloadMb)
                    example != null -> stringResource(R.string.voice_model_language_ready_try, name, stringResource(model.title), example)
                    else -> stringResource(R.string.voice_model_language_ready, name, stringResource(model.title))
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.voice_model_language_tts, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val downloading = listOf(state, liveState).filterIsInstance<SpeechModels.State.Downloading>().firstOrNull()
            when {
                downloading != null -> LinearProgressIndicator(progress = { downloading.percent / 100f }, modifier = Modifier.fillMaxWidth())
                phoneListens -> Unit
                model == null || !liveReady -> Button(onClick = {
                    // download() leaves alone a model already there or on its way.
                    if (!liveReady) SpeechModels.download(context, live)
                    if (model == null) {
                        SpeechModels.download(context, needed)
                        SpeechModels.selectFinal(context, needed)
                    }
                }) {
                    Text(
                        if (!liveReady) stringResource(R.string.setup_guide_voice_download)
                        else stringResource(R.string.voice_model_language_download, stringResource(needed.title)),
                    )
                }
            }
        }
    }
}
