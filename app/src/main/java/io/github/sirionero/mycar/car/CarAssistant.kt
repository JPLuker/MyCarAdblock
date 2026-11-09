package io.github.sirionero.mycar.car

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.mirror.PhoneMirror
import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.speech.PhoneVoice
import io.github.sirionero.mycar.speech.SpeechEngine
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.VoiceFocus
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A voice assistant: myCar's own [SpeechEngine] (whisper.cpp) to listen, the system's
 * [TextToSpeech] to answer. Listening used to go through the system's [SpeechRecognizer] alone,
 * but many phones have no recognizer at all (GrapheneOS ships only text-to-speech), so the
 * system one is now just the fallback for when the bundled engine can't run.
 *
 * Listening and speaking live here; deciding what a command *means* is [AssistantCommands], kept
 * separate because it needs a [CarNavigator] and a frame to act on, neither of which this object
 * has - see [pendingCommand].
 */
object CarAssistant {

    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    data class State(
        val phase: Phase = Phase.IDLE,
        val heard: String = "",
        val reply: String = "",
        val missingPermission: Boolean = false,
        /** False from the press until the microphone is actually open, so the overlay doesn't claim to listen early. */
        val ready: Boolean = false,
        /** Home Assistant is being asked (AssistantBrain): the overlay says so while it waits. */
        val brain: Boolean = false,
        /**
         * A line over "Listening…": why the assistant listens in English though myCar is in another
         * language, or, once a drive, that the phone is short of memory for the big model (smallModelOnce).
         */
        val hint: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Set once a recognition result comes in; [reply] clears it once handled. */
    private val _pendingCommand = MutableStateFlow<String?>(null)
    val pendingCommand: StateFlow<String?> = _pendingCommand

    private var appContext: Context? = null
    private var recognizer: SpeechRecognizer? = null
    /** The phone's recognizer has the music paused, as myCar's own engine does (VoiceFocus). */
    private var systemFocus = false
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    /** The engine didn't start, or stopped speaking (updated in the background): made anew (SpeechRestart). */
    @Volatile private var ttsFailed = false
    /** When the engine was last made (elapsed clock), and which one it is: an older one's answer counts for nothing. */
    @Volatile private var ttsStartedAt = 0L
    private var engineNumber = 0
    /** The language the voice was last set to, so it's set again only when that changes. */
    private var voiceIn: VoiceLanguage? = null
    /** The engine the voice was set on (its package), the locale's tag, and whether it said it had no voice for it. */
    @Volatile private var voiceEngine: String? = null
    @Volatile private var voiceLocale = ""
    @Volatile private var voiceMissing = false
    /**
     * The language of a command written for the Jolly key ([runPhrase]): what the assistant reads it and
     * answers in, whatever model the phone has to listen. Until the microphone listens again.
     */
    @Volatile private var written: VoiceLanguage? = null

    /**
     * The phone's language for the assistant, once a model hears it (VoiceLanguages.effective):
     * what it listens for, what it answers in, and the voice it answers with. Checked at every
     * press and answer, so a model downloaded mid-drive counts at once.
     */
    internal fun syncLanguage() {
        val context = appContext ?: return
        val language = written ?: VoiceLanguages.effective(context, phoneListens(context))
        AssistantLanguage.language = language
        val engine = tts ?: return
        if (!ttsReady || voiceIn == language) return
        voiceIn = language
        val locale = VoiceLanguages.speakingLocale(language)
        val result = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        // Which engine speaks, with which voice, and what it said of the language: once per engine and
        // language. Without a voice for it the engine goes on with the one it has, and may still speak
        // (see DirectionsVoice): only a voice that doesn't start counts as none (watchStart).
        val installed = runCatching { engine.engines }.getOrNull().orEmpty()
        val pkg = runCatching { engine.defaultEngine }.getOrNull()
        val label = installed.firstOrNull { it.name == pkg }?.label
        val voice = runCatching { engine.voice }.getOrNull()
        voiceEngine = pkg
        voiceLocale = locale.toLanguageTag()
        voiceMissing = result < TextToSpeech.LANG_AVAILABLE
        val line = "assistant: speech engine ${pkg ?: "none"} (${label ?: "?"}; installed: ${installed.joinToString { it.name }.ifEmpty { "none" }}), " +
            "voice ${voice?.name ?: "?"} - $locale: ${languageResult(result)}"
        if (voiceMissing) MyCarLog.w(line) else MyCarLog.i(line)
    }

    /** What setLanguage answered, for the log. */
    private fun languageResult(result: Int): String = when (result) {
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_AVAILABLE -> "available ($result)"
        TextToSpeech.LANG_MISSING_DATA -> "its voice data is missing ($result)"
        TextToSpeech.LANG_NOT_SUPPORTED -> "no voice for it ($result)"
        else -> "$result"
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        // The beep before listening, on the car's channel for prompts as the answers are.
        SpeechEngine.cueOnCar = { onPromptChannel() && DirectionsVoice.cueForAssistant() }
        // Said on the car once per drive (see smallModelOnce, silentOnce).
        smallModelSaid = false
        silentSaid = false
        // Started again, at the next drive, after a start that failed or an engine that went away
        // (updated in the background, Android never binds it again), as DirectionsVoice does:
        // otherwise answers and messages stayed silent until myCar's process went. A start that never
        // answered too, and inside a drive at an answer ([restartIfDue]).
        if (tts != null && !SpeechRestart.due(ttsReady, ttsFailed, ttsStartedAt, SystemClock.elapsedRealtime())) return
        startEngine()
        syncLanguage()
    }

    /** Makes the phone's engine, the first time or anew; on the main thread. */
    private fun startEngine() {
        tts?.shutdown()
        voiceIn = null
        ttsReady = false
        ttsFailed = false
        ttsStartedAt = SystemClock.elapsedRealtime()
        val mine = ++engineNumber
        tts = TextToSpeech(appContext) { status ->
            // An engine made again meanwhile: what this one says is no news of it.
            if (mine == engineNumber) {
                ttsReady = status == TextToSpeech.SUCCESS
                ttsFailed = !ttsReady
                if (ttsFailed) MyCarLog.w("assistant: text-to-speech didn't start (status $status) - trying again later")
                if (ttsReady) mainHandler.post(::syncLanguage)
            }
        }
    }

    /**
     * The engine made again inside a drive, as at the next one, when it can't speak and its last start
     * has had its time ([SpeechRestart]). The answer of now is shown without a voice, the next said.
     */
    private fun restartIfDue() {
        // Not before init made the first one.
        if (tts == null || !SpeechRestart.due(ttsReady, ttsFailed, ttsStartedAt, SystemClock.elapsedRealtime())) return
        // Marked at once: another answer before the main thread gets to it doesn't start it twice.
        ttsStartedAt = SystemClock.elapsedRealtime()
        ttsReady = false
        MyCarLog.i("assistant: starting text-to-speech again")
        if (Looper.myLooper() == Looper.getMainLooper()) startEngine() else mainHandler.post(::startEngine)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Starts listening, or does nothing if the assistant is already busy with the last request.
     * [SpeechRecognizer] must be driven from a thread with a Looper, but this is also called from
     * the raw wire-protocol worker thread ([MyCarRunner]'s key-event handling for the head
     * unit's own PTT button) which has none - so this always hops to the main thread itself
     * rather than asking every caller to remember to.
     */
    fun start() {
        if (Looper.myLooper() == Looper.getMainLooper()) startOnMainThread() else mainHandler.post { startOnMainThread() }
    }

    /**
     * myCar's own listens, whichever assistant the microphone wakes: for the Jolly key's assistant with
     * the phone locked and Home Assistant there to answer, or one that wouldn't start.
     */
    fun listenHere() {
        if (Looper.myLooper() == Looper.getMainLooper()) startOnMainThread(own = true) else mainHandler.post { startOnMainThread(own = true) }
    }

    /**
     * [text] as if the driver had said it - the Jolly key's command, written on the phone or picked in
     * the car - in [language], the one it was written in: understood, shown and answered as the
     * microphone's words are, without listening. Not while a call rings: its card has the screen.
     */
    fun runPhrase(text: String, language: VoiceLanguage) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { runPhrase(text, language) }
            return
        }
        if (appContext == null || CarCalls.state.value is CallUiState.Ringing) return
        // Listening or answering still: this goes first, as a new press of the microphone does.
        releaseRecognizer()
        stopVoice()
        replyGivesWay()
        written = language
        syncLanguage()
        MyCarLog.i("assistant: a written command, in ${language.code}")
        heard(text)
    }

    /**
     * A message being read or answered gives way to the assistant, as for the microphone: a reply
     * counting down goes now, one still read back is dropped (see [startOnMainThread]).
     */
    internal fun replyGivesWay() {
        if (!VoiceReply.active) return
        val reply = VoiceReply.state.value
        if (reply.phase == VoiceReply.Phase.CONFIRMING && reply.until > 0L) VoiceReply.sendNow() else VoiceReply.cancel()
    }

    private fun startOnMainThread(own: Boolean = false) {
        val context = appContext ?: return
        // A ringing call has the screen: nothing to listen for, and the ringtone would be heard as words.
        if (CarCalls.state.value is CallUiState.Ringing) {
            MyCarLog.i("assistant: not while a call rings")
            return
        }
        if (_state.value.phase == Phase.LISTENING) return
        // Still working on the last words (the model, Home Assistant): the microphone starts over
        // instead of doing nothing, which looked like the assistant had hung.
        if (_state.value.phase == Phase.THINKING) {
            MyCarLog.i("assistant: asked again while still working on the last words - starting over")
            releaseRecognizer()
            _pendingCommand.value = null
            _state.value = State(Phase.IDLE)
        }
        written = null
        syncLanguage()
        // A message being read or answered gives way: the driver asked for the assistant instead,
        // and the reply left waiting would keep the car's voice key for itself. A reply counting
        // down goes now, as the wheel's voice key sends it: dropping it lost it without a word. One
        // still read back doesn't (as for a ringing call, CarUi): the driver hasn't heard it yet, and
        // the microphone is what a driver who hears it come out wrong touches to say "cancel".
        if (VoiceReply.active) {
            val reply = VoiceReply.state.value
            if (reply.phase == VoiceReply.Phase.CONFIRMING && reply.until > 0L) VoiceReply.sendNow() else VoiceReply.cancel()
        }
        // The driver may have picked another assistant, which then takes the button instead.
        if (!own && AssistantChoice.startOther(context)) return
        // While the phone's screen is shared there's no car screen to show the assistant or act on
        // the words: the sharing ends, and the car's screen, back in a moment, takes it from here.
        if (PhoneMirror.active.value) {
            MyCarLog.i("assistant: the phone's screen was shared - back to myCar's screen")
            PhoneMirror.requestStop()
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = State(Phase.ERROR, reply = AssistantLanguage.answer("Needs the microphone permission."), missingPermission = true)
            return
        }
        // Android hands every app but the dialer a silent microphone during a call.
        if (CarCalls.state.value is CallUiState.Active) {
            _state.value = State(Phase.ERROR, reply = AssistantLanguage.answer("Voice commands work once the call ends."))
            return
        }
        stopVoice()
        _state.value = State(Phase.LISTENING, hint = languageHint(context))
        watch()
        listenOnce(
            onListening = { _state.value = _state.value.copy(ready = true) },
            onPartial = { text -> _state.value = _state.value.copy(heard = text) },
            onHeard = { _state.value = _state.value.copy(phase = Phase.THINKING) },
            onResult = { text ->
                _state.value = _state.value.copy(phase = Phase.THINKING, heard = text)
                _pendingCommand.value = text
            },
            onError = { message -> _state.value = State(Phase.ERROR, reply = message) },
        )
    }

    /** Closes [shown], an error left up a while, unless something else has the overlay since. */
    fun dismissError(shown: State) {
        if (_state.value === shown && shown.phase == Phase.ERROR) _state.value = State(Phase.IDLE)
    }

    /** Closes the overlay from wherever it's showing: mid-listen, mid-think, or after speaking. */
    fun cancel() {
        releaseRecognizer()
        stopVoice()
        written = null
        _pendingCommand.value = null
        _state.value = State(Phase.IDLE)
    }

    /**
     * Called once [pendingCommand] has been turned into an answer - from [CarUi], the one place
     * that actually has a [CarNavigator] to have acted on it with.
     */
    fun reply(english: String) {
        _pendingCommand.value = null
        syncLanguage()
        // AssistantCommands answers in English; the driver hears and reads it in their language.
        speakReply(AssistantLanguage.answer(english))
        forgetWritten()
    }

    /**
     * Home Assistant's answer to [heard] (AssistantBrain), already in the driver's language. Dropped
     * when the driver has moved on meanwhile - closed the assistant, or asked again - as it can take
     * a few seconds. When it asks something back the microphone doesn't open by itself: the driver
     * asked for the assistant to go away after an answer, and answers with the microphone.
     */
    fun replyAsSaid(heard: String, text: String) {
        if (_pendingCommand.value != heard || _state.value.phase != Phase.THINKING) return
        _pendingCommand.value = null
        speakReply(text)
        forgetWritten()
    }

    /**
     * A written command's language ([runPhrase]) was for its own answer, now on its way: what comes
     * next - a message read aloud, "do you want to reply?" and the "yes" listened for - is in the one the
     * assistant listens in. Not set again here: the answer is being said in the written one.
     */
    internal fun forgetWritten() {
        written = null
    }

    /** [heard] has gone to Home Assistant (AssistantBrain): the overlay says who it's waiting for. */
    fun askingHome(heard: String) {
        if (_pendingCommand.value == heard && _state.value.phase == Phase.THINKING) _state.value = _state.value.copy(brain = true)
    }

    /** As if the driver had said [text]: a written command ([runPhrase]), and the car preview, where no speech model runs. */
    internal fun heard(text: String) {
        _state.value = State(Phase.THINKING, heard = text)
        _pendingCommand.value = text
        watch()
    }

    /** Shows and says [text], then [then] once it has been said to the end. */
    private fun speakReply(text: String, then: () -> Unit = {}) {
        _state.value = _state.value.copy(phase = Phase.SPEAKING, reply = text, brain = false)
        // Long enough for any voice to say it, so only an engine that never says it's done hits it:
        // longer with the phone's speech rate turned down.
        speakLimitMs = ((SPEAK_BASE_MS + text.length * SPEAK_PER_CHAR_MS) * slowness()).toLong().coerceAtMost(SPEAK_MAX_MS)

        restartIfDue()
        val engine = tts
        if (text.isBlank()) {
            _state.value = _state.value.copy(phase = Phase.IDLE)
            return
        }
        val mine = ++replies
        if (engine == null || !ttsReady) {
            // No voice to say it with (a phone without a speech engine, or one still starting): on
            // screen long enough to read. It used to go at once, and the answer was never seen.
            mainHandler.postDelayed({
                if (mine == replies && _state.value.phase == Phase.SPEAKING) _state.value = _state.value.copy(phase = Phase.IDLE)
            }, SILENT_REPLY_MS)
            return
        }
        appContext?.let(VoiceFocus::acquire)
        speaking = speak(engine, text) { stopped, refused ->
            VoiceFocus.release()
            // Still this answer on screen: done speaking, or cut short by a message read aloud. Not
            // when the microphone or Cancel stopped it, which have moved the overlay on already.
            if (mine == replies && _state.value.phase == Phase.SPEAKING) {
                // Not said, the engine refused: on screen long enough to read, as with no engine.
                if (refused) {
                    mainHandler.postDelayed({
                        if (mine == replies && _state.value.phase == Phase.SPEAKING) {
                            _state.value = _state.value.copy(phase = Phase.IDLE)
                            then()
                        }
                    }, SILENT_REPLY_MS)
                    return@speak
                }
                _state.value = _state.value.copy(phase = Phase.IDLE)
                if (!stopped) mainHandler.post(then)
            }
        }
    }

    /** The answer being said, so the [watchdog] can end it. */
    private var speaking: String? = null
    private var speakLimitMs = SPEAK_MAX_MS
    /** When the speech engine last said it reached a word: still talking, however long the answer. */
    @Volatile private var lastWordAt = 0L

    /** How much slower than normal the phone speaks (Android's speech rate, 100 = normal). */
    private fun slowness(): Float {
        val rate = runCatching {
            android.provider.Settings.Secure.getInt(appContext?.contentResolver, android.provider.Settings.Secure.TTS_DEFAULT_RATE, 100)
        }.getOrDefault(100)
        return if (rate in 1 until 100) 100f / rate else 1f
    }

    /**
     * Never left up by itself: whatever stalls - the final model loading, a phrase the recognizer
     * never finishes, Home Assistant, a speech engine that never says it's done - the overlay goes
     * after a while and the microphone works again. It used to sit on "…" with the microphone
     * deaf, which the driver took for a hang; asked for it to go away by itself instead.
     */
    private val watchdog = object : Runnable {
        private var seen: State? = null
        private var since = 0L

        override fun run() {
            val now = SystemClock.uptimeMillis()
            val s = _state.value
            if (s.phase == Phase.IDLE || s.phase == Phase.ERROR) {
                seen = null
                return
            }
            val last = seen
            if (last == null || last.phase != s.phase || last.ready != s.ready || last.brain != s.brain) {
                seen = s
                since = now
            }
            val limit = when (s.phase) {
                Phase.LISTENING -> if (s.ready) LISTEN_LIMIT_MS else OPEN_LIMIT_MS
                Phase.THINKING -> THINK_LIMIT_MS
                else -> speakLimitMs
            }
            val stillTalking = s.phase == Phase.SPEAKING && now - lastWordAt < STILL_TALKING_MS
            if (now - since < limit || stillTalking) {
                mainHandler.postDelayed(this, WATCH_MS)
                return
            }
            seen = null
            MyCarLog.w("assistant: still ${s.phase.name.lowercase()}${if (s.brain) " for Home Assistant" else ""} after ${limit / 1000} s - closing")
            giveUp(s.phase)
        }
    }

    private fun watch() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.post(watchdog)
    }

