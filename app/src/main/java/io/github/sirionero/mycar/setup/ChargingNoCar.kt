package io.github.sirionero.mycar.setup

/**
 * Home's hint for a car that doesn't answer on the cable. Android tells myCar about a car only
 * once the car has switched the phone's USB port over and Android has handed it to myCar. A cable
 * that only charges, GrapheneOS's USB-C port left to charging, or Android's question left
 * unanswered all look the same from here: a phone charging from a USB port, and nothing else. A
 * computer looks the same too, so the hint waits a while and only says what to check. No Android
 * here: Home feeds it what it reads.
 *
 * A car that came to myCar on this cable keeps the hint away until the cable is out: a drive that
 * ended, or a car that stopped answering after one, isn't a cable to check.
 */
class ChargingNoCar(private val waitMs: Long = WAIT_MS) {
    private var since = NEVER
    private var answered = false

    /**
     * At [now] (ms, on a clock that doesn't jump): whether the hint shows. [usb]: the phone
     * charges from a USB port; [car]: a car came to myCar on it, a drive is under way, or a car
     * is being reached without the cable.
     */
    fun update(now: Long, usb: Boolean, car: Boolean): Boolean {
        if (!usb) {
            since = NEVER
            answered = false
            return false
        }
        if (car) answered = true
        if (answered) return false
        if (since == NEVER) since = now
        return now - since >= waitMs
    }

    companion object {
        /** How long a phone may charge with no car before the hint: cars take a few seconds to answer. */
        const val WAIT_MS = 20_000L
        private const val NEVER = Long.MIN_VALUE
    }
}
