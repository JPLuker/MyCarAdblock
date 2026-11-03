package io.github.sirionero.mycar.media

import java.util.concurrent.atomic.AtomicInteger

/**
 * What the driver asks of the music, counted: play, ▶, next, previous, a track from the list, a
 * song by voice. A pause put off until later ([unlessAskedSince]) happens only if nothing was asked
 * in between: "pause", then at once "resume", "next", "play X" or ▶, and the pause sent again once
 * myCar gave the music its focus back (VoiceFocus.afterRelease) stopped the music a moment later.
 */
internal class MusicAsks {

    private val count = AtomicInteger()

    fun asked() {
        count.incrementAndGet()
    }

    /** [action] for later, which does nothing if the music was asked for anything after this call. */
    fun unlessAskedSince(action: () -> Unit): () -> Unit {
        val at = count.get()
        return { if (count.get() == at) action() }
    }
}
