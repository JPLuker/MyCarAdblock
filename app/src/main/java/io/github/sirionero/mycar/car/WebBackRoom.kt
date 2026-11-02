package io.github.sirionero.mycar.car

import kotlin.math.ceil

/** myCar's Back on a music service's page (WebMusicApp): 4 dp of margin and a 52 dp touch. */
internal const val WEB_BACK_DP = 56f

/**
 * The room at the left of a music service's top bar for myCar's Back, in the page's CSS pixels: the
 * page is laid out [cssWidth] px wide in its [widthPx] real pixels, and a dp of the car's screen is
 * [dpPx] real pixels (density times myCar's size). At the page's own width a CSS pixel is a dp; laid
 * out wider than its place (WebMusicPage.MIN_WIDTH, 800) a CSS pixel is smaller, and a fixed 52 px
 * left the Back over the service's menu - YouTube Music's, at 800x480 in Large and Extra large.
 */
internal fun webBackRoomCss(dpPx: Float, cssWidth: Int, widthPx: Int): Int {
    if (dpPx <= 0f || cssWidth <= 0 || widthPx <= 0) return WEB_BACK_DP.toInt()
    return ceil(WEB_BACK_DP * dpPx * cssWidth / widthPx).toInt()
}
