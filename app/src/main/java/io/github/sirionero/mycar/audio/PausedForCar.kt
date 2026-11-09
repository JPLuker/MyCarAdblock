package io.github.sirionero.mycar.audio

import java.util.concurrent.atomic.AtomicReference

/**
 * The phone's music paused while the car had its speakers for a moment (AudioLink: LOSS_TRANSIENT),
 * and what becomes of it at the car's GAIN (CarServices.resumePhoneMedia), without Android: played
 * again; later, while a call goes on (Android has the phone's sound then, and a car may give its
 * speakers back before the call's end); or not at all - nothing was paused, the driver asked the
 * music for something meanwhile (a pause, a song, another app: theirs), or a call that won't end.
 */
internal object PausedForCar {

    enum class Then { PLAY, WAIT, DROP }

    /** How often a call still on is looked at again, and how many times at most: after half an hour the music stays paused. */
    const val CALL_CHECK_MS = 2_000L
    const val CALL_CHECKS = 900

    /**
     * At the car's GAIN, and again every [CALL_CHECK_MS] while [waitsLeft]: [paused] - something is
     * still remembered as paused for the car (forgotten at the drive's end, the radio, the car leaving).
     */
    fun atGain(paused: Boolean, driverAsked: Boolean, inCall: Boolean, waitsLeft: Int): Then = when {
        !paused || driverAsked -> Then.DROP
        inCall -> if (waitsLeft > 0) Then.WAIT else Then.DROP
        else -> Then.PLAY
    }

    /**
     * What the car's moments paused: [players] (the phone's media sessions), [own] when it was the
     * music app myCar drives, paused through it (no notification access), and where the driver's asks
     * of the music ([askMark]) and the car's music app ([app]) stood then.
     */
    class Paused<P>(val players: List<P>, val own: Boolean, val askMark: Int?, val app: Any?) {
        /** The car's GAIN came for it ([Memory.given]): it plays again, after a call's end if one is on. */
        @Volatile var given = false
    }

    /**
     * What waits for the car's GAIN, from its LOSS_TRANSIENT (the session's reading thread) to the
     * music playing again (the main thread, after a call's end if one is on). Players told apart by [key].
     */
    class Memory<P>(private val key: (P) -> Any?) {
        private val current = AtomicReference<Paused<P>?>(null)

        /**
         * The car took its speakers for a moment, and [players] were paused for it ([own]: myCar's own
         * music app). The car can take them again before what its last moment paused has played again:
         * its GAIN came during a call, or a moment ago, the main thread not there yet. That still waits,
         * with this moment's, for this one's GAIN; it used to be forgotten, nothing playing now, and the
         * music stayed paused after the call. A moment without its GAIN is from a stream stopped since
         * (AudioLink.stop: no GAIN comes for it): it plays nothing again, as before.
         */
        fun paused(players: List<P>, own: Boolean, askMark: Int?, app: Any?) {
            val before = current.get()?.takeIf { it.given }
            current.set(
                when {
                    before != null -> Paused((before.players + players).distinctBy(key), before.own || own, before.askMark, before.app)
                    players.isEmpty() && !own -> null
                    else -> Paused(players, own, askMark, app)
                },
            )
        }

        /** The car's GAIN: what waits to play again, from now on [Paused.given]; null when nothing does. */
        fun given(): Paused<P>? = current.get()?.also { it.given = true }

        /** Whether [paused] still waits to play again: not forgotten, nor taken up by a newer moment of the car's. */
        fun waits(paused: Paused<P>): Boolean = current.get() === paused

        /** [paused] is done with, played again or dropped; false if a newer moment took its place meanwhile. */
        fun done(paused: Paused<P>): Boolean = current.compareAndSet(paused, null)

        /** Nothing plays again: the drive ended, the car took its own source, or it left. */
        fun forget() = current.set(null)
    }
}
