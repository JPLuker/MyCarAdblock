package io.github.sirionero.mycar.cinema.gecko

import io.github.sirionero.mycar.cinema.CinemaExtensionConsent
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * One Gecko-wide prompt delegate. GeckoView has a single controller prompt slot,
 * so uBO, SponsorBlock, and a user-imported XPI must never replace one another's
 * permission callbacks. Only known official extensions can install without an
 * additional dialog after the user explicitly enables their setting.
 */
internal object CinemaExtensionPrompts {
    private var controller: WebExtensionController? = null
    private val trustedPending = mutableSetOf<String>()
    private var userXpiPending = false

    fun ensureAttached(runtime: GeckoRuntime) = attach(runtime)

    fun allowOfficial(runtime: GeckoRuntime, id: String, enable: Boolean) {
        attach(runtime)
        if (enable) trustedPending.add(id) else trustedPending.remove(id)
    }

    fun beginUserXpi(runtime: GeckoRuntime): Boolean {
        attach(runtime)
        if (userXpiPending) return false
        userXpiPending = true
        return true
    }

    fun endUserXpi() {
        userXpiPending = false
        // Any outstanding question must be denied, never silently approved.
        CinemaExtensionConsent.decide(false)
    }

    private fun attach(runtime: GeckoRuntime) {
        val actual = runtime.webExtensionController
        if (controller === actual) return
        controller = actual
        actual.setPromptDelegate(object : WebExtensionController.PromptDelegate {
            override fun onInstallPromptRequest(
                extension: WebExtension,
                permissions: Array<String>,
                origins: Array<String>,
                dataCollectionPermissions: Array<String>,
            ): GeckoResult<WebExtension.PermissionPromptResponse> {
                if (trustedPending.contains(extension.id)) {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(true, false, false))
                }
                if (!userXpiPending) {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
                }
                val result = GeckoResult<WebExtension.PermissionPromptResponse>()
                val offered = CinemaExtensionConsent.offer(
                    extension.id, permissions, origins, dataCollectionPermissions,
                ) { approved ->
                    result.complete(WebExtension.PermissionPromptResponse(approved, false, false))
                }
                if (!offered) {
                    return GeckoResult.fromValue(WebExtension.PermissionPromptResponse(false, false, false))
                }
                return result
            }
        })
    }
}
