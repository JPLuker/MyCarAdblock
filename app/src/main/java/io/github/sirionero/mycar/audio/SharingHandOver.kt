package io.github.sirionero.mycar.audio

/**
 * Where the capture of the phone's sound stands while the phone's screen is shared with the car (see
 * AudioCaptureService.followSharing). Android keeps one projection, so the sharing's takes the place of
 * the one the capture runs on. Rather than end there, and leave the car without music for the whole
 * sharing, the capture moves onto the sharing's projection - playback capture asks only that a
 * projection be the current one, and makes no display of its own - and ends with it. No Android here:
 * the rules are tried on the JVM.
 *
 * Every move happens on the main thread; [captureEnded] is asked from a capture's own thread.
 */
internal class SharingHandOver {

    enum class Stage {
        /** On the service's own projection, as with no sharing. */
        OWN,
        /** The sharing's Start was tapped: the own projection gives way, and the capture waits for the sharing's. */
        MOVING,
        /** On the sharing's projection, which is the sharing's to stop, never the capture's. */
        SHARED,
    }

    @Volatile var stage = Stage.OWN
        private set

    /** The sharing's Start was tapped. True when a capture on its own projection, [ownAlive], moves along. */
    fun sharingStarts(ownAlive: Boolean): Boolean {
        if (stage != Stage.OWN || !ownAlive) return false
        stage = Stage.MOVING
        return true
    }

    /** The service's own projection stopped: true when that's the sharing taking its place, and the capture waits. */
    fun ownStopped(): Boolean = stage == Stage.MOVING

    /** The sharing's projection is up, its picture made: true when the capture moves onto it. */
    fun sharingUp(): Boolean {
        if (stage != Stage.MOVING) return false
        stage = Stage.SHARED
        return true
    }

    /** What the end of a capture's reading means; see [captureEnded]. */
    enum class End {
        /** Nothing to do: the capture is moving, or has moved, onto the sharing's projection, or it ended with the sharing already. */
        NOTHING,
        /** The capture on the sharing's projection ended by itself: it ends with the sharing ([sharingOver]). */
        WITH_SHARING,
        /** As with no sharing. */
        AS_BEFORE,
    }

    /**
     * A capture's reading ended. [onSharing]: it ran on the sharing's projection. [stoppedHere]: myCar
     * stopped it (the driver's Stop, the car gone), rather than its projection going.
     */
    fun captureEnded(onSharing: Boolean, stoppedHere: Boolean): End = when {
        // The own capture's end, as its projection gives way: the next one keeps the phone muted and the car's sound.
        !onSharing -> if (stage == Stage.OWN) End.AS_BEFORE else End.NOTHING
        // On the sharing's projection, once the capture ended with the sharing (or as it does): done there.
        stage != Stage.SHARED -> End.NOTHING
        stoppedHere -> End.AS_BEFORE
        else -> End.WITH_SHARING
    }

    /** What becomes of the capture when the sharing is over; see [sharingOver]. */
    enum class Over {
        /** Nothing followed the sharing. */
        NOTHING,
        /** The sharing never took the own projection: the capture goes on there. */
        STAY,
        /** The capture ends with the sharing. */
        END,
    }

    /**
     * The service goes (the driver's Stop, the sound moved to Bluetooth, the car gone). True when the
     * capture's end is said there: while it waits for the sharing's projection no reading is left to
     * say it - the own one's ends, or has ended, with [End.NOTHING].
     */
    fun serviceEnds(): Boolean = stage == Stage.MOVING

    /**
     * The sharing is over, its projection stopped or never came, or the capture on it couldn't go on.
     * [ownAlive]: the service's own projection hasn't stopped.
     */
    fun sharingOver(ownAlive: Boolean): Over {
        val over = when (stage) {
            Stage.OWN -> Over.NOTHING
            Stage.MOVING -> if (ownAlive) Over.STAY else Over.END
            Stage.SHARED -> Over.END
        }
        stage = Stage.OWN
        return over
    }
}
