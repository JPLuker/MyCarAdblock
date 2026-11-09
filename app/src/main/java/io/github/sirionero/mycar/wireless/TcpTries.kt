package io.github.sirionero.mycar.wireless

/**
 * When the TCP tries to the car (WirelessLink.connectTcp) stop before their minute is up. No Android
 * here: TcpTriesTest.
 *
 * The tries go on for a minute because a car can open its port well after its Wi-Fi comes, with its
 * Bluetooth channel open. Not once nothing they try can reach the car: then the start-up starts over
 * at once, with a new channel, a new StartRequest and a new network.
 */
internal object TcpTries {

    /** What the tries do once Android has dropped the car's network. */
    enum class AfterLoss {
        /** On, as before: over the car's Wi-Fi joined by hand, or the ordinary route behind a VPN. */
        GO_ON,

        /** A moment without trying: the car, its channel still open, may ask for its Wi-Fi again. */
        WAIT,

        /** The car asked for its Wi-Fi again on the same channel: that join decides, these tries end. */
        YIELD,

        /** Nothing leads to the car: the tries end and the start-up starts over. */
        STOP,
    }

    /**
     * [carLeft]: another network of the phone is the car's (joined by hand: CarNetwork.Verdict.CAR).
     * [bindRefused]: Android refuses to bind to the car's networks (a VPN), and the ordinary route,
     * the only one left, went on as before. [newerJoin]: the car asked for its Wi-Fi again.
     * [channelOpen]: the start-up's Bluetooth channel is still open; [sinceLossMs]: since the loss.
     * Without this the tries spent the rest of their minute on the phone's ordinary route - its home
     * Wi-Fi, its mobile data - which doesn't lead to the car.
     */
    fun afterLoss(carLeft: Boolean, bindRefused: Boolean, newerJoin: Boolean, channelOpen: Boolean, sinceLossMs: Long): AfterLoss = when {
        bindRefused || carLeft -> AfterLoss.GO_ON
        newerJoin -> AfterLoss.YIELD
        channelOpen && sinceLossMs < LOSS_GRACE_MS -> AfterLoss.WAIT
        else -> AfterLoss.STOP
    }

    /** A car with its channel open asked for its Wi-Fi again 0.2 s after Android dropped it. */
    const val LOSS_GRACE_MS = 4_000L
}
