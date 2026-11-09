package io.github.sirionero.mycar.wireless

import io.github.sirionero.mycar.wireless.TcpTries.AfterLoss
import org.junit.Assert.assertEquals
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
}
