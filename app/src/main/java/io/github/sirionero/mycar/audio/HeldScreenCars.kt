package io.github.sirionero.mycar.audio

import android.content.Context
import io.github.sirionero.mycar.HeldScreen

/**
 * The cars that gave their screen only once the music channel was open ([HeldScreen]), at
 * [HeldScreen.TIMES] drives in a row: at their next drive the channel opens at once, rather than after
 * 5 s of black screen. Counted for each car as its audio route is kept ([CarRoutes]).
 */
internal object HeldScreenCars {

    private const val PREFS = "mycar_held_screen"

    @Volatile private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** The drive's car is one of them. */
    fun known(): Boolean = HeldScreen.knownAfter(times())

    /** The drive's car gave its screen only once the channel was open, once more; true when that makes it one of them. */
    fun neededAgain(): Boolean {
        val car = CarRoutes.car ?: return false
        val times = times() + 1
        prefs()?.edit()?.putInt(car, times)?.apply()
        return HeldScreen.knownAfter(times)
    }

    /** The drive's car gave its screen with the channel closed: the drives counted start over. True if there were any. */
    fun notNeeded(): Boolean {
        val car = CarRoutes.car ?: return false
        if (times() == 0) return false
        prefs()?.edit()?.remove(car)?.apply()
        return true
    }

    /** Drives in a row the drive's car gave its screen only once the channel was open. */
    private fun times(): Int {
        val car = CarRoutes.car ?: return 0
        return runCatching { prefs()?.getInt(car, 0) ?: 0 }.getOrDefault(0)
    }

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
