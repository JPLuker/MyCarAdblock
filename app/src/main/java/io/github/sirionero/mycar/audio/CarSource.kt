package io.github.sirionero.mycar.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.sirionero.mycar.MyCarLog

/**
 * Whether a car's last drive ended on the car's own source: the driver picked its radio (the car
 * took its speakers back for good, a LOSS) and the phone's music didn't come back to the car for a
 * minute after. Kept for each car, as its audio route is ([CarRoutes]): with the car's radio kept
 * until the phone plays (CarSettings.radioFirst), the next drive's Autoplay leaves the radio on, as a
 * car radio comes back on the source it was left on. A LOSS less than a minute before the drive
 * ends is the car switching off, not a choice. Forgotten as soon as the phone's music reaches the
 * car again.
 */
internal object CarSource {

    private const val PREFS = "mycar_car_source"

    /** How long the phone's music stays away after the LOSS for the drive to count as ended on the radio. */
    private const val SETTLE_MS = 60_000L

    @Volatile private var app: Context? = null
    private val main = Handler(Looper.getMainLooper())

    /** The car whose LOSS waits out [SETTLE_MS]; null when none does. */
    @Volatile private var lostIn: String? = null

    private val settled = Runnable {
        val car = lostIn ?: return@Runnable
        lostIn = null
        // Another car since (the drive ended and a new one began): this one's LOSS didn't settle.
        if (car != CarRoutes.car) return@Runnable
        prefs()?.edit()?.putBoolean(car, true)?.apply()
        MyCarLog.i("audio: a minute on the car's own source - remembered for the next drive's Autoplay")
    }

    fun init(context: Context) {
        app = context.applicationContext
        // Read from disk now, not first on the capture's thread (musicBack).
        prefs()
    }

    /** The car took its speakers back for good (the driver picked its radio). From the session's thread. */
    fun takenBack() {
        val car = CarRoutes.car ?: return
        lostIn = car
        main.removeCallbacks(settled)
        main.postDelayed(settled, SETTLE_MS)
    }

    /** The phone's music reached the car: a LOSS before it doesn't count, and the car's mark goes. From the audio's threads. */
    fun musicBack() {
        if (lostIn != null) {
            lostIn = null
            main.removeCallbacks(settled)
        }
        val car = CarRoutes.car ?: return
        val prefs = prefs() ?: return
        if (!prefs.getBoolean(car, false)) return
        prefs.edit().remove(car).apply()
        MyCarLog.i("audio: the phone's music is back in this car - Autoplay plays again from the next drive")
    }

    /** The drive's music channel went: a LOSS less than a minute old was the car switching off. */
    fun driveEnded() {
        lostIn = null
        main.removeCallbacks(settled)
    }

    /** The drive's car ended its last drive on its own source. */
    fun lastDriveOnCarSource(): Boolean {
        val car = CarRoutes.car ?: return false
        return prefs()?.getBoolean(car, false) == true
    }

    private fun prefs() = app?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
