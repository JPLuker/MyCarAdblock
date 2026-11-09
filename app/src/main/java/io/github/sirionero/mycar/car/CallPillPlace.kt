package io.github.sirionero.mycar.car

import java.util.Locale

/** A rectangle in dp of the car's screen beside the dock, or of an area inside it. */
internal data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    /** The same rectangle seen from an area whose corner is [dx], [dy] dp away: into that area's own dp. */
    fun moved(dx: Float, dy: Float) = ScreenRect(left + dx, top + dy, right + dx, bottom + dy)

    /** Whether it comes nearer to [other] than [gap] dp, or over it (half a dp spared: sums of fractions aren't exact). */
    fun near(other: ScreenRect, gap: Float): Boolean {
        val g = gap - PILL_SLACK
        return left < other.right + g && other.left < right + g && top < other.bottom + g && other.top < bottom + g
    }
}

/**
 * Where a call's pill sits over the car's screen (see CallPill): a place as the mini player over the map
 * has one ([MiniPlace]: fractions of the room it can move in, so it keeps its spot at every size), and how
 * that mini player keeps out of its way.
 */
internal object CallPillPlace {

    /** Along the top, in the middle: where it starts on a screen with room on every side. */
    private val TOP = MiniPlace(0.5f, 0f)

    /**
     * Where it starts until the driver moves it, in an area [areaW] wide where it is [ownW] wide (dp: the
     * screen beside the dock inside [PILL_EDGE]): along the top, where the call's band was, in the middle -
     * but right of the card of the next turn a map shows at its top left (which the dock shows too, during a
     * call) and left of the map's buttons at its top right, where there's room between them. Where there
     * isn't (an 800x480 screen at a large size), left of the buttons all the same: over the end of the card,
     * not over a button.
     */
    fun start(areaW: Float, ownW: Float): MiniPlace {
        val roomW = areaW - ownW
        if (roomW <= 0f) return TOP
        val x = maxOf(roomW / 2, turnCardEnd(areaW))
            .coerceAtMost(areaW - PILL_BUTTONS_STEP - ownW)
            .coerceIn(0f, roomW)
        return MiniPlace(x / roomW, 0f)
    }

    /**
     * How much smaller it's drawn where it doesn't fit beside the card a map shows at its left, as the mini
     * player is ([MiniPlace.fitScale], in an area [areaW] wide): on an 800x480 screen at Extra large about
     * its size at Normal there; 1 where it fits.
     */
    fun fitScale(areaW: Float): Float = MiniPlace.fitScale(areaW, PILL_WIDTH)

    /**
     * Where the mini player over the map goes while a call's pill is over the map: where it rests ([rest],
     * in its own area [areaW] x [areaH] where it is [ownW] x [ownH], dp) unless the pill ([pill], in that
     * area's dp) is there or next to it. Then the pill wins and the mini player moves to the nearest place
     * clear of it - just above or below it, just left or right of it, or a corner - kept clear of the map's
     * buttons as a place it's let go at is. Where nothing is clear (a map too small for both), it stays as
     * it rests, under the pill.
     */
    fun miniApart(rest: MiniPlace, pill: ScreenRect, areaW: Float, areaH: Float, ownW: Float, ownH: Float): MiniPlace {
        val roomW = areaW - ownW
        val roomH = areaH - ownH
        if (roomW <= 0f || roomH <= 0f) return rest
        fun at(place: MiniPlace) = ScreenRect(place.x * roomW, place.y * roomH, place.x * roomW + ownW, place.y * roomH + ownH)
        if (!at(rest).near(pill, PILL_GAP)) return rest
        val restX = rest.x * roomW
        val restY = rest.y * roomH
        fun away(place: MiniPlace): Float {
            val dx = place.x * roomW - restX
            val dy = place.y * roomH - restY
            return dx * dx + dy * dy
        }
        val ways = listOf(
            restX to pill.top - PILL_GAP - ownH,
            restX to pill.bottom + PILL_GAP,
            pill.left - PILL_GAP - ownW to restY,
            pill.right + PILL_GAP to restY,
            0f to 0f, roomW to 0f, 0f to roomH, roomW to roomH,
        )
        return ways
            .map { (x, y) ->
                MiniPlace.clearOfButtons(
                    MiniPlace((x / roomW).coerceIn(0f, 1f), (y / roomH).coerceIn(0f, 1f)),
                    areaW, areaH, ownW, ownH,
                )
            }
            .filter { !at(it).near(pill, PILL_GAP) }
            .minByOrNull(::away)
            ?: rest
    }

    /** Where the card of the next turn ends at a map's top left, with the gap after it, in an area [areaW] wide. */
    private fun turnCardEnd(areaW: Float): Float {
        val mapW = areaW + 2 * PILL_EDGE
        return PILL_CHROME_PAD + minOf(PILL_TURN_CARD_MAX, mapW * PILL_TURN_CARD_SHARE) + PILL_GAP - PILL_EDGE
    }
}

/** A call's time as a phone shows it: "0:07", "12:34", "1:02:03". */
internal fun callClock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0L)
    val hours = s / 3_600
    val minutes = s / 60 % 60
    val rest = s % 60
    return if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, rest) else "%d:%02d".format(Locale.ROOT, minutes, rest)
}

/** Its margin from the sides, the top and the bottom of the screen beside the dock, in dp: the mini player's ([MINI_EDGE]). */
internal const val PILL_EDGE = 12f

/** Its width in dp, until it's drawn smaller ([CallPillPlace.fitScale]): the caller's photo, their name and the call's time, the red button. */
internal const val PILL_WIDTH = 296f

/** The room it leaves between itself and the mini player, and between itself and a map's card, in dp. */
private const val PILL_GAP = 8f

/** How far left of the area's right edge it ends to clear a map's buttons at the top right (54 dp, 12 from the edge, and 8). */
private const val PILL_BUTTONS_STEP = 62f

/** The card of the next turn at a map's top left (TemplateChrome's RoutingCard): at most this wide in dp... */
private const val PILL_TURN_CARD_MAX = 320f

/** ...and at most this share of the map's width. */
private const val PILL_TURN_CARD_SHARE = 0.6f

/** How far in from a map's edges its cards and buttons sit, in dp (TemplateChrome). */
private const val PILL_CHROME_PAD = 12f

/** Half a dp: places are fractions, and the sums back to dp don't come out exact. */
private const val PILL_SLACK = 0.5f
