package io.github.sirionero.mycar

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How myCar's earlier processes ended, at the top of the new log. A crash in native code (the web
 * engine, the graphics, a library) or Android closing myCar writes nothing in myCar's own log: the
 * file just stops, and the drive with it. Android keeps the reason (ApplicationExitInfo), and for a
 * native crash the tombstone: its signal, abort message and the crashing thread's frames go in the
 * log here; for an ANR, the main thread's stack. Each ending is told once.
 */
internal object LastExit {

    private const val PREFS = "mycar_last_exit"
    private const val TOLD = "told_until"
    private const val MAX_FRAMES = 24

    fun log(context: Context) {
        val app = context.applicationContext
        Thread({ runCatching { tell(app) }.onFailure { MyCarLog.w("last exit: not readable (${it.javaClass.simpleName})") } }, "mycar-last-exit").start()
    }

    private fun tell(context: Context) {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val told = prefs.getLong(TOLD, 0L)
        // This process's own start is not an ending: only those before it, newest first. Android lists
        // the web engine's page processes here too, ended with myCar ("isolated not needed"): only
        // myCar's own, and those of the web engine that crashed.
        val own = context.packageName
        val exits = manager.getHistoricalProcessExitReasons(null, 0, 16)
            .filter { it.pid != android.os.Process.myPid() && it.timestamp > told }
            .filter {
                val name = it.processName.orEmpty()
                name == own || name.startsWith("$own:") ||
                    it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || it.reason == ApplicationExitInfo.REASON_CRASH
            }
        if (exits.isEmpty()) return
        // The first time (the first start of a version that has this), those of the last two days:
        // installing it closes a running myCar, and that "updated" alone hid the crash before it.
        val recent = if (told == 0L) exits.filter { it.timestamp > System.currentTimeMillis() - 2 * 86_400_000L } else exits
        val shown = recent.take(5)
        if (recent.size > shown.size) MyCarLog.i("last exit: ${recent.size - shown.size} earlier endings not shown")
        shown.reversed().forEach(::tellOne)
        prefs.edit().putLong(TOLD, exits.maxOf { it.timestamp }).apply()
    }

