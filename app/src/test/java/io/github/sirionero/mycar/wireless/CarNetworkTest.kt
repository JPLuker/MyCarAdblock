package io.github.sirionero.mycar.wireless

import io.github.sirionero.mycar.wireless.CarNetwork.Name
import io.github.sirionero.mycar.wireless.CarNetwork.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** Whether another Wi-Fi of the phone in the car's address range is the car's own network. */
class CarNetworkTest {

    private val car = "CarWifi-1234"
    private val carAddress = "AA:BB:CC:DD:EE:01"

    @Test
    fun `a home Wi-Fi that reaches the Internet, with another name, is never the car's`() {
        val name = CarNetwork.name("\"Home\"", "11:22:33:44:55:66", car, carAddress)
        assertEquals(Name.OTHER, name)
        assertEquals(Verdict.NOT_CAR, CarNetwork.judge(validated = true, name = name))
    }

    @Test
    fun `the car's Wi-Fi joined by hand, with the car's name, goes first`() {
        assertEquals(Name.SAME, CarNetwork.name("\"$car\"", "AA:BB:CC:DD:EE:02", car, carAddress))
        assertEquals(Verdict.CAR, CarNetwork.judge(validated = false, name = Name.SAME))
        // A car that shares its Internet over the same Wi-Fi: validated, but its name says it.
        assertEquals(Verdict.CAR, CarNetwork.judge(validated = true, name = Name.SAME))
    }

    @Test
    fun `the car's address alone says it too`() {
        assertEquals(Name.SAME, CarNetwork.name(null, "aa:bb:cc:dd:ee:01", car, carAddress))
    }

    @Test
    fun `a name hidden from myCar decides nothing - no Internet is the car's (the Pixel 6)`() {
        val name = CarNetwork.name("<unknown ssid>", "02:00:00:00:00:00", car, carAddress)
        assertEquals(Name.UNKNOWN, name)
        assertEquals(Verdict.CAR, CarNetwork.judge(validated = false, name = name))
    }

    @Test
    fun `a name hidden from myCar, with the Internet, can't be told - not first`() {
        assertEquals(Verdict.MAYBE, CarNetwork.judge(validated = true, name = Name.UNKNOWN))
        assertEquals(Verdict.MAYBE, CarNetwork.judge(validated = null, name = Name.UNKNOWN))
        assertEquals(Name.UNKNOWN, CarNetwork.name(null, null, car, carAddress))
    }

    @Test
    fun `another name without the Internet may still be the car's - tried, not first`() {
        assertEquals(Verdict.MAYBE, CarNetwork.judge(validated = false, name = Name.OTHER))
        assertEquals(Verdict.NOT_CAR, CarNetwork.judge(validated = null, name = Name.OTHER))
    }

    @Test
    fun `another address alone says nothing - a car can have one per band`() {
        assertEquals(Name.UNKNOWN, CarNetwork.name(null, "11:22:33:44:55:66", car, carAddress))
        // The car gave no address, or zeros.
        assertEquals(Name.UNKNOWN, CarNetwork.name(null, "11:22:33:44:55:66", car, ""))
        assertEquals(Name.UNKNOWN, CarNetwork.name(null, "00:00:00:00:00:00", car, "00:00:00:00:00:00"))
    }

    @Test
    fun `an empty name is no name`() {
        assertEquals(Name.UNKNOWN, CarNetwork.name("\"\"", null, car, carAddress))
    }
}
