package io.github.sirionero.mycar

import io.github.sirionero.mycar.SessionEnd.CarState
import io.github.sirionero.mycar.SessionEnd.Facts
import io.github.sirionero.mycar.SessionEnd.Kind
import io.github.sirionero.mycar.SessionEnd.Music
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a wireless drive that reached the car's screen ended, from what the phone saw last. */
class SessionEndTest {

    private val stopped = CarState(drivingStatus = 0, speedE3 = 0, gear = 0)
    private val moving = CarState(drivingStatus = 27, speedE3 = 7711, gear = 3)

    private fun facts(
        endedAt: Long = 100_000,
        lastFromCarAt: Long? = 100_000,
        wifiLostAt: Long? = 100_000,
        channelClosedAt: Long? = null,
        gaveUpAfterS: Long? = null,
        music: Music = Music.CONNECTED,
        musicLostAt: Long? = null,
        musicOnBluetooth: Boolean = true,
        ownScreenSince: Long? = null,
        car: CarState? = stopped,
    ) = Facts(endedAt, lastFromCarAt, wifiLostAt, channelClosedAt, gaveUpAfterS, music, musicLostAt, musicOnBluetooth, ownScreenSince, car)

    @Test
    fun `the car switched off at the journey's end - quiet first, its Bluetooth music gone`() {
        // As in a drive's log: the last ping 6.7 s before the Wi-Fi went, the Bluetooth music 5 s before.
        val f = facts(endedAt = 47_150, lastFromCarAt = 40_430, wifiLostAt = 47_134, music = Music.LOST, musicLostAt = 42_000)
        assertEquals(Kind.SWITCHED_OFF, SessionEnd.kind(f))
        assertEquals("ended - the car switched off", SessionEnd.result(f))
    }

    @Test
    fun `the car switched off showing its own screen`() {
        val f = facts(lastFromCarAt = 92_000, ownScreenSince = 90_000, music = Music.NEVER, musicOnBluetooth = false)
        assertEquals(Kind.SWITCHED_OFF, SessionEnd.kind(f))
    }

    @Test
    fun `an adapter's session on a car already switching off - music on Bluetooth that never came`() {
        val f = facts(lastFromCarAt = 91_000, music = Music.NEVER, musicOnBluetooth = true)
        assertEquals(Kind.SWITCHED_OFF, SessionEnd.kind(f))
    }

    @Test
    fun `channel and Wi-Fi closed together, the pings up to the end - the car switched off`() {
        val f = facts(lastFromCarAt = 99_800, wifiLostAt = 100_000, channelClosedAt = 99_850, music = Music.NEVER, musicOnBluetooth = false)
        assertEquals(Kind.SWITCHED_OFF, SessionEnd.kind(f))
        assertEquals(Kind.OTHER, SessionEnd.kind(f.copyChannel(99_000)))
    }

    private fun Facts.copyChannel(closedAt: Long) = Facts(
        endedAt, lastFromCarAt, wifiLostAt, closedAt, gaveUpAfterS, music, musicLostAt, musicOnBluetooth, ownScreenSince, car,
    )

    @Test
    fun `the Wi-Fi dropped on the road - however long the car was quiet first`() {
        // The one real drop in the logs: the car quiet 8.4 s, moving at ~28 km/h, its Bluetooth music on.
        val f = facts(lastFromCarAt = 91_600, music = Music.CONNECTED, car = moving)
        assertEquals(Kind.DROPPED_DRIVING, SessionEnd.kind(f))
        assertEquals("FAILED - Wi-Fi dropped while driving", SessionEnd.result(f))
    }

    @Test
    fun `moving but switching off - its Bluetooth music lost - isn't a drop`() {
        val f = facts(lastFromCarAt = 92_000, music = Music.LOST, musicLostAt = 93_000, car = moving)
        assertEquals(Kind.SWITCHED_OFF, SessionEnd.kind(f))
    }

    @Test
    fun `myCar ended it after the car's silence`() {
        val f = facts(wifiLostAt = null, lastFromCarAt = 76_000, gaveUpAfterS = 24)
        assertEquals(Kind.SILENT, SessionEnd.kind(f))
        assertEquals("ended - the car went silent (24 s)", SessionEnd.result(f))
        assertEquals("still up, closed by myCar after 24 s of silence", SessionEnd.rows(f).toMap()["Wi-Fi"])
    }

    @Test
    fun `a Wi-Fi gone with the car talking up to the end, stopped - as before`() {
        val f = facts(lastFromCarAt = 99_500)
        assertEquals(Kind.OTHER, SessionEnd.kind(f))
        assertNull(SessionEnd.result(f))
    }

    @Test
    fun `the rows say what the phone saw`() {
        val f = facts(endedAt = 47_150, lastFromCarAt = 40_430, wifiLostAt = 47_134, music = Music.LOST, musicLostAt = 41_950)
        val rows = SessionEnd.rows(f).toMap()
        assertEquals("6.7 s before the end", rows["last from the car"])
        assertEquals("lost 6.7 s after the car's last sign", rows["Wi-Fi"])
        assertEquals("lost 5.2 s before the end", rows["car Bluetooth audio"])
        assertEquals("no", rows["car's own screen"])
        assertEquals("stopped (driving state 0, 0 km/h, neutral)", rows["car at the end"])
        assertEquals("shown at the end for 3.0 s", SessionEnd.rows(facts(endedAt = 100_000, ownScreenSince = 97_000)).toMap()["car's own screen"])
    }

    @Test
    fun `speed rounded to 5 km-h, gears by name`() {
        assertEquals("moving (driving state 27, ~30 km/h, gear 3)", moving.describe())
        assertEquals("moving (~45 km/h, drive)", CarState(speedE3 = 11_872, gear = 100).describe())
        assertEquals("stopped (park)", CarState(gear = 101).describe())
        assertEquals("moving (driving state 1)", CarState(drivingStatus = 1).describe())
    }

    /** A sensor event with one reading: { [type]: { 1: [value] } }, values under 128. */
    private fun event(type: Int, value: Int) = byteArrayOf(((type shl 3) or 2).toByte(), 0x02, 0x08, value.toByte())

    @Test
    fun `the car's state builds from its sensor events`() {
        val a = CarState.merge(null, event(3, 0))
        assertEquals(CarState(speedE3 = 0), a)
        val b = CarState.merge(a, event(13, 27))
        assertEquals(CarState(drivingStatus = 27, speedE3 = 0), b)
        assertTrue(b!!.moving)
        val c = CarState.merge(b, event(8, 102))
        assertEquals("moving (driving state 27, 0 km/h, reverse)", c!!.describe())
        // Night mode (10) says nothing about moving.
        assertNull(CarState.merge(c, event(10, 1)))
        assertFalse(CarState.merge(c, event(13, 0))!!.moving)
    }
}
