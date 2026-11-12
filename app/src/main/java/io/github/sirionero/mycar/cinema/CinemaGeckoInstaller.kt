package io.github.sirionero.mycar.cinema

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.app.PendingIntent
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Optional GeckoView feature delivery for GitHub builds. Base APK needs no Gecko libraries,
 * Play Feature Delivery or third-party code loader. Android itself verifies same package,
 * versionCode and signing certificate when a matching split is appended to this install.
 *
 * This component is intentionally inert until a release publishes a version-matched zip.
 */
object CinemaGeckoInstaller {
    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data class Downloading(val fraction: Float) : Status
        data class Downloaded(val bytes: Long) : Status
        data object Installing : Status
        data object Installed : Status
        data class Failed(val reason: String) : Status
    }

    private const val RELEASES = "https://api.github.com/repos/JPLuker/MyCarAdblock/releases/tags/v"
    private const val ASSET = "myCar-gecko-arm64-v8a.zip"
    private const val ZIP_MAX_BYTES = 450L * 1024 * 1024
    private const val APK_MAX_BYTES = 400L * 1024 * 1024
    private val APK_NAMES = setOf("cinema_gecko-master.apk", "cinema_gecko-arm64_v8a.apk")
    private const val CALLBACK_REQUEST = 7321

    private val flow = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = flow.asStateFlow()
    private val cancelled = AtomicBoolean(false)

    private fun directory(context: Context): File = File(context.cacheDir, "optional-gecko")
    private fun archive(context: Context): File = File(directory(context), ASSET)
    private fun receipt(context: Context): File = File(directory(context), "verified.json")

    fun refresh(context: Context) {
        if (CinemaGeckoBridge.installed(context)) {
            flow.value = Status.Installed
            return
        }
        if (flow.value is Status.Installed) flow.value = Status.Idle
        // Android may have stopped myCar while it opened the package-install permission page.
        // The verified download is in app-private cache, so a return can offer Install again.
        if (flow.value is Status.Idle && !BuildConfig.DEBUG) {
            val file = archive(context)
            if (file.isFile && runCatching { readReceipt(context, file) }.isSuccess) {
                flow.value = Status.Downloaded(file.length())
            }
        }
    }

    /** Explicitly started from Settings on the phone, never automatically on first launch. */
    fun download(context: Context) {
        if (BuildConfig.DEBUG) {
            flow.value = Status.Failed(
                "The Firefox engine needs a matching signed release build; debug APKs use a different package ID.",
            )
            return
        }
        if (flow.value is Status.Checking || flow.value is Status.Downloading || flow.value is Status.Installing) return
        val app = context.applicationContext
        if (CinemaGeckoBridge.installed(app)) { flow.value = Status.Installed; return }
        cancelled.set(false)
        flow.value = Status.Checking
        Thread({
            try {
                val asset = getReleaseAsset()
                if (cancelled.get()) throw InterruptedException()
                val file = archive(app)
                directory(app).apply { mkdirs() }
                downloadVerified(asset, file)
                validateSplitArchive(file)
                if (cancelled.get()) throw InterruptedException()
                saveReceipt(app, asset)
                flow.value = Status.Downloaded(file.length())
            } catch (_: InterruptedException) {
                archive(app).delete()
                receipt(app).delete()
                flow.value = Status.Idle
            } catch (error: Exception) {
                archive(app).delete()
                receipt(app).delete()
                MyCarLog.w("cinema: optional Gecko download failed (" + error.javaClass.simpleName + ")")
                flow.value = Status.Failed(error.message ?: "Optional browser download failed")
            }
        }, "mycar-gecko-download").start()
    }

    /**
     * Debug APKs cannot install the official release split (different application ID and key).
     * For device verification, import the matching ZIP from the very same Actions build.
     * This is deliberately absent from release APKs and never enables code from raw archives:
     * Android's PackageInstaller still checks the package, version and signing certificate.
     */
    fun importDebugSplit(context: Context, source: Uri) {
        if (!BuildConfig.DEBUG) return
        if (flow.value is Status.Checking || flow.value is Status.Downloading ||
            flow.value is Status.Installing) return
        val app = context.applicationContext
        cancelled.set(false)
        flow.value = Status.Checking
        Thread({
            val partial = File(directory(app), "$ASSET.part")
            try {
                directory(app).mkdirs()
                receipt(app).delete()
                archive(app).delete()
                partial.delete()
                val digest = MessageDigest.getInstance("SHA-256")
                var bytes = 0L
                val input = app.contentResolver.openInputStream(source)
                    ?: throw IOException("Cannot open the selected feature ZIP")
                input.use { sourceStream ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled.get()) throw InterruptedException()
                            val count = sourceStream.read(buffer)
                            if (count < 0) break
                            bytes += count
                            if (bytes > ZIP_MAX_BYTES) throw IOException("Oversized feature ZIP")
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (cancelled.get()) throw InterruptedException()
                val zip = archive(app)
                if (!partial.renameTo(zip)) throw IOException("Could not finalize imported ZIP")
                validateSplitArchive(zip)
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                saveReceipt(app, Asset("", bytes, hash))
                flow.value = Status.Downloaded(bytes)
            } catch (_: InterruptedException) {
                archive(app).delete()
                receipt(app).delete()
                flow.value = Status.Idle
            } catch (error: Exception) {
                archive(app).delete()
                receipt(app).delete()
                flow.value = Status.Failed(error.message ?: "Could not import the test engine")
            } finally {
                partial.delete()
            }
        }, "mycar-gecko-debug-import").start()
    }

    fun cancel(context: Context) {
        cancelled.set(true)
        // Network request is bounded by timeouts. Its thread removes the partial file.
        if (flow.value is Status.Downloaded) {
            archive(context).delete()
            receipt(context).delete()
            flow.value = Status.Idle
        }
    }

    fun askInstallPermission(context: Context) {
        if (context.packageManager.canRequestPackageInstalls()) return
        context.startActivity(Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Called after Android's unknown-app-source permission is granted. */
    fun install(context: Context) {
        if (flow.value !is Status.Downloaded) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            flow.value = Status.Failed("Allow installations from myCar in Android settings, then download again.")
            return
        }
        val app = context.applicationContext
        val zip = archive(app)
        val installer = app.packageManager.packageInstaller
        flow.value = Status.Installing
        Thread({
            var sessionId = -1
            try {
                // Re-hash immediately before use. A cached ZIP's existence/shape alone
                // cannot authorize installing code after an interrupted download or restart.
                verifyReceipt(app, zip)
                validateSplitArchive(zip)
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING).apply {
                    setAppPackageName(app.packageName)
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                sessionId = installer.createSession(params)
                val session = installer.openSession(sessionId)
                try {
                    ZipFile(zip).use { archive ->
                        for (name in APK_NAMES.sorted()) {
                            val entry = archive.getEntry(name) ?: throw IOException("Missing split: $name")
                            session.openWrite(name, 0, entry.size).use { output ->
                                archive.getInputStream(entry).use { input ->
                                    val buffer = ByteArray(64 * 1024)
                                    var written = 0L
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        written += count
                                        if (written > APK_MAX_BYTES) throw IOException("Oversized split")
                                        output.write(buffer, 0, count)
                                    }
                                    if (written != entry.size) throw IOException("Incomplete split")
                                }
                                session.fsync(output)
                            }
                        }
                    }
                    val reply = Intent(app, CinemaGeckoInstallReceiver::class.java)
                    val sender = PendingIntent.getBroadcast(
                        app, CALLBACK_REQUEST, reply,
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ).intentSender
                    session.commit(sender)
                } finally {
                    session.close()
                }
            } catch (e: Exception) {
                if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
                flow.value = Status.Failed("Firefox installation failed: " + (e.message ?: e.javaClass.simpleName))
                MyCarLog.w("cinema: split installation error: " + e.javaClass.simpleName)
            }
        }, "mycar-gecko-install").start()
    }

    internal fun onInstallerResult(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirm == null) {
                    flow.value = Status.Failed("Android did not provide an installation confirmation")
                } else {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { flow.value = Status.Failed("Could not show Android's installation confirmation") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                archive(context).delete()
                receipt(context).delete()
                flow.value = if (CinemaGeckoBridge.installed(context)) Status.Installed
                    else Status.Failed("Android installed the engine. Restart myCar to finish enabling it.")
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                flow.value = Status.Failed("Android refused the optional browser: $message")
            }
        }
    }

    private data class Asset(val url: String, val size: Long, val sha256: String)

    /** A download receipt is only a hint for the UI; installation always re-hashes the ZIP. */
    private fun saveReceipt(context: Context, asset: Asset) {
        val file = AtomicFile(receipt(context))
        val bytes = JSONObject()
            .put("version", BuildConfig.VERSION_NAME)
            .put("size", asset.size)
            .put("sha256", asset.sha256.lowercase())
            .toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun readReceipt(context: Context, zip: File): Pair<Long, String> {
        if (!zip.isFile || zip.length() !in 1..ZIP_MAX_BYTES) {
            throw IOException("Optional browser package is missing")
        }
        val payload = JSONObject(AtomicFile(receipt(context)).openRead().bufferedReader().use { it.readText() })
        val length = payload.optLong("size")
        val digest = payload.optString("sha256")
        if (payload.optString("version") != BuildConfig.VERSION_NAME ||
            length != zip.length() || !digest.matches(Regex("[a-f0-9]{64}"))) {
            throw IOException("Cached optional browser does not match this app release")
        }
        return length to digest
    }

    private fun verifyReceipt(context: Context, zip: File) {
        val (expectedLength, expectedDigest) = readReceipt(context, zip)
        val md = MessageDigest.getInstance("SHA-256")
        var read = 0L
        zip.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                read += count
                if (read > ZIP_MAX_BYTES) throw IOException("Oversized optional engine")
                md.update(buffer, 0, count)
            }
        }
        val actual = md.digest().joinToString("") { "%02x".format(it) }
        if (read != expectedLength || actual != expectedDigest) {
            throw IOException("Cached optional engine failed SHA-256 verification. Download it again.")
        }
    }

    /** Only the release for this app's exact version; no unpinned latest/nightly packages. */
    private fun getReleaseAsset(): Asset {
        val version = BuildConfig.VERSION_NAME
        val connection = URL(RELEASES + version).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "MyCarAdblock/$version")
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("No matching GeckoView release for myCar $version")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText().take(1_000_001) }
            if (body.length > 1_000_000) throw IOException("Oversized release metadata")
            val release = JSONObject(body)
            if (release.optString("tag_name") != "v$version" || release.optBoolean("draft")) {
                throw IOException("Unexpected release identity")
            }
            val assets = release.optJSONArray("assets") ?: throw IOException("No release assets")
            val entry = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == ASSET }
                ?: throw IOException("Optional engine is not published for this version yet")
            val url = entry.getString("browser_download_url")
            val size = entry.getLong("size")
            val sha = entry.optString("digest").removePrefix("sha256:")
            if (!url.startsWith("https://github.com/JPLuker/MyCarAdblock/releases/download/") ||
                size !in 1..ZIP_MAX_BYTES || !sha.matches(Regex("[a-fA-F0-9]{64}"))) {
                throw IOException("Optional engine metadata is incomplete or untrusted")
            }
            return Asset(url, size, sha)
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadVerified(asset: Asset, into: File) {
        // Write to a temporary file first. A failed/cancelled download must never appear
        // to be a complete package in the next launch.
        val partial = File(into.parentFile, into.name + ".part")
        partial.delete()
        val connection = URL(asset.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("User-Agent", "MyCarAdblock/${BuildConfig.VERSION_NAME}")
        try {
            if (connection.responseCode !in 200..299) throw IOException("Server refused the download")
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            connection.inputStream.use { source ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled.get()) throw InterruptedException()
                        val count = source.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > ZIP_MAX_BYTES || written > asset.size) throw IOException("Oversized download")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        flow.value = Status.Downloading(written.toFloat() / asset.size)
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (written != asset.size || !actual.equals(asset.sha256, ignoreCase = true)) {
                throw IOException("Optional engine failed integrity verification")
            }
            if (cancelled.get()) throw InterruptedException()
            if (into.exists() && !into.delete()) throw IOException("Cannot replace cached engine")
            if (!partial.renameTo(into)) throw IOException("Cannot finalize optional engine")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun validateSplitArchive(zip: File) {
        if (!zip.isFile || zip.length() !in 1..ZIP_MAX_BYTES) throw IOException("Missing optional engine archive")
        ZipFile(zip).use { archive ->
            val entries = archive.entries().asSequence().toList()
            if (entries.size != APK_NAMES.size || entries.map { it.name }.toSet() != APK_NAMES ||
                entries.any { it.isDirectory || it.size !in 1..APK_MAX_BYTES }) {
                throw IOException("Unexpected optional engine APK set")
            }
        }
    }
}

/** System installer callbacks, not public downloads or untrusted broadcast messages. */
class CinemaGeckoInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CinemaGeckoInstaller.onInstallerResult(context, intent)
    }
}
