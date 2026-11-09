package io.github.sirionero.mycar.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Process
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.mirror.MirrorService
import kotlin.math.abs

/**
 * Hears one app's audio so it can be played on the car.
 *
 * Playback capture is the one public route to another app's sound, and it hangs off a
 * MediaProjection - so, as with [io.github.sirionero.mycar.mirror.MirrorService], the consent
 * result is carried into a foreground service of type mediaProjection before it can be redeemed.
 * Capture is scoped to media playback: notification pings, calls and alarms stay on the phone.
 * The grant lasts as long as this service does, which is as long as the car is connected, so the
 * driver answers the consent dialog once per drive rather than once per app. While the phone's
 * screen is shared with the car, the capture runs on the sharing's projection, which takes the
 * place of its own: see [followSharing].
 */
class AudioCaptureService : Service() {

    @Volatile private var reading = false
    private var projection: MediaProjection? = null
    private var thread: Thread? = null
    private var mutedPhone = false

    /**
     * The screen sharing's projection, once the capture has moved onto it ([followSharing]): the
     * sharing's to stop, never this service's.
     */
    private var sharedProjection: MediaProjection? = null

    /** Whether the capture is moving, or has moved, onto the screen sharing's projection. */
    private val handOver = SharingHandOver()

    /**
     * Silence the phone's own speaker while the car is being fed.
     *
     * Playback capture copies the stream instead of moving it, so without this the music plays in
     * two places at once. Muting the stream does not affect what is captured - the tap sits before
     * the volume the speaker gets - so the car still receives full-level audio.
     */
    private fun mutePhone() {
        if (mutedPhone) return
        runCatching {
            getSystemService(android.media.AudioManager::class.java)
                .adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
            mutedPhone = true
            // Remembered past this process: killed mid-drive, nothing here runs to unmute, and the
            // next start does it instead (see restorePhoneIfLeftMuted).
            io.github.sirionero.mycar.AppSettings.putString(this, KEY_LEFT_MUTED, "true")
            MyCarLog.i("audio: muted the phone's speaker for the drive")
        }.onFailure { MyCarLog.w("audio: could not mute the phone: ${it.message}") }
    }

    private fun unmutePhone() {
        if (!mutedPhone) return
        mutedPhone = false
        unmute(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** The capture was started again once after ending on its own; see beginCapture. */
    private var restarted = false

    /**
     * Android ended the projection: sharing the phone's screen took it (Android keeps one), or the
     * driver stopped it. Nothing more can be recorded on it, and no question is asked now.
     */
    private var projectionStopped = false

    /** Which capture is the current one, so an old capture's end doesn't stop a new one. */
    @Volatile private var captureRun = 0

    override fun onCreate() {
        super.onCreate()
        running = true
        current = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // While still in the foreground: Android 17 ignores volume calls from an app that
            // isn't, and the phone stayed muted after the drive.
            unmutePhone()
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (data == null) {
            MyCarLog.e("audio: started without a consent result")
            CarAudio.denied()
            stopSelf()
            return START_NOT_STICKY
        }

        // Android silences the microphone for apps in the background unless a running service
        // claims it. Claiming it here lets voice replies hear the driver with the phone locked.
        // Without the permission, claiming it would crash, so it's left off.
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
            (if (micGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        startForeground(NOTIFICATION_ID, notification(), type)
        ScreenShareGuard.lift(this)
        // Asked again while a capture was still here (the last one's reading had ended): let it go
        // first. Android stops it anyway for the new one, and its end used to stop this service,
        // the new capture with it.
        projection?.let { old ->
            reading = false
            runCatching { thread?.join(500) }
            projection = null
            runCatching { old.stop() }
        }
        projection = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            MyCarLog.e("audio: getMediaProjection failed", t)
            null
        }
        val token = projection
        if (token == null) {
            CarAudio.denied()
            stopSelf()
            return START_NOT_STICKY
        }
        projectionStopped = false
        token.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (token !== projection) return
                if (handOver.ownStopped()) {
                    // The screen sharing took it, Android keeping one: the capture moves onto the
                    // sharing's once its picture is up (follow), the phone still muted meanwhile.
                    MyCarLog.i("audio: the screen sharing took the projection - the capture moves onto it")
                    projection = null
                    return
                }
                MyCarLog.w("audio: projection stopped")
                projectionStopped = true
                unmutePhone()
                stopSelf()
            }
        }, null)
        beginCapture(token)
        return START_NOT_STICKY
    }

