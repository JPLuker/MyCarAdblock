package io.github.sirionero.mycar.wireless

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a start-up waiting for the car calls the car's own service again. */
class KnockPlanTest {

    private val step = 8_000L

    /** The calls of a wait where none answers, each failing after [callMs], until [until]. */
    private fun calls(waitFrom: Long, arrival: Long?, quick: Boolean, callMs: Long, until: Long): List<Long> {
        val out = mutableListOf<Long>()
        var lastEnd = waitFrom
        while (true) {
            val due = KnockPlan.next(lastEnd, arrival, quick, step)
            if (due.at > until) return out
            out += due.at
            lastEnd = due.at + callMs
        }
    }

    @Test
    fun `the car's Bluetooth there, its service not yet - 3 s after it came, then every 2 s, then the usual step`() {
        // The car's Bluetooth came at 0, the first call failed 0.2 s later.
        val at = calls(waitFrom = 200, arrival = 0, quick = true, callMs = 200, until = 32_000)
        assertEquals(3_000L, at.first())
        assertTrue(at.zipWithNext().takeWhile { it.second <= 15_000 }.all { it.second - it.first <= 2_200 })
        assertTrue(at.filter { it <= 15_000 }.size >= 5)
        // After 15 s, the usual 8 s step.
        val late = at.filter { it > 15_000 }
        assertEquals(step, late[1] - late[0] - 200)
    }

    @Test
    fun `never sooner than 3 s after the car's Bluetooth came`() {
        assertEquals(3_000L, KnockPlan.next(lastEnd = 100, arrival = 0, quick = true, step = step).at)
        // Came again during the wait: counted from then.
        val due = KnockPlan.next(lastEnd = 20_000, arrival = 21_000, quick = true, step = step)
        assertEquals(24_000L, due.at)
        assertTrue(due.quick)
    }

    @Test
    fun `without quick calls - the usual step, as before`() {
        val due = KnockPlan.next(lastEnd = 200, arrival = 0, quick = false, step = step)
        assertEquals(8_200L, due.at)
        assertFalse(due.quick)
        // The car's Bluetooth not known to be there.
        assertEquals(8_200L, KnockPlan.next(lastEnd = 200, arrival = null, quick = true, step = step).at)
    }

    @Test
    fun `a car's Bluetooth that came long ago - the usual step`() {
        val due = KnockPlan.next(lastEnd = 60_000, arrival = 0, quick = true, step = step)
        assertEquals(68_000L, due.at)
        assertFalse(due.quick)
    }

    @Test
    fun `a channel closed at first contact`() {
        // Closed 2.6 s after it opened, before the Wi-Fi details: the adapter's first-contact closing.
        assertTrue(KnockPlan.closedEarly(openedAt = 10_000, closedAt = 12_600, gotWifiDetails = false))
        // The Wi-Fi details came: a closing that leads on to the Wi-Fi.
        assertFalse(KnockPlan.closedEarly(openedAt = 10_000, closedAt = 12_600, gotWifiDetails = true))
        // Closed 11 s on: the adapter gave up on the phone's Wi-Fi, not a first contact.
        assertFalse(KnockPlan.closedEarly(openedAt = 10_000, closedAt = 21_000, gotWifiDetails = false))
    }

    @Test
    fun `a quick call's channel closed at first contact costs no start-over - on the first start-up too`() {
        // The first start-up has all three: given one back, the start-over that follows leaves three.
        assertEquals(4, KnockPlan.startOversLeft(left = 3, quickChannel = true, startsOver = true, overPending = false))
        assertEquals(2, KnockPlan.startOversLeft(left = 1, quickChannel = true, startsOver = true, overPending = false))
        // A channel the first call or the usual step opened pays as before.
        assertEquals(3, KnockPlan.startOversLeft(left = 3, quickChannel = false, startsOver = true, overPending = false))
        // A tap doesn't start over by itself; a start-over already on its way isn't taken twice.
        assertEquals(3, KnockPlan.startOversLeft(left = 3, quickChannel = true, startsOver = false, overPending = false))
        assertEquals(2, KnockPlan.startOversLeft(left = 2, quickChannel = true, startsOver = true, overPending = true))
    }
}
