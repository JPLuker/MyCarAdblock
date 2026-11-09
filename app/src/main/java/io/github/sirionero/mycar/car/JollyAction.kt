package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage

/** The kinds of command the Jolly key can run, in the order its chooser shows them. */
internal enum class JollyKind { CALL, AI, HOME, NAVIGATE, SAY, APP }

/**
 * What the Jolly key does: the one command the driver picked for it. Kept under its own preference
 * (JollySetting) as a line of "key=value" pairs joined by "&" ([JollyActions]). No Android here, so
 * every saved value can be tried on the JVM (JollyActionTest).
 */
internal sealed interface JollyAction {
    val kind: JollyKind

    /** A contact's number, called as the dialer calls; [label]: which of their numbers ("Mobile"), or empty. */
    data class Call(val name: String, val number: String, val label: String = "") : JollyAction {
        override val kind get() = JollyKind.CALL
    }

    /** An assistant on the phone, woken listening: PhoneAssistants.PHONE for the phone's own, or an app's package. */
    data class Assistant(val app: String) : JollyAction {
        override val kind get() = JollyKind.AI
    }

    /** [act] on one thing in the driver's Home Assistant ([entity]: "script.open_gate"), called [name] there. */
    data class Home(val entity: String, val act: HomeAct, val name: String) : JollyAction {
        override val kind get() = JollyKind.HOME
    }

    /** A place for the car's map app, as written on the phone; [name]: what the driver calls it ("Home"), or empty. */
    data class Navigate(val place: String, val name: String = "") : JollyAction {
        override val kind get() = JollyKind.NAVIGATE
    }

    /** A phrase for myCar's assistant, as if said: in [lang], the code of the language it was written in. */
    data class Say(val phrase: String, val lang: String) : JollyAction {
        override val kind get() = JollyKind.SAY
    }

    /** An app of the car's, by the id a pin knows it by (Entry.componentId); [name] for when it's gone. */
    data class OpenApp(val id: String, val name: String) : JollyAction {
        override val kind get() = JollyKind.APP
    }
}

/**
 * The Jolly key's command as saved, and back. Anything this version doesn't know - a kind or a
 * language added later, a field missing, a Home Assistant action that doesn't fit its thing, a
 * broken escape - reads as nothing chosen: the key then asks again, rather than doing something else.
 */
internal object JollyActions {

    fun saved(action: JollyAction): String = when (action) {
        is JollyAction.Call -> line("call", "name" to action.name, "number" to action.number, "label" to action.label)
        is JollyAction.Assistant -> line("ai", "app" to action.app)
        is JollyAction.Home -> line("home", "entity" to action.entity, "act" to action.act.id, "name" to action.name)
        is JollyAction.Navigate -> line("navigate", "place" to action.place, "name" to action.name)
        is JollyAction.Say -> line("say", "phrase" to action.phrase, "lang" to action.lang)
        is JollyAction.OpenApp -> line("app", "id" to action.id, "name" to action.name)
    }

    fun parse(saved: String): JollyAction? {
        if (saved.isBlank()) return null
        val fields = HashMap<String, String>()
        for (pair in saved.split('&')) {
            val at = pair.indexOf('=')
            if (at <= 0) continue
            // The first of a field written twice; one whose escape is broken spoils the whole line.
            fields.putIfAbsent(pair.substring(0, at), decode(pair.substring(at + 1)) ?: return null)
        }
        fun need(key: String): String? = fields[key]?.trim()?.takeIf { it.isNotEmpty() }
        fun may(key: String): String = fields[key]?.trim().orEmpty()
        return when (fields["type"]) {
            "call" -> JollyAction.Call(
                need("name") ?: return null,
                need("number")?.takeIf { number -> number.any(Char::isDigit) } ?: return null,
                may("label"),
            )
            "ai" -> JollyAction.Assistant(need("app") ?: return null)
            "home" -> {
                val entity = need("entity") ?: return null
                val act = HomeAct.entries.firstOrNull { it.id == fields["act"] } ?: return null
                if (HomeActs.service(entity, act) == null) return null
                JollyAction.Home(entity, act, need("name") ?: entity)
            }
            "navigate" -> JollyAction.Navigate(need("place") ?: return null, may("name"))
            "say" -> JollyAction.Say(
                need("phrase") ?: return null,
                need("lang")?.takeIf { lang -> VoiceLanguage.entries.any { it.code == lang } } ?: return null,
            )
            "app" -> JollyAction.OpenApp(need("id") ?: return null, may("name"))
            else -> null
        }
    }

