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

    /**
     * The port the car gave in its StartRequest is gone. An adapter that doesn't see the phone on its
     * Wi-Fi within moments closes the Bluetooth channel and restarts, and the port goes with it: the
     * car answers on its Wi-Fi but refuses that port, and the tries spent their minute on it with
     * the car already on the road. So: this start-up's channel closed before the phone joined the
     * Wi-Fi, and the port has been refused for [REFUSED_MIN_MS], with the car's Bluetooth back
     * [RESTART_GAP_MS] or more after the channel closed (it restarted), or refused for
     * [REFUSED_ALONE_MS]. A car that opens its port late keeps its channel open: it still gets the minute.
     *
     * All on one clock (elapsedRealtime). [channelClosedAt]: this start-up's channel, null while open;
     * [joinedAt]: the Wi-Fi; [refusedSince]: the first refusal in a row on the car's network, null
     * without one; [carBackAt]: the car's Bluetooth came last, null if never seen.
     */
    fun stalePort(channelClosedAt: Long?, joinedAt: Long, refusedSince: Long?, carBackAt: Long?, now: Long): Boolean {
        if (channelClosedAt == null || channelClosedAt > joinedAt || refusedSince == null) return false
        val refused = now - refusedSince
        if (refused < REFUSED_MIN_MS) return false
        val restarted = carBackAt != null && carBackAt - channelClosedAt >= RESTART_GAP_MS
        return restarted || refused >= REFUSED_ALONE_MS
    }

    const val REFUSED_MIN_MS = 4_000L
    const val REFUSED_ALONE_MS = 5_000L

    /** An adapter that restarts is away from Bluetooth for 20 s and more; a channel it just closes, about 1 s. */
    const val RESTART_GAP_MS = 10_000L
}
