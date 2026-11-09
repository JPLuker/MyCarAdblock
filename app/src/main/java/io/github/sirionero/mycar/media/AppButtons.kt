package io.github.sirionero.mycar.media

/**
 * A music app's own buttons (its custom actions), read without Android: whether a toggle like heart,
 * shuffle or repeat is on - the app says it only through the button's icon (CarMedia gives the
 * icon's name, and whether the app also has an outline of it) - and where a button of myCar's goes.
 */
internal object AppButtons {

    /**
     * Whether a toggle is on. Read from the icon's name: Tidal swaps ic_favorite for
     * ic_favorite_filled and ic_shuffle for ic_shuffle_active, and keeps the button's name - whole
     * words only, so "inactive" doesn't count as "active". Or the icon is the plain one of a pair
     * whose other is an outline ([hasOutline]): Metrolist swaps ic_heart_outline for ic_heart.
     */
    fun on(iconName: String, hasOutline: Boolean): Boolean =
        hasOutline || iconName.lowercase().split('_', '.', '-').any { it in ON_WORDS }

    /**
     * The names an outline of the icon [iconName] would have in the app (ic_heart: ic_heart_outline,
     * ic_heart_border…), for CarMedia to look for; none when it has no name or is an outline already.
     */
    fun outlineNames(iconName: String): List<String> =
        if (iconName.isEmpty() || OUTLINE_WORDS.any { iconName.contains(it, ignoreCase = true) }) emptyList()
        else OUTLINE_WORDS.map { "${iconName}_$it" }

    /**
     * [actions] with [extra] second (first when there are none): the full-screen player shows an
     * app's first two buttons only, and AntennaPod's are Rewind and Fast-forward, so a third would
     * never show. Second, Rewind stays beside it.
     */
    fun <T> secondPlace(actions: List<T>, extra: T): List<T> = actions.toMutableList().apply { add(minOf(1, size), extra) }

    private val ON_WORDS = setOf("filled", "fill", "active", "on", "selected", "checked", "enabled", "solid", "one", "all")

    /** How apps name the empty version of an icon. */
    private val OUTLINE_WORDS = listOf("outline", "outlined", "border", "empty")
}
