package io.github.sirionero.mycar.wireless

import io.github.sirionero.mycar.wireless.TcpTries.AfterLoss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the TCP tries to the car stop before their minute is up. */
class TcpTriesTest {

    private fun afterLoss(
        carLeft: Boolean = false,
        bindRefused: Boolean = false,
        newerJoin: Boolean = false,
        channelOpen: Boolean = false,
        sinceLossMs: Long = 0,
    ) = TcpTries.afterLoss(carLeft, bindRefused, newerJoin, channelOpen, sinceLossMs)

    @Test
    fun `the car's network gone with its channel closed and nothing else - start over at once`() {
        // The home Wi-Fi in the car's address range doesn't count: CarNetwork said it isn't the car's.
        assertEquals(AfterLoss.STOP, afterLoss())
    }

    @Test
    fun `the car's Wi-Fi joined by hand is left - the tries go on over it`() {
        assertEquals(AfterLoss.GO_ON, afterLoss(carLeft = true))
    }

    @Test
    fun `behind a VPN the ordinary route goes on as before`() {
        assertEquals(AfterLoss.GO_ON, afterLoss(bindRefused = true))
        assertEquals(AfterLoss.GO_ON, afterLoss(bindRefused = true, channelOpen = true, sinceLossMs = 60_000))
    }

    @Test
    fun `the car asked for its Wi-Fi again - that join decides`() {
        assertEquals(AfterLoss.YIELD, afterLoss(newerJoin = true, channelOpen = true))
        assertEquals(AfterLoss.YIELD, afterLoss(newerJoin = true, channelOpen = true, sinceLossMs = 10_000))
    }

    @Test
    fun `with the channel open the car gets a moment to ask again, then the start-up starts over`() {
        assertEquals(AfterLoss.WAIT, afterLoss(channelOpen = true, sinceLossMs = 200))
        assertEquals(AfterLoss.WAIT, afterLoss(channelOpen = true, sinceLossMs = TcpTries.LOSS_GRACE_MS - 1))
        assertEquals(AfterLoss.STOP, afterLoss(channelOpen = true, sinceLossMs = TcpTries.LOSS_GRACE_MS))
    }

    // An adapter's restart, as in a drive's log: the channel closed 11 s after the StartRequest, the
    // adapter back on Bluetooth 20.7 s later, the Wi-Fi joined 4 s after that, every try refused.
    private val closed = 24_935L
    private val back = 45_665L
    private val joined = 49_736L
    private val firstRefusal = 49_748L

    @Test
    fun `the adapter restarted - its old port is dead after 4 s of refusals`() {
        assertFalse(TcpTries.stalePort(closed, joined, firstRefusal, back, now = firstRefusal + 3_999))
        assertTrue(TcpTries.stalePort(closed, joined, firstRefusal, back, now = firstRefusal + 4_000))
    }

    @Test
    fun `no restart seen - the old port is dead after 5 s of refusals`() {
        assertFalse(TcpTries.stalePort(closed, joined, firstRefusal, carBackAt = null, now = firstRefusal + 4_500))
        assertTrue(TcpTries.stalePort(closed, joined, firstRefusal, carBackAt = null, now = firstRefusal + 5_000))
        // Back 1 s after the channel closed is the closing itself, not a restart.
        assertFalse(TcpTries.stalePort(closed, joined, firstRefusal, closed + 1_400, now = firstRefusal + 4_500))
    }

    @Test
    fun `a channel closed at first contact with the old port working - nothing refused, nothing ends`() {
        assertFalse(TcpTries.stalePort(closed, joined, refusedSince = null, carBackAt = closed + 1_400, now = joined + 30_000))
    }

    @Test
    fun `a car that opens its port late with the channel open keeps the minute`() {
        assertFalse(TcpTries.stalePort(channelClosedAt = null, joinedAt = joined, refusedSince = firstRefusal, carBackAt = back, now = firstRefusal + 50_000))
    }

    @Test
    fun `a channel closed after the Wi-Fi came isn't the restart's`() {
        assertFalse(TcpTries.stalePort(joined + 2_000, joined, firstRefusal, back, now = firstRefusal + 10_000))
    }
}
