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
 * plays straight into the car's channel, with no capture.
 */
internal fun autoplayWaits(deciding: Boolean, throughMyCar: Boolean, captureOn: Boolean, straightIn: Boolean): Boolean =
    deciding || (throughMyCar && !captureOn && !straightIn)
