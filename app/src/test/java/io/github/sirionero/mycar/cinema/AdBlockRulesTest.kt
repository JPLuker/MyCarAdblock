package io.github.sirionero.mycar.cinema

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockRulesTest {
    @Test
    fun parsesOnlyNullRoutedHostnames() {
        val rules = AdBlockRules.parseHosts(
            """
            # hosts-style domain list
            0.0.0.0 ads.example.com tracker.example.org # comment
            127.0.0.1 other.ads.example.net
            :: pixel.example.com
            192.168.1.50 my.jellyfin.local
            0.0.0.0 localhost bad..host -invalid.example.com
            0.0.0.0 ads.example.com
            """.trimIndent(),
        )
        assertEquals(setOf("ads.example.com", "tracker.example.org", "other.ads.example.net", "pixel.example.com"), rules)
    }

    @Test
    fun matchesSubdomainsWithoutMatchingLookalikes() {
        val rules = setOf("doubleclick.net", "ads.example.com")
        assertTrue(AdBlockRules.blocks("doubleclick.net", rules))
        assertTrue(AdBlockRules.blocks("A.B.DoubleClick.Net.", rules))
        assertTrue(AdBlockRules.blocks("ads.example.com", rules))
        assertFalse(AdBlockRules.blocks("notdoubleclick.net", rules))
        assertFalse(AdBlockRules.blocks("doubleclick.net.evil.example", rules))
        assertFalse(AdBlockRules.blocks("video.example.com", rules))
        assertFalse(AdBlockRules.blocks("jellyfin.local", rules))
    }

    @Test
    fun ignoresAdblockSyntaxNotInHostsFormat() {
        val rules = AdBlockRules.parseHosts(
            """
            ||ads.example.com^
            @@||safe.example.com^
            0.0.0.0 ads.example.com
            0.0.0.0 example.com/path
            """.trimIndent(),
        )
        assertEquals(setOf("ads.example.com"), rules)
    }
}
