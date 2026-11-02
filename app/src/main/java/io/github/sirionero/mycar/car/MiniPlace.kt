package io.github.sirionero.mycar.car

/**
 * Where the mini player over the map sits: fractions of the room it can move in, 0 at the left
 * or top, 1 at the right or bottom. Fractions, not pixels, so it stays in the same corner on a
 * small screen and a wide one, and at every size of the car's screen.
 */
internal data class MiniPlace(val x: Float, val y: Float) {

    /** Saved as "x,y", always with a dot: Float.toString doesn't follow the phone's language. */
    fun saved(): String = "$x,$y"

    companion object {
        /** Bottom right: clear of a map's list card (left), its buttons (top right), the trip's time (bottom left). */
        val CORNER = MiniPlace(1f, 1f)

        fun parse(saved: String): MiniPlace? {
            val parts = saved.split(',')
            if (parts.size != 2) return null
            val x = parts[0].trim().toFloatOrNull() ?: return null
            val y = parts[1].trim().toFloatOrNull() ?: return null
            if (x.isNaN() || y.isNaN()) return null
            return MiniPlace(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
        }

        /**
         * Where it starts until the driver moves it, in an area [areaW] x [areaH] where it is [ownW] x
         * [ownH] (dp; the area is the map inside [MINI_EDGE] and above [MINI_DOTS_STRIP]). The bottom
         * right ([CORNER]) where the map leaves that clear; on a small map (an 800x480 screen at a large
         * size) the corner is beside the trip's time card or under the map's buttons (TemplateChrome),
         * so it starts above the one and to the left of the other.
         */
        fun start(areaW: Float, areaH: Float, ownW: Float, ownH: Float): MiniPlace {
            val roomW = areaW - ownW
            val roomH = areaH - ownH
            var x = roomW
            var y = roomH
            // Beside the trip's time card (bottom left): above it.
            if (x < TRIP_CARD_WIDTH) y = roomH - TRIP_CARD_RISE
            // Under the map's buttons (top right): to their left, which may take it over the trip's time.
            if (y < BUTTONS_REACH) {
                x = roomW - BUTTONS_STEP
                if (x < TRIP_CARD_WIDTH) y = minOf(y, roomH - TRIP_CARD_RISE)
            }
            if (x == roomW && y == roomH) return CORNER
            return MiniPlace(
                if (roomW > 0) (x / roomW).coerceIn(0f, 1f) else 1f,
                if (roomH > 0) (y / roomH).coerceIn(0f, 1f) else 1f,
            )
        }

        /** From its top-left corner in pixels, in a room [roomW] x [roomH] beyond its own size. No room that way: the far end. */
        fun of(left: Float, top: Float, roomW: Int, roomH: Int) = MiniPlace(
            if (roomW > 0) (left / roomW).coerceIn(0f, 1f) else 1f,
            if (roomH > 0) (top / roomH).coerceIn(0f, 1f) else 1f,
        )
    }
}

/** Its margin from the map's sides and top, in dp. */
internal const val MINI_EDGE = 12f

/** Its margin from the map's bottom, in dp: clear of the page dots (44 dp, with 8 under and over them; see HomePager). */
internal const val MINI_DOTS_STRIP = 60f

/** Its size, in dp, until it's measured: the cover, back, play, next and the X (see MapMiniPlayer). */
internal const val MINI_WIDTH = 276f
internal const val MINI_HEIGHT = 64f

// The map's own chrome near its corners (TemplateChrome), in dp from the edges of the mini player's area:
/** The trip's time card at the bottom left: about this wide. */
private const val TRIP_CARD_WIDTH = 240f
/** How far above the area's bottom the mini player ends to clear that card (its top, and 8 dp). */
private const val TRIP_CARD_RISE = 40f
/** How far down the map's buttons at the top right reach: an app's row of actions and four map buttons, 54 dp each. */
private const val BUTTONS_REACH = 302f
/** How far left of the area's right edge the mini player ends to clear those buttons (their width, and 8 dp). */
private const val BUTTONS_STEP = 62f

/**
 * Whether the mini player shows: music on (through the column's short wait too) over a map that has
 * the whole of Home - nothing beside it - on the map's page in view, not while the map is panned,
 * not put away for the drive with its X, and not turned off.
 */
internal fun miniPlayerShows(on: Boolean, hidden: Boolean, music: Boolean, besideMap: Boolean, inView: Boolean, panning: Boolean): Boolean =
    on && !hidden && music && !besideMap && inView && !panning
