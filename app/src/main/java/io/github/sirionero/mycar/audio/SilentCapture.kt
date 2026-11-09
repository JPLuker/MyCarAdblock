package io.github.sirionero.mycar.audio

/**
 * When the capture's silence is worth a line in the log. An app that doesn't let its audio be
 * captured hands back exact zeros - the same as a broken link to the car - but so does every app
 * before it starts: the warning came 5 s into each capture, while Spotify Web was still loading.
 * Now only once the car's music has said it plays for [holdMs] and the capture has heard nothing for
 * as long; once per stretch of playing, again after sound or a pause. Without Android (SilentCaptureTest).
 */
internal class SilentCapture(startedAt: Long, private val holdMs: Long = HOLD_MS) {
    private var soundAt = startedAt
    private var playingSince = NONE
    private var said = false

    /**
     * A piece of the capture read at [now] (ms): [sound], any sample not zero; [playing], the car's
     * music says it plays (not myCar's own, which goes straight to the car). True: say it now.
     */
    fun piece(now: Long, sound: Boolean, playing: Boolean): Boolean {
        if (sound) {
            soundAt = now
            said = false
        }
        if (!playing) {
            playingSince = NONE
            said = false
            return false
        }
        if (playingSince == NONE) playingSince = now
        if (said || now - playingSince < holdMs || now - soundAt < holdMs) return false
        said = true
        return true
    }

    companion object {
        const val HOLD_MS = 5_000L
        private const val NONE = -1L
    }
}
