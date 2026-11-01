package io.github.sirionero.mycar.car

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import android.view.WindowManager
import android.webkit.WebView
import androidx.annotation.StringRes
import androidx.car.app.CarToast
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import org.json.JSONObject

/**
 * The phone's clipboard for the car's keyboard: Paste puts its text in the field on the car's
 * screen (a password copied on the phone, say), Copy puts the field's there.
 *
 * Writing is always allowed. Reading isn't: from Android 10 an app reads the clipboard only while
 * one of its windows has the phone's focus, or it's the keyboard. The car's screen doesn't count -
 * it's a display of its own, below the phone's, and never the one in focus. So Paste reads at once
 * while myCar is in front on the phone; otherwise a see-through screen of myCar's
 * ([CarClipboardActivity]) comes up on the phone for a moment, reads it with the focus and goes.
 * With the phone locked nothing is read, and the car says so.
 */
internal object CarClipboard {
    private val main = Handler(Looper.getMainLooper())

    /** Where the text of the Paste waiting for [CarClipboardActivity] goes, and which Paste it was. */
    private var waiting: ((String) -> Unit)? = null
    private var request = 0

    /** Paste: the phone's clipboard as text, handed to [into]; or the car says why there's none. */
    fun paste(context: Context, into: (String) -> Unit) {
        val app = context.applicationContext
        read(app)?.let {
            MyCarLog.i("clipboard: ${it.length} characters pasted in the car")
            into(it)
            return
        }
        val locked = app.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val asleep = app.getSystemService(PowerManager::class.java)?.isInteractive == false
        if (locked || asleep) {
            MyCarLog.i("clipboard: the phone is locked - nothing read")
            say(R.string.car_clipboard_locked)
            return
        }
        val id = ++request
        waiting = into
        val intent = Intent(app, CarClipboardActivity::class.java)
            .putExtra(EXTRA_REQUEST, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        runCatching { app.startActivity(intent, options.toBundle()) }
            .onFailure { MyCarLog.w("clipboard: couldn't come up on the phone: ${it.message}") }
        // Android keeps a screen from opening without a word (myCar in the background, with
        // neither its accessibility service nor a car it manages): then the car says what to do.
        main.postDelayed({
            if (request != id || waiting == null) return@postDelayed
            waiting = null
            MyCarLog.w("clipboard: Android didn't let myCar read it - myCar isn't in front on the phone")
            say(R.string.car_clipboard_denied)
        }, READ_WITHIN_MS)
    }

    /** From [CarClipboardActivity], which has the phone's focus now: the clipboard for Paste [id]. */
    internal fun readWithFocus(context: Context, id: Int) {
        if (id != request) return
        val into = waiting ?: return
        waiting = null
        val text = read(context)
        if (text == null) {
            MyCarLog.i("clipboard: no text on the phone's clipboard")
            say(R.string.car_clipboard_empty)
            return
        }
        MyCarLog.i("clipboard: ${text.length} characters pasted in the car, read from the phone's screen")
        into(text)
    }

    /** Copy: [text] onto the phone's clipboard. From Android 13 the phone shows it was copied. */
    fun copy(context: Context, text: String) {
        if (text.isEmpty()) {
            say(R.string.car_clipboard_nothing)
            return
        }
        val manager = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { manager.setPrimaryClip(ClipData.newPlainText("myCar", text)) }
            .onSuccess {
                MyCarLog.i("clipboard: ${text.length} characters copied from the car")
                say(R.string.car_clipboard_copied)
            }
            .onFailure { MyCarLog.w("clipboard: couldn't copy: ${it.message}") }
    }

    /** Copy from a page: the text chosen in its field or on it, else the field's whole text; never a password. */
    fun copyFromPage(web: WebView) {
        web.evaluateJavascript(COPY_JS) { result ->
            val found = runCatching { JSONObject(result) }.getOrNull()
            when {
                found == null -> say(R.string.car_clipboard_nothing)
                found.optBoolean("password") -> {
                    MyCarLog.i("clipboard: a password field - not copied")
                    say(R.string.car_clipboard_password)
                }
                else -> copy(web.context, found.optString("text"))
            }
        }
    }

    private fun read(context: Context): String? = runCatching {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }?.take(MAX_TEXT)
    }.getOrNull()

    private fun say(@StringRes text: Int) = CarToasts.show(this, Words.get(text), CarToast.LENGTH_SHORT)

    internal const val EXTRA_REQUEST = "request"

    /** Time for the see-through screen to come up and read, before the car says it didn't. */
    private const val READ_WITHIN_MS = 2_500L
    private const val MAX_TEXT = 10_000

    /** What a page's field holds: the part chosen in it, or on the page, else all of it. Not a password. */
    private const val COPY_JS = """
(function () {
  var el = document.activeElement;
  while (el && el.shadowRoot && el.shadowRoot.activeElement) el = el.shadowRoot.activeElement;
  var field = !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA');
  if (field && String(el.type || '').toLowerCase() === 'password') return { password: true, text: '' };
  var text = '';
  try { if (field && el.selectionEnd > el.selectionStart) text = el.value.substring(el.selectionStart, el.selectionEnd); } catch (e) {}
  if (!text && window.getSelection) text = String(window.getSelection() || '');
  if (!text && el) text = field ? String(el.value || '') : (el.isContentEditable ? String(el.innerText || '') : '');
  return { password: false, text: text.slice(0, 10000) };
})()
"""
}

/**
 * See-through and gone at once: up on the phone for a Paste in the car, it has the phone's focus,
 * so Android lets myCar read the clipboard (see [CarClipboard]). Touches go through it to the app
 * below, and it never stays: it finishes once it has read, or after a moment without the focus.
 */
class CarClipboardActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        handler.postDelayed(::close, GIVE_UP_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || done) return
        CarClipboard.readWithFocus(this, intent.getIntExtra(CarClipboard.EXTRA_REQUEST, 0))
        close()
    }

    override fun onStop() {
        super.onStop()
        close()
    }

    private fun close() {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val GIVE_UP_MS = 3_000L
    }
}
