package io.github.sirionero.mycar

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Debug only, for CarPreviewActivity's --es script: a car's touches and events from a script, sent
 * the way a car's reach myCar - from a thread of their own, each one put at the front of the main
 * thread's queue with the time it arrived (as MyCarActivity.onTouch does) - and with a car's timing:
 * a finger dragged across the screen in so many ms, a move every so many ms. Steps, split by ";":
 *
 *   wait:ms                      a pause
 *   tap:x,y[,holdMs]             a tap (70 ms down unless said)
 *   drag:x1,y1,x2,y2,ms[,every[,holdMs]]   a finger from x1,y1 to x2,y2 in that time, a move every
 *                                "every" ms (16 unless said), held still holdMs first (the mini player)
 *   fling:x1,y1,x2,y2,ms[,every] the same, faster and faster to the lift
 *   tapText:label[,holdMs]       a tap on what the car's screen shows with that text or description
 *                                (the topmost one in sight)
 *   focus:ms                     the car shows its own screen that long, then hands it back (as
 *                                MyCarRunner.onVideoFocus: CarFocus, then the picture drawn again)
 *   rebuild                      the car's screen made again, as with a new encoder
 *   demo                         the made-up song again in the car's player, its music app kept
 *   key:code                     a key of the car pressed and let go (CarKeys)
 *   unfocus                      the focus taken from the view that has it (a web view after a tap)
 *   focusweb                     the focus given to the web view in sight, as a tap in it can
 *   snap:name                    the picture now, as name.png
 *   log:text                     a line in the log
 *   rep:n ... end                the steps between n times
 *
 * Any number can be a range, "120-1500": each step drawn anew from it, with the seed's random.
 * Every step goes in the log ("preview: step"), and the last pictures are kept as they come in
 * ring/ (a few a second, the last 16): after a crash, what the screen showed just before.
 */