    private fun giveUp(phase: Phase) {
        releaseRecognizer()
        _pendingCommand.value = null
        if (phase == Phase.SPEAKING) {
            // The engine never said it finished: ended here, or the music it paused stayed paused.
            speaking?.let { end(it, stopped = true) }
            stopVoice()
            _state.value = State(Phase.IDLE)
            return
        }
        stopVoice()
        val why = if (phase == Phase.LISTENING && !_state.value.ready) "The microphone isn't available right now." else "Speech recognition failed."
        _state.value = State(Phase.ERROR, reply = AssistantLanguage.answer(why))
    }

    /**
     * The phone's own speech recognition listens: chosen, and there is one on the phone. Chosen on a
     * phone without any (GrapheneOS), myCar's models listen, as for the microphone (listenOnce):
     * taken as the phone's, the assistant said "The speech model didn't load." at every press.
     */
    private fun phoneListens(context: Context): Boolean =
        CarSettings.systemSpeech.value && SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * myCar is in a language its downloaded models don't hear: the assistant listens in English
     * (VoiceLanguages.effective), and Italian said to it came out as nonsense with no word why.
     * Said in myCar's own language, with the model to download.
     */
    private fun languageHint(context: Context): String {
        val phone = VoiceLanguages.phone()
        if (phone == VoiceLanguage.EN || AssistantLanguage.language != VoiceLanguage.EN || phoneListens(context)) return ""
        val model = SpeechModels.recommended(phone).last()
        MyCarLog.w("assistant: no model hears ${phone.code} - listening in English")
        return Words.get(
            R.string.assistant_needs_model,
            phone.locale.getDisplayLanguage(java.util.Locale.getDefault()),
            Words.get(model.title),
        )
    }

