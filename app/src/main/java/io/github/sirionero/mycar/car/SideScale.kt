package io.github.sirionero.mycar.car

/** The side's share of the screen beside the map at Small: one part to the map's 2.1. */
internal const val MAP_SHARE = 3.1f

/**
 * How big the music beside the map is drawn: the size chosen for it ([wanted], see
 * CarSettings.playerSize), but no bigger than leaves the map [mapMin] wide (an app's card over it)
 * and the column tall enough for its X above the glass pane ([columnNeeds]). Never under 1, the
 * column at Small, as it always was. [width] and [height] are the map's page in dp, the gaps round the
 * two panels and between them taken off: an Extra large player on a small screen at a large size left
 * the map narrower than an app's card, and the pane over the X.
 */
internal fun sideScaleFor(wanted: Float, width: Float, height: Float, mapMin: Float): Float {
    if (wanted <= 1f || width <= 0f || height <= 0f) return wanted
    // The map is width * (1 - scale / MAP_SHARE) wide.
    val byMap = MAP_SHARE * (1f - mapMin / width)
    // The column, in its own dp, is width / MAP_SHARE wide at any size, and height / scale tall.
    val byHeight = height / columnNeeds(width / MAP_SHARE)
    return minOf(wanted, byMap, byHeight).coerceAtLeast(1f)
}

/**
 * The height the docked player needs in a column [width] wide, both in the column's own dp: its X at
 * the top (10 dp down, 52 tall) over the glass pane, whose buttons shrink in a narrow column (see
 * GlassDockedPlayer).
 */
internal fun columnNeeds(width: Float): Float {
    // Inside the pane's margin (10 dp) and padding (12 dp) on either side.
    val inside = width - 44f
    val skip = minOf(48f, inside / 3.3f)
    val mode = minOf(44f, inside / 5f)
    // The X's 62; the pane's margins and padding (44), title (24), artist (20), seek line (36), the
    // gap between its two rows of buttons (8); play a step bigger than skip.
    return 62f + 132f + skip * 1.25f + mode
}
