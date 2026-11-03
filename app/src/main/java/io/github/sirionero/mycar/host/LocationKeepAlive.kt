package io.github.sirionero.mycar.host

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarAppService
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionService
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.mirror.TouchRelayService
import io.github.sirionero.mycar.wireless.WirelessLink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lets a hosted navigation app keep receiving the phone's location while myCar is not on screen.
 *
 * Android only gives an app location in the background if something visible to the user is
 * running for it. A nav app bound as a car app has nothing of its own showing, so it goes
 * without a fix as soon as the phone screen leaves it. Binding with BIND_INCLUDE_CAPABILITIES
 * lends it this service's location capability (recomputed as it changes, not only at bind time),
 * which is why this foreground service exists. It needs the ordinary location permission.
 *
 * Android lets it lend only if it was started from an eligible state: myCar on screen, within
 * 10 s of one of myCar's screens being created (the car's arrival creates one), or with myCar's
 * accessibility service on. Once started that way it lends for as long as it runs, phone locked
 * or not - so the drive takes it as the car arrives ([holdForDrive]), not when the map binds.
 */
class LocationKeepAlive : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, Words.get(R.string.car_host_location_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(Words.get(R.string.car_host_location_notification))
            .setOngoing(true)
            .build()
        // The system can refuse a location-type foreground service if myCar is not in an eligible
        // state by the time this runs - the phone locked, myCar's screen created more than 10 s ago.
        // That refusal throws, and left uncaught it takes the whole app down with it.
        runCatching {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }.onSuccess {
            MyCarLog.i("location keep-alive: running")
            settle(State.ON)
            // Every hold went while it was starting: stop now, as it may only once in the foreground.
            main.post {
                if (holders == 0) {
                    stopSelf()
                    state = State.OFF
                }
            }
        }.onFailure {
            MyCarLog.w("location keep-alive: could not start the foreground service (${phoneState(this)}) - ${it.message}")
            refused(NoPosition.LOCKED)
            settle(State.FAILED)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (state == State.ON) state = State.OFF
        super.onDestroy()
    }

    enum class State { OFF, STARTING, ON, FAILED }

    /** Why a car map has no position to borrow, for the car to say (see BluetoothPairOverlay). */
    enum class NoPosition { NONE, LOCKED, NO_PERMISSION }

    companion object {
        private const val CHANNEL = "mycar_location"
        private const val NOTIFICATION_ID = 44

        /** How often a start-up's hold checks whether that start-up is still going on. */
        private const val START_UP_CHECK_MS = 60_000L

        /**
         * How long the drive's hold stays once no drive, start-up or listening for the car is going on
         * (see [startUpCheck]): a car back within it finds the service running.
         */
        private const val AFTER_DRIVE_MS = 5 * 60_000L

        /** Public flag value; the constant itself is hidden from the SDK. */
        const val BIND_INCLUDE_CAPABILITIES = 0x1000

        private val main = Handler(Looper.getMainLooper())

        @Volatile var state = State.OFF
            private set

        /** Each connected car app holds one, and the drive one more; the service stops only when the last lets go. */
        private var holders = 0
        private val waiting = mutableListOf<() -> Unit>()

        /** The drive's own hold: see [holdForDrive]. */
        private var driveHold = false
        /** [driveHold] taken as the car's Bluetooth (or a tap) started a start-up, before SessionService holds it. */
        private var startUpOnly = false
        /** When a drive, a start-up or the listening after a drive was last seen going on (elapsedRealtime). */
        private var lastNeeded = 0L
        private var appContext: Context? = null
        /** The driver said OK to the car's "no position" card this drive: it doesn't come back. */
        private var told = false

        private val _noPosition = MutableStateFlow(NoPosition.NONE)

        /**
         * A car app holds the service and Android refused it (the phone locked, myCar not on screen),
         * or myCar has no location permission: the car's map has no position. For the car to say so
         * once it has stopped. NONE once the service runs, the driver says OK, or no one holds it.
         */
        val noPosition: StateFlow<NoPosition> = _noPosition.asStateFlow()

        /** The car's card answered: not shown again this drive. */
        fun dismissNoPosition() {
            main.post {
                told = true
                _noPosition.value = NoPosition.NONE
            }
        }

        /**
         * The drive's hold, once no drive holds it (a start-up's, or a drive's that ended), goes only
         * when nothing has gone on for [AFTER_DRIVE_MS]: no drive, no start-up, no start-over and no
         * listening for the car after its drive. Android lets this service start with the phone locked
         * only within 10 s of one of myCar's screens being created, so a hold given back between two
         * sessions of the same trip was never had again: in 0.7.0 it went 1 s after each session the
         * car's Wi-Fi dropped (the listening after a drive didn't count, so as not to keep the
         * notification for its 10 minutes), the car came back 4 s later, the start was refused, and
         * the map had no position - black - for the rest of the trip.
         */
        private val startUpCheck = object : Runnable {
            override fun run() {
                if (!driveHold || !startUpOnly) return
                val now = SystemClock.elapsedRealtime()
                if (startUpGoesOn()) lastNeeded = now
                if (now - lastNeeded < AFTER_DRIVE_MS) {
                    main.postDelayed(this, START_UP_CHECK_MS)
                    return
                }
                MyCarLog.i("location keep-alive: no drive or start-up going on for ${AFTER_DRIVE_MS / 60_000} minutes - letting go")
                startUpOnly = false
                driveHold = false
                appContext?.let { release(it) }
            }
        }

        /**
         * Takes a hold on the service, starting it if it isn't running, and calls [ready] (on the
         * main thread) once it's running, has failed, or [timeoutMs] has passed - whichever comes
         * first. A nav app bound before this has settled has no location capability to borrow,
         * and some (MapQuest) crash outright when their own location service is refused.
         */
        fun acquire(context: Context, timeoutMs: Long = 3_000, ready: () -> Unit) {
            main.post {
                holders++
                if (state == State.OFF || state == State.FAILED) begin(context.applicationContext)
                if (state != State.STARTING) {
                    ready()
                    return@post
                }
                var done = false
                val once = { if (!done) { done = true; ready() } }
                waiting += once
                main.postDelayed({
                    if (!done) MyCarLog.w("location keep-alive: still not running after ${timeoutMs}ms, binding anyway")
                    waiting.remove(once)
                    once()
                }, timeoutMs)
            }
        }

        /** Lets go of one hold; the last one stops the service. */
        fun release(context: Context) {
            main.post {
                holders = (holders - 1).coerceAtLeast(0)
                if (holders > 0) return@post
                // No one holds it: the drive is over, and what the car was told with it.
                _noPosition.value = NoPosition.NONE
                told = false
                // Stopped before it calls startForeground, Android takes myCar down with it: it
                // stops itself once started (see onStartCommand).
                if (state == State.STARTING) return@post
                context.stopService(Intent(context, LocationKeepAlive::class.java))
                state = State.OFF
            }
        }

        /**
         * The drive's hold, taken as the car arrives: from [SessionService.start] (the car's
         * Bluetooth channel, the cable, the car's picture) and, earlier still, as a start-up begins
         * ([untilStartUpEnds]: the car's Bluetooth opening myCar, a tap on a car) - given
         * back by itself if that start-up never reaches a drive. Android lets this service lend its
         * location with the phone locked only if started within 10 s of myCar's screen being
         * created: taken once the map bound (14 s later through a slow Wi-Fi) it was refused, and
         * the map had no position until myCar was opened. Every call retries a refused start.
         */
        fun holdForDrive(context: Context, untilStartUpEnds: Boolean = false) {
            val app = context.applicationContext
            main.post {
                appContext = app
                if (!driveHold) {
                    // No car map on the phone: no service, and no notification, for nothing.
                    if (!carMapInstalled(app)) return@post
                    driveHold = true
                    told = false
                    holders++
                    startUpOnly = untilStartUpEnds
                    lastNeeded = SystemClock.elapsedRealtime()
                    if (startUpOnly) main.postDelayed(startUpCheck, START_UP_CHECK_MS)
                } else if (!untilStartUpEnds && startUpOnly) {
                    // The drive's service holds it from here: given back with SessionService.stop.
                    startUpOnly = false
                    main.removeCallbacks(startUpCheck)
                }
                if (state == State.OFF || state == State.FAILED) begin(app)
            }
        }

        /**
         * No one holds the drive's service any more: the drive's hold becomes a start-up's, kept while a
         * wireless start-up, a start-over or the listening after the drive goes on, and for
         * [AFTER_DRIVE_MS] after (see [startUpCheck]). Given back, it was taken anew from the
         * background, past Android's 10 s, and refused: the map had no position for the next session.
         */
        fun releaseDrive(context: Context) {
            val app = context.applicationContext
            main.post {
                // Taken again meanwhile: a start-up's end on WirelessLink's thread racing a drive's start.
                if (!driveHold || SessionService.held) return@post
                appContext = app
                startUpOnly = true
                lastNeeded = SystemClock.elapsedRealtime()
                main.removeCallbacks(startUpCheck)
                main.postDelayed(startUpCheck, START_UP_CHECK_MS)
            }
        }

        /**
         * The driver tapped Disconnect: the drive's hold goes now, without waiting for the car to come
         * back. A drive still holding the service keeps it (its end gives it back).
         */
        fun letGo(context: Context) {
            val app = context.applicationContext
            main.post {
                if (!driveHold || SessionService.held) return@post
                MyCarLog.i("location keep-alive: disconnected by the driver - letting go")
                main.removeCallbacks(startUpCheck)
                startUpOnly = false
                driveHold = false
                release(app)
            }
        }

        /**
         * A wireless start-up still going, the listening after a drive among them (a car whose session
         * dropped comes back through it), a start-up about to start over, or the drive's service held.
         */
        private fun startUpGoesOn(): Boolean =
            WirelessLink.busy || WirelessLink.startingOver || SessionService.held

        /**
         * Tries again after a refusal, while something holds the service: myCar on screen, or its
         * accessibility service just switched on, makes Android let it start.
         *
         * On Android 12 and 13 a start from the background isn't refused: the service runs, with no
         * location to lend, and nothing shows it. Started once more from here, Android gives the
         * running service what it lends for the rest of the drive.
         */
        fun retry(context: Context, why: String = "myCar is on screen") {
            main.post {
                if (holders > 0 && state == State.FAILED) {
                    MyCarLog.i("location keep-alive: $why, trying again")
                    begin(context.applicationContext)
                } else if (holders > 0 && state == State.ON && Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val app = context.applicationContext
                    runCatching { app.startForegroundService(Intent(app, LocationKeepAlive::class.java)) }
                        .onFailure { MyCarLog.w("location keep-alive: $why, but couldn't start it again - ${it.message}") }
                }
            }
        }

        private fun begin(context: Context) {
            // Approximate location is enough for the service to lend: the map asks for what it needs.
            val granted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                MyCarLog.w("location keep-alive: no location permission, car maps won't get a fix in the background")
                refused(NoPosition.NO_PERMISSION)
                settle(State.FAILED)
                return
            }
            state = State.STARTING
            runCatching { context.startForegroundService(Intent(context, LocationKeepAlive::class.java)) }
                .onFailure {
                    MyCarLog.w("location keep-alive: start refused (${phoneState(context)}) - ${it.message}")
                    refused(NoPosition.LOCKED)
                    settle(State.FAILED)
                }
        }

        /** For the log of a refusal: what Android weighs, and what the next trip's log should tell apart. */
        private fun phoneState(context: Context): String {
            val locked = runCatching { context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked }.getOrNull()
            return "phone locked: $locked, myCar's accessibility: ${if (TouchRelayService.enabled) "on" else "off"}"
        }

        /** A car app (a map) holding the service and getting nothing to borrow: the car says why. */
        private fun refused(why: NoPosition) {
            main.post {
                val carApps = holders - (if (driveHold) 1 else 0)
                if (carApps > 0 && !told) _noPosition.value = why
            }
        }

        /** A car navigation app on the phone (found through <queries> on CarAppService). */
        private fun carMapInstalled(context: Context): Boolean = runCatching {
            context.packageManager.queryIntentServices(
                Intent(CarAppService.SERVICE_INTERFACE).addCategory(CarAppService.CATEGORY_NAVIGATION_APP), 0,
            ).any { it.serviceInfo.packageName != context.packageName }
        }.getOrDefault(true).also { if (!it) MyCarLog.i("location keep-alive: no car map on the phone - not needed") }

        private fun settle(to: State) {
            main.post {
                state = to
                if (to == State.ON) _noPosition.value = NoPosition.NONE
                val run = waiting.toList()
                waiting.clear()
                run.forEach { it() }
            }
        }
    }
}
