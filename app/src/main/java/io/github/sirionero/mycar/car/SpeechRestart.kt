package io.github.sirionero.mycar.car

/**
 * When the phone's text-to-speech engine is made again, for the spoken turns (DirectionsVoice) and
 * the assistant (CarAssistant): one whose start said no, one that later refused to speak (updated
 * in the background: Android never binds it again), and one whose start never said anything at
 * all - each once its last start has had [AFTER_MS], inside a drive too. A start with no answer
 * counted as none of these, and the rest only at the next drive: in Gearslip one such start left
 * 21 turns unspoken in 23 minutes (issue #35).
 */
internal object SpeechRestart {

    /** How long a start has to say the engine is ready, and the least time between two starts. */
    const val AFTER_MS = 30_000L

    /**
     * Whether to make the engine again at [now]: it can't speak ([ready] not said, or [failed]), and
     * its last start, at [startedAt] on the same clock, has had its time.
     */
    fun due(ready: Boolean, failed: Boolean, startedAt: Long, now: Long): Boolean =
        (failed || !ready) && now - startedAt >= AFTER_MS
}
