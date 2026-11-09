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
    /** At earlier drives this car gave its screen only once the channel was open (HeldScreenCars). */
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

    /** What the screen's coming says of the car, for HeldScreenCars. */
    enum class Seen {
        /** It came only once the channel was opened for it, and soon after: it may need it open. */
        NEEDED,
        /** It came with the channel held closed: the car doesn't need it open. */
        NOT_NEEDED,
        /** Nothing: a car known already, the channel open anyway, or the screen much later. */
        NOTHING,
    }

    /**
     * The screen came, at [now], with the channel still [held] closed or not. Soon after the channel
     * was opened for it ([REMEMBER_WITHIN_MS]): [Seen.NEEDED]. Later, it's the driver who chose the
     * phone's screen (a car on its own menus when the phone came): the channel wasn't the reason.
     * HeldScreenCars counts a car as needing the channel only after [Seen.NEEDED] at [TIMES] drives in a
     * row, as a car known to need it gets the channel at every drive, music over Bluetooth or not: one
     * drive's slow screen (the driver on the car's menus, its system still starting) isn't for good.
     */
    fun screenCame(now: Long, held: Boolean): Seen {
        val at = openedAt
        return when {
            known -> Seen.NOTHING
            at != null -> if (now - at <= REMEMBER_WITHIN_MS) Seen.NEEDED else Seen.NOTHING
            held -> Seen.NOT_NEEDED
            else -> Seen.NOTHING
        }
    }

    companion object {
        /** How long a car gets to give its screen with the channel closed: the user's cars give it in half a second. */
        const val WAIT_MS = 5_000L

        /** Between opening the channel and asking again: the car's answer to the opening comes first. */
        const val ASK_AGAIN_MS = 1_000L

        /** From opening the channel to the screen, for the opening to count as its reason: the second ask, and a few seconds. */
        const val REMEMBER_WITHIN_MS = 5_000L

        /** Drives in a row with [Seen.NEEDED] before a car's channel opens at once; a [Seen.NOT_NEEDED] starts over. */
        const val TIMES = 2

        /** A car seen [times] drives in a row giving its screen only once the channel was open is known to need it. */
        fun knownAfter(times: Int): Boolean = times >= TIMES
    }
}
