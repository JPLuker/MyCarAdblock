package io.github.sirionero.mycar.cinema

import org.junit.Assert.assertEquals
import org.junit.Test

class CinemaAdBlockTransitionPolicyTest {
    private fun action(
        desired: Boolean?,
        applied: Boolean?,
        busy: Boolean = false,
        opened: Boolean = false,
    ) = CinemaAdBlockTransitionPolicy.next(desired, applied, busy, opened)

    @Test fun firstVisitWaitsUntilBlockingIsActuallyEnabled() {
        assertEquals(CinemaAdBlockAction.SYNCHRONIZE, action(true, null))
        assertEquals(CinemaAdBlockAction.WAIT, action(true, null, busy = true))
        assertEquals(CinemaAdBlockAction.NAVIGATE, action(true, true))
    }

    @Test fun firstVisitWaitsUntilPreviouslyInstalledBlockingIsDisabled() {
        assertEquals(CinemaAdBlockAction.SYNCHRONIZE, action(false, null))
        assertEquals(CinemaAdBlockAction.SYNCHRONIZE, action(false, true))
        assertEquals(CinemaAdBlockAction.NAVIGATE, action(false, false))
    }

    @Test fun togglingWithoutReloadKeepsExistingNavigation() {
        assertEquals(CinemaAdBlockAction.SYNCHRONIZE, action(false, true, opened = true))
        assertEquals(CinemaAdBlockAction.IDLE, action(false, false, opened = true))
        assertEquals(CinemaAdBlockAction.SYNCHRONIZE, action(true, false, opened = true))
        assertEquals(CinemaAdBlockAction.IDLE, action(true, true, opened = true))
    }

    @Test fun rapidToggleDuringAnOngoingOperationDoesNotRunInParallel() {
        assertEquals(CinemaAdBlockAction.WAIT, action(false, null, busy = true))
        assertEquals(CinemaAdBlockAction.SYNCHRONIZE, action(false, true))
        assertEquals(CinemaAdBlockAction.NAVIGATE, action(false, false))
    }

    @Test fun missingSettingCannotOpenPage() {
        assertEquals(CinemaAdBlockAction.WAIT, action(null, null))
        assertEquals(CinemaAdBlockAction.WAIT, action(null, true))
    }
}
