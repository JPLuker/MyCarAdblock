package io.github.sirionero.mycar.cinema

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Permission request from a user-selected Firefox XPI. The UI must show the actual
 * extension ID, requested permissions and origins, and require an affirmative tap.
 * Unknown extensions cannot get the permissive silent install path used by our two
 * explicit, official AMO add-ons.
 *
 * Calls are made on Android's main thread. The pending approval callback never
 * persists across a process restart; silence means DENY, not automatic approval.
 */
object CinemaExtensionConsent {
    data class Request(
        val id: String,
        val permissions: List<String>,
        val origins: List<String>,
        val dataCollectionPermissions: List<String>,
    )

    private val pendingFlow = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = pendingFlow.asStateFlow()
    private var reply: ((Boolean) -> Unit)? = null

    fun offer(
        id: String,
        permissions: Array<String>,
        origins: Array<String>,
        dataCollectionPermissions: Array<String>,
        respond: (Boolean) -> Unit,
    ): Boolean {
        if (reply != null) return false
        pendingFlow.value = Request(
            id = id.take(200),
            permissions = permissions.take(50).map { it.take(200) },
            origins = origins.take(50).map { it.take(300) },
            dataCollectionPermissions = dataCollectionPermissions.take(50).map { it.take(200) },
        )
        reply = respond
        return true
    }

    fun decide(allow: Boolean) {
        val answer = reply
        reply = null
        pendingFlow.value = null
        answer?.invoke(allow)
    }
}
