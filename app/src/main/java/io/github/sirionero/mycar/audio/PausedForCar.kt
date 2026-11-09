package io.github.sirionero.mycar.audio

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
}
