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
data class CinemaInstalledExtension(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
)

/** Built-in curated add-ons have dedicated settings; their state is not managed here. */
object CinemaExtensionManagementPolicy {
    private val curatedIds = setOf("uBlock0@raymondhill.net", "sponsorBlocker@ajay.app")
    fun userManaged(id: String): Boolean = id.isNotBlank() && id !in curatedIds
}

interface CinemaSignedXpiInstaller {
    fun install(context: Context, xpiFile: File, finished: (id: String?, error: String?) -> Unit)
    fun cancel()
    fun list(context: Context, finished: (List<CinemaInstalledExtension>?, String?) -> Unit)
    fun setEnabled(context: Context, id: String, enabled: Boolean, finished: (String?) -> Unit)
    fun uninstall(context: Context, id: String, finished: (String?) -> Unit)
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

    private val installedFlow = MutableStateFlow<List<CinemaInstalledExtension>>(emptyList())
    val installed: StateFlow<List<CinemaInstalledExtension>> = installedFlow.asStateFlow()
    private val managementMessageFlow = MutableStateFlow<String?>(null)
    val managementMessage: StateFlow<String?> = managementMessageFlow.asStateFlow()
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
                val temporary = File(folder, "${UUID.randomUUID()}.xpi")
                xpi = temporary
                val input = app.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open the selected document.")
                input.use { source ->
                    temporary.outputStream().use { out ->
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
                require(temporary.length() > 0L) { "The selected file is empty." }
                ZipFile(temporary).use { zip ->
                    val manifest = zip.getEntry("manifest.json")
                    require(manifest != null && !manifest.isDirectory &&
                            manifest.size in 1L..(1024L * 1024L)) {
                        "The selected file is not a valid Firefox XPI (manifest.json missing)."
                    }
                }
                val selectedFile = temporary
                main.post {
                    state.value = Status.Installing
                    var finished = false
                    var activeInstaller: CinemaSignedXpiInstaller? = null
                    val timeout = Runnable {
                        if (!finished) {
                            finished = true
                            CinemaExtensionConsent.decide(false)
                            activeInstaller?.cancel()
                            complete(selectedFile, null, "Firefox extension installation timed out.")
                        }
                    }
                    main.postDelayed(timeout, INSTALL_TIMEOUT_MS)
                    try {
                        val cls = Class.forName(FACTORY, true, app.classLoader)
                        val installer = cls.getDeclaredConstructor().newInstance() as CinemaSignedXpiInstaller
                        activeInstaller = installer
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

    /** Refreshes user-installed extensions without conflating them with our curated toggles. */
    fun refresh(context: Context) {
        if (!CinemaGeckoBridge.installed(context)) return
        runInstaller(context) { installer, app ->
            installer.list(app) { extensions, error ->
                main.post {
                    if (error != null) managementMessageFlow.value = error
                    else {
                        installedFlow.value = extensions.orEmpty()
                            .filter { CinemaExtensionManagementPolicy.userManaged(it.id) }
                        managementMessageFlow.value = null
                    }
                }
            }
        }
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        if (!CinemaExtensionManagementPolicy.userManaged(id)) return
        runInstaller(context) { installer, app ->
            installer.setEnabled(app, id, enabled) { error ->
                main.post {
                    managementMessageFlow.value = error
                    refresh(app)
                }
            }
        }
    }

    fun uninstall(context: Context, id: String) {
        if (!CinemaExtensionManagementPolicy.userManaged(id)) return
        runInstaller(context) { installer, app ->
            installer.uninstall(app, id) { error ->
                main.post {
                    managementMessageFlow.value = error
                    refresh(app)
                }
            }
        }
    }

    private fun runInstaller(
        context: Context,
        action: (CinemaSignedXpiInstaller, Context) -> Unit,
    ) {
        val app = context.applicationContext
        try {
            val cls = Class.forName(FACTORY, true, app.classLoader)
            val installer = cls.getDeclaredConstructor().newInstance() as CinemaSignedXpiInstaller
            action(installer, app)
        } catch (failure: Exception) {
            MyCarLog.e("cinema: extension management could not start", failure, tag = "CINEMA")
            managementMessageFlow.value = "Firefox extension management is unavailable."
        }
    }

    private fun complete(file: File?, id: String?, error: String?) {
        file?.delete()
        state.value = if (error == null && !id.isNullOrBlank()) {
            Status.Installed(id)
        } else {
            Status.Failed(error ?: "Firefox did not confirm extension installation.")
        }
        inProgress.set(false)
        if (error == null && !id.isNullOrBlank()) {
            // Refresh from the Gecko profile rather than trusting the just-imported filename.
            // Signed XPI files are cached only until Gecko confirms installation.
            // Caller supplies no context here; Settings refreshes when it resumes.
        }
    }
}
