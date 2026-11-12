package io.github.sirionero.mycar.cinema

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.sirionero.mycar.MyCarLog
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional feature implementation, loaded only after Android confirms the Gecko split. */
interface CinemaSignedXpiInstaller {
    fun install(context: Context, xpiFile: File, finished: (id: String?, error: String?) -> Unit)
}

/**
 * User-directed Firefox .xpi sideload from Android's document picker. The file is copied
 * to our private cache without assuming the originating DocumentsProvider's path or trust.
 * GeckoView itself verifies the Mozilla signature and shows a permission review via
 * CinemaExtensionConsent; myCar does NOT permit unsigned installBuiltIn() packages.
 */
object CinemaSignedXpi {
    sealed interface Status {
        data object Idle : Status
        data object Reading : Status
        data object Installing : Status
        data class Installed(val id: String) : Status
        data class Failed(val reason: String) : Status
    }

    private const val MAX_BYTES = 25L * 1024L * 1024L
    private const val INSTALL_TIMEOUT_MS = 90_000L
    private const val FACTORY = "io.github.sirionero.mycar.cinema.gecko.CinemaSignedXpiInstallerImpl"

    private val state = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = state.asStateFlow()
    private val inProgress = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    fun importDocument(context: Context, uri: Uri) {
        if (!inProgress.compareAndSet(false, true)) return
        val app = context.applicationContext
        if (!CinemaGeckoBridge.installed(app)) {
            inProgress.set(false)
            state.value = Status.Failed("Install the Firefox engine before importing an extension.")
            return
        }
        state.value = Status.Reading
        Thread({
            var xpi: File? = null
            try {
                val folder = File(app.cacheDir, "cinema-signed-xpi")
                check(folder.isDirectory || folder.mkdirs()) { "Cannot create a private extension cache." }
                xpi = File(folder, "${UUID.randomUUID()}.xpi")
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open the selected document.")
                input.use { source ->
                    xpi.outputStream().use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val length = source.read(buffer)
                            if (length < 0) break
                            total += length
                            require(total <= MAX_BYTES) { "Extension exceeds the 25 MB import limit." }
                            out.write(buffer, 0, length)
                        }
                    }
                }
                require(xpi.length() > 0L) { "The selected file is empty." }
                ZipFile(xpi).use { zip ->
                    val manifest = zip.getEntry("manifest.json")
                    require(manifest != null && !manifest.isDirectory &&
                            manifest.size in 1..(1024 * 1024)) {
                        "The selected file is not a valid Firefox XPI (manifest.json missing)."
                    }
                }
                val selectedFile = xpi
                main.post {
                    state.value = Status.Installing
                    var finished = false
                    val timeout = Runnable {
                        if (!finished) {
                            finished = true
                            CinemaExtensionConsent.decide(false)
                            complete(selectedFile, null, "Firefox extension installation timed out.")
                        }
                    }
                    main.postDelayed(timeout, INSTALL_TIMEOUT_MS)
                    try {
                        val cls = Class.forName(FACTORY, true, app.classLoader)
                        val installer = cls.getDeclaredConstructor().newInstance() as CinemaSignedXpiInstaller
                        installer.install(app, selectedFile) { id, error ->
                            main.post {
                                if (!finished) {
                                    finished = true
                                    main.removeCallbacks(timeout)
                                    complete(selectedFile, id, error)
                                }
                            }
                        }
                    } catch (failure: Exception) {
                        if (!finished) {
                            finished = true
                            main.removeCallbacks(timeout)
                            MyCarLog.e("cinema: could not load signed XPI installer", failure, tag = "CINEMA")
                            complete(selectedFile, null, "Firefox's extension installer could not start.")
                        }
                    }
                }
            } catch (failure: Exception) {
                val disposable = xpi
                main.post {
                    MyCarLog.w("cinema: XPI import rejected (${failure.javaClass.simpleName})", "CINEMA")
                    complete(disposable, null, failure.message ?: "Firefox extension file could not be read.")
                }
            }
        }, "CinemaSignedXpiImport").start()
    }

    private fun complete(file: File?, id: String?, error: String?) {
        file?.delete()
        state.value = if (error == null && !id.isNullOrBlank()) {
            Status.Installed(id)
        } else {
            Status.Failed(error ?: "Firefox did not confirm extension installation.")
        }
        inProgress.set(false)
    }
}
