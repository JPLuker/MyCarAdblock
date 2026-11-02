package io.github.sirionero.mycar.notify

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.SystemClock

/**
 * Whether the car is moving, judged from the phone's GPS speed.
 *
 * Used only to keep notification popups off the screen while driving. With no permission or no
 * recent fix it answers "not moving", so a missing signal never hides anything for good.
 */
object CarMotion {

    private const val MOVING_MS = 2.0f          // about 7 km/h
    private const val STALE_MS = 15_000L

    private var listener: LocationListener? = null
    private var speed = 0f
    private var fixAt = 0L

    val moving: Boolean
        get() = SystemClock.elapsedRealtime() - fixAt < STALE_MS && speed > MOVING_MS

    /**
     * For video (CarDriving), the safe way round: the last speed holds until a new fix says the car
     * stopped. With no fix for a while - a tunnel, a ramp down to a garage - [moving] answers "not
     * moving", right for popups, but on a car that doesn't say itself that it moves, the film showed
     * with the car still going. At most [VIDEO_STALE_MS], or a garage kept it covered for good.
     */
    val movingForVideo: Boolean
        get() = SystemClock.elapsedRealtime() - fixAt < VIDEO_STALE_MS && speed > MOVING_MS

    private const val VIDEO_STALE_MS = 5 * 60_000L

    fun start(context: Context) {
        if (listener != null) return
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(LocationManager::class.java) ?: return
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                speed = if (location.hasSpeed()) location.speed else 0f
                fixAt = SystemClock.elapsedRealtime()
            }
        }
        listener = l
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, l) }
    }

    fun stop(context: Context) {
        listener?.let { l -> context.getSystemService(LocationManager::class.java)?.removeUpdates(l) }
        listener = null
    }
}