    private fun tellOne(exit: ApplicationExitInfo) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(exit.timestamp))
        MyCarLog.i(
            "last exit: ${reasonName(exit.reason)} at $stamp (${exit.processName}, status ${exit.status}, " +
                "importance ${exit.importance}, pss ${exit.pss / 1024} MB, rss ${exit.rss / 1024} MB)" +
                exit.description?.let { ": $it" }.orEmpty(),
        )
        // Android 17's memory limit for each app (based on the phone's RAM) closes it this way, with
        // nothing in its own log: said in plain words.
        if (exit.description.orEmpty().contains("MemoryLimiter")) {
            MyCarLog.w("last exit: Android's memory limit closed myCar: it was using more memory than Android allows an app")
        }
        when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> exit.traceInputStream?.use { tombstone(it.readBytes()) }
            ApplicationExitInfo.REASON_ANR -> exit.traceInputStream?.use { anr(it.bufferedReader().readLines()) }
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "exited by itself"
        ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "closed by Android for memory"
        ApplicationExitInfo.REASON_CRASH -> "crash (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash (native)"
        ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "a permission changed"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "closed by Android for using too much"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped on request"
        ApplicationExitInfo.REASON_USER_STOPPED -> "stopped by the user"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "something it depends on died"
        ApplicationExitInfo.REASON_OTHER -> "closed by Android (other)"
        // REASON_FREEZER, REASON_PACKAGE_STATE_CHANGE, REASON_PACKAGE_UPDATED: Android 13's, by number.
        14 -> "closed while frozen"
        15 -> "the app was turned off"
        16 -> "updated"
        else -> "reason $reason"
    }

    /** The ANR's trace: the main thread's stack, the part that says what it was stuck on. */
    private fun anr(lines: List<String>) {
        val start = lines.indexOfFirst { it.startsWith("\"main\"") }
        if (start < 0) return
        lines.drop(start).takeWhile { it.isNotBlank() }.take(40).forEach { MyCarLog.i("last exit:   $it") }
    }

    /**
     * A native crash's tombstone (Android's protobuf, debuggerd's tombstone.proto): the signal and
     * where, the abort message, what Android made of it, the crashing thread's frames, and the
     * process's last warnings before it went.
     */
    private fun tombstone(data: ByteArray) {
        val top = Proto.fields(data)
        val tid = top.varint(6)
        top.message(10)?.let { signal ->
            val fault = if (signal.varint(8) != 0L) " at 0x${signal.varint(9).toString(16)}" else ""
            MyCarLog.i("last exit:   signal ${signal.string(2)} (${signal.string(4)})$fault")
        }
        top.string(14).takeIf { it.isNotBlank() }?.let { MyCarLog.i("last exit:   abort: $it") }
        top.all(15).forEach { MyCarLog.i("last exit:   cause: ${Proto.fields(it).string(1)}") }
        // Threads are a map: entries with the thread id (1) and the thread (2).
        val thread = top.all(16).map { Proto.fields(it) }.firstOrNull { it.varint(1) == tid }?.message(2)
        if (thread != null) {
            MyCarLog.i("last exit:   thread ${thread.string(2)} ($tid)")
            thread.all(4).take(MAX_FRAMES).forEachIndexed { i, raw ->
                val frame = Proto.fields(raw)
                val function = frame.string(4).takeIf { it.isNotBlank() }?.let { " ($it+${frame.varint(5)})" }.orEmpty()
                MyCarLog.i("last exit:   #%02d pc %x %s%s".format(i, frame.varint(1), frame.string(6), function))
            }
        }
        // The process's own last lines of warning and worse, from the log the tombstone kept: the
        // web engine's FATAL line, a library's complaint.
        val lines = top.all(18).flatMap { Proto.fields(it).all(2) }.map { Proto.fields(it) }
            .filter { it.varint(4) >= 5 && it.string(5) != MyCarLog.TAG }
        lines.takeLast(15).forEach { MyCarLog.i("last exit:   log ${it.string(1)} ${it.string(5)}: ${it.string(6).take(300)}") }
    }

    /** Just enough protobuf to read a tombstone: each field's number and its value. */
    private object Proto {
        fun fields(data: ByteArray): List<Pair<Int, Any>> {
            val out = ArrayList<Pair<Int, Any>>()
            var i = 0
            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (i < data.size && shift < 64) {
                    val b = data[i++].toInt() and 0xff
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b < 0x80) return result
                    shift += 7
                }
                return result
            }
            fun fixed(bytes: Int): Long {
                var v = 0L
                for (k in 0 until bytes) v = v or ((data[i + k].toLong() and 0xff) shl (8 * k))
                i += bytes
                return v
            }
            while (i < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                val type = (key and 7).toInt()
                if (type == 0) {
                    out.add(field to varint())
                } else if (type == 1 && i + 8 <= data.size) {
                    out.add(field to fixed(8))
                } else if (type == 5 && i + 4 <= data.size) {
                    out.add(field to fixed(4))
                } else if (type == 2) {
                    val length = varint().toInt()
                    if (length < 0 || i + length > data.size) break
                    out.add(field to data.copyOfRange(i, i + length))
                    i += length
                } else {
                    break
                }
            }
            return out
        }
    }

    private fun List<Pair<Int, Any>>.all(field: Int): List<ByteArray> = filter { it.first == field }.mapNotNull { it.second as? ByteArray }
    private fun List<Pair<Int, Any>>.varint(field: Int): Long = (firstOrNull { it.first == field }?.second as? Long) ?: 0L
    private fun List<Pair<Int, Any>>.string(field: Int): String = all(field).firstOrNull()?.let { String(it, Charsets.UTF_8) }.orEmpty()
    private fun List<Pair<Int, Any>>.message(field: Int): List<Pair<Int, Any>>? = all(field).firstOrNull()?.let { Proto.fields(it) }
}
