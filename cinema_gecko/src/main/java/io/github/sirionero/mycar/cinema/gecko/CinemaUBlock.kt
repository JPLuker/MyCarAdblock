package io.github.sirionero.mycar.cinema.gecko

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * The genuine, Mozilla-signed Firefox uBlock Origin add-on. No copied code, custom
 * lookalike, unsigned installBuiltIn() package, or third-party mirrors.
 */
internal object CinemaUBlock {
    private const val EXTENSION_ID = "uBlock0@raymondhill.net"
    private const val OFFICIAL_XPI = "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi"

    fun ensure(
        runtime: GeckoRuntime,
        enabled: Boolean,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val controller = runtime.webExtensionController
        controller.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                // A user explicitly selected GeckoView/uBO. Only accept the well-known official
                // add-on ID; Gecko itself verifies its Mozilla signature and package manifest.
                return GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(
                        enabled && extension.id == EXTENSION_ID,
                        false,
                        false,
                    ),
                )
            }
        })
        controller.list().accept({ extensions ->
            val existing = extensions.firstOrNull { it.id == EXTENSION_ID }
            if (existing != null) {
                val action = if (enabled) {
                    controller.enable(existing, WebExtensionController.EnableSource.USER)
                } else {
                    controller.disable(existing, WebExtensionController.EnableSource.USER)
                }
                action.accept({ onReady() }, { onError("uBlock Origin could not be toggled: ${it.message}") })
            } else if (!enabled) {
                onReady()
            } else {
                controller.install(OFFICIAL_XPI, WebExtensionController.INSTALLATION_METHOD_MANAGER)
                    .accept({ extension ->
                        if (extension.id == EXTENSION_ID) {
                            onReady()
                        } else {
                            controller.uninstall(extension)
                            onError("The downloaded add-on was not uBlock Origin.")
                        }
                    }, {
                        onError("Official uBlock Origin could not be installed: ${it.message}")
                    })
            }
        }, {
            onError("Firefox add-ons could not be checked: ${it.message}")
        })
    }
}
