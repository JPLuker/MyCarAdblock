package io.github.sirionero.mycar

import android.content.Context
import io.github.sirionero.mycar.stats.UsageStats
import android.os.Build
import android.os.SystemClock

/**
 * The facts a bug report needs, gathered as a session runs and printed as one readable block
 * when it ends. The log holds the full trace; this holds the answer to "what went wrong,
 * on what car, on what phone" so nobody has to read the trace to find out.
 *
 * Nothing here identifies a person: no serial numbers, accounts, locations or accessory
 * serials. The head unit and vehicle strings are what the head unit itself announces.
 */
object SessionReport {

    /** Where a session failed, coarse enough to count and group reports by. */
    enum class Category(val label: String) {
        NONE("none"),
        USB("USB transport"),
        NO_HANDSHAKE("head unit never started talking"),
        TLS("TLS handshake failed"),
        CERTIFICATE("certificate rejected by the head unit"),
        AUTH("authentication failed"),
        SERVICE_DISCOVERY("service discovery failed"),
        VIDEO("video stream failed"),
        BYEBYE("head unit ended the session"),
        CRASH("app crash"),
        UNKNOWN("unclassified"),
    }

    /**
     * How far a session got, in the order a connection is built. The usage note's "Where
     * connections fail" share sends the furthest one reached, so a failure says where it broke.
     */
    enum class Stage(val key: String) {
        USB("usb"),             // the link opened; the car may have said nothing
        VERSIONS("versions"),   // the car said which protocol it speaks
        TLS("tls"),             // the secure link is up
        ACCEPTED("accepted"),   // the car accepted the certificate
        CHANNELS("channels"),   // the car listed its channels and said who it is
        VIDEO("video"),         // the car's screen is set up for myCar
        STREAMING("streaming"), // video frames are reaching the car
    }

    private var appVersion = "?"
    private var appCode = 0L

