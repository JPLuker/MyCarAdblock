package io.github.sirionero.mycar

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * How a wireless drive that reached the car's screen ended, told from what the phone saw last, for
 * the session report. It said "FAILED - Wi-Fi transport" at the end of every drive: the car
 * switched off at the journey's end as much as its Wi-Fi dropped on the road. Only the report's
 * words: the category (and what the usage note counts) stays. No Android here: SessionEndTest.
 */
internal object SessionEnd {

    /** The car's Bluetooth music at the end: connected, lost during the session, or never there. */
    enum class Music { CONNECTED, LOST, NEVER }

    /** What the car said last of how it moves (its sensor events): driving state, speed in m/s x1000, gear. */
    data class CarState(val drivingStatus: Long? = null, val speedE3: Long? = null, val gear: Long? = null) {

        /** Moving: the car restricts video, as it does while it moves, or its speed isn't 0. */
        val moving: Boolean
            get() = (drivingStatus ?: 0L) and NO_VIDEO != 0L || (speedE3 ?: 0L) != 0L

        /** "moving (driving state 27, ~30 km/h, gear 3)". */
        fun describe(): String {
            val parts = listOfNotNull(
                drivingStatus?.let { "driving state $it" },
                speedE3?.let { kmh(it) },
                gear?.let { gearName(it) },
            )
            return (if (moving) "moving" else "stopped") + if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
        }

        companion object {
            /** [before] with what the sensor event [body] carries; null if it carries none of the three. */
            fun merge(before: CarState?, body: ByteArray): CarState? {
                val fields = Wire.fields(body)
                fun reading(type: Int) = Wire.bytes(fields, type)?.let { Wire.varint(Wire.fields(it), 1) }
                val status = reading(SENSOR_DRIVING_STATUS)
                val speed = reading(SENSOR_SPEED)
                val gear = reading(SENSOR_GEAR)
                if (status == null && speed == null && gear == null) return null
                val base = before ?: CarState()
                return CarState(status ?: base.drivingStatus, speed ?: base.speedE3, gear ?: base.gear)
            }
        }
    }

    class Facts(
        /** When the session ended. All times on one clock (elapsedRealtime). */
        val endedAt: Long,
        /** The car's last ping or video ack; null if none came. */
        val lastFromCarAt: Long?,
        /** Android dropped the car's network under the session; null while it was up. */
        val wifiLostAt: Long?,
        /** The car closed its Bluetooth channel during the session; null if it didn't. */
        val channelClosedAt: Long?,
        /** myCar ended the session after this many seconds of the car's silence; null if it didn't. */
        val gaveUpAfterS: Long?,
        val music: Music,
        /** When the car's Bluetooth music went, with [Music.LOST]. */
        val musicLostAt: Long?,
        /** This drive's music goes over Bluetooth (not through myCar). */
        val musicOnBluetooth: Boolean,
        /** The car shows its own screen since then; null while it shows myCar. */
        val ownScreenSince: Long?,
        val car: CarState?,
    )

    enum class Kind {
        /** myCar ended it: the car had said nothing for long. */
        SILENT,

        /** The car's Wi-Fi went with the car moving, and no sign of it switching off. */
        DROPPED_DRIVING,

        /** The car went quiet first and its Bluetooth music or its own screen said so, or its channel and Wi-Fi went together. */
        SWITCHED_OFF,

        /** Nothing to tell: the report says what it always did. */
        OTHER,
    }

    fun kind(f: Facts): Kind {
        if (f.gaveUpAfterS != null) return Kind.SILENT
        val wifiLost = f.wifiLostAt
        val closed = f.channelClosedAt
        // The VW way: the Bluetooth channel and the Wi-Fi closed within moments, the pings up to the end.
        val together = wifiLost != null && closed != null && abs(wifiLost - closed) <= TOGETHER_MS
        val ownScreen = f.ownScreenSince != null
        // A car switching off drops its Bluetooth music, takes its screen back, or closes channel and Wi-Fi at once.
        val switchingOff = f.music == Music.LOST || ownScreen || together
        // However long it was quiet: the one real drop on the road came 8 s after the car's last word, as a switch-off does.
        if (wifiLost != null && f.car?.moving == true && !switchingOff) return Kind.DROPPED_DRIVING
        val last = f.lastFromCarAt
        val quietFirst = wifiLost != null && last != null && wifiLost - last >= QUIET_BEFORE_MS
        // Music on Bluetooth that never came: an adapter on a car whose system is switching off.
        val musicGone = f.music == Music.LOST || (f.music == Music.NEVER && f.musicOnBluetooth)
        if (together || (quietFirst && (musicGone || ownScreen))) return Kind.SWITCHED_OFF
        return Kind.OTHER
    }

    /** The report's result for [f], or null to keep the one it had. */
    fun result(f: Facts): String? = when (kind(f)) {
        Kind.SILENT -> "ended - the car went silent (${f.gaveUpAfterS} s)"
        Kind.DROPPED_DRIVING -> "FAILED - Wi-Fi dropped while driving"
        Kind.SWITCHED_OFF -> "ended - the car switched off"
        Kind.OTHER -> null
    }

    /** The report's rows on the end, as label and value. */
    fun rows(f: Facts): List<Pair<String, String>> {
        val last = f.lastFromCarAt
        val wifiLost = f.wifiLostAt
        return listOf(
            "last from the car" to (last?.let { "${seconds(f.endedAt - it)} before the end" } ?: "nothing (no ping, no ack)"),
            "Wi-Fi" to when {
                wifiLost != null && last != null -> "lost ${seconds(wifiLost - last)} after the car's last sign"
                wifiLost != null -> "lost ${seconds(f.endedAt - wifiLost)} before the end"
                f.gaveUpAfterS != null -> "still up, closed by myCar after ${f.gaveUpAfterS} s of silence"
                else -> "still up"
            },
            "car Bluetooth audio" to when (f.music) {
                Music.CONNECTED -> "connected"
                Music.LOST -> "lost ${seconds(f.endedAt - (f.musicLostAt ?: f.endedAt))} before the end"
                Music.NEVER -> "not used"
            },
            "car's own screen" to (f.ownScreenSince?.let { "shown at the end for ${seconds(f.endedAt - it)}" } ?: "no"),
            "car at the end" to (f.car?.describe() ?: "unknown (no sensor data)"),
        )
    }

    private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.1f s", maxOf(0L, ms) / 1000.0)

    /** Rounded to 5 km/h: the report says how the car moved, not how fast to the metre. */
    private fun kmh(speedE3: Long): String {
        val rounded = (abs(speedE3) * 3.6 / 1000 / 5).roundToLong() * 5
        return if (speedE3 == 0L) "0 km/h" else "~$rounded km/h"
    }

    private fun gearName(gear: Long): String = when (gear) {
        0L -> "neutral"
        100L -> "drive"
        101L -> "park"
        102L -> "reverse"
        else -> "gear $gear"
    }

    /** The car's Bluetooth channel and Wi-Fi closed within this of each other: one switch-off. */
    const val TOGETHER_MS = 500L

    /** The car quiet this long before its Wi-Fi went. */
    const val QUIET_BEFORE_MS = 3_000L

    /** SensorEventIndication's fields for these sensor types, as the type's number. */
    private const val SENSOR_SPEED = 3
    private const val SENSOR_GEAR = 8
    private const val SENSOR_DRIVING_STATUS = 13

    /** DrivingStatus's "no video" bit: set while the car moves (as CarDriving reads it). */
    private const val NO_VIDEO = 1L
}
