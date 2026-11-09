package io.github.sirionero.mycar.audio

import android.content.Context

/**
 * The cars that gave their screen only once the music channel was open ([io.github.sirionero.mycar.HeldScreen]):
 * at their next drive the channel opens at once, rather than after 5 s of black screen. Kept for
 * each car as its audio route is ([CarRoutes]).
 */
internal object HeldScreenCars {

    private const val PREFS = "mycar_held_screen"

    @Volatile private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** The drive's car is one of them. */
    fun known(): Boolean {
        val car = CarRoutes.car ?: return false
        return prefs()?.getBoolean(car, false) == true
    }

    /** The drive's car is one of them from now on. */
    fun remember() {
        val car = CarRoutes.car ?: return
        prefs()?.edit()?.putBoolean(car, true)?.apply()
    }

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
