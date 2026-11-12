package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import android.net.Uri
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.cinema.CinemaSignedXpiInstaller
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
