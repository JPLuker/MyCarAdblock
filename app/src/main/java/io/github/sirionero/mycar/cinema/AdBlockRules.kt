package io.github.sirionero.mycar.cinema

import java.util.Locale

/**
 * A small, testable hosts-file matcher. Only null-routing hosts entries are used: this is not
 * an EasyList cosmetic-filter parser, and it never blocks an entire site by a URL substring.
 */
internal object AdBlockRules {
    fun parseHosts(text: String): Set<String> {
        val domains = HashSet<String>()
        text.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val columns = line.split(Regex("\\s+"))
            if (columns.size < 2 || columns[0] !in NULL_ROUTES) return@forEach
            columns.drop(1).forEach { candidate ->
                val domain = candidate.lowercase(Locale.ROOT).trimEnd('.')
                if (validHost(domain)) domains.add(domain)
            }
        }
        return domains
    }

    /** A match covers a domain and its subdomains, not misleading lookalike suffixes. */
    fun blocks(host: String, domains: Set<String>): Boolean {
        var candidate = host.lowercase(Locale.ROOT).trimEnd('.')
        if (!validHost(candidate)) return false
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun validHost(host: String): Boolean {
        if (host.length !in 4..253 || '.' !in host || host.any { it !in VALID_CHARS }) return false
        return host.split('.').all { label ->
            label.length in 1..63 && label.first().isLetterOrDigit() && label.last().isLetterOrDigit()
        }
    }

    private val NULL_ROUTES = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private const val VALID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789-."
}
