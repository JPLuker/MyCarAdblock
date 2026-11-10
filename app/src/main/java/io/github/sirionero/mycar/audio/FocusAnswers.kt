package io.github.sirionero.mycar.audio

import io.github.sirionero.mycar.MyCarLog

/**
 * The car's AudioFocusNotification, which comes on the control channel for the whole phone: the
 * car's focus is the phone's, not a sink's. The answer goes to the directions while they wait on one
 * of their own ([AudioLink.askForSpeakers], [AudioLink.handBackSpeakers], up to half a second), as in
 * Gearslip, and to the media sink otherwise.
 *
 * Not porting this before (Gearslip dc61607) was because an answer meant for the directions could
 * reach the music late and start it. That is closed on both sides: a grant the music never asked for
 * starts nothing ([AudioLink.onFocus]), and two answers no longer go the wrong way here:
 * - a GAIN while the music waits for its own is the music's: it answers a GAIN, the directions
 *   having asked for the speakers only for a moment (a car that answers them with GAIN too, while the
 *   music waits, lets them speak after their half second anyway);
 * - a LOSS soon after the directions gave the speakers back, with the music asking or starting since,
 *   is the car's late answer to that RELEASE: it would stop the music and pause the phone.
 */
internal object FocusAnswers {

    /** How soon after the directions' RELEASE a LOSS is taken for its late answer, the music having asked since. */
    const val LATE_LOSS_MS = 1_500L

    /** The car's answer [state], to the sink it's for. From the session's reading thread. */
    fun route(state: Int, media: AudioLink?, guidance: AudioLink?) {
        val musicsGain = media != null && media.awaitsGrant &&
            (state == AudioLink.FOCUS_STATE_GAIN || state == AudioLink.FOCUS_STATE_GAIN_MEDIA_ONLY)
        if (guidance != null && guidance.expectsFocusAnswer() && !musicsGain) {
            guidance.onFocus(state)
            return
        }
        if (media == null) return
        if (state == AudioLink.FOCUS_STATE_LOSS && guidance != null && lateForRelease(media.askedAt, guidance.releasedAt, media.now())) {
            MyCarLog.i("audio: <- AudioFocusResponse state=LOSS, the car's late answer to the directions' RELEASE - the music goes on")
            return
        }
        media.onFocus(state)
    }

    /** A LOSS now answers the directions' RELEASE at [releasedAt]: the music asked at [askedAt], after it, and it's still recent. */
    fun lateForRelease(askedAt: Long, releasedAt: Long, now: Long): Boolean =
        askedAt > releasedAt && now - releasedAt < LATE_LOSS_MS
}
