package io.github.sirionero.mycar.cinema.gecko

import android.content.Context
import io.github.sirionero.mycar.MyCarLog
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Optional Mozilla-signed SponsorBlock from AMO. It is NOT an ad blocker:
 * it skips community-identified sponsor segments in YouTube videos.
 *
 * Never bundle or re-sign an extension, and never claim an install succeeded until
 * GeckoView confirms the expected signed extension ID and requested enabled state.
 */
internal object CinemaSponsorBlock {
    private const val ID = "sponsorBlocker@ajay.app"
    private const val XPI = "https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/latest.xpi"
    private const val KEY_LAST_UPDATE = "sponsorblock_update_checked"
    private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000L

    fun ensure(
        context: Context,
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
            ): GeckoResult<WebExtension.PermissionPromptResponse> =
                GeckoResult.fromValue(
                    WebExtension.PermissionPromptResponse(
                        enabled && extension.id == ID,
                        false,
                        false,
                    ),
                )
        })
        controller.list().accept({ extensions ->
            val existing = extensions?.firstOrNull { it.id == ID }
            when {
                existing != null && existing.metaData.enabled == enabled -> {
                    MyCarLog.i("gecko: SponsorBlock already ${if (enabled) "enabled" else "disabled"}", "CINEMA")
                    if (enabled) checkForUpdate(context, controller, existing)
                    onReady()
                }
                existing != null -> {
                    val action = if (enabled) {
                        controller.enable(existing, WebExtensionController.EnableSource.USER)
                    } else {
                        controller.disable(existing, WebExtensionController.EnableSource.USER)
                    }
                    action.accept({ updated ->
                        if (updated != null && updated.metaData.enabled == enabled) {
                            MyCarLog.i("gecko: SponsorBlock ${if (enabled) "enabled" else "disabled"}", "CINEMA")
                            if (enabled) checkForUpdate(context, controller, updated)
                            onReady()
                        } else {
                            onError("Firefox did not confirm the requested SponsorBlock state.")
                        }
                    }, { failure ->
                        onError("SponsorBlock could not be toggled: ${failure?.message ?: "unknown error"}")
                    })
                }
                !enabled -> {
                    MyCarLog.i("gecko: SponsorBlock not installed; OFF confirmed", "CINEMA")
                    onReady()
                }
                else -> {
                    MyCarLog.i("gecko: requesting official Mozilla-signed SponsorBlock XPI", "CINEMA")
                    controller.install(XPI, WebExtensionController.INSTALLATION_METHOD_MANAGER)
                        .accept({ extension ->
                            if (extension?.id != ID) {
                                if (extension != null) controller.uninstall(extension)
                                onError("Downloaded extension ID does not match official SponsorBlock.")
                            } else if (extension.metaData.enabled) {
                                MyCarLog.i("gecko: signed SponsorBlock installed and enabled", "CINEMA")
                                onReady()
                            } else {
                                controller.enable(extension, WebExtensionController.EnableSource.USER)
                                    .accept({ updated ->
                                        if (updated != null && updated.metaData.enabled) {
                                            MyCarLog.i("gecko: signed SponsorBlock enabled after install", "CINEMA")
                                            onReady()
                                        } else {
                                            onError("SponsorBlock installed but could not be enabled.")
                                        }
                                    }, { failure ->
                                        onError("SponsorBlock enabling failed: ${failure?.message ?: "unknown error"}")
                                    })
                            }
                        }, { failure ->
                            onError("Official SponsorBlock could not be installed: ${failure?.message ?: "unknown error"}")
                        })
                }
            }
        }, { failure ->
            onError("Firefox could not inspect SponsorBlock: ${failure?.message ?: "unknown error"}")
        })
    }

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
            { /* Existing SponsorBlock settings are retained by GeckoView. */ },
            { MyCarLog.w("cinema: SponsorBlock update failed (" + (it?.message ?: "unknown error") + ")") },
        )
    }
}
