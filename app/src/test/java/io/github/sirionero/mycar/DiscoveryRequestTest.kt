package io.github.sirionero.mycar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phone's ServiceDiscoveryRequest: the icons and the id only for cars on protocol 1.0 and 1.1,
 * and for every other car byte for byte the request myCar has always sent.
 */
class DiscoveryRequestTest {

    private val icons = listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6, 7), ByteArray(300) { it.toByte() })

    @Test fun `only 1_0 and 1_1 get the icons`() {
        assertTrue(DiscoveryRequest.wantsIcons(1, 0))
        assertTrue(DiscoveryRequest.wantsIcons(1, 1))
        for ((major, minor) in listOf(1 to 2, 1 to 3, 1 to 5, 1 to 6, 1 to 7, 2 to 0, 2 to 1, 4 to 1, 0 to 0)) {
            assertFalse("$major.$minor", DiscoveryRequest.wantsIcons(major, minor))
        }
    }

    @Test fun `without icons and id it is the request of before`() {
        val before = Protobuf.stringField(4, "myCar") + Protobuf.stringField(5, "Pixel 10 Pro")
        assertArrayEquals(before, DiscoveryRequest.body("myCar", "Pixel 10 Pro"))
        assertArrayEquals(before, DiscoveryRequest.body("myCar", "Pixel 10 Pro", emptyList(), null))
    }

    @Test fun `an old car gets three icons, the label, the model and the id, in field order`() {
        val body = DiscoveryRequest.body("myCar", "Pixel 10 Pro", icons, "the-id")
        val fields = Wire.fields(body)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), fields.map { it.number })
        icons.forEachIndexed { i, png -> assertArrayEquals(png, Wire.bytes(fields, i + 1)) }
        assertEquals("myCar", String(Wire.bytes(fields, 4)!!))
        assertEquals("Pixel 10 Pro", String(Wire.bytes(fields, 5)!!))
        val phoneInfo = Wire.fields(Wire.bytes(fields, 6)!!)
        assertEquals("the-id", String(Wire.bytes(phoneInfo, 1)!!))
        assertNull(Wire.bytes(phoneInfo, 2))
    }

    @Test fun `no more than three icons, and none when they could not be drawn`() {
        val four = DiscoveryRequest.body("myCar", "x", icons + listOf(byteArrayOf(9)), "id")
        assertEquals(listOf(1, 2, 3, 4, 5, 6), Wire.fields(four).map { it.number })
        val none = DiscoveryRequest.body("myCar", "x", emptyList(), "id")
        assertEquals(listOf(4, 5, 6), Wire.fields(none).map { it.number })
    }
}
