# Cinema Firefox extensions — implementation and test plan

**Status:** Source implementation committed on `feature/cinema-adblock`; **CI and device validation are pending**. This is not a release approval.

## Scope

Only the optional Firefox GeckoView engine supports these add-ons. Android WebView
remains the safe, lightweight fallback and does not execute Firefox extensions.

### Curated options

- **uBlock Origin:** official Mozilla-signed XPI from
  `https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/latest.xpi`;
  ID `uBlock0@raymondhill.net`; controlled by the main Cinema ad-block toggle.
- **SponsorBlock:** official Mozilla-signed XPI from
  `https://addons.mozilla.org/firefox/downloads/latest/sponsorblock/latest.xpi`;
  ID `sponsorBlocker@ajay.app`; separately controlled in Settings and **OFF by default**.
  SponsorBlock skips known community-tagged sponsor segments in YouTube videos;
  it does not replace ad/tracker blocking. It accesses the SponsorBlock community
  service to determine which segments have been submitted.

Both are installed only when selected, verified by extension ID and enabled
metadata, and checked for updates at most daily. Existing installed extensions
are explicitly enabled/disabled when the relevant setting changes. Extension
operations are serialized within the running Cinema Gecko session. A setting
change no longer keys/destroys the session or forces the YouTube page to reload.

A uBlock failure blocks initial navigation and displays the existing WebView
recovery error rather than silently navigating unprotected. SponsorBlock is
optional: a failure is recorded in Settings and the log, but the page can still
open. If a newly enabled extension needs a fresh content-script injection,
the current page may require a manual reload.

### User-selected signed XPIs

Settings > Cinema > Firefox extensions provides:

1. A system document picker for a user-selected XPI.
2. A streamed copy to an app-private temporary file, limited to **25 MiB**.
3. ZIP/manifest sanity checks without extracting arbitrary package contents.
4. Rejection of declared IDs for the two curated add-ons, which must be managed
   through their dedicated settings.
5. `WebExtensionController.install(file://..., INSTALLATION_METHOD_MANAGER)`
   within the optional Gecko feature. **GeckoView performs Mozilla signature
   and manifest validation**. No unsigned `installBuiltIn()` path is used.
6. A shared Gecko-wide `PromptDelegate` that shows the extension ID and
   **complete** requested permissions, origins and data-collection permissions
   in an Android phone dialog. Large/malformed permission lists are denied
   instead of being truncated. No install consent is granted automatically
   to an unknown extension.
7. Imported extensions can be listed (manual refresh), enabled/disabled or
   uninstalled after confirmation. The curated IDs are excluded from this
   general manager. A timed-out import is cancelled and temporary files are
   deleted; abandoned cache files older than a day are cleaned on next import.

A signed add-on can still be malicious or excessively permissive. Users should
install only extensions they trust. This **is not a complete Firefox add-on
browser/toolbar**: extension popups, custom settings pages, and arbitrary UI
integration are not implemented and some add-ons may not work correctly in the
car interface.

### Process and safety boundaries

- Dynamic-feature installation/ABI/signing remains separate from XPI installation.
  The base APK does not include GeckoView or third-party XPI code.
- The Gecko runtime retains the feature-master `omni.ja` resource-path fix.
- Only signed XPIs are supported; Chrome CRX files and unsigned code are not.
- A single Gecko permission delegate handles curated add-on and user-import
  installations; otherwise one install operation could overwrite another
  install's approval handler.
- Importing an XPI must be triggered from the phone, not by arbitrary websites.
- An add-on's permissions to read/modify websites remain real capabilities.
- Source-level input and state-machine tests exist; this does not replace
  native-device and signing verification.

## Remaining validation — do not merge or release until passed

1. **Build:** confirm a successful `feature/cinema-adblock` Actions run with
   `:app:testDebugUnitTest`, translations, base smoke test, signed matching
   Gecko splits, and no missing feature classes. CI status has not been
   independently retrieved for the latest commits.
2. **Firefox baseline:** in phone Car Preview, open YouTube and confirm
   `first contentful paint`; play one video with picture and sound.
3. **uBlock OFF:** after installing uBlock with ON, switch OFF in Settings.
   Reopen or reload a page and verify blocking actually stops; confirm logs
   report disabled metadata. Then switch ON and verify the reverse.
4. **SponsorBlock:** enable with uBlock independently ON/OFF; verify official
   ID, successful install, skipping of a known crowd-tagged sponsor segment
   in mobile YouTube, and no blank screen if SponsorBlock fails. Verify OFF
   disables the installed extension and does not reload the browser session.
5. **XPI import:** use a harmless **Mozilla-signed** Firefox XPI chosen in
   Android's picker, review and approve its exact permissions, and verify
   it appears in the installed add-on list. Test cancel/deny, disable/enable,
   uninstall confirmation, and rejection of unsigned/invalid XPIs.
6. **Video lifecycle:** fullscreen enter/exit, Maps/Cinema split toggle,
   navigation away/back without playback seek reset, and car keyboard.
7. **Release:** same-run app/split signing, updates and rollback,
   installed feature reactivation, and real head-unit integration.

## Useful project source paths

- `cinema_gecko/.../CinemaUBlock.kt`
- `cinema_gecko/.../CinemaSponsorBlock.kt`
- `cinema_gecko/.../CinemaExtensionPrompts.kt`
- `cinema_gecko/.../CinemaSignedXpiInstallerImpl.kt`
- `cinema_gecko/.../GeckoBrowserFactory.kt`
- `app/.../cinema/CinemaSignedXpi.kt`
- `app/.../cinema/CinemaExtensionConsent.kt`
- `app/.../cinema/CinemaSponsorTransitionPolicy.kt`
- `app/.../cinema/CinemaAdBlockTransitionPolicy.kt`
- `app/.../ui/CinemaSettings.kt`
- `app/src/test/java/io/github/sirionero/mycar/cinema/CinemaExtensionPoliciesTest.kt`

Upstream documentation:
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html
- https://addons.mozilla.org/en-US/firefox/addon/sponsorblock/
- https://github.com/ajayyy/SponsorBlock/wiki/Android