    private fun beginCapture(token: MediaProjection, again: Boolean = false, onSharing: Boolean = false) {
        val rate = CarAudio.sampleRate
        val channels = CarAudio.channels
        val record = try {
            // Matched by usage, not by app. A uid filter would have to be rebuilt every time the
            // driver changed media app, and rebuilding means a fresh consent dialog - so the
            // filter is what the sound is for, and any app playing media reaches the car.
            val config = AudioPlaybackCaptureConfiguration.Builder(token)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val mask = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(mask)
                .build()
            val minimum = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum, CHUNK_BYTES * BUFFER_CHUNKS))
                .build()
        } catch (t: Throwable) {
            MyCarLog.e("audio: could not set up playback capture", t)
            // On the screen sharing's projection: the music pauses for the sharing, as before the
            // capture could follow it. Not the driver's "no" either.
            if (onSharing) {
                endWithSharing("no capture on the screen sharing's projection")
                return
            }
            stopSelf()
            // Started again after ending on its own: this isn't the driver's "no", and DENIED would
            // leave the car without music for the rest of the drive. The question starts clean instead.
            if (!again) CarAudio.denied() else if (!MirrorService.sharing) CarAudio.captureNow()
            return
        }

        reading = true
        val run = ++captureRun
        thread = Thread({
            // A busy phone (a map app drawing, the video encoder) would otherwise starve this
            // loop, and the car hears every late read as a skip.
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(CHUNK_BYTES)
            var peak = 0
            var heardAt = System.currentTimeMillis()
            val startedAt = heardAt
            var everHeard = false
            var warnedSilent = false
            try {
                record.startRecording()
                MyCarLog.i("audio: capturing media playback at ${rate}Hz x$channels")
                mutePhone()
                CarAudio.captureStarted()
                while (reading) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) {
                        MyCarLog.e("audio: AudioRecord.read returned $n - capture has stopped")
                        break
                    }
                    for (i in 0 until n - 1 step 2) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        peak = maxOf(peak, abs(sample.toShort().toInt()))
                    }
                    if (peak > 0) everHeard = true
                    val now = System.currentTimeMillis()
                    val level = peak / 32768f
                    if (now - heardAt >= LEVEL_LOG_MS) {
                        MyCarLog.i("audio: peak %.3f".format(level))
                        heardAt = now
                        peak = 0
                    }
                    // Capture never fails for an app that opts out; it just hands back digital
                    // silence. Say so, because the symptom is identical to a broken car link.
                    if (!everHeard && !warnedSilent && now - startedAt >= SILENCE_MS) {
                        warnedSilent = true
                        MyCarLog.w(
                            "audio: ${SILENCE_MS / 1000}s of capture and every sample is zero. " +
                                "Either nothing is playing, or the app does not allow its audio " +
                                "to be captured.",
                        )
                    }
                    CarAudio.deliver(buffer, n, level)
                }
            } catch (t: Throwable) {
                MyCarLog.e("audio: capture loop ended", t)
            } finally {
                runCatching { record.stop() }
                record.release()
                when (handOver.captureEnded(onSharing, stoppedHere = !reading)) {
                    // Moving onto the screen sharing's projection, or there already: that capture keeps
                    // the phone muted and the car's sound.
                    SharingHandOver.End.NOTHING -> return@Thread
                    // On the sharing's projection, ended without a stop from here: as its projection
                    // went, or the audio server restarted. It ends with the sharing.
                    SharingHandOver.End.WITH_SHARING -> {
                        main.post { if (run == captureRun) endWithSharing("the capture on the screen sharing's projection ended") }
                        return@Thread
                    }
                    SharingHandOver.End.AS_BEFORE -> Unit
                }
                unmutePhone()
                CarAudio.captureStopped()
                // Ended on its own (the audio server restarted). Once, a new recorder on the same
                // projection carries on, with no new question and the microphone kept for voice
                // replies. Otherwise the service goes, so the next question starts clean - and it's
                // asked now, not only when a music app is opened again. Not when Android ended the
                // projection: then the question comes with the next music app, or when the screen
                // sharing that took it ends (asked during it, it would end the sharing).
                if (reading && run == captureRun) main.post {
                    if (run != captureRun) return@post
                    val token = projection
                    val ended = projectionStopped || MirrorService.sharing
                    if (token != null && !restarted && !ended) {
                        restarted = true
                        MyCarLog.w("audio: capture ended on its own - starting it again on the same projection")
                        beginCapture(token, again = true)
                    } else {
                        stopSelf()
                        if (!ended) CarAudio.captureNow()
                    }
                }
            }
        }, "mycar-audio").apply { isDaemon = true; start() }
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacks(giveUp)
        reading = false
        runCatching { thread?.join(500) }
        // Belt and braces: a phone left muted after the drive is a bug the driver blames on their
        // phone, not on myCar, so unmute on every way out of here.
        unmutePhone()
        // Waiting for the screen sharing's projection, no capture's reading is left to say it ended:
        // unsaid, the car kept its speakers and the capture read as on for the rest of the drive.
        if (handOver.serviceEnds()) CarAudio.captureStopped()
        // The service's own only: the screen sharing's, if the capture runs on it, stays the sharing's.
        runCatching { projection?.stop() }
        projection = null
        sharedProjection = null
        ScreenShareGuard.restore(this)
        running = false
        started = false
        if (current === this) current = null
    }

    // --- Screen sharing -----------------------------------------------------------------

    /** See [followSharing]. */
    private fun prepareHandOver(): Boolean {
        if (!handOver.sharingStarts(ownAlive = projection != null)) return false
        // Notifications stay hidden in the shared screen, as Android has them while a screen is shown:
        // the guard is for the sound alone. Back on before the sharing's projection starts.
        ScreenShareGuard.restore(this)
        main.postDelayed(giveUp, HAND_OVER_MS)
        MyCarLog.i("audio: the screen sharing starts - the capture moves onto its projection")
        return true
    }

    /** See [follow]. */
    private fun takeSharing(token: MediaProjection) {
        if (!handOver.sharingUp()) return
        main.removeCallbacks(giveUp)
        // The service's own projection is gone, whether its stop has reached here or not: Android
        // stopped it for this one, keeping one. Stopped from here too (for a stopped one it's nothing),
        // so that two captures could never feed the car at once.
        val own = projection
        projection = null
        runCatching { own?.stop() }
        sharedProjection = token
        token.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (token === sharedProjection) endWithSharing("the screen sharing's projection stopped")
            }
        }, null)
        MyCarLog.i("audio: capturing on the screen sharing's projection - the music carries on in the car")
        beginCapture(token, onSharing = true)
    }

    /** The sharing's projection hasn't reached the capture in time: it ends, or stays on its own. */
    private val giveUp = Runnable {
        if (handOver.stage == SharingHandOver.Stage.MOVING) endWithSharing("the screen sharing's projection didn't come")
    }

    /**
     * The capture that followed the screen sharing ends with it: the sharing is over, its projection
     * stopped or never came, or the capture on it couldn't go on. The phone's music pauses first: with
     * the capture gone the phone isn't muted any more, and the music would play out of its speaker
     * until Android's question is answered again, which comes when the sharing is over ([sharingEnded]).
     */
    private fun endWithSharing(why: String) {
        when (handOver.sharingOver(ownAlive = projection != null)) {
            SharingHandOver.Over.NOTHING -> return
            SharingHandOver.Over.STAY -> {
                // The sharing never took the service's own projection: the capture goes on there.
                main.removeCallbacks(giveUp)
                MyCarLog.i("audio: $why - the capture stays on its own projection")
                ScreenShareGuard.lift(this)
                return
            }
            SharingHandOver.Over.END -> Unit
        }
        main.removeCallbacks(giveUp)
        reading = false
        sharedProjection = null
        askAfterSharing = true
        MyCarLog.i("audio: $why - the capture ends with the screen sharing")
        io.github.sirionero.mycar.car.CarServices.pausePhoneMedia("screen sharing", keep = packageName)
        unmutePhone()
        CarAudio.captureStopped()
        stopSelf()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, Words.get(R.string.channel_audio), NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, AudioCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(Words.get(R.string.audio_notification_title))
            .setContentText(Words.get(R.string.audio_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, Words.get(R.string.service_notification_stop), stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "mycar_audio"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "io.github.sirionero.mycar.STOP_AUDIO"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        /** About 21 ms of 48 kHz stereo: small enough for lip-sync, big enough not to flood the link. */
        private const val CHUNK_BYTES = 4096
        /** About half a second: room to wait out a slow ack from the car without losing sound. */
        private const val BUFFER_CHUNKS = 24
        private const val LEVEL_LOG_MS = 3_000L
        private const val SILENCE_MS = 5_000L

        private var appContext: Context? = null

        private const val KEY_LEFT_MUTED = "audio_phone_left_muted"

        /** How long the capture waits for the screen sharing's projection, from the sharing's Start. */
        private const val HAND_OVER_MS = 10_000L

        /** The running service, for the screen sharing's calls below: all on the main thread. */
        private var current: AudioCaptureService? = null

        /** A capture ended with the screen sharing: Android's question comes back once the sharing is over. */
        private var askAfterSharing = false

        /**
         * The phone's screen is about to be shared with the car (the driver tapped Start), with the
         * capture on. Android keeps one projection, and the sharing's takes the place of this one: the
         * capture moves onto it ([follow]) rather than end, so the car's music carries on through the
         * sharing, and ends with it ([sharingEnded]). False when there's no capture to move.
         */
        fun followSharing(): Boolean = current?.prepareHandOver() ?: false

        /** The sharing's projection is up, its picture made: the capture waiting for it moves onto it. */
        fun follow(token: MediaProjection) {
            current?.takeSharing(token)
        }

        /**
         * The screen sharing is over (its service gone), before its projection is stopped: a capture
         * on it ends with it, the phone's music paused first. Then Android's question comes back for
         * the car, as the car's screen asks it when it comes back - and when it doesn't need to.
         */
        fun sharingEnded() {
            current?.endWithSharing("the screen sharing ended")
            if (!askAfterSharing) return
            askAfterSharing = false
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (CarAudio.stillWanted) CarAudio.request() }
        }

        private fun unmute(context: Context) {
            runCatching {
                val audio = context.getSystemService(android.media.AudioManager::class.java)
                audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
                // Android 17 can ignore the call without a word: the flag stays, for the next try.
                if (audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) {
                    MyCarLog.w("audio: the phone's speaker is still muted - trying again when myCar opens")
                } else {
                    io.github.sirionero.mycar.AppSettings.putString(context, KEY_LEFT_MUTED, "false")
                    MyCarLog.i("audio: gave the phone's speaker back")
                }
            }.onFailure { MyCarLog.w("audio: could not unmute the phone: ${it.message}") }
        }

        /**
         * A capture that ended without its service shutting down - myCar closed by a crash, or by
         * Android - left the phone's music muted. Gives it back at the next start.
         */
        fun restorePhoneIfLeftMuted(context: Context) {
            if (running) return
            if (io.github.sirionero.mycar.AppSettings.getString(context, KEY_LEFT_MUTED, "false") != "true") return
            MyCarLog.i("audio: the phone was left muted by an earlier drive")
            unmute(context.applicationContext)
        }

        /** True between onCreate and onDestroy; lets the activity tell a live capture from a crashed one. */
        @Volatile var running = false
            private set

        /** From [start] until the service ends, so [stop] knows there's something to stop. */
        @Volatile private var started = false

        fun start(context: Context, resultCode: Int, data: Intent) {
            appContext = context.applicationContext
            started = true
            context.startForegroundService(
                Intent(context, AudioCaptureService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data),
            )
        }

        fun stop() {
            // Starting the service only to stop it, when it never ran, is refused while myCar is in
            // the background (Android 12 on) - as at the end of a drive with the phone locked - and
            // the refusal is an exception that would close myCar.
            if (!started) return
            val context = appContext ?: return
            runCatching { context.startService(Intent(context, AudioCaptureService::class.java).setAction(ACTION_STOP)) }
                .onFailure { MyCarLog.w("audio: could not stop the capture service: $it") }
        }
    }
}
