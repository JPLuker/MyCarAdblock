package io.github.sirionero.mycar.host

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A modified copy that refused myCar is known by its install time, not by its signer. */
class PatchRefusalsTest {

    @Test
    fun theCopyThatRefusedIsStillRefused() {
        assertTrue(PatchRefusals.sameCopy(refused = 1_700_000_000_000, installed = 1_700_000_000_000))
    }

    @Test
    fun aCopyPatchedAgainIsNotTheOneThatRefused() {
        // Morphe signs every copy with the same key: only the install time tells them apart.
        assertFalse(PatchRefusals.sameCopy(refused = 1_700_000_000_000, installed = 1_700_000_900_000))
    }

    @Test
    fun nothingRememberedOrNothingInstalledIsNoRefusal() {
        assertFalse(PatchRefusals.sameCopy(refused = null, installed = 1_700_000_000_000))
        assertFalse(PatchRefusals.sameCopy(refused = 1_700_000_000_000, installed = null))
    }
}
