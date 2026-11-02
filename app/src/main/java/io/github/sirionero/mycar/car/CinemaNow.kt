package io.github.sirionero.mycar.car

import android.os.Handler
import android.os.Looper

/**
 * The film Cinema is playing now, for the car taking its speakers back for good (the driver picks
 * the radio): it has no media session for [CarServices.pausePhoneMedia] to reach, and it ran on
 * unseen and unheard, losing minutes, and kept the car from being asked for the speakers again.
 */
internal object CinemaNow {
    @Volatile var pause: (() -> Unit)? = null

    /** From the car's thread: the player is the main thread's. */
    fun pauseForCar() {
        Handler(Looper.getMainLooper()).post { pause?.invoke() }
    }
}
