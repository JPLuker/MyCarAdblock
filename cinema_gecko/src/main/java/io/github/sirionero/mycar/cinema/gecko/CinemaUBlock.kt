package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import io.github.sirionero.mycar.MyCarLog
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
    private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val KEY_LAST_UPDATE = "ubo_update_checked"

    fun ensure(
        context: Context,
        runtime: GeckoRuntime,
        enabled: Boolean,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        MyCarLog.i("gecko: entering uBO initialization (enabled=$enabled)", "CINEMA")
        val controller = runtime.webExtensionController
        MyCarLog.i("gecko: web extension controller obtained", "CINEMA")
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
        MyCarLog.i("gecko: extension prompt delegate installed; listing extensions", "CINEMA")
        controller.list().accept({ extensions ->
            MyCarLog.i("gecko: extension list returned", "CINEMA")
            val existing = extensions?.firstOrNull { it.id == EXTENSION_ID }
            if (existing != null && existing.metaData.enabled == enabled) {
                MyCarLog.i("gecko: signed uBlock Origin already ${if (enabled) "enabled" else "disabled"}", "CINEMA")
                if (enabled) checkForUpdate(context, controller, existing)
                onReady()
            } else if (existing != null) {
                MyCarLog.i("gecko: ${if (enabled) "enabling" else "disabling"} persisted signed uBlock Origin", "CINEMA")
                val action = if (enabled) {
                    controller.enable(existing, WebExtensionController.EnableSource.USER)
                } else {
                    controller.disable(existing, WebExtensionController.EnableSource.USER)
                }
                action.accept({ toggled ->
                    if (toggled == null || toggled.metaData.enabled != enabled) {
                        onError("Firefox did not confirm the requested uBlock Origin state.")
                    } else {
                        MyCarLog.i("gecko: signed uBlock Origin ${if (enabled) "enabled" else "disabled"}", "CINEMA")
                        if (enabled) checkForUpdate(context, controller, toggled)
                        onReady()
                    }
                }, { failure -> onError("uBlock Origin could not be toggled: ${failure?.message ?: "unknown error"}") })
            } else if (!enabled) {
                MyCarLog.i("gecko: uBlock Origin not installed; blocking OFF confirmed", "CINEMA")
                onReady()
            } else {
                MyCarLog.i("gecko: requesting Mozilla-signed uBO XPI", "CINEMA")
                controller.install(OFFICIAL_XPI, WebExtensionController.INSTALLATION_METHOD_MANAGER)
                    .accept({ extension ->
                        if (extension?.id == EXTENSION_ID) {
                            if (extension.metaData.enabled) {
                                MyCarLog.i("gecko: signed uBlock Origin installed and enabled", "CINEMA")
                                onReady()
                            } else {
                                // Successful download alone is not sufficient: extension might
                                // be installed disabled. Enable it before loading any page.
                                controller.enable(extension, WebExtensionController.EnableSource.USER)
                                    .accept({ activated ->
                                        if (activated != null && activated.metaData.enabled) {
                                            MyCarLog.i("gecko: signed uBlock Origin activated after install", "CINEMA")
                                            onReady()
                                        } else {
                                            onError("uBlock Origin installed but could not be enabled.")
                                        }
                                    }, { failure ->
                                        onError("uBlock Origin was installed but enabling it failed: ${failure?.message ?: "unknown error"}")
                                    })
                            }
                        } else {
                            if (extension != null) controller.uninstall(extension)
                            onError("The downloaded add-on was not uBlock Origin.")
                        }
                    }, { failure ->
                        onError("Official uBlock Origin could not be installed: ${failure?.message ?: "unknown error"}")
                    })
            }
        }, { failure ->
            onError("Firefox add-ons could not be checked: ${failure?.message ?: "unknown error"}")
        })
    }
    /** GeckoView does not automatically keep external extensions current. Check at most daily. */
    private fun checkForUpdate(
        context: Context,
        controller: WebExtensionController,
        extension: WebExtension,
    ) {
        val prefs = context.applicationContext.getSharedPreferences("cinema_firefox", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val previous = prefs.getLong(KEY_LAST_UPDATE, 0)
        if (now - previous in 0 until UPDATE_INTERVAL_MS) return
        prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
        controller.update(extension).accept(
            { /* GeckoView preserves extension settings across updates. */ },
            { MyCarLog.w("cinema: uBlock update check failed (" + (it?.javaClass?.simpleName ?: "unknown error") + ")") },
        )
    }
}
