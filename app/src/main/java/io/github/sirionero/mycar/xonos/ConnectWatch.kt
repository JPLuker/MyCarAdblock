package io.github.sirionero.mycar.xonos

import java.util.Locale

/**
 * Whether and when Xonos inside myCar reaches Music Assistant, for myCar's own log: the engine copied
 * from Xonos writes "Collegato" and its errors to logcat only, so a drive's log couldn't tell a
 * connection made after a few refusals (the server on the phone starting) from one never made.
 * Fed the connection's states (see [Xonos]), it gives the lines to write. Without Android, so it can
 * be tried on the JVM (ConnectWatchTest). Times on any one clock, in ms.
 */
internal class ConnectWatch(private val slowMs: Long = SLOW_MS) {
    /** When the trying began; [NONE] while connected or with nothing to connect. */
    private var since = NONE
    private var failures = 0
    private var problem: String? = null
    private var slowSaid = false
    private var connectedBefore = false

    /** Trying an address. */
    fun trying(now: Long) {
        if (since == NONE) start(now)
    }

    /** A try failed with [problem] (Music Assistant's [Problem] name: no addresses, no texts). */
    fun failed(now: Long, problem: String) {
        if (since == NONE) start(now)
        failures++
        this.problem = problem
    }

    /** Connected: the line that says after how long and how many tries, or null if it wasn't trying. */
    fun connected(now: Long): String? {
        if (since == NONE) return null
        val tries = failures + 1
        val line = "connected to Music Assistant ${if (connectedBefore) "again " else ""}after " +
            String.format(Locale.ROOT, "%.1f", (now - since) / 1000.0) + " s ($tries ${if (tries == 1) "try" else "tries"})"
        since = NONE
        connectedBefore = true
        return line
    }

    /** Let go (the drive ended) or no account: nothing to say about it. */
    fun idle() {
        since = NONE
        connectedBefore = false
    }

    /** How long until [slow] has something to say: null when it won't (connected, idle, or said). */
    fun slowIn(now: Long): Long? = if (since == NONE || slowSaid) null else (since + slowMs - now).coerceAtLeast(0L)

    /** The line for a connection still not made [slowMs] after the trying began, once; null before or after. */
    fun slow(now: Long): String? {
        if (since == NONE || slowSaid || now - since < slowMs) return null
        slowSaid = true
        return "still not connected to Music Assistant after ${slowMs / 1000} s ($failures failed ${if (failures == 1) "try" else "tries"}): ${problem ?: "no answer yet"}"
    }

    private fun start(now: Long) {
        since = now
        failures = 0
        problem = null
        slowSaid = false
    }

    companion object {
        const val SLOW_MS = 60_000L
        private const val NONE = -1L
    }
}
