package io.github.sirionero.mycar.wireless

/**
 * When a start-up waiting for the car calls the car's own Android Auto service again
 * (WirelessLink.waitForCar). No Android here: KnockPlanTest.
 *
 * The usual step is 8 s. An adapter whose Bluetooth has just come has its service ready a few
 * seconds later, and it answered at the second call, 8.3-8.6 s after its Bluetooth came, with the
 * car waiting. So when the first call fails fast - the car's Bluetooth answers, its service isn't
 * there yet - the calls come 3 s after the car's Bluetooth came, then every 2 s until 15 s after
 * it, then at the usual step. Not sooner than 3 s: that adapter closed channels opened right after
 * its Bluetooth came, and the start-over each closing caused cost one of the three.
 */
internal object KnockPlan {

    /** A first call failing faster found the car's Bluetooth there and its service not yet; with no car it takes ~5 s. */
    const val FAST_FAIL_MS = 1_000L

    /** The first quick call after the car's Bluetooth came. */
    const val AFTER_ARRIVAL_MS = 3_000L

    const val QUICK_EVERY_MS = 2_000L

    /** Quick calls until this long after the car's Bluetooth came. */
    const val QUICK_FOR_MS = 15_000L

    /** A channel the car closes this soon after it opened, before its Wi-Fi details: back to the usual step. */
    const val EARLY_CLOSE_MS = 4_000L

    /** The next call: when, and whether it is a quick one. */
    class Due(val at: Long, val quick: Boolean)

    /**
     * [lastEnd]: when the last call ended, or the wait began. [arrival]: when the car's Bluetooth
     * came, while it's known to be there; null otherwise. [quick]: quick calls allowed at all.
     * [step]: the usual step. All on one clock.
     */
    fun next(lastEnd: Long, arrival: Long?, quick: Boolean, step: Long): Due {
        if (quick && arrival != null) {
            val at = maxOf(arrival + AFTER_ARRIVAL_MS, lastEnd + QUICK_EVERY_MS)
            if (at <= arrival + QUICK_FOR_MS) return Due(at, quick = true)
        }
        return Due(lastEnd + step, quick = false)
    }

    /** A channel the car closed at its first contact: this soon after it opened, before its Wi-Fi details. */
    fun closedEarly(openedAt: Long, closedAt: Long, gotWifiDetails: Boolean): Boolean =
        !gotWifiDetails && closedAt - openedAt < EARLY_CLOSE_MS

    /**
     * The start-overs [left] once a channel the car closed at its first contact is over, before the
     * start-over that follows takes one. A channel a quick call opened ([quickChannel]) gives that one
     * back, in a start-up that starts over by itself ([startsOver]) with no start-over on its way yet
     * ([overPending]). Not capped at the full count: the start-over takes it again at once, and capped,
     * the first start-up - where it happens most - paid all the same. Once a start-up and its
     * start-overs at most: no quick calls follow an early closing.
     */
    fun startOversLeft(left: Int, quickChannel: Boolean, startsOver: Boolean, overPending: Boolean): Int =
        if (quickChannel && startsOver && !overPending) left + 1 else left

    /**
     * The car's Bluetooth arrival the quick calls count from as the wait begins: [latest] if the first
     * call found the car's Bluetooth there ([carThere]: it failed fast) or the car's Bluetooth came
     * while that call ran ([before]: the latest arrival when it began); null otherwise. An adapter back
     * from a restart arrived while a call that found no car took its 5 s, and the wait took that
     * arrival for one seen already: its calls stayed at the usual step.
     */
    fun firstArrival(carThere: Boolean, before: Long?, latest: Long?): Long? =
        if (carThere || latest != before) latest else null
}