    private fun line(type: String, vararg fields: Pair<String, String>): String =
        (listOf("type" to type) + fields).joinToString("&") { (key, value) -> "$key=${encode(value)}" }

    /** What would end a field or a pair, and lines, as %XX: everything else as it is, in any script. */
    private fun encode(text: String): String = buildString {
        for (c in text) {
            if (c == '%' || c == '&' || c == '=' || c < ' ') {
                append('%').append(HEX[c.code shr 4]).append(HEX[c.code and 15])
            } else {
                append(c)
            }
        }
    }

    /** [encode] undone; null for a "%" not followed by two hex digits ("%+1" isn't: toIntOrNull would take it). */
    private fun decode(text: String): String? {
        if ('%' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '%') {
                out.append(c)
                i++
                continue
            }
            if (i + 3 > text.length) return null
            val high = Character.digit(text[i + 1], 16)
            val low = Character.digit(text[i + 2], 16)
            if (high < 0 || low < 0) return null
            out.append((high * 16 + low).toChar())
            i += 3
        }
        return out.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}

/** What the Jolly key does to a thing in Home Assistant: what fits depends on what the thing is ([HomeActs.of]). */
internal enum class HomeAct(val id: String) {
    /** A script or a scene: started. */
    RUN("run"),
    /** A button: pressed. */
    PRESS("press"),
    /** A switch, a light, a gate or a garage door: turned to the other state. */
    TOGGLE("toggle"),
    LOCK("lock"),
    /** A lock opened: only after a second tap on the key ([HomeActs.confirms]). */
    UNLOCK("unlock"),
}

/**
 * Which things of Home Assistant's the Jolly key offers, what it does with each and the service that
 * does it. Alarms aren't offered: disarming one takes its code, and arming one by mistake in the car
 * is no good either.
 */
internal object HomeActs {
    /** The domains offered, in the order the lists show them. */
    val DOMAINS = listOf("script", "scene", "button", "input_button", "switch", "input_boolean", "light", "cover", "lock")

    fun domain(entity: String): String = entity.substringBefore('.', "")

    /** What can be done with [entity], in the order offered: a lock both ways. */
    fun of(entity: String): List<HomeAct> = when (domain(entity)) {
        "script", "scene" -> listOf(HomeAct.RUN)
        "button", "input_button" -> listOf(HomeAct.PRESS)
        "switch", "input_boolean", "light", "cover" -> listOf(HomeAct.TOGGLE)
        "lock" -> listOf(HomeAct.LOCK, HomeAct.UNLOCK)
        else -> emptyList()
    }

    /** The service Home Assistant runs for [act] on [entity], as domain and service; null where it doesn't fit. */
    fun service(entity: String, act: HomeAct): Pair<String, String>? {
        if (!ENTITY.matches(entity) || act !in of(entity)) return null
        return domain(entity) to when (act) {
            HomeAct.RUN -> "turn_on"
            HomeAct.PRESS -> "press"
            HomeAct.TOGGLE -> "toggle"
            HomeAct.LOCK -> "lock"
            HomeAct.UNLOCK -> "unlock"
        }
    }

    /**
     * Opening a lock waits for a second tap on the key. A gate or a garage door (a cover, toggled) lets
     * someone in too, and opens with one tap on purpose: that's what the driver arriving home has the
     * key for.
     */
    fun confirms(act: HomeAct): Boolean = act == HomeAct.UNLOCK

    /** One line of the Jolly's list of Home Assistant's things. */
    data class Choice(val entity: String, val act: HomeAct, val name: String)

    /**
     * The list from Home Assistant's states ([states]: entity id to its friendly name, as get_states
     * gives them): the things offered, scripts first and by name within a domain, a lock twice.
     */
    fun choices(states: List<Pair<String, String>>): List<Choice> =
        states.filter { (entity, _) -> ENTITY.matches(entity) && domain(entity) in DOMAINS }
            .distinctBy { it.first }
            .sortedWith(compareBy<Pair<String, String>>({ DOMAINS.indexOf(domain(it.first)) }, { it.second.ifBlank { it.first }.lowercase() }))
            .flatMap { (entity, name) -> of(entity).map { Choice(entity, it, name.ifBlank { entity }) } }

