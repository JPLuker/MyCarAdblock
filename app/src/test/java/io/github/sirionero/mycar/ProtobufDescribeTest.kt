package io.github.sirionero.mycar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtobufDescribeTest {
    // Field 1, length-delimited, with a length of 2^32 - 1: an Int of -1.
    private val hugeLength = byteArrayOf(0x0A, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F, 0x01, 0x02)

    @Test
    fun `a length that overflows an Int ends the dump, it doesn't throw`() {
        assertEquals("", Protobuf.describe(hugeLength))
    }

    @Test
    fun `a length past the end isn't a field`() {
        assertNull(Protobuf.readInt64Field(hugeLength, 2))
        assertNull(Protobuf.readInt64Field(byteArrayOf(0x0A, 0x7F, 0x01), 2))
    }

    @Test
    fun `a sound message still reads`() {
        // Field 1 "ab", then field 2 = 5.
        val ok = byteArrayOf(0x0A, 0x02, 'a'.code.toByte(), 'b'.code.toByte(), 0x10, 0x05)
        assertEquals(5L, Protobuf.readInt64Field(ok, 2))
        assertEquals("  #1 string = \"ab\"\n  #2 varint = 5\n", Protobuf.describe(ok))
    }
}
