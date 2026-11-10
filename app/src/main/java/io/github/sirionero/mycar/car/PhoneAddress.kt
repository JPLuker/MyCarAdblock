package io.github.sirionero.mycar.car

/**
 * The phone's own Bluetooth address as the driver writes it in Settings > Connection: Android
 * doesn't give it to apps, and some cars want it to tell the phone on the cable or the Wi-Fi from
 * another (CarBluetooth). Written by hand, so written any way: "aa-bb-cc-dd-ee-ff", without
 * separators, with spaces around.
 */
internal object PhoneAddress {

    /** What Android gives apps instead of the phone's address: not the phone's. */
    const val HIDDEN = "02:00:00:00:00:00"

    private val ADDRESS = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")
    private val BARE = Regex("[0-9A-F]{12}")

    /**
     * [text] as AA:BB:CC:DD:EE:FF; "" when nothing is written (no address, as by default); null when
     * it isn't an address, or is Android's stand-in or all zeros.
     */
    fun tidy(text: String): String? {
        val upper = text.trim().uppercase().replace('-', ':').replace(" ", "")
        if (upper.isEmpty()) return ""
        val address = if (BARE.matches(upper)) upper.chunked(2).joinToString(":") else upper
        if (!ADDRESS.matches(address) || address == HIDDEN || address == "00:00:00:00:00:00") return null
        return address
    }
}