internal class PreviewScript(
    private val projector: ScreenProjector,
    private val frame: () -> Bitmap?,
    private val dir: File,
    private val seed: Int,
    private val rebuild: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val random = Random(seed)
    @Volatile private var running = true
    @Volatile private var stepNow = 0

    private class Touch(val action: Int, val x: Float, val y: Float, val at: Long)

    private val touches = ConcurrentLinkedQueue<Touch>()

    // One runnable for all that are waiting, at the front of the queue: as MyCarActivity.onTouch.
    private val dispatch = Runnable {
        while (true) {
            val touch = touches.poll() ?: break
            projector.dispatchTouch(touch.action, 0, listOf(TouchPoint(0, touch.x, touch.y)), touch.at)
        }
    }

    /** The car screen's root view (ScreenProjector keeps it to itself). */
    private fun rootView(): View? = runCatching {
        ScreenProjector::class.java.getDeclaredField("root").apply { isAccessible = true }.get(projector) as View?
    }.getOrNull()

    /** On the main thread, with the car's screen up. */
    fun start(text: String) {
        CrashProbe.install(::rootView)
        val steps = expand(text.split(';').map { it.trim() }.filter { it.isNotEmpty() })
        MyCarLog.i("preview: script of ${steps.size} steps, seed $seed")
        Thread({ run(steps) }, "preview-car").apply { isDaemon = true }.start()
        Thread({ record() }, "preview-frames").apply { isDaemon = true }.start()
    }

    /** rep:n ... end unrolled. */
    private fun expand(tokens: List<String>): List<String> {
        val bodies = ArrayDeque<MutableList<String>>().apply { addLast(mutableListOf()) }
        val counts = ArrayDeque<Int>()
        for (token in tokens) {
            when {
                token.startsWith("rep:") -> {
                    counts.addLast(token.removePrefix("rep:").trim().toInt())
                    bodies.addLast(mutableListOf())
                }
                token == "end" && counts.isNotEmpty() -> {
                    val body = bodies.removeLast()
                    val times = counts.removeLast()
                    repeat(times) { bodies.last().addAll(body) }
                }
                else -> bodies.last().add(token)
            }
        }
        while (bodies.size > 1) {
            val body = bodies.removeLast()
            bodies.last().addAll(body)
        }
        return bodies.first()
    }

    private fun run(steps: List<String>) {
        steps.forEachIndexed { i, step ->
            stepNow = i + 1
            try {
                perform(i + 1, step)
            } catch (t: Throwable) {
                MyCarLog.e("preview: step ${i + 1} ($step) failed", t)
            }
        }
        running = false
        MyCarLog.i("preview: script done after ${steps.size} steps")
    }

    /** "a-b": a number drawn between the two; "a": that number. */
    private fun value(text: String): Long {
        val parts = text.trim().split('-')
        if (parts.size == 2 && parts[0].isNotEmpty()) {
            val low = parts[0].toLong()
            val high = parts[1].toLong()
            return if (high <= low) low else random.nextLong(low, high + 1)
        }
        return text.trim().toLong()
    }

    private fun perform(n: Int, step: String) {
        val op = step.substringBefore(':')
        val rest = step.substringAfter(':', "")
        val args = rest.split(',').map { it.trim() }
        when (op) {
            "wait" -> Thread.sleep(value(args[0]))
            "log" -> MyCarLog.i("preview: step $n: $rest")
            "tap" -> {
                val x = value(args[0]).toFloat()
                val y = value(args[1]).toFloat()
                val hold = args.getOrNull(2)?.let(::value) ?: TAP_MS
                MyCarLog.i("preview: step $n: tap at (${x.toInt()},${y.toInt()})")
                tap(x, y, hold)
            }
            "drag", "fling" -> gesture(n, args, faster = op == "fling")
            "tapText" -> {
                val label = args[0]
                val hold = args.getOrNull(1)?.let(::value) ?: TAP_MS
                var spot = onMain { find(label) }
                // On another page of the app grid: its pages turned towards it, a few times at most.
                var turns = 0
                while (spot != null && !spot.inSight && turns < 4) {
                    val toLeft = spot.x > spot.width
                    MyCarLog.i("preview: step $n: '$label' is on a page to the ${if (toLeft) "right" else "left"} - turning")
                    val y = spot.height / 2
                    if (toLeft) swipe(spot.width * 0.85f, y, spot.width * 0.3f, y) else swipe(spot.width * 0.3f, y, spot.width * 0.85f, y)
                    Thread.sleep(900)
                    spot = onMain { find(label) }
                    turns++
                }
                if (spot == null || !spot.inSight) {
                    MyCarLog.w("preview: step $n: no '$label' in sight on the car's screen")
                } else {
                    MyCarLog.i("preview: step $n: tap on '$label' at (${spot.x.toInt()},${spot.y.toInt()})")
                    tap(spot.x, spot.y, hold)
                }
            }
            "focus" -> {
                val ms = args.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let(::value) ?: 2_500L
                MyCarLog.i("preview: step $n: the car shows its own screen for ${ms}ms")
                onMain { CarFocus.set(false) }
                Thread.sleep(ms)
                onMain {
                    CarFocus.set(true)
                    projector.refresh()
                }
                MyCarLog.i("preview: step $n: the car handed its screen back - the picture drawn again")
            }
            // The focus taken from whatever has it on the car's screen (a web view after a tap in it).
            "unfocus" -> {
                val from = onMain {
                    val focused = rootView()?.findFocus()
                    focused?.clearFocus()
                    focused?.javaClass?.simpleName ?: "nothing"
                }
                MyCarLog.i("preview: step $n: focus taken from $from")
            }
            // The focus given to the web view in sight (Plancia's page), as a tap in it gives it.
            "focusweb" -> {
                val result = onMain {
                    val web = rootView()?.let { firstWebView(it) }
                    if (web == null) "no web view in sight" else "${web.requestFocus()}, focused ${web.hasFocus()}"
                }
                MyCarLog.i("preview: step $n: focus to the web view: $result")
            }
            "rebuild" -> {
                MyCarLog.i("preview: step $n: the car's screen made again")
                onMain { rebuild() }
            }
            "demo" -> {
                val done = onMain { demoSongAgain() } == true
                MyCarLog.i("preview: step $n: the made-up song again${if (done) "" else " - no car media yet"}")
            }
            "key" -> {
                val code = value(args[0]).toInt()
                MyCarLog.i("preview: step $n: key $code")
                onMain {
                    io.github.sirionero.mycar.car.CarKeys.onKey(code, down = true, longPress = false)
                    io.github.sirionero.mycar.car.CarKeys.onKey(code, down = false, longPress = false)
                }
            }
            "snap" -> {
                val name = args[0].ifEmpty { "step-$n" }
                val bmp = frame()
                if (bmp == null) {
                    MyCarLog.w("preview: step $n: no picture yet for $name")
                } else {
                    FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    MyCarLog.i("preview: step $n: wrote $name.png")
                }
            }
            else -> MyCarLog.w("preview: step $n: unknown step '$step'")
        }
    }

    private fun firstWebView(view: View): android.webkit.WebView? {
        if (view is android.webkit.WebView && view.isShown) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) firstWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** A quick drag, to turn a page: 250 ms, a move every 16. */
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        touch(MotionEvent.ACTION_DOWN, x1, y1)
        for (i in 1..15) {
            Thread.sleep(16)
            touch(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * i / 15, y1 + (y2 - y1) * i / 15)
        }
        touch(MotionEvent.ACTION_UP, x2, y2)
    }

    private fun tap(x: Float, y: Float, hold: Long) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        Thread.sleep(hold)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun gesture(n: Int, args: List<String>, faster: Boolean) {
        val x1 = value(args[0]).toFloat()
        val y1 = value(args[1]).toFloat()
        val x2 = value(args[2]).toFloat()
        val y2 = value(args[3]).toFloat()
        val duration = value(args[4]).coerceAtLeast(1)
        val every = args.getOrNull(5)?.takeIf { it.isNotEmpty() }?.let(::value)?.coerceAtLeast(4) ?: 16L
        val hold = args.getOrNull(6)?.takeIf { it.isNotEmpty() }?.let(::value) ?: 0L
        MyCarLog.i(
            "preview: step $n: ${if (faster) "fling" else "drag"} (${x1.toInt()},${y1.toInt()}) -> (${x2.toInt()},${y2.toInt()}) " +
                "in ${duration}ms, a move every ${every}ms" + if (hold > 0) ", held ${hold}ms first" else "",
        )
        touch(MotionEvent.ACTION_DOWN, x1, y1)
        if (hold > 0) Thread.sleep(hold)
        val start = SystemClock.uptimeMillis()
        while (true) {
            Thread.sleep(every)
            val t = SystemClock.uptimeMillis() - start
            if (t >= duration) break
            val f = t.toFloat() / duration
            val p = if (faster) f * f else f
            touch(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * p, y1 + (y2 - y1) * p)
        }
        touch(MotionEvent.ACTION_MOVE, x2, y2)
        touch(MotionEvent.ACTION_UP, x2, y2)
        MyCarLog.i("preview: step $n: lifted at (${x2.toInt()},${y2.toInt()})")
    }

    private fun touch(action: Int, x: Float, y: Float) {
        touches.add(Touch(action, x, y, SystemClock.uptimeMillis()))
        main.removeCallbacks(dispatch)
        main.postAtFrontOfQueue(dispatch)
    }

    private fun <T> onMain(block: () -> T): T? {
        val task = FutureTask(Callable { block() })
        main.post(task)
        return try {
            task.get(10, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            MyCarLog.w("preview: the main thread didn't answer in 10 s (${t.javaClass.simpleName})")
            null
        }
    }

    /** Where something that says a label is: [inSight] or off the screen (another page). */
    private class Spot(val x: Float, val y: Float, val inSight: Boolean, val width: Float, val height: Float)

    /**
     * The middle of the topmost thing in sight that says [label] (its text or its description); if
     * none is in sight, the topmost one off the screen, for its direction.
     */
    private fun find(label: String): Spot? {
        val view = runCatching {
            ScreenProjector::class.java.getDeclaredField("root").apply { isAccessible = true }.get(projector) as View?
        }.onFailure { MyCarLog.w("preview: no car screen to look on (${it.javaClass.simpleName})") }.getOrNull() ?: return null
        val owner = semanticsOwner(view)
        if (owner == null) {
            MyCarLog.w("preview: no Compose root on the car's screen (${view.javaClass.name})")
            return null
        }
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        var inSight: Spot? = null
        var away: Spot? = null
        val seen = ArrayList<String>()
        fun visit(node: SemanticsNode) {
            val config = node.config
            val words = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            if (seen.size < 60) seen.addAll(words)
            val bounds = node.boundsInRoot
            val x = bounds.center.x
            val y = bounds.center.y
            // The last in drawing order: a screen over Home has Home's words under it.
            if (words.any { it.equals(label, ignoreCase = true) } && bounds.width > 0f && bounds.height > 0f) {
                if (x in 0f..width && y in 0f..height) inSight = Spot(x, y, true, width, height)
                else away = Spot(x, y, false, width, height)
            }
            for (child in node.children) visit(child)
        }
        // The unmerged tree: each text where it's drawn, whatever merges it for a screen reader.
        visit(owner.unmergedRootSemanticsNode)
        if (inSight == null && away == null) MyCarLog.w("preview: '$label' not found; words on the car's screen: ${seen.take(60)}")
        return inSight ?: away
    }

    /** The car screen's Compose root, as tests reach it (RootForTest), or by its getter. */
    private fun semanticsOwner(view: View): SemanticsOwner? {
        if (view is RootForTest) return view.semanticsOwner
        val getter = view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterTypes.isEmpty() }
        if (getter != null) (runCatching { getter.invoke(view) }.getOrNull() as? SemanticsOwner)?.let { return it }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) semanticsOwner(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** As showDemoSong, but the car's music app stays what it is (a music service's web player). */
    @Suppress("UNCHECKED_CAST")
    private fun demoSongAgain(): Boolean {
        val media = io.github.sirionero.mycar.car.CarServices.mediaOrNull() ?: return false
        fun <T> state(name: String): MutableStateFlow<T> =
            CarMedia::class.java.getDeclaredField(name).apply { isAccessible = true }.get(media) as MutableStateFlow<T>
        state<NowPlaying>("_now").value = io.github.sirionero.mycar.car.demoSong()
        state<CarMedia.Phase>("_phase").value = CarMedia.Phase.READY
        return true
    }

    /** The last pictures, a few a second, in ring/: what the screen showed before a crash. */
    private fun record() {
        val ring = File(dir, "ring").apply { mkdirs() }
        val kept = ArrayDeque<File>()
        var seq = 0
        var last: Bitmap? = null
        while (running) {
            Thread.sleep(RING_EVERY_MS)
            val bmp = frame() ?: continue
            if (bmp === last) continue
            last = bmp
            val file = File(ring, String.format(Locale.ROOT, "f%05d-s%04d.jpg", seq++, stepNow))
            runCatching { FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 75, it) } }
            kept.addLast(file)
            while (kept.size > RING_KEPT) kept.removeFirst().delete()
        }
    }

    private companion object {
        const val TAP_MS = 70L
        const val RING_EVERY_MS = 250L
        const val RING_KEPT = 16
    }
}
