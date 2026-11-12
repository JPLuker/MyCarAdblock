package io.github.sirionero.mycar.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaExtensionPoliciesTest {
    @Test fun sponsorBlockDefaultsCanBeSynchronizedBeforeFirstVisit() {
        assertTrue(CinemaSponsorTransitionPolicy.needsSync(false, null, null))
        assertFalse(CinemaSponsorTransitionPolicy.allowsNavigation(false, null, null))
        assertTrue(CinemaSponsorTransitionPolicy.allowsNavigation(false, false, null))
    }

    @Test fun enablingSponsorBlockRequiresConfirmedState() {
        assertTrue(CinemaSponsorTransitionPolicy.needsSync(true, false, null))
        assertFalse(CinemaSponsorTransitionPolicy.allowsNavigation(true, false, null))
        assertTrue(CinemaSponsorTransitionPolicy.allowsNavigation(true, true, null))
    }

    @Test fun optionalSponsorFailureIsVisibleButDoesNotBlockPage() {
        assertFalse(CinemaSponsorTransitionPolicy.needsSync(true, false, true))
        assertTrue(CinemaSponsorTransitionPolicy.allowsNavigation(true, false, true))
        assertTrue(CinemaSponsorTransitionPolicy.needsSync(false, true, null))
        assertFalse(CinemaSponsorTransitionPolicy.allowsNavigation(false, true, false))
        assertFalse(CinemaSponsorTransitionPolicy.allowsNavigation(null, null, null))
    }

    @Test fun curatedExtensionsCannotBeRemovedThroughUserManager() {
        assertFalse(CinemaExtensionManagementPolicy.userManaged("uBlock0@raymondhill.net"))
        assertFalse(CinemaExtensionManagementPolicy.userManaged("sponsorBlocker@ajay.app"))
        assertFalse(CinemaExtensionManagementPolicy.userManaged(""))
        assertTrue(CinemaExtensionManagementPolicy.userManaged("example-addon@example.org"))
    }

    @Test fun oversizedPermissionPromptsAreRejectedInsteadOfTruncated() {
        CinemaExtensionConsent.decide(false)
        val excessive = Array(51) { "host-$it" }
        assertFalse(CinemaExtensionConsent.offer(
            "example@example.org", emptyArray(), excessive, emptyArray(),
        ) {})
        assertFalse(CinemaExtensionConsent.offer(
            "example@example.org", emptyArray(), arrayOf("x".repeat(301)), emptyArray(),
        ) {})
        assertNull(CinemaExtensionConsent.pending.value)
    }

    @Test fun userSelectedXpiPermissionsRequireAnExplicitDecision() {
        CinemaExtensionConsent.decide(false)
        var decision: Boolean? = null
        val offered = CinemaExtensionConsent.offer(
            "example@example.org", arrayOf("storage"), arrayOf("https://example.org/*"), emptyArray(),
        ) { decision = it }
        assertTrue(offered)
        assertNull(decision)
        val pending = CinemaExtensionConsent.pending.value
        assertEquals("example@example.org", pending?.id)
        assertEquals(listOf("storage"), pending?.permissions)
        assertFalse(CinemaExtensionConsent.offer("other@example.org", emptyArray(), emptyArray(), emptyArray()) {})
        CinemaExtensionConsent.decide(false)
        assertEquals(false, decision)
        assertNull(CinemaExtensionConsent.pending.value)
    }
}
