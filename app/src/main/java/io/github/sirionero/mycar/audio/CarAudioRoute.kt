package io.github.sirionero.mycar.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.SessionReport
import io.github.sirionero.mycar.car.CarBluetooth
import io.github.sirionero.mycar.car.CarSettings

/** How the phone's sound reaches the car; the car's settings choose, Automatic by default. */
enum class AudioRoute(val id: String) {
    /** Bluetooth when the car takes the phone's music over it, otherwise through myCar. */
    AUTO("auto"),

    /** As the phone plays it: in the car only while the car takes the phone's music over Bluetooth. */
    BLUETOOTH("bluetooth"),

    /** Captured and sent over myCar's own link to the car, the cable or Wi-Fi, as Android Auto sends it. */
    CAR_LINK("link"),
    ;

    companion object {
        fun of(id: String): AudioRoute? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Automatic, decided once a drive. Many cars keep Bluetooth for calls while Android Auto runs and
 * take music only over the projection link, so music left to Bluetooth plays on the phone; others
 * play the phone's Bluetooth music as they always have. So when the car
 * shows myCar, this looks for a Bluetooth music connection, giving the car a few seconds to make
 * one: with it, the phone plays over Bluetooth as usual and Android never asks; without it, myCar
 * sends the sound through its link, behind Android's capture consent. Should that Bluetooth
 * connection drop mid-drive, the sound moves to the link.
 *
 * When the car named its Bluetooth address, only that device counts; otherwise any Bluetooth music
 * connection does, as in [io.github.sirionero.mycar.car.BluetoothAudioCheck].
 */
object CarAudioRoute {

    private val log = MyCarLog.tagged("audio")
    private val main = Handler(Looper.getMainLooper())
    private var audio: AudioManager? = null
    private var projecting = false
    private var watching: AudioDeviceCallback? = null
    /** Tags the wait for the car's Bluetooth, so cancelling it leaves anything else queued alone. */
    private val settling = Any()

    fun init(context: Context) {
        audio = context.applicationContext.getSystemService(AudioManager::class.java)
    }

    /** Automatic left this drive's music to Bluetooth, the map app's voice with it. */
    val onBluetooth: Boolean
        get() = CarSettings.audioRoute.value == AudioRoute.AUTO && !CarSettings.pipeAudio.value

    /** The car shows myCar. Called again whenever the video restarts, so it decides once a drive. */
    fun onProjecting() = main.post {
        if (projecting) return@post
        projecting = true
        if (CarSettings.audioRoute.value == AudioRoute.AUTO) decide()
    }

    fun onCarDisconnected() = main.post {
        projecting = false
        sawCall = false
        callWaits = 0
        dropCallWaits = 0
        main.removeCallbacksAndMessages(settling)
        stopWatching()
        CarSettings.forgetCarLink()
    }

    /** Automatic chosen in the settings: decided now in the car, at the next drive otherwise. */
    fun chosen() = main.post {
        if (projecting) decide() else CarSettings.useCarLink(false)
    }

    private fun decide() {
        main.removeCallbacksAndMessages(settling)
        sawCall = false
        callWaits = 0
        if (bluetoothMusic()) {
            useBluetooth()
            return
        }
        log.i("no Bluetooth music connection to the car yet - giving it ${SETTLE_MS / 1000}s")
        main.postDelayed({ settle() }, settling, SETTLE_MS)
    }

    /**
     * Automatic's choice for the drive, a moment after the picture. A drive started during a call
     * waits for it to end: some cars give the phone's Bluetooth music back only then, and through
     * myCar was picked for the whole drive (Android's question asked mid-call). At most
     * [MAX_CALL_WAITS] waits: an app can leave the phone "in communication" for good.
     */
    private fun settle() {
        if (!projecting || CarSettings.audioRoute.value != AudioRoute.AUTO) return
        if (bluetoothMusic()) {
            sawCall = false
            useBluetooth()
            return
        }
        if (inCall() && callWaits++ < MAX_CALL_WAITS) {
            if (!sawCall) log.i("no Bluetooth music connection during a call - deciding once the call ends")
            sawCall = true
            main.postDelayed({ settle() }, settling, SETTLE_MS)
            return
        }
        // The call just ended: the car is given a moment to bring its Bluetooth music back.
        if (sawCall) {
            sawCall = false
            main.postDelayed({ settle() }, settling, SETTLE_MS)
            return
        }
        log.i("route: through myCar - the car takes no music from the phone over Bluetooth")
        SessionReport.audioRoute("auto: through myCar (no Bluetooth music connection to the car)")
        CarSettings.useCarLink(true)
    }

    /** Waits for a call at the start of a drive, [SETTLE_MS] each: five minutes. */
    private var callWaits = 0
    private const val MAX_CALL_WAITS = 60

    private fun useBluetooth() {
        log.i("route: Bluetooth - the car takes the phone's music over it")
        SessionReport.audioRoute("auto: Bluetooth (the car takes the phone's music over it)")
        CarSettings.useCarLink(false)
        watch()
    }

    /** While the music is on Bluetooth, losing that connection moves it to myCar's link. */
    private fun watch() {
        if (watching != null) return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                if (!projecting || !onBluetooth || bluetoothMusic()) return
                // Some cars drop Bluetooth music for a call, or for a moment, and take it back:
                // moving the sound at once opened the car's music channel and put Android's
                // question up mid-drive. Moved only if it stays gone, once no call is on.
                log.i("the Bluetooth music connection dropped - waiting ${DROP_MS / 1000}s before moving the sound")
                // Dropped for a call: noted now. A call shorter than the wait was over by the check,
                // which then moved the sound to myCar for the rest of the drive, seconds before the
                // car brought its Bluetooth music back.
                if (inCall()) sawCall = true
                dropCallWaits = 0
                main.removeCallbacksAndMessages(settling)
                main.postDelayed({ checkDropped() }, settling, DROP_MS)
            }
        }
        watching = callback
        audio?.registerAudioDeviceCallback(callback, main)
    }

