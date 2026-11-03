package io.github.sirionero.mycar.car

/**
 * Whether the drive's own Autoplay (the music it picked as the car arrived) waits, rather than play
 * now: until the phone's sound has a way to the car it played on the phone's speaker, in the
 * driver's pocket - Automatic still giving the car's Bluetooth its 5 s, or the sound to go through
 * myCar with Android's question not yet answered (it waits for myCar on the phone's screen). No
 * Android here, so it's tried on the JVM (AutoplayWaitTest).
 *
 * [deciding]: Automatic hasn't picked the drive's way yet. [throughMyCar]: the sound goes through
 * myCar. [captureOn]: Android's capture runs. [straightIn]: myCar's own player (Xonos inside myCar)
 * plays straight into the car's channel, with no capture. [bluetoothOut]: the phone has a Bluetooth
 * output for music; on the Bluetooth way without one (the car's Bluetooth music not connected yet, or
 * dropped with a reset of the car) the music played on the phone's speaker, in the driver's pocket:
 * 27 s at a reconnection in a driver's log.
 */
internal fun autoplayWaits(
    deciding: Boolean,
    throughMyCar: Boolean,
    captureOn: Boolean,
    straightIn: Boolean,
    bluetoothOut: Boolean = true,
): Boolean =
    deciding || (throughMyCar && !captureOn && !straightIn) || (!throughMyCar && !bluetoothOut)