    @Volatile private var startedAt = 0L
    @Volatile var category = Category.NONE; private set
    @Volatile private var failure = ""
    @Volatile var stage = Stage.USB; private set
    /** What the car or the phone said when it broke: a TLS alert, an auth status, a ByeBye reason, an errno. */
    @Volatile private var failCode = ""
    @Volatile private var failedAfter = 0L
    @Volatile private var protocolVersion = ""
    @Volatile private var certSource = ""
    @Volatile private var certIssuer = ""
    @Volatile private var tlsProtocol = ""
    @Volatile private var tlsCipher = ""
    @Volatile private var authStatus: Int? = null
    @Volatile private var headUnit = ""
    @Volatile private var profile = ""
    @Volatile private var video = ""
    @Volatile private var audio = ""
    /** The car audio route chosen, and for Automatic the way this drive went; see CarAudioRoute. */
    @Volatile private var audioRoute = ""
    @Volatile private var lastStream = ""
    @Volatile private var lastState = ""
    private var printed = false

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        appVersion = info?.versionName ?: "?"
        appCode = info?.longVersionCode ?: 0
    }

    /** How this drive reached the car, and how its music did: "usb" or "wireless", "usb" or "bluetooth". */
    @Volatile var link = ""; private set
    @Volatile var audioMode = ""; private set

    fun begin(wireless: Boolean) {
        startedAt = System.currentTimeMillis()
        link = if (wireless) "wireless" else "usb"
        audioMode = io.github.sirionero.mycar.car.CarSettings.audioRoute.value.id
        audioRoute = audioMode
        category = Category.NONE
        failure = ""; protocolVersion = ""; certSource = ""; certIssuer = ""
        stage = Stage.USB; failCode = ""; failedAfter = 0
        tlsProtocol = ""; tlsCipher = ""; authStatus = null
        headUnitInfo = null
        carScreen = ""; carDpi = 0
        headUnit = ""; profile = ""; video = ""; lastStream = ""; lastState = ""
        lastFromCarAt = 0; wifiLostAt = 0; channelClosedAt = 0; gaveUpAfterS = -1
        musicSeen = false; musicNow = false; musicLostAt = 0; ownScreenSince = 0; carState = null
        endedAt = 0; musicOnBluetoothAtEnd = false
        printed = false
    }

    // How a wireless drive ended (SessionEnd), all on elapsedRealtime, 0 for "never".
    @Volatile private var lastFromCarAt = 0L
    @Volatile private var wifiLostAt = 0L
    @Volatile private var channelClosedAt = 0L
    @Volatile private var gaveUpAfterS = -1L
    @Volatile private var musicSeen = false
    @Volatile private var musicNow = false
    @Volatile private var musicLostAt = 0L
    @Volatile private var ownScreenSince = 0L
    @Volatile private var carState: SessionEnd.CarState? = null
    /** When the report was first printed, the session's end; and whether the drive's music was on Bluetooth then. */
    @Volatile private var endedAt = 0L
    @Volatile private var musicOnBluetoothAtEnd = false

    /** The car pinged, or acked a video frame. */
    fun heardFromCar(at: Long = SystemClock.elapsedRealtime()) { lastFromCarAt = at }

    /** Android dropped the car's network under the session. */
    fun carWifiLost(at: Long = SystemClock.elapsedRealtime()) { if (wifiLostAt == 0L) wifiLostAt = at }

    /** The car closed its Bluetooth channel during the session. */
    fun carChannelClosed(at: Long = SystemClock.elapsedRealtime()) { channelClosedAt = at }

    /** myCar ended the session after [silentS] seconds of the car's silence. */
    fun gaveUp(silentS: Long) { gaveUpAfterS = silentS }

    /** Whether the phone has the car's Bluetooth music now (watched from the car's picture to the end). */
    fun carMusicBluetooth(connected: Boolean, at: Long = SystemClock.elapsedRealtime()) {
        if (connected) {
            musicSeen = true
            musicLostAt = 0
        } else if (musicNow) {
            musicLostAt = at
        }
        musicNow = connected
    }

    /** The car shows its own screen ([own]) or myCar's again. */
    fun carOwnScreen(own: Boolean, at: Long = SystemClock.elapsedRealtime()) {
        ownScreenSince = if (!own) 0L else ownScreenSince.takeIf { it != 0L } ?: at
    }

    /** The car's driving state, speed and gear, as it said last. */
    internal fun car(state: SessionEnd.CarState) { carState = state }

    private fun endFacts(): SessionEnd.Facts {
        val ended = endedAt != 0L
        return SessionEnd.Facts(
            endedAt = if (ended) endedAt else SystemClock.elapsedRealtime(),
            lastFromCarAt = lastFromCarAt.takeIf { it != 0L },
            wifiLostAt = wifiLostAt.takeIf { it != 0L },
            channelClosedAt = channelClosedAt.takeIf { it != 0L },
            gaveUpAfterS = gaveUpAfterS.takeIf { it >= 0 },
            music = when {
                musicNow -> SessionEnd.Music.CONNECTED
                musicSeen -> SessionEnd.Music.LOST
                else -> SessionEnd.Music.NEVER
            },
            musicLostAt = musicLostAt.takeIf { it != 0L },
            musicOnBluetooth = if (ended) musicOnBluetoothAtEnd else !io.github.sirionero.mycar.car.CarSettings.pipeAudio.value,
            ownScreenSince = ownScreenSince.takeIf { it != 0L },
            car = carState,
        )
    }

    /** Moves the session's furthest stage on; never back. */
    private fun reached(next: Stage) { if (next > stage) stage = next }

    fun protocolVersion(major: Int, minor: Int) { protocolVersion = "$major.$minor"; reached(Stage.VERSIONS) }
    fun certificate(source: String, issuer: String) { certSource = source; certIssuer = issuer }
    fun tls(protocol: String, cipher: String) { tlsProtocol = protocol; tlsCipher = cipher; reached(Stage.TLS) }
    fun auth(status: Int) { authStatus = status; if (status == 0) reached(Stage.ACCEPTED) }
    fun headUnit(info: String) { headUnit = info }

    /** Who the car said it is, for the opt-in usage note; null until service discovery. */
    @Volatile var headUnitInfo: ServiceDiscovery.HeadUnitInfo? = null
        set(value) { field = value; if (value != null) reached(Stage.CHANNELS) }

    /** The car screen's usable size once its margins are cropped, and its density; blank before video starts. */
    @Volatile var carScreen = ""; private set
    @Volatile var carDpi = 0; private set
    fun carScreen(size: String, dpi: Int) { carScreen = size; carDpi = dpi; reached(Stage.VIDEO) }
    fun profile(name: String?) { profile = name ?: "none matched" }
    fun video(summary: String) { video = summary }
    fun audio(summary: String) { audio = summary }

    fun audioRoute(summary: String) { audioRoute = summary }

    /**
     * The car's own route, set as the car is recognised (CarSettings.carIdentified): taken from the
     * route for every car at [begin], a wireless car with Bluetooth music read "audio route link".
     */
    fun carRoute(id: String) {
        audioMode = id
        audioRoute = "this car's own route: $id"
    }

    /** The category as the report says it: a transport that failed over Wi-Fi isn't the USB's. */
    private fun shown(category: Category): String =
        if (category == Category.USB && link == "wireless") "Wi-Fi transport" else category.label
    fun stream(summary: String) { lastStream = summary; reached(Stage.STREAMING) }

    /**
     * Records the first failure only: later ones are usually fallout from it. [code] is the short
     * thing the car or the phone said, like "auth -3" or "bye 1", for the usage note.
     */
    fun fail(category: Category, detail: String, state: String = "", code: String = "") {
        if (this.category != Category.NONE) return
        this.category = category
        failure = detail
        lastState = state
        failCode = code
        failedAfter = if (startedAt == 0L) 0 else (System.currentTimeMillis() - startedAt) / 1000
    }

    fun fail(category: Category, detail: String, t: Throwable, state: String = "") =
        fail(category, "$detail (${MyCarLog.rootCause(t)})", state, codeOf(t))

    /**
     * A short code from an exception, never its whole message: the TLS alert's name, the errno
     * of a USB failure, or else the exception's class.
     */
    private fun codeOf(t: Throwable): String {
        var root = t
        while (root.cause != null && root.cause !== root) root = root.cause!!
        val text = "${t.message} ${root.message}"
        Regex("(?i)alert[ _:]*\\(?([a-z_ ]{3,40}?)\\)?(?:\\s|$|[,;.])").find(text)?.let {
            return "alert ${it.groupValues[1].trim().lowercase().replace(' ', '_')}"
        }
        Regex("\\b(E[A-Z]{2,12})\\b").find(text)?.let { return it.groupValues[1] }
        return root.javaClass.simpleName.take(40)
    }

    /**
     * One session for the usage note's "Where connections fail" share: how far it got, how and
     * when it ended, and with which kind of certificate. Counts and codes only, nothing personal.
     */
    fun sessionNote(): org.json.JSONObject {
        val seconds = if (startedAt == 0L) 0 else (System.currentTimeMillis() - startedAt) / 1000
        val source = certSource.lowercase()
        return org.json.JSONObject()
            .put("stage", stage.key)
            .put("ended", category.name.lowercase())
            .put("code", failCode)
            .put("failed_at", if (category == Category.NONE) 0 else rounded(failedAfter))
            .put("seconds", rounded(seconds))
            .put("cert", when {
                "imported" in source -> "imported"
                "downloaded" in source -> "downloaded"
                "self" in source -> "self-signed"
                source.isEmpty() -> ""
                else -> "other"
            })
    }

    /** Seconds rounded so a drive's length can't be matched to anything: 5 s, then minutes, then 5 minutes. */
    private fun rounded(s: Long): Long = when {
        s < 60 -> (s + 2) / 5 * 5
        s < 3600 -> (s + 30) / 60 * 60
        else -> (s + 150) / 300 * 300
    }

    /** The report as plain text, for the log file and for anything that gets sent. */
    fun render(): String {
        val seconds = if (startedAt == 0L) 0 else (System.currentTimeMillis() - startedAt) / 1000
        // Always a space after the label: "last stream stat" filled its 16 and ran into its value.
        fun row(name: String, value: String) = if (value.isEmpty()) "" else "  ${name.padEnd(LABEL_WIDTH)} $value\n"
        // A wireless drive that reached the car's screen: how it ended, from what the phone saw last.
        val end = if (link == "wireless" && carScreen.isNotEmpty()) endFacts() else null
        val result = if (category == Category.NONE) "ok / no failure recorded" else "FAILED - ${shown(category)}"
        return buildString {
            append("=".repeat(60)).append('\n')
            append("SESSION REPORT\n")
            // Only the words: a transport that failed stays one for the usage note (print).
            append(row("result", end?.takeIf { category == Category.USB }?.let { SessionEnd.result(it) } ?: result))
            append(row("detail", failure))
            append(row("failed in state", lastState))
            append(row("duration", "${seconds}s"))
            end?.let { facts -> SessionEnd.rows(facts).forEach { (name, value) -> append(row(name, value)) } }
            append("\n")
            append(row("mycar", "$appVersion (code $appCode)"))
            append(row("phone", "${Build.MANUFACTURER} ${Build.MODEL}"))
            append(row("android", "${Build.VERSION.RELEASE} (sdk ${Build.VERSION.SDK_INT}, patch ${Build.VERSION.SECURITY_PATCH})"))
            append("\n")
            append(row("head unit", headUnit))
            append(row("AA protocol", protocolVersion))
            append(row("profile", profile))
            append("\n")
            append(row("phone cert", certSource))
            append(row("cert issuer", certIssuer))
            append(row("TLS", listOf(tlsProtocol, tlsCipher).filter { it.isNotEmpty() }.joinToString(" / ")))
            append(row("auth status", authStatus?.let { "$it (${authName(it)})" } ?: ""))
            append("\n")
            append(row("video", video))
            append(row("audio", audio))
            append(row("audio route", audioRoute))
            append(row("last stream stat", lastStream))
            append("=".repeat(60))
        }
    }

    /** Prints the report into the log, once per session. */
    fun print() {
        if (printed || startedAt == 0L) return
        printed = true
        // The session's end, for the report printed again later (a log shared): seconds since then stay.
        endedAt = SystemClock.elapsedRealtime()
        musicOnBluetoothAtEnd = !io.github.sirionero.mycar.car.CarSettings.pipeAudio.value
        render().lines().forEach { MyCarLog.i(it) }
        appContext?.let { context ->
            // Video started means the car showed myCar: that's a working car, and unplugging or
            // switching the car off at the end shows up as a USB or ByeBye "failure" that isn't one.
            val reached = carScreen.isNotEmpty()
            val outcome = when {
                reached && category in setOf(Category.NONE, Category.USB, Category.BYEBYE) -> "connected"
                reached -> "connected, then ${category.label}"
                category == Category.NONE -> "ended before video"
                else -> category.label
            }
            runCatching {
                UsageStats.recordSession(context, headUnitInfo, protocolVersion, outcome, carScreen, carDpi, sessionNote(), link, audioMode)
            }
            if (outcome == "connected") runCatching {
                io.github.sirionero.mycar.stats.WorkedPrompt.onDriveWorked(context, headUnitInfo, protocolVersion, carScreen, carDpi)
                io.github.sirionero.mycar.ui.DonatePrompt.onDriveWorked(context)
            }
            runCatching { io.github.sirionero.mycar.update.UpdateChecker.onDriveEnded(context) }
        }
        MyCarLog.flush()
    }

    /** The longest label, "car Bluetooth audio". */
    private const val LABEL_WIDTH = 19

    private fun authName(status: Int) = when (status) {
        0 -> "success"
        -2 -> "certificate error"
        -3 -> "authentication failure"
        else -> "unknown"
    }
}