    private fun checkDropped() {
        if (!projecting || !onBluetooth || bluetoothMusic()) {
            // Back (or nothing to watch): a call seen this time doesn't lengthen the next drop's wait.
            sawCall = false
            dropCallWaits = 0
            return
        }
        // An app can leave the phone "in communication" for good (as settle knows), and the music then
        // never moved to myCar for the rest of the drive: that waits at most half an hour. Not a
        // phone call, which always ends: cut short, a long one would move the sound and put Android's
        // question up mid-call.
        if (inCall() && (audio?.mode != AudioManager.MODE_IN_COMMUNICATION || dropCallWaits++ < MAX_DROP_CALL_WAITS)) {
            sawCall = true
            main.postDelayed({ checkDropped() }, settling, DROP_MS)
            return
        }
        // The call just ended: the car takes a few seconds to bring its Bluetooth music back.
        if (sawCall) {
            sawCall = false
            main.postDelayed({ checkDropped() }, settling, DROP_MS)
            return
        }
        log.i("route: through myCar - the Bluetooth music connection dropped")
        SessionReport.audioRoute("auto: through myCar (the Bluetooth music connection dropped)")
        CarSettings.useCarLink(true)
        stopWatching()
    }

    /** Waits "in communication" once the Bluetooth music connection dropped, [DROP_MS] each: half an hour. */
    private var dropCallWaits = 0
    private const val MAX_DROP_CALL_WAITS = 180

    /** A call was on while the Bluetooth music connection was gone (see [checkDropped]). */
    private var sawCall = false

    private fun inCall(): Boolean = when (audio?.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private fun stopWatching() {
        watching?.let { audio?.unregisterAudioDeviceCallback(it) }
        watching = null
    }

    /** A Bluetooth music output that may be the car: any, unless the car named another address. */
    private fun bluetoothMusic(): Boolean {
        val outputs = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            .filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        if (outputs.isEmpty()) return false
        val car = CarBluetooth.carAddress?.uppercase() ?: return true
        // An address Android masks can't be told apart from the car's, so it counts.
        return outputs.any { it.address.uppercase().let { address -> address == car || !MAC.matches(address) } }
    }

    private val MAC = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")

    /** Long enough for a car that connects Bluetooth music by itself at the start of a drive. */
    private const val SETTLE_MS = 5_000L

    /** How long a dropped Bluetooth music connection may take to come back before the sound moves. */
    private const val DROP_MS = 10_000L
}