    /** An entity id as Home Assistant writes one: "domain.object_id", lower case, digits and underscores. */
    private val ENTITY = Regex("[a-z0-9_]+\\.[a-z0-9_]+")
}

/**
 * What Home Assistant's word on a thing means for the Jolly key: before its command, and after a lock's.
 * Home Assistant answers "done" to a command even for a thing it doesn't have or can't reach (it skips
 * it without a word), so the key looks at the thing's state rather than at that answer.
 */
internal object HomeChecks {
    enum class Before { GO, MISSING, UNAVAILABLE }

    /**
     * [state] before the command; null where Home Assistant doesn't have the thing. "unavailable": the
     * device can't be reached, and the command would be skipped. "unknown" goes ahead: a scene or a
     * button never used yet, or a gate without a sensor, which still do what they're told.
     */
    fun before(state: String?): Before = when (state) {
        null -> Before.MISSING
        "unavailable" -> Before.UNAVAILABLE
        else -> Before.GO
    }

    enum class After { DONE, JAMMED, STILL_LOCKED, STILL_UNLOCKED, NO_NEWS }

    /** A lock's [state] after [act]: done, jammed, still as it was, or not there yet (on its way, or silent). */
    fun after(act: HomeAct, state: String?): After = when {
        state == "jammed" -> After.JAMMED
        act == HomeAct.UNLOCK && state in OPEN -> After.DONE
        act == HomeAct.LOCK && state == "locked" -> After.DONE
        act == HomeAct.UNLOCK && state == "locked" -> After.STILL_LOCKED
        act == HomeAct.LOCK && state in OPEN -> After.STILL_UNLOCKED
        else -> After.NO_NEWS
    }

    /** Whether the wait for a lock is over: it did it, or it's jammed. */
    fun settled(act: HomeAct, state: String?): Boolean = after(act, state).let { it == After.DONE || it == After.JAMMED }

    /** A lock that's open: unlocked, or with its latch open too where it can. */
    private val OPEN = setOf("unlocked", "open")
}

/**
 * The second tap a command that lets someone in waits for (see [HomeActs.confirms]): the first tap
 * arms it, a second between [minMs] and [windowMs] later goes ahead. One sooner is a double tap made
 * before the question could be read, or a finger that bounced: it's let go, and the question stays.
 * A tap after the window arms it afresh.
 */
internal class SecondTap(private val windowMs: Long = 3_000, private val minMs: Long = 700) {
    enum class Tap { ARMED, TOO_SOON, GO }

    private var armedAt: Long? = null

    /** What this tap at [now] (uptime ms) does: arms, goes ahead, or nothing (too soon after the first). */
    fun tap(now: Long): Tap {
        val at = armedAt
        if (at != null && now - at in 0 until minMs) return Tap.TOO_SOON
        if (at != null && now - at in minMs..windowMs) {
            armedAt = null
            return Tap.GO
        }
        armedAt = now
        return Tap.ARMED
    }

    /** Whether a tap at [now] would go ahead. */
    fun armed(now: Long): Boolean = armedAt?.let { now - it in 0..windowMs } == true

    fun disarm() {
        armedAt = null
    }
}

/**
 * One Home Assistant command of the Jolly key's at a time, and not again at once: a tap while it runs,
 * or within [quietMs] after, is the same press made twice (a garage door toggled twice opens and then
 * stops halfway). Keyed by what the command does, so another one isn't held up.
 */
internal class RepeatGuard(private val quietMs: Long = 1_000) {
    private var running: String? = null
    private var last: String? = null
    private var endedAt = 0L

    /** Whether a tap at [now] (uptime ms) for [key] is that same press again. */
    fun busy(key: String, now: Long): Boolean = running == key || (last == key && now - endedAt in 0 until quietMs)

    /** [key] starts at [now], unless [busy]: then false. */
    fun start(key: String, now: Long): Boolean {
        if (busy(key, now)) return false
        running = key
        return true
    }

    /** [key] has finished at [now]: quiet for a moment more. */
    fun end(key: String, now: Long) {
        if (running == key) running = null
        last = key
        endedAt = now
    }
}
