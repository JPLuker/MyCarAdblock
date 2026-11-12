package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import android.net.Uri
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.cinema.CinemaSignedXpiInstaller
import io.github.sirionero.mycar.cinema.CinemaInstalledExtension
import io.github.sirionero.mycar.cinema.CinemaExtensionManagementPolicy
import java.io.File
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Optional-feature handler for user-selected, Mozilla-signed Firefox XPI files.
 * Never installBuiltIn() and never silently approve requested host permissions.
 */
class CinemaSignedXpiInstallerImpl : CinemaSignedXpiInstaller {
    private var installation: GeckoResult<WebExtension>? = null
    private var finished = false

    override fun install(
        context: Context,
        xpiFile: File,
        finished: (id: String?, error: String?) -> Unit,
    ) {
        check(!this.finished && installation == null) { "This XPI importer is already used." }
        val runtime = CinemaGeckoRuntime.get(context.applicationContext)
        check(CinemaExtensionPrompts.beginUserXpi(runtime)) {
            "Another signed XPI permission request is pending."
        }
        val uri = Uri.fromFile(xpiFile).toString()
        try {
            val request = runtime.webExtensionController.install(
                uri,
                org.mozilla.geckoview.WebExtensionController.INSTALLATION_METHOD_MANAGER,
            )
            installation = request
            request.accept({ extension ->
                done()
                if (extension == null || extension.id.isBlank()) {
                    finished(null, "Firefox did not confirm the signed extension.")
                } else {
                    MyCarLog.i("cinema: signed user XPI installed (id=${extension.id})", "CINEMA")
                    finished(extension.id, null)
                }
            }, { error ->
                done()
                finished(null, "Firefox rejected the extension: ${error?.message ?: "invalid signature or permissions"}")
            })
        } catch (failure: Exception) {
            done()
            finished(null, "Firefox extension installation failed: ${failure.message ?: "unknown error"}")
        }
    }

    override fun list(
        context: Context,
        finished: (List<CinemaInstalledExtension>?, String?) -> Unit,
    ) {
        try {
            val runtime = CinemaGeckoRuntime.get(context.applicationContext)
            CinemaExtensionPrompts.ensureAttached(runtime)
            runtime.webExtensionController.list().accept({ extensions ->
                val entries = extensions.orEmpty().filter { !it.isBuiltIn }.map { extension ->
                    CinemaInstalledExtension(
                        id = extension.id,
                        name = extension.metaData.name ?: extension.id,
                        version = extension.metaData.version,
                        enabled = extension.metaData.enabled,
                    )
                }
                finished(entries, null)
            }, { failure ->
                finished(null, "Could not list Firefox extensions: ${failure?.message ?: "unknown error"}")
            })
        } catch (failure: Exception) {
            finished(null, "Could not list Firefox extensions: ${failure.message ?: "unknown error"}")
        }
    }

    override fun setEnabled(
        context: Context,
        id: String,
        enabled: Boolean,
        finished: (String?) -> Unit,
    ) {
        if (!CinemaExtensionManagementPolicy.userManaged(id)) {
            finished("Use the dedicated Cinema setting for this extension.")
            return
        }
        try {
            val controller = CinemaGeckoRuntime.get(context.applicationContext).webExtensionController
            controller.list().accept({ extensions ->
                val extension = extensions?.firstOrNull { it.id == id && !it.isBuiltIn }
                if (extension == null) {
                    finished("Firefox extension not found.")
                } else if (extension.metaData.enabled == enabled) {
                    finished(null)
                } else {
                    val operation = if (enabled) {
                        controller.enable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                    } else {
                        controller.disable(extension, org.mozilla.geckoview.WebExtensionController.EnableSource.USER)
                    }
                    operation.accept({ result ->
                        finished(if (result != null && result.metaData.enabled == enabled) null
                            else "Firefox did not confirm this extension state.")
                    }, { error -> finished("Extension setting failed: ${error?.message ?: "unknown error"}") })
                }
            }, { failure -> finished("Could not inspect this extension: ${failure?.message ?: "unknown error"}") })
        } catch (failure: Exception) {
            finished("Extension setting failed: ${failure.message ?: "unknown error"}")
        }
    }

    override fun uninstall(
        context: Context,
        id: String,
        finished: (String?) -> Unit,
    ) {
        if (!CinemaExtensionManagementPolicy.userManaged(id)) {
            finished("Use the dedicated Cinema setting for this extension.")
            return
        }
        try {
            val controller = CinemaGeckoRuntime.get(context.applicationContext).webExtensionController
            controller.list().accept({ extensions ->
                val extension = extensions?.firstOrNull { it.id == id && !it.isBuiltIn }
                if (extension == null) {
                    finished("Firefox extension not found.")
                } else {
                    controller.uninstall(extension).accept(
                        { finished(null) },
                        { failure -> finished("Could not uninstall extension: ${failure?.message ?: "unknown error"}") },
                    )
                }
            }, { failure -> finished("Could not inspect extensions: ${failure?.message ?: "unknown error"}") })
        } catch (failure: Exception) {
            finished("Could not uninstall extension: ${failure.message ?: "unknown error"}")
        }
    }

    override fun cancel() {
        if (finished) return
        installation?.cancel()
        done()
    }

    private fun done() {
        if (finished) return
        finished = true
        installation = null
        CinemaExtensionPrompts.endUserXpi()
    }
}
