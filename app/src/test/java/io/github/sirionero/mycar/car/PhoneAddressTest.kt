package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The phone's Bluetooth address as written by hand in Settings > Connection. */
class PhoneAddressTest {

    @Test fun `written the usual way, or any other`() {
        assertEquals("12:34:56:78:9A:BC", PhoneAddress.tidy("12:34:56:78:9A:BC"))
        assertEquals("12:34:56:78:9A:BC", PhoneAddress.tidy("12:34:56:78:9a:bc"))
        assertEquals("12:34:56:78:9A:BC", PhoneAddress.tidy(" 12-34-56-78-9a-bc "))
        assertEquals("12:34:56:78:9A:BC", PhoneAddress.tidy("123456789abc"))
        assertEquals("12:34:56:78:9A:BC", PhoneAddress.tidy("12 : 34 : 56 : 78 : 9A : BC"))
    }

    @Test fun `nothing written is no address`() {
        assertEquals("", PhoneAddress.tidy(""))
        assertEquals("", PhoneAddress.tidy("   "))
    }

    @Test fun `what isn't an address`() {
        assertNull(PhoneAddress.tidy("12:34:56:78:9A"))
        assertNull(PhoneAddress.tidy("12:34:56:78:9A:BC:DE"))
        assertNull(PhoneAddress.tidy("12:34:56:78:9A:BG"))
        assertNull(PhoneAddress.tidy("1234:5678:9ABC"))
        assertNull(PhoneAddress.tidy("my phone"))
        // Android's stand-in for apps, and all zeros, are no phone's.
        assertNull(PhoneAddress.tidy(PhoneAddress.HIDDEN))
        assertNull(PhoneAddress.tidy("00:00:00:00:00:00"))
    }
}
