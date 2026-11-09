package io.github.sirionero.mycar.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A music app's own like, shuffle and repeat buttons: on or off, from their icons. */
class AppButtonsTest {

    @Test fun `Tidal says it in the icon's name`() {
        assertTrue(AppButtons.on("ic_favorite_filled", hasOutline = false))
        assertFalse(AppButtons.on("ic_favorite", hasOutline = false))
        assertTrue(AppButtons.on("ic_shuffle_active", hasOutline = false))
        assertFalse(AppButtons.on("ic_shuffle", hasOutline = false))
        assertTrue(AppButtons.on("ic_repeat_one", hasOutline = false))
        // Whole words: "inactive" is not "active".
        assertFalse(AppButtons.on("ic_shuffle_inactive", hasOutline = false))
    }

    @Test fun `Metrolist swaps an outline for the plain icon`() {
        // Liked: the plain heart, of which the app also has an outline.
        assertTrue(AppButtons.on("ic_heart", hasOutline = true))
        // Not liked: the outline itself, of which there's no outline again.
        assertEquals(emptyList<String>(), AppButtons.outlineNames("ic_heart_outline"))
        assertFalse(AppButtons.on("ic_heart_outline", hasOutline = false))
    }

    @Test fun `the outlines looked for`() {
        assertEquals(
            listOf("ic_heart_outline", "ic_heart_outlined", "ic_heart_border", "ic_heart_empty"),
            AppButtons.outlineNames("ic_heart"),
        )
        assertEquals(emptyList<String>(), AppButtons.outlineNames(""))
        assertEquals(emptyList<String>(), AppButtons.outlineNames("ic_favorite_border"))
        assertEquals(emptyList<String>(), AppButtons.outlineNames("ic_star_empty"))
    }
}
