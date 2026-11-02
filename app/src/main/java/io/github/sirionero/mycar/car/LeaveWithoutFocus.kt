package io.github.sirionero.mycar.car

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.sirionero.mycar.MyCarLog

/*
 * A view inside the car's screen (a web page) that has the focus must lose it before Compose takes
 * it off the screen. Otherwise Compose crashes: "LayoutNode should be attached to an owner". This
 * happened in 0.6.7-0.6.9 after a tap in Plancia's page in a part of a Home page, then a turn back
 * to the map or the app grid. The preview's crash-* shots reproduce it every time.
 *
 * As the view goes, Android hands its focus on, and Compose's focus search measures the pager again
 * while the part is only halfway through the removal. The part is measured with the view still in
 * it and isn't marked for measuring again, so the next frame places a node that's no longer on
 * screen. If the focus is taken first, in an effect after the frame, the removal has no focus to
 * hand on.
 */

/**
 * True while [shows] is, and for one frame more after it turns false. In that frame [letGo] takes
 * the focus from what's about to go, so it leaves the screen without it.
 */
@Composable
internal fun leavesWithoutFocus(shows: Boolean, letGo: () -> String?): Boolean {
    var here by remember { mutableStateOf(shows) }
    LaunchedEffect(shows) {
        // Only on the way out: a part made out of view must leave alone a page typed into elsewhere.
        if (!shows && here) letGo()?.let { MyCarLog.i("focus: taken from $it before it leaves the screen") }
        here = shows
    }
    return shows || here
}

/**
 * Takes the focus from a view inside the car's screen (a web page tapped), never from Compose's
 * own controls. Returns what had it, for the log, or null if nothing did.
 */
internal fun View.dropInteropFocus(): String? {
    val focused = findFocus() ?: return null
    if (focused === this) return null
    focused.clearFocus()
    return focused.javaClass.simpleName
}
