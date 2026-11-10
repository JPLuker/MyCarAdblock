package io.github.sirionero.mycar.wireless

import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.Wire

/**
 * The phone's answer to a car's VersionRequest on the Bluetooth channel. Laid out the way a real
 * phone's is (aa-proxy-rs and LIVI both parse it from real phones): 1 and 2 the version, echoing
 * the car's; 3 a serial; 4 the status; 6 the device's ids. And 5, the Wi-Fi channel type the car
 * asked for (its request's field 3), which strict head units (Toyota's, by Gearslip 0.3.39) wait
 * for before their StartRequest - only for a car that went quiet without it once
 * ([wantsChannelType]): every car that goes on today gets the very reply it gets today.
 */
internal object WirelessVersion {

    private const val STATUS_SUCCESS = 0L

    /** The VersionResponse to [request]; with field 5 only when [channelType] and the request named one. */
    fun reply(request: ByteArray, serial: String, sessionId: String, channelType: Boolean): ByteArray {
        val f = Wire.fields(request)
        val ids = Protobuf.stringField(1, serial) + Protobuf.stringField(2, sessionId)
        val type = if (channelType) Wire.varint(f, 3) else null
        return Protobuf.varintField(1, Wire.varint(f, 1) ?: 1) +
            Protobuf.varintField(2, Wire.varint(f, 2) ?: 0) +
            Protobuf.stringField(3, serial) +
            Protobuf.varintField(4, STATUS_SUCCESS) +
            (type?.let { Protobuf.varintField(5, it) } ?: ByteArray(0)) +
            Protobuf.varint(((6 shl 3) or 2).toLong()) + Protobuf.varint(ids.size.toLong()) + ids
    }

    /**
     * The car's channel ended ([byCar]: closed by the car, or quiet for too long) after the phone's
     * VersionResponse ([answered]) with nothing that leads to its Wi-Fi ([wentOn]: no StartRequest,
     * no Wi-Fi details), and the reply hadn't the channel type ([gaveIt]): from now on it gets it.
     * Not while listening after a drive ([afterDrive]): a car that drove has no use for it, and one
     * switched off in the middle of the exchange ends it the same way.
     */
    fun wantsChannelType(answered: Boolean, wentOn: Boolean, byCar: Boolean, gaveIt: Boolean, afterDrive: Boolean): Boolean =
        answered && !wentOn && byCar && !gaveIt && !afterDrive
}