    private var replies = 0

    /**
     * Speaks [text] for someone other than the assistant (a message read aloud), then runs
     * [onDone] on the main thread - straight away if speech isn't available. [text] is said as it
     * is: what the assistant itself says in it is already in the driver's language (VoiceReply).
     */
    fun say(text: String, onDone: () -> Unit) {
        syncLanguage()
        restartIfDue()
        val engine = tts
        val context = appContext
        if (engine == null || context == null || !ttsReady || text.isBlank()) {
            mainHandler.post(onDone)
            return
        }
        VoiceFocus.acquire(context)
        val id = speak(engine, text) { _, refused ->
            VoiceFocus.release()
            // Always, stopped part way too: a turn that cut a message read out on the car's channel
            // for spoken prompts (DirectionsVoice) left the reply waiting for good. Whoever stopped
            // it on purpose (Cancel, the microphone) has moved VoiceReply on, whose callbacks only
            // act for the run they were made in. Not said (refused, or a voice that never started):
            // its card stays long enough to read, as an answer does, the music back meanwhile.
            if (refused) mainHandler.postDelayed(onDone, SILENT_REPLY_MS) else mainHandler.post(onDone)
        }
        // The assistant's watchdog doesn't see a message read aloud (its phase stays idle): an engine
        // that never says it finished (updated in the background mid-message) kept the music paused and
        // the reply stuck on its card. Ended after as long as any voice takes to say it, as an answer is,
        // and not while the engine still tells the words it says.
        val limit = ((SPEAK_BASE_MS + text.length * SPEAK_PER_CHAR_MS) * slowness()).toLong().coerceAtMost(SPEAK_MAX_MS)
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!endings.containsKey(id)) return
                if (SystemClock.uptimeMillis() - lastWordAt < STILL_TALKING_MS) {
                    mainHandler.postDelayed(this, WATCH_MS)
                    return
                }
                MyCarLog.w("assistant: a message read aloud never said it ended after ${limit / 1000} s - closing it")
                tts?.stop()
                end(id, stopped = true)
            }
        }, limit)
    }

    private var utterances = 0

    /**
     * What to do when each utterance ends, by its id: stopped part way, or refused by the engine
     * (never said).
     */
    private val endings = java.util.concurrent.ConcurrentHashMap<String, (stopped: Boolean, refused: Boolean) -> Unit>()

    /**
     * One listener for every utterance, which finds its own ending by id. With a listener per
     * utterance, the end of the one cut short by the next went to the next's: the voice focus was
     * given back while it still spoke, and its caller was told it had finished.
     */
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            startedUtterance = utteranceId
            // How soon the engine starts, for the log: whether it starts at all is what counts (watchStart).
            if (utteranceId == askedUtterance) MyCarLog.i("assistant: the phone's voice started in ${SystemClock.uptimeMillis() - askedAt} ms")
            // The phone's voice speaks: its settings stop saying it didn't.
            appContext?.let(PhoneVoice::spoke)
        }
        override fun onDone(utteranceId: String?) = end(utteranceId, stopped = false)
        @Deprecated("Deprecated in Java, no replacement on this API level")
        override fun onError(utteranceId: String?) = end(utteranceId, stopped = false)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = end(utteranceId, stopped = true)
        // Engines that tell each word as they say it keep the watchdog off a long answer.
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            lastWordAt = SystemClock.uptimeMillis()
        }
    }

    private fun end(utteranceId: String?, stopped: Boolean, refused: Boolean = false) {
        endings.remove(utteranceId ?: return)?.invoke(stopped, refused)
    }

    /**
     * Speaks [text], then calls [ending] once. A [TextToSpeech.speak] that fails (the speech
     * engine went away mid-drive) sends no callback at all: it ends at once, or the voice focus
     * stayed taken and the music paused for the rest of the drive.
     */
    private fun speak(engine: TextToSpeech, text: String, ending: (stopped: Boolean, refused: Boolean) -> Unit): String {
        val id = "mycar-${++utterances}"
        endings[id] = ending
        // On the car's channel for spoken prompts, where the car has one, while the phone's own sound
        // doesn't reach the car through myCar (see onPromptChannel).
        if (onPromptChannel() &&
            DirectionsVoice.speakForAssistant(text, VoiceLanguages.speakingLocale(AssistantLanguage.language)) { cut, refused ->
                end(id, stopped = cut, refused = refused)
            }
        ) {
            return id
        }
        engine.setOnUtteranceProgressListener(progress)
        askedUtterance = id
        askedAt = SystemClock.uptimeMillis()
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            // A text too long for any engine says nothing about the engine; otherwise it went away
            // (updated in the background) and is made again at a later answer or the next drive.
            if (text.length < TextToSpeech.getMaxSpeechInputLength()) {
                MyCarLog.w("assistant: speech engine refused to speak - it will be started again")
                ttsFailed = true
            } else {
                MyCarLog.w("assistant: too long to speak (${text.length} characters)")
            }
            end(id, stopped = false, refused = true)
        } else {
            watchStart(id, text)
        }
        return id
    }

    /**
     * The assistant's voice - its answers, the messages read out, the listening cue - goes on the car's
     * channel for spoken prompts while the phone's own sound doesn't reach the car through myCar:
     * nothing captured (myCar's own music, Xonos, goes straight into the car), or the car's speakers
     * not myCar's (the driver picked the radio, or the radio stays until the phone plays). Said on the
     * phone, it came out of the phone's speaker; or, captured with the radio on, it was lost (the phone
     * is muted while it captures) and took the radio away for the rest of the drive.
     */
    private fun onPromptChannel(): Boolean = CarSettings.pipeAudio.value &&
        (CarAudio.capture.value != CarAudio.Capture.ON || !CarAudio.speakersOurs)

    /** The last utterance the engine said it started (on its own thread). */
    @Volatile private var startedUtterance: String? = null
    /** The last utterance given to the engine, and when (uptime). */
    @Volatile private var askedUtterance: String? = null
    @Volatile private var askedAt = 0L

    /**
     * An engine with no voice for the language may never start at all, nor say it finished: the music
     * stayed paused 11-20 s for an answer nobody heard. Not started in a few seconds (a little more for
     * a long text, which some engines make whole before they start), it's taken as no voice: the text
     * stays on the car's screen, as without an engine, and the music comes back.
     */
    private fun watchStart(id: String, text: String) {
        val wait = (NO_START_MS + text.length * NO_START_PER_CHAR_MS).coerceAtMost(NO_START_MAX_MS)
        mainHandler.postDelayed({
            if (!endings.containsKey(id) || startedUtterance == id) return@postDelayed
            MyCarLog.w("assistant: the phone's voice didn't start in ${wait / 1000.0} s - shown on the car instead")
            val context = appContext
            val engine = voiceEngine
            if (context != null && engine != null) PhoneVoice.silent(context, engine, voiceLocale, voiceMissing)
            end(id, stopped = false, refused = true)
            // Not left to start later, over the music given back.
            tts?.stop()
            // Once the answer has had its time on the screen: under it, the line wasn't seen.
            mainHandler.postDelayed(::silentOnce, SILENT_REPLY_MS)
        }, wait)
    }

    /** Whether this drive's car was told the phone's voice doesn't speak. */
    private var silentSaid = false

    /** Said on the car once per drive: the phone's voice doesn't speak, and where to see about it. */
    private fun silentOnce() {
        if (silentSaid) return
        silentSaid = true
        CarToasts.show(this, Words.get(R.string.car_voice_silent), androidx.car.app.CarToast.LENGTH_LONG)
    }

    fun stopSpeaking() { stopVoice() }

    /** The voice stops, the phone's engine or the car's channel for prompts (see [speak]). */
    private fun stopVoice() {
        tts?.stop()
        DirectionsVoice.stopAssistant()
        // Ended here, not left to the engine's callbacks: one that never came kept the music paused.
        endings.keys.toList().forEach { end(it, stopped = true) }
    }

    /**
     * Listens once, the same way the assistant does, and hands back what was said without acting
     * on it. Callbacks arrive on the main thread; [onError] gets a sentence fit to show.
     */
    fun dictate(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        cue: Boolean = true,
        onSpeaking: () -> Unit = {},
        /** The phrase has ended and its words are being made out. */
        onHeard: () -> Unit = {},
    ) {
        cancel()
        syncLanguage()
        listenOnce(onPartial, onHeard = onHeard, onResult = onResult, onError = onError, cue = cue, onSpeaking = onSpeaking)
    }

    /**
     * One phrase, through myCar's own [SpeechEngine], or the phone's recognizer when the driver
     * chose it in settings. Never the phone's on its own: on most phones that's Google's.
     */
    private fun listenOnce(
        onPartial: (String) -> Unit,
        onListening: () -> Unit = {},
        onHeard: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        cue: Boolean = true,
        onSpeaking: () -> Unit = {},
    ) {
        // Every error is shown and said in the driver's language.
        val failed: (String) -> Unit = { onError(AssistantLanguage.answer(it)) }
        val context = appContext ?: return failed("Speech isn't ready yet.")
        if (CarSettings.systemSpeech.value && SpeechRecognizer.isRecognitionAvailable(context)) {
            MyCarLog.i("assistant: listening with the phone's recognizer in ${AssistantLanguage.language.code}")
            return systemListen(context, onListening, onPartial, onHeard, onResult, failed)
        }
        val final = SpeechModels.finalFor(context, AssistantLanguage.language)
        MyCarLog.i("assistant: listening in ${AssistantLanguage.language.code}, final model ${final?.label ?: "none"}")
        SpeechEngine.listen(
            context,
            language = AssistantLanguage.language,
            cue = cue,
            onSpeaking = onSpeaking,
            onPartial = onPartial,
            onListening = onListening,
            onHeard = onHeard,
            onResult = onResult,
            onError = failed,
            onLiveWords = ::smallModelOnce,
        )
    }

    /** Whether this drive's car was told the phone is short of memory for the big voice model. */
    private var smallModelSaid = false

    /**
     * The phone hasn't the memory for the model that rereads the phrase (SpeechEngine): the small one
     * hears alone, and may get names and places wrong. Said on the car once per drive: over
     * "Listening…", where the driver looks while talking (a toast there would be under the assistant),
     * or as a toast when that line says something else already, or for a reply being dictated.
     */
    private fun smallModelOnce() {
        if (smallModelSaid) return
        smallModelSaid = true
        val text = Words.get(R.string.car_voice_small_model)
        val now = _state.value
        if (now.phase == Phase.LISTENING && now.hint.isBlank()) {
            _state.value = now.copy(hint = text)
        } else {
            CarToasts.show(this, text, androidx.car.app.CarToast.LENGTH_LONG)
        }
    }

    private fun systemListen(
        context: Context,
        onListening: () -> Unit,
        onPartial: (String) -> Unit,
        onHeard: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onHeard()
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                releaseRecognizer()
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                releaseRecognizer()
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onError("Didn't catch that.") else onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
        })
        // The music paused while the phone's recognizer listens, as with myCar's own: it heard the song.
        if (!systemFocus) {
            VoiceFocus.acquire(context)
            systemFocus = true
        }
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceLanguages.speakingLocale(AssistantLanguage.language).toLanguageTag())
            },
        )
    }

    /**
     * Stops listening, for someone else's sake (a message read, a quick reply). The overlay closes
     * too if it was listening: with nothing left to call back, it stayed on "Listening…" and the
     * microphone and voice key were ignored until its X.
     */
    fun stopListening() {
        releaseRecognizer()
        val phase = _state.value.phase
        if (phase == Phase.LISTENING || phase == Phase.THINKING) {
            _pendingCommand.value = null
            _state.value = State(Phase.IDLE)
        }
    }

    private fun releaseRecognizer() {
        SpeechEngine.cancel()
        recognizer?.let { runCatching { it.cancel() }; it.destroy() }
        recognizer = null
        if (systemFocus) {
            systemFocus = false
            VoiceFocus.release()
        }
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Needs the microphone permission."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech recognizer needs a network it doesn't have."
        else -> "Speech recognition failed."
    }

    fun shutdown() {
        releaseRecognizer()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}

/** An answer with no voice to say it stays on screen this long. */
private const val SILENT_REPLY_MS = 5_000L

/** How long the phone's voice gets to start saying something, and a little more per letter, up to the most. */
private const val NO_START_MS = 3_000L
private const val NO_START_PER_CHAR_MS = 10L
private const val NO_START_MAX_MS = 8_000L

/** How often the overlay is checked for being stuck. */
private const val WATCH_MS = 1_000L
/** From the press to the microphone open: the cue, the car's microphone, the live model loading. */
private const val OPEN_LIMIT_MS = 10_000L
/** Listening: the recognizer ends a phrase at 15 s; a little more for the car's microphone to stop. */
private const val LISTEN_LIMIT_MS = 25_000L
/** Working out the words, or waiting for Home Assistant (which gets 15 s of its own). */
private const val THINK_LIMIT_MS = 20_000L
/** Saying an answer: this, and a little more per letter. */
private const val SPEAK_BASE_MS = 8_000L
private const val SPEAK_PER_CHAR_MS = 120L
private const val SPEAK_MAX_MS = 120_000L
/** A word said this recently: the engine is still talking. */
private const val STILL_TALKING_MS = 3_000L
