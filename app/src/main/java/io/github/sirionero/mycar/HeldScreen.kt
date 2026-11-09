package io.github.sirionero.mycar

/**
 * A car that keeps its own screen while the phone's music channel is closed. myCar keeps that
 * channel closed while the music goes over Bluetooth (CarAudio.openSink): many cars switch their
 * source to Android Auto the moment it opens. But some older head units don't show the phone's
 * screen until it's open: a 2024 Mirage, black since Gearslip 0.3.12 closed it, showed it again
 * with it opened 5 s after asking (Gearslip 0.3.39, issue #49). One session's part, without
 * Android: MyCarRunner asks for the screen, waits [WAIT_MS], and tells this what happened.
 */
internal class HeldScreen(
    /** At an earlier drive this car gave its screen only once the channel was open (HeldScreenCars). */
    private val known: Boolean,
) {
    /** When the channel was opened here for the screen, on the session's clock; null until then. */
    @Volatile private var openedAt: Long? = null

    /** At the drive's start, with the channel held closed: opened at once for a car known to need it. */
    fun openAtOnce(): Boolean = known

    /**
     * [WAIT_MS] after asking for the screen, at [now]: whether to open the held channel and ask again.
     * Only with the session still on, no screen yet and the channel still held; once a session.
     */
    fun openNow(sessionOn: Boolean, screenCame: Boolean, channelHeld: Boolean, now: Long): Boolean {
        if (openedAt != null || !sessionOn || screenCame || !channelHeld) return false
        openedAt = now
        return true
    }

    /**
     * The screen came, at [now]: whether to remember the car - it came only once the channel was
     * opened for it, and soon after ([REMEMBER_WITHIN_MS]). Later, it's the driver who chose the
     * phone's screen (a car on its own menus when the phone came): the channel wasn't the reason,
     * and a car remembered for nothing would get it open at every drive, music over Bluetooth or not.
     */
    fun rememberCar(now: Long): Boolean {
        val at = openedAt ?: return false
        return !known && now - at <= REMEMBER_WITHIN_MS
    }

    companion object {
        /** How long a car gets to give its screen with the channel closed: the user's cars give it in half a second. */
        const val WAIT_MS = 5_000L

        /** Between opening the channel and asking again: the car's answer to the opening comes first. */
        const val ASK_AGAIN_MS = 1_000L

        /** From opening the channel to the screen, for the opening to count as its reason: the second ask, and a few seconds. */
        const val REMEMBER_WITHIN_MS = 5_000L
    }
}
