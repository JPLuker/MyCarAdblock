package io.github.sirionero.mycar.cinema

/** Browser-independent rules for applying Firefox add-on state without reloading a page. */
enum class CinemaAdBlockAction {
    /** The add-on operation is in flight or no preference has been supplied. */
    WAIT,
    /** Enable/disable the persistent add-on before first navigation. */
    SYNCHRONIZE,
    /** Extension state matches the user's setting: first navigation may begin. */
    NAVIGATE,
    /** An already-open page needs neither navigation nor a new add-on operation. */
    IDLE,
}

object CinemaAdBlockTransitionPolicy {
    fun next(
        desired: Boolean?,
        applied: Boolean?,
        operationPending: Boolean,
        pageOpened: Boolean,
    ): CinemaAdBlockAction = when {
        desired == null || operationPending -> CinemaAdBlockAction.WAIT
        applied != desired -> CinemaAdBlockAction.SYNCHRONIZE
        !pageOpened -> CinemaAdBlockAction.NAVIGATE
        else -> CinemaAdBlockAction.IDLE
    }
}
