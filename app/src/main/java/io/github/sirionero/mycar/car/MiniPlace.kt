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

        /** From its top-left corner in pixels, in a room [roomW] x [roomH] beyond its own size. No room that way: the far end. */
        fun of(left: Float, top: Float, roomW: Int, roomH: Int) = MiniPlace(
            if (roomW > 0) (left / roomW).coerceIn(0f, 1f) else 1f,
            if (roomH > 0) (top / roomH).coerceIn(0f, 1f) else 1f,
        )
    }
}

/**
 * Whether the mini player shows: music on (through the column's short wait too) over a map that has
 * the whole of Home - nothing beside it - on the map's page in view, not while the map is panned,
 * not put away for the drive with its X, and not turned off.
 */
internal fun miniPlayerShows(on: Boolean, hidden: Boolean, music: Boolean, besideMap: Boolean, inView: Boolean, panning: Boolean): Boolean =
    on && !hidden && music && !besideMap && inView && !panning
