package io.github.sirionero.mycar.home

import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** What Home Assistant says of one thing: nothing yet, that it doesn't have it, or its state. */
internal sealed interface Seen {
    data object Waiting : Seen
    data object Missing : Seen
    data class Is(val state: String) : Seen
}

/** The state Home Assistant gave, or null (nothing yet, or the thing isn't there). */
internal val Seen.stateOrNull: String? get() = (this as? Seen.Is)?.state

/**
 * [entity]'s state, kept up to date while [block] runs: subscribe_entities for that one id, so the
 * answer is a line about one thing, not the whole house (get_states can be megabytes). Null when
 * Home Assistant refuses the subscription (a version from before 2022, without entity_ids).
 */
internal suspend fun <T> HaClient.watchEntity(entity: String, block: suspend (StateFlow<Seen>) -> T): T? {
    val seen = MutableStateFlow<Seen>(Seen.Waiting)
    val id = try {
        stream("subscribe_entities", { event -> seen.value = seenAfter(seen.value, event, entity) }) {
            put("entity_ids", JSONArray().put(entity))
        }
    } catch (e: HaException) {
        // Refused (a command's error, not a connection that's gone): an old Home Assistant.
        if (e.reason != HaException.Reason.OTHER) throw e
        MyCarLog.w("plancia: Home Assistant won't say one thing's state (${e.code ?: "refused"})")
        return null
    }
    try {
        return block(seen.asStateFlow())
    } finally {
        forget(id)
        // Not long: what came of the command is said after this.
        withContext(NonCancellable) {
            withTimeoutOrNull(UNSUBSCRIBE_MS) { runCatching { command("unsubscribe_events") { put("subscription", id) } } }
        }
    }
}

/**
 * [event] of subscribe_entities on top of [before]: "a" adds (the first one, sent at once, lists all
 * there is, so the thing missing from it isn't there), "c" changes ("+" has the new state, when it
 * changed), "r" removes.
 */
private fun seenAfter(before: Seen, event: JSONObject, entity: String): Seen {
    event.optJSONObject("a")?.let { added ->
        val state = added.optJSONObject(entity)?.str("s")
        return when {
            state != null -> Seen.Is(state)
            before == Seen.Waiting -> Seen.Missing
            else -> before
        }
    }
    event.optJSONObject("c")?.optJSONObject(entity)?.optJSONObject("+")?.str("s")?.let { return Seen.Is(it) }
    event.optJSONArray("r")?.let { removed ->
        for (i in 0 until removed.length()) if (removed.optString(i) == entity) return Seen.Missing
    }
    return before
}

private const val UNSUBSCRIBE_MS = 2_000L
