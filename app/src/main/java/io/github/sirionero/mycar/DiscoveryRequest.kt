package io.github.sirionero.mycar

/**
 * The phone's ServiceDiscoveryRequest (aasdk's: small_icon 1, medium_icon 2, large_icon 3,
 * label_text 4, device_name 5, phone_info 6 = PhoneInfo { instance_id 1 }).
 *
 * myCar sends the label and the phone's model only, as it always has: the cars that work with it
 * were tried that way (protocol 1.3 and 4.1 among them). A car on protocol 1.0 or 1.1 (Hyundai
 * 2017-2018) never answered that and waited until the cable dropped; with the phone's icon at three
 * sizes and an id new at each connection, as a phone with Android Auto sends them, it went on
 * (Gearslip 0.3.40, issue #47). So those two versions get them, and no other.
 */
internal object DiscoveryRequest {

    /** Whether a car whose VersionRequest says [major].[minor] gets the icons and the id: 1.0 and 1.1. */
    fun wantsIcons(major: Int, minor: Int): Boolean = major == 1 && minor <= 1

    /**
     * The request's body: [icons] (PNGs, smallest first, at most three) and [sessionId] only when
     * given; without them it's the very request myCar has always sent.
     */
    fun body(label: String, model: String, icons: List<ByteArray> = emptyList(), sessionId: String? = null): ByteArray {
        var body = ByteArray(0)
        icons.take(3).forEachIndexed { i, png -> body += Protobuf.bytesField(i + 1, png) }
        body += Protobuf.stringField(4, label) + Protobuf.stringField(5, model)
        if (sessionId != null) body += Protobuf.bytesField(6, Protobuf.stringField(1, sessionId))
        return body
    }
}
