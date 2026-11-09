package io.github.sirionero.mycar.xonos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What myCar's log says about Xonos reaching Music Assistant (see ConnectWatch). */
class ConnectWatchTest {

    @Test
    fun `the server on the phone starting - connected after a few refusals`() {
        val watch = ConnectWatch()
        watch.trying(1_000)
        watch.failed(1_100, "NO_ANSWER")
        watch.trying(3_100)
        watch.failed(3_200, "NO_ANSWER")
        watch.trying(7_200)
        assertEquals("connected to Music Assistant after 6.4 s (3 tries)", watch.connected(7_400))
        // Connected: nothing more to say.
        assertNull(watch.slowIn(8_000))
        assertNull(watch.slow(70_000))
    }

    @Test
    fun `first try`() {
        val watch = ConnectWatch()
        watch.trying(500)
        assertEquals("connected to Music Assistant after 0.3 s (1 try)", watch.connected(800))
    }

    @Test
    fun `still not connected after a minute, said once, with the last problem`() {
        val watch = ConnectWatch()
        watch.trying(0)
        watch.failed(10, "NETWORK")
        assertEquals(59_990L, watch.slowIn(10))
        assertNull(watch.slow(59_999))
        watch.failed(20_000, "NO_ANSWER")
        assertEquals("still not connected to Music Assistant after 60 s (2 failed tries): NO_ANSWER", watch.slow(60_000))
        assertNull(watch.slow(120_000))
        assertNull(watch.slowIn(120_000))
        // Connected in the end: said too, with every try.
        assertEquals("connected to Music Assistant after 90.0 s (3 tries)", watch.connected(90_000))
    }

    @Test
    fun `a connection lost and made again`() {
        val watch = ConnectWatch()
        watch.trying(0)
        watch.connected(100)
        watch.failed(50_000, "LOST")
        assertEquals("connected to Music Assistant again after 2.0 s (2 tries)", watch.connected(52_000))
    }

    @Test
    fun `let go at the end of the drive - nothing said`() {
        val watch = ConnectWatch()
        watch.trying(0)
        watch.failed(100, "NO_ANSWER")
        watch.idle()
        assertNull(watch.slowIn(200))
        assertNull(watch.slow(61_000))
        assertNull(watch.connected(62_000))
        // The next drive starts afresh, not "again".
        watch.trying(100_000)
        assertEquals("connected to Music Assistant after 1.0 s (1 try)", watch.connected(101_000))
    }
}
