package io.github.sirionero.mycar

/**
 * Which of the pictures a car offers fills its screen best, when no vehicle profile names one
 * (MyCarRunner). No Android here, so the choice can be tried on the JVM for any car.
 */
internal object ScreenFit {

    /**
     * [index] is the config chosen, null when none offered has a size myCar knows; [upright] says
     * whether only the upright ones counted.
     */
    class Choice(val index: Int?, val upright: Boolean)

    /**
     * The offered config ([offered] are indices into [configs]) that best fills the car's screen:
     * the smallest whose visible width reaches most of the touchscreen's, else the largest there is.
     * Always taking the smallest put 800x480 on a 1540x720 Audi screen and stretched it. A car that
     * gives no touchscreen size gets the largest up to 1080p: a car only offers sizes it can show,
     * and the 2024 VW (touchscreen 0x0) blurred 800x480 across a 1080p screen.
     *
     * A touchscreen taller than wide counts only the upright pictures, when the car offers any: the
     * smallest whose visible height reaches most of the touchscreen's, else the largest. By width
     * alone a 1080x1920 screen got 1280x720 over 1080x1920, for having fewer pixels.
     */
    fun choose(configs: List<ServiceDiscovery.VideoConfig>, offered: List<Int>, touchWidth: Int, touchHeight: Int): Choice {
        val known = offered.filter { configs.getOrNull(it)?.pixelSize() != null }
        fun pixels(idx: Int) = configs[idx].pixelCount()
        if (touchWidth > 0 && touchHeight > touchWidth) {
            val upright = known.filter { configs[it].visibleSize()?.let { (w, h) -> h > w } == true }
            if (upright.isNotEmpty()) {
                val tall = upright.filter { (configs[it].visibleSize()?.second ?: 0) >= touchHeight * 8 / 10 }
                return Choice(tall.minByOrNull(::pixels) ?: upright.maxByOrNull(::pixels), upright = true)
            }
        }
        if (touchWidth <= 0) {
            val largest = known.filter { pixels(it) <= 1920 * 1080 }.maxByOrNull(::pixels) ?: known.minByOrNull(::pixels)
            return Choice(largest, upright = false)
        }
        val wide = known.filter { (configs[it].visibleSize()?.first ?: 0) >= touchWidth * 8 / 10 }
        return Choice(wide.minByOrNull(::pixels) ?: known.maxByOrNull(::pixels), upright = false)
    }
}
