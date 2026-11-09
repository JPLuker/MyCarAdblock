package io.github.sirionero.mycar.wireless

/**
 * Whether another Wi-Fi network of the phone, with an address in the car's /24, is the car's own
 * network - the driver joined it by hand in the Wi-Fi list - and how the TCP tries use it
 * (WirelessLink.connectTcp). No Android here: CarNetworkTest.
 *
 * Joined by hand, the phone's main Wi-Fi is the car's: that is how Android Auto itself connects, and
 * a Pixel 6 passed pings but no TCP over the side connection Android opens for myCar's request, so
 * that network goes first. But a home Wi-Fi with addresses like the car's (both 192.168.1.x) looked
 * the same to the address alone: the first try went there and waited 5 s for nothing, by which time
 * an adapter that wants the phone within moments of its Wi-Fi had closed it, and the start-up took
 * minutes. A home or office Wi-Fi reaches the Internet (Android validated it) and has its own name;
 * the car's has no Internet, or the name the car gave.
 */
internal object CarNetwork {

    /** How the other network's name compares with the one the car gave (its InfoResponse). */
    enum class Name { SAME, OTHER, UNKNOWN }

    enum class Verdict {
        /** The car's, joined by hand: the tries start on it and go there every other time. */
        CAR,

        /** Can't tell: every other try, after one on the network asked for the car. */
        MAYBE,

        /** Another network (home, office): never tried. */
        NOT_CAR,
    }

    /** [validated]: Android found the Internet through it; null when that couldn't be read. */
    fun judge(validated: Boolean?, name: Name): Verdict = when (name) {
        Name.SAME -> Verdict.CAR
        // Another name for sure: a home or office Wi-Fi when it reaches the Internet. Without it, it
        // may still be the car's under a second name: tried, but never first.
        Name.OTHER -> if (validated == false) Verdict.MAYBE else Verdict.NOT_CAR
        // The name hidden from myCar decides nothing: no Internet is the car's (the Pixel 6 case).
        Name.UNKNOWN -> if (validated == false) Verdict.CAR else Verdict.MAYBE
    }

    /**
     * [ssid] and [bssid] as Android gives them for the other network: the name in quotes, and
     * "<unknown ssid>" and "02:00:00:00:00:00" (or nothing) when Android hides them from myCar (no
     * precise location, location off). [carSsid] and [carBssid] as the car gave them.
     */
    fun name(ssid: String?, bssid: String?, carSsid: String, carBssid: String): Name {
        val theirName = ssid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != HIDDEN_SSID }
        val theirAddress = bssid?.takeIf { isAddress(it) }
        if (theirName != null && theirName == carSsid) return Name.SAME
        // A car's Wi-Fi can have one address per band, so only a match says something.
        if (theirAddress != null && isAddress(carBssid) && theirAddress.equals(carBssid, ignoreCase = true)) return Name.SAME
        return if (theirName != null) Name.OTHER else Name.UNKNOWN
    }

    /** A Wi-Fi address that says something: not missing, hidden or all zeros. */
    private fun isAddress(text: String): Boolean =
        MAC.matches(text) && !text.equals(HIDDEN_BSSID, ignoreCase = true) && text != NO_BSSID

    private const val HIDDEN_SSID = "<unknown ssid>"
    private const val HIDDEN_BSSID = "02:00:00:00:00:00"
    private const val NO_BSSID = "00:00:00:00:00:00"
    private val MAC = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
}
