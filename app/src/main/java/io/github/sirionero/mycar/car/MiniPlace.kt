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
         * right ([CORNER]) where the map leaves that clear. On a small map (an 800x480 screen at a large
         * size) the corner can be under the map's buttons (top right) or beside the trip's time card
         * (bottom left; see TemplateChrome): it moves left of the buttons, but never over the card a map
         * shows at its left (a list of places, a search), which hides it whole - and above the trip's time.
         * Where there's no room between the card and the buttons and it would sit under the buttons most
         * maps have (800x480 at Extra large), left of the buttons all the same: they're always there, the
         * card only with a list open, and under them its first touch only woke them and its X sat under
         * the map's zoom.
         */
        fun start(areaW: Float, areaH: Float, ownW: Float, ownH: Float): MiniPlace {
            val roomW = areaW - ownW
            val roomH = areaH - ownH
            var x = roomW
            var y = roomH
            if (y < BUTTONS_REACH) {
                val besideCard = maxOf((areaW + 2 * MINI_EDGE) * PANE_FRACTION, PANE_MIN_EDGE) - MINI_EDGE + GAP
                x = maxOf(roomW - BUTTONS_STEP, besideCard).coerceAtMost(roomW)
                if (besideCard > roomW - BUTTONS_STEP && y < BUTTONS_REACH_MOST) x = (roomW - BUTTONS_STEP).coerceAtLeast(0f)
            }
            if (x < TRIP_CARD_WIDTH) y = minOf(y, roomH - TRIP_CARD_RISE)
            if (x == roomW && y == roomH) return CORNER
            return MiniPlace(
                if (roomW > 0) (x / roomW).coerceIn(0f, 1f) else 1f,
                if (roomH > 0) (y / roomH).coerceIn(0f, 1f) else 1f,
            )
        }

        /**
         * How much smaller it's drawn on a map too small for it beside the card a map shows at its left
         * (an 800x480 screen at Extra large: 173 dp of room for 276): to fit that room, never under
         * [MIN_FIT] (about its size at the Normal size there). 1 where it fits.
         */
        fun fitScale(areaW: Float, ownW: Float = MINI_WIDTH): Float {
            val besideCard = maxOf((areaW + 2 * MINI_EDGE) * PANE_FRACTION, PANE_MIN_EDGE) - MINI_EDGE + GAP
            val room = areaW - besideCard
            if (ownW <= 0f || room >= ownW) return 1f
            return (room / ownW).coerceIn(MIN_FIT, 1f)
        }

        /**
         * Where it's let go, but not under the map's buttons at the top right, which are drawn over it
         * (a tap on its Pause went to Search): from under the app's row of actions just below it, and
         * from beside the map's buttons left of them, or below them where left would put it over the
         * card a map shows at its left. Elsewhere it stays where it was let go. Same units as [start].
         */
        fun clearOfButtons(at: MiniPlace, areaW: Float, areaH: Float, ownW: Float, ownH: Float): MiniPlace {
            val roomW = areaW - ownW
            val roomH = areaH - ownH
            if (roomW <= 0f || roomH <= 0f) return at
            var x = at.x * roomW
            var y = at.y * roomH
            val besideCard = cardEdge(areaW)
            // The app's row reaches left from the right edge as far as its actions do: right of the card, clear of it.
            if (y < ROW_CLEAR && x + ownW > besideCard) y = ROW_CLEAR.coerceAtMost(roomH)
            if (x > roomW - BUTTONS_STEP && y < BUTTONS_REACH_MOST) {
                when {
                    roomW - BUTTONS_STEP >= besideCard -> x = roomW - BUTTONS_STEP
                    BUTTONS_REACH_MOST <= roomH -> y = BUTTONS_REACH_MOST
                    else -> x = (roomW - BUTTONS_STEP).coerceAtLeast(0f)
                }
            }
            return MiniPlace((x / roomW).coerceIn(0f, 1f), (y / roomH).coerceIn(0f, 1f))
        }

        /** Where the card a map shows at its left ends, with the gap after it, in an area [areaW] wide. */
        private fun cardEdge(areaW: Float) = maxOf((areaW + 2 * MINI_EDGE) * PANE_FRACTION, PANE_MIN_EDGE) - MINI_EDGE + GAP

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
/** How far down they reach on most maps: an app's row of actions and three map buttons, as Vela has. */
private const val BUTTONS_REACH_MOST = 240f
/** How far down the app's row of actions at the top right reaches, and 8 dp: its buttons are 54 dp. */
private const val ROW_CLEAR = 62f
/** How far left of the area's right edge the mini player ends to clear those buttons (their width, and 8 dp). */
private const val BUTTONS_STEP = 62f
/** The card at a map's left (MapPaneLayout) ends at this fraction of the map's width, or at least at [PANE_MIN_EDGE] (MAP_PANE_MIN_EDGE). */
private const val PANE_FRACTION = 0.42f
private const val PANE_MIN_EDGE = 244f
/** The room left between the mini player and the map's cards. */
private const val GAP = 8f
/** The smallest it's drawn on a small map ([MiniPlace.fitScale]): at Extra large (1.5) about 0.9 of its Normal size. */
private const val MIN_FIT = 0.6f

/**
 * Whether the mini player shows: music on (through the column's short wait too) over a map that has
 * the whole of Home - nothing beside it - on the map's page in view, not while the map is panned,
 * not put away for the drive with its X, and not turned off.
 */
internal fun miniPlayerShows(on: Boolean, hidden: Boolean, music: Boolean, besideMap: Boolean, inView: Boolean, panning: Boolean): Boolean =
    on && !hidden && music && !besideMap && inView && !panning
