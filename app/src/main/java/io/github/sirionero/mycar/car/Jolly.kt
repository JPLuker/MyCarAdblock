package io.github.sirionero.mycar.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import androidx.annotation.StringRes
import androidx.car.app.CarToast
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.home.HaClient
import io.github.sirionero.mycar.home.HaException
import io.github.sirionero.mycar.home.HomeAssistant
import io.github.sirionero.mycar.home.Seen
import io.github.sirionero.mycar.home.stateOrNull
import io.github.sirionero.mycar.home.watchEntity
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * The Jolly key's settings, the same in the car and on the phone: what it does ([action], null until
 * the driver picks something) and whether the dock shows it at all ([shown], on to begin with).
 */
internal object JollySetting {
    private const val KEY = "car_jolly"
    private const val KEY_SHOWN = "car_jolly_shown"
    private var app: Context? = null
    private val actionFlow = MutableStateFlow<JollyAction?>(null)
    private val shownFlow = MutableStateFlow(true)
    val action: StateFlow<JollyAction?> = actionFlow.asStateFlow()
    val shown: StateFlow<Boolean> = shownFlow.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        actionFlow.value = JollyActions.parse(AppSettings.getString(app, KEY, ""))
        shownFlow.value = AppSettings.getString(app, KEY_SHOWN, "true") != "false"
    }

    fun set(action: JollyAction?) {
        actionFlow.value = action
        Jolly.disarm()
        app?.let { AppSettings.putString(it, KEY, action?.let(JollyActions::saved).orEmpty()) }
    }

    fun setShown(on: Boolean) {
        shownFlow.value = on
        app?.let { AppSettings.putString(it, KEY_SHOWN, on.toString()) }
    }
}

/**
 * What a tap on the Jolly key does: the command picked for it, run the way the rest of myCar runs
 * the same thing - the dialer's call, the microphone's assistant on the phone, Plancia's connection to
 * Home Assistant, the assistant's "navigate to", its commands, the app grid's apps. Nothing here
 * types or needs reading: a call or a Home Assistant action is one tap, as a favourite is in Android
 * Auto, and only opening a lock waits for a second one.
 */
internal object Jolly {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val second = SecondTap(CONFIRM_MS, CONFIRM_MIN_MS)
    private val repeats = RepeatGuard(REPEAT_QUIET_MS)
    /** A lock waits for its second tap, a Home Assistant command runs (a lock until it says it's done). */
    private var asking = false
    private var working = false
    private val litFlow = MutableStateFlow(false)

    /** Lit while a lock waits for its second tap, and while a Home Assistant command runs. */
    val lit: StateFlow<Boolean> = litFlow.asStateFlow()

    private fun relight() {
        litFlow.value = asking || working
    }

    /**
     * The key tapped (on the main thread): the command runs, or with none picked yet the chooser
     * opens. [entries] and [running]: the apps as the dock knows them, [frame] the car's picture.
     */
    fun tap(context: Context, navigator: CarNavigator, entries: List<Entry>, running: Map<String, RunningSlot>, frame: CarEnvironment.Frame) {
        val action = JollySetting.action.value
        if (action == null) {
            navigator.jolly()
            return
        }
        // A ringing call has the screen. One under way has the line and the microphone: another call,
        // an assistant or a spoken answer would go over it.
        val phoneCall = CarCalls.state.value
        if (phoneCall is CallUiState.Ringing) return
        if (phoneCall is CallUiState.Active && action.kind in BUSY_ON_A_CALL) {
            toast(Words.get(R.string.jolly_after_call))
            return
        }
        MyCarLog.i("jolly: ${action.kind.name.lowercase()}")
        // The phone's own context, not the car screen's: an assistant started from that one would open on
        // the car's display, which Android doesn't let it draw on.
        val app = context.applicationContext
        when (action) {
            is JollyAction.Call -> call(app, navigator, action)
            is JollyAction.Assistant -> talk(app, action.app)
            is JollyAction.Home -> home(action)
            is JollyAction.Navigate -> navigate(app, navigator, entries, frame, action)
            is JollyAction.Say -> CarAssistant.runPhrase(action.phrase, language(action.lang))
            is JollyAction.OpenApp -> open(navigator, entries, running, frame, action)
        }
    }

    /** The second tap a lock waited for isn't coming: a new choice, or the chooser opened. */
    fun disarm() {
        second.disarm()
        asking = false
        relight()
    }

