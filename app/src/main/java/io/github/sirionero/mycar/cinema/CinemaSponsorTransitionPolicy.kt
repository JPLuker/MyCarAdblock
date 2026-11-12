package io.github.sirionero.mycar.cinema

/** SponsorBlock is optional. A failed add-on must not stop an otherwise working browser. */
object CinemaSponsorTransitionPolicy {
    fun needsSync(desired: Boolean?, applied: Boolean?, failedFor: Boolean?): Boolean =
        desired != null && desired != applied && desired != failedFor

    fun allowsNavigation(desired: Boolean?, applied: Boolean?, failedFor: Boolean?): Boolean =
        desired != null && (desired == applied || (desired && desired == failedFor))
}
