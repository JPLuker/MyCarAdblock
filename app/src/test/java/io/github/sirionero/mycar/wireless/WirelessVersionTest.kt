package io.github.sirionero.mycar.wireless

import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.Wire
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The VersionResponse on a car's Bluetooth channel, with and without the Wi-Fi channel type. */
class WirelessVersionTest {

    /** A car's request as a wireless adapter sends it: version 1.0, channel type 0, and its field 4. */
    private val request = Protobuf.varintField(1, 1) + Protobuf.varintField(2, 0) + Protobuf.varintField(3, 0) + Protobuf.varintField(4, 36)

    /** The reply as myCar built it before (WirelessLink up to 0.7.3). */
    private fun before(request: ByteArray, serial: String, session: String): ByteArray {
        val f = Wire.fields(request)
        val ids = Protobuf.stringField(1, serial) + Protobuf.stringField(2, session)
        return Protobuf.varintField(1, Wire.varint(f, 1) ?: 1) +
            Protobuf.varintField(2, Wire.varint(f, 2) ?: 0) +
            Protobuf.stringField(3, serial) +
            Protobuf.varintField(4, 0L) +
            Protobuf.varint(((6 shl 3) or 2).toLong()) + Protobuf.varint(ids.size.toLong()) + ids
    }

    @Test fun `without the channel type the reply is byte for byte the one of before`() {
        assertArrayEquals(before(request, "abc123", "s-1"), WirelessVersion.reply(request, "abc123", "s-1", channelType = false))
        // A request with nothing in it: version 1.0, as before.
        assertArrayEquals(before(ByteArray(0), "abc123", "s-1"), WirelessVersion.reply(ByteArray(0), "abc123", "s-1", channelType = false))
    }

    @Test fun `with it, field 5 echoes the channel type the car asked for`() {
        val asked = Protobuf.varintField(1, 1) + Protobuf.varintField(2, 6) + Protobuf.varintField(3, 2)
        val fields = Wire.fields(WirelessVersion.reply(asked, "abc123", "s-1", channelType = true))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), fields.map { it.number })
        assertEquals(2L, Wire.varint(fields, 5))
        assertEquals(1L, Wire.varint(fields, 1))
        assertEquals(6L, Wire.varint(fields, 2))
        assertEquals("abc123", String(Wire.bytes(fields, 3)!!))
        // The cars seen so far ask for type 0: it would go back as 0.
        assertEquals(0L, Wire.varint(Wire.fields(WirelessVersion.reply(request, "abc123", "s-1", channelType = true)), 5))
    }

    @Test fun `a car that names no channel type gets none back`() {
        val noType = Protobuf.varintField(1, 1) + Protobuf.varintField(2, 3)
        assertNull(Wire.varint(Wire.fields(WirelessVersion.reply(noType, "abc123", "s-1", channelType = true)), 5))
        assertArrayEquals(before(noType, "abc123", "s-1"), WirelessVersion.reply(noType, "abc123", "s-1", channelType = true))
    }

    @Test fun `only a car gone quiet right after the reply is remembered`() {
        assertTrue(WirelessVersion.wantsChannelType(answered = true, wentOn = false, byCar = true, gaveIt = false, afterDrive = false))
        // It went on: StartRequest or its Wi-Fi details came.
        assertFalse(WirelessVersion.wantsChannelType(answered = true, wentOn = true, byCar = true, gaveIt = false, afterDrive = false))
        // Closed before any VersionRequest (an adapter does that to a channel opened too soon).
        assertFalse(WirelessVersion.wantsChannelType(answered = false, wentOn = false, byCar = true, gaveIt = false, afterDrive = false))
        // Closed by myCar (stopped, a newer start-up).
        assertFalse(WirelessVersion.wantsChannelType(answered = true, wentOn = false, byCar = false, gaveIt = false, afterDrive = false))
        // It had the channel type already.
        assertFalse(WirelessVersion.wantsChannelType(answered = true, wentOn = false, byCar = true, gaveIt = true, afterDrive = false))
        // Listening after a drive: a car switched off in the middle ends it the same way.
        assertFalse(WirelessVersion.wantsChannelType(answered = true, wentOn = false, byCar = true, gaveIt = false, afterDrive = true))
    }
}