    /**
     * Through Telecom, as the dialer calls: the call's own card comes up on the car. The number as the
     * contacts have it now: changed or gone there, the key asks again rather than call an old one.
     */
    private fun call(context: Context, navigator: CarNavigator, action: JollyAction.Call) {
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            toast(Words.get(R.string.dialer_permissions))
            return
        }
        scope.launch {
            if (withContext(Dispatchers.IO) { stillInContacts(context, action.name, action.number) } == false) {
                MyCarLog.i("jolly: the number chosen isn't in the contacts any more - asking again")
                toast(Words.get(R.string.jolly_call_gone, action.name))
                navigator.jolly()
                return@launch
            }
            placeCall(context, action.number)
        }
    }

    /** Whether [number] is still [name]'s in the contacts; null where that can't be told (no permission). */
    private fun stillInContacts(context: Context, name: String, number: String): Boolean? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            context.contentResolver.query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
            )?.use { cursor ->
                var found = false
                while (!found && cursor.moveToNext()) found = cursor.getString(0) == name
                found
            }
        }.getOrNull()
    }

    /**
     * The phone's assistant, or an assistant app, woken listening as the microphone wakes it
     * (PhoneAssistants: Gemini, Claude, Google...). It listens and answers on the phone, its voice in
     * the car over Bluetooth. Locked, the phone is opened first, as for the microphone - or, with Home
     * Assistant there to answer, myCar listens instead. One that won't start: myCar listens.
     */
    private fun talk(context: Context, app: String) {
        val name = PhoneAssistants.label(context, app)
        // myCar's own, or a message being read or answered, gives way: the driver asked for another.
        if (CarAssistant.state.value.phase != CarAssistant.Phase.IDLE) CarAssistant.cancel()
        CarAssistant.replyGivesWay()
        if (AssistantUnlock.needed(context)) {
            if (AssistantBrain.ready) {
                MyCarLog.i("jolly: the phone is locked, myCar listens and Home Assistant answers")
                toast(Words.get(R.string.assistant_locked_brain))
                CarAssistant.listenHere()
                return
            }
            AssistantUnlock.start(context, name) { talk(context, app) }
            return
        }
        if (!PhoneAssistants.launch(context, app)) {
            toast(Words.get(R.string.assistant_start_failed, name))
            CarAssistant.listenHere()
            return
        }
        toast(
            if (app == PhoneAssistants.PHONE) Words.get(R.string.assistant_listening_on_phone)
            else Words.get(R.string.assistant_app_listening_on_phone, name),
        )
    }

    /**
     * On the drive's connection to Home Assistant (Plancia inside myCar), with what came of it as a toast.
     * Home Assistant answers "done" even for a thing it doesn't have or can't reach, so the thing's state
     * is asked first (missing or unavailable: nothing is sent), and a lock is watched until it says it did
     * it (or, after [LOCK_WAIT_MS], how it was left).
     */
    private fun home(action: JollyAction.Home) {
        if (!HomeAssistant.config.value.isComplete) {
            toast(Words.get(R.string.jolly_home_not_set))
            return
        }
        val (domain, service) = HomeActs.service(action.entity, action.act) ?: return
        val client = HomeAssistant.live
        if (client == null) {
            toast(Words.get(R.string.jolly_home_offline))
            return
        }
        // The same command again while it runs, or a moment after: the same press twice, let go.
        val key = "${action.entity}/${action.act.id}"
        if (repeats.busy(key, SystemClock.uptimeMillis())) {
            MyCarLog.i("jolly: $domain.$service still running - the tap let go")
            return
        }
        if (HomeActs.confirms(action.act) && !confirmed(action.name)) return
        if (!repeats.start(key, SystemClock.uptimeMillis())) return
        working = true
        relight()
        scope.launch {
            try {
                val outcome = client.watchEntity(action.entity) { seen -> runWatched(client, action, domain, service, seen) }
                    // A Home Assistant too old to say one thing's state: the command alone, as before.
                    ?: run {
                        callService(client, domain, service, action.entity)
                        HomeOutcome.DONE
                    }
                MyCarLog.i("jolly: Home Assistant $domain.$service - ${outcome.name.lowercase()}")
                toast(Words.get(outcome.text, action.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not its words in the log: they may name the house's things.
                MyCarLog.w("jolly: Home Assistant didn't run $domain.$service (${e.javaClass.simpleName})")
                toast(Words.get(R.string.jolly_home_failed, action.name, e.message.orEmpty()))
            } finally {
                repeats.end(key, SystemClock.uptimeMillis())
                working = false
                relight()
            }
        }
    }

    /** What came of a Home Assistant command, as the car says it (each text takes the thing's name). */
    private enum class HomeOutcome(@StringRes val text: Int) {
        DONE(R.string.jolly_home_done),
        MISSING(R.string.jolly_home_missing),
        UNAVAILABLE(R.string.jolly_home_unavailable),
        JAMMED(R.string.jolly_lock_jammed),
        STILL_LOCKED(R.string.jolly_lock_still_locked),
        STILL_UNLOCKED(R.string.jolly_lock_still_unlocked),
        NO_NEWS(R.string.jolly_lock_no_news),
    }

    /** The command, sent only to a thing that's there and reachable; a lock then watched (see [HomeChecks]). */
    private suspend fun runWatched(client: HaClient, action: JollyAction.Home, domain: String, service: String, seen: StateFlow<Seen>): HomeOutcome {
        val first = withTimeoutOrNull(STATE_MS) { seen.first { it != Seen.Waiting } }
            ?: throw HaException(Words.get(R.string.ha_not_answering), HaException.Reason.NO_RESPONSE)
        when (HomeChecks.before(first.stateOrNull)) {
            HomeChecks.Before.MISSING -> return HomeOutcome.MISSING
            HomeChecks.Before.UNAVAILABLE -> return HomeOutcome.UNAVAILABLE
            HomeChecks.Before.GO -> Unit
        }
        callService(client, domain, service, action.entity)
        if (action.act != HomeAct.LOCK && action.act != HomeAct.UNLOCK) return HomeOutcome.DONE
        val last = withTimeoutOrNull(LOCK_WAIT_MS) { seen.first { HomeChecks.settled(action.act, it.stateOrNull) } } ?: seen.value
        return when (HomeChecks.after(action.act, last.stateOrNull)) {
            HomeChecks.After.DONE -> HomeOutcome.DONE
            HomeChecks.After.JAMMED -> HomeOutcome.JAMMED
            HomeChecks.After.STILL_LOCKED -> HomeOutcome.STILL_LOCKED
            HomeChecks.After.STILL_UNLOCKED -> HomeOutcome.STILL_UNLOCKED
            HomeChecks.After.NO_NEWS -> HomeOutcome.NO_NEWS
        }
    }

    private suspend fun callService(client: HaClient, domain: String, service: String, entity: String) {
        client.command("call_service") {
            put("domain", domain)
            put("service", service)
            put("target", JSONObject().put("entity_id", entity))
        }
    }

    /**
     * The first tap arms, a second between [CONFIRM_MIN_MS] and [CONFIRM_MS] later goes ahead (see
     * [SecondTap]); one sooner, a double tap made before the question could be read, does nothing.
     */
    private fun confirmed(name: String): Boolean {
        when (second.tap(SystemClock.uptimeMillis())) {
            SecondTap.Tap.GO -> {
                asking = false
                relight()
                return true
            }
            SecondTap.Tap.TOO_SOON -> {
                MyCarLog.i("jolly: the second tap came too soon - still asking")
                return false
            }
            SecondTap.Tap.ARMED -> Unit
        }
        asking = true
        relight()
        toast(Words.get(R.string.jolly_confirm_unlock, name), CarToast.LENGTH_SHORT)
        main.postDelayed({
            if (!second.armed(SystemClock.uptimeMillis())) {
                asking = false
                relight()
            }
        }, CONFIRM_MS + 100)
        return false
    }

    /**
     * To the place written on the phone, with the map app myCar's assistant would use for "navigate
     * to": the one used last, or the first. A map app already guiding takes the new place at once.
     */
    private fun navigate(context: Context, navigator: CarNavigator, entries: List<Entry>, frame: CarEnvironment.Frame, action: JollyAction.Navigate) {
        scope.launch {
            // The dock's apps, unless the phone's haven't been read yet this drive.
            val apps = entries.takeIf { list -> list.any { it.template != null } }
                ?: withContext(Dispatchers.IO) { LauncherCache.load(context) }
            val maps = apps.filter { it.template?.isNavigation == true }
            val map = maps.firstOrNull { it.componentId == CarSettings.lastNav.value } ?: maps.firstOrNull()
            val template = map?.template
            if (map == null || template == null) {
                toast(Words.get(R.string.jolly_no_map))
                return@launch
            }
            CarServices.connectNav(template, frame, action.place)
            navigator.home()
            toast(Words.get(R.string.jolly_navigating, action.name.ifBlank { action.place }, map.label))
        }
    }

    /** As its icon on the grid or in the dock opens it: back to it if it's open. */
    private fun open(navigator: CarNavigator, entries: List<Entry>, running: Map<String, RunningSlot>, frame: CarEnvironment.Frame, action: JollyAction.OpenApp) {
        // Saved as the Plancia or Xonos app, or a music service's: the one that stands for it now, as a pin.
        val shown = pinShown(action.id, entries)
        val entry = entries.find { it.componentId == shown } ?: findEntry(action.id)
        if (entry == null) {
            toast(Words.get(R.string.jolly_app_gone, action.name.ifBlank { action.id }))
            return
        }
        openApp(entry, entry.componentId?.let { running[it] }, navigator, frame)
    }

    /** The language a command was written in, as the assistant knows it; myCar's own for one it doesn't. */
    private fun language(code: String): VoiceLanguage =
        VoiceLanguage.entries.firstOrNull { it.code == code } ?: VoiceLanguages.phone()

    private fun toast(text: String, duration: Int = CarToast.LENGTH_LONG) = CarToasts.show(this, text, duration)

    /** What [action] does, in a few words in myCar's language: in the chooser, the settings, the toasts. */
    fun title(context: Context, action: JollyAction): String = when (action) {
        is JollyAction.Call -> Words.get(R.string.jolly_title_call, action.name)
        is JollyAction.Assistant ->
            if (action.app == PhoneAssistants.PHONE) Words.get(R.string.jolly_title_ai_phone)
            else Words.get(R.string.jolly_title_ai, PhoneAssistants.label(context, action.app))
        is JollyAction.Home -> Words.get(homeTitle(action.entity, action.act), action.name)
        is JollyAction.Navigate -> Words.get(R.string.jolly_title_navigate, action.name.ifBlank { action.place })
        is JollyAction.Say -> Words.get(R.string.jolly_title_say, action.phrase)
        is JollyAction.OpenApp -> Words.get(R.string.jolly_title_app, action.name.ifBlank { action.id })
    }

    /** "Start …", "Turn … on or off", "Unlock …": what [act] does to [entity], with its name to fill in. */
    @StringRes
    fun homeTitle(entity: String, act: HomeAct): Int = when (act) {
        HomeAct.RUN -> R.string.jolly_title_run
        HomeAct.PRESS -> R.string.jolly_title_press
        // A cover is a gate, a garage door or a blind: it opens and closes rather than going on and off.
        HomeAct.TOGGLE -> if (HomeActs.domain(entity) == "cover") R.string.jolly_title_open_close else R.string.jolly_title_toggle
        HomeAct.LOCK -> R.string.jolly_title_lock
        HomeAct.UNLOCK -> R.string.jolly_title_unlock
    }

    /** What the Jolly key can do in Home Assistant, from [client]'s states, as its lists show it. */
    suspend fun homeChoices(client: HaClient): List<HomeActs.Choice> {
        val states = client.command("get_states") as? JSONArray ?: return emptyList()
        return withContext(Dispatchers.Default) {
            val things = (0 until states.length()).mapNotNull { i ->
                val state = states.optJSONObject(i) ?: return@mapNotNull null
                val entity = state.optString("entity_id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                entity to state.optJSONObject("attributes")?.optString("friendly_name").orEmpty()
            }
            HomeActs.choices(things)
        }
    }

    /**
     * The same from the phone's settings, on a connection of its own: the drive's isn't there without
     * a car. The address at home first, then the one away; the last one's failure if neither answers.
     */
    suspend fun homeChoices(config: HomeAssistant.Config): List<HomeActs.Choice> {
        var failure: Exception? = null
        for (url in config.urls) {
            val client = HomeAssistant.newClient()
            try {
                client.connect(url, config.token)
                return homeChoices(client)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            } finally {
                client.close()
            }
        }
        throw failure ?: IllegalStateException("no Home Assistant address")
    }

    /** What a call under way leaves no room for: another call, an assistant listening, an answer spoken. */
    private val BUSY_ON_A_CALL = setOf(JollyKind.CALL, JollyKind.AI, JollyKind.SAY)

    /** How long a lock waits for its second tap, and how long at least: time to read the question. */
    private const val CONFIRM_MS = 3_000L
    private const val CONFIRM_MIN_MS = 700L

    /** How long after a Home Assistant command a tap on the key is still that same press. */
    private const val REPEAT_QUIET_MS = 1_000L

    /** How long Home Assistant has to say a thing's state, and a lock to say it did what it was told. */
    private const val STATE_MS = 5_000L
    private const val LOCK_WAIT_MS = 10_000L
}
