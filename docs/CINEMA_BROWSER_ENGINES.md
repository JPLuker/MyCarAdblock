# Cinema dual-browser delivery goals

**Fork lineage (verified from GitHub):**
[JPLuker/MyCarAdblock](https://github.com/JPLuker/MyCarAdblock) →
[SirioNero/MyCar](https://github.com/SirioNero/MyCar) →
[twinksoftware/Gearslip](https://github.com/twinksoftware/Gearslip).
An upstream contribution from this fork should go to **SirioNero/MyCar first**.
The F-Droid packaging/signing caveat should be documented for its maintainer;
it must not prevent testing or delivering the GitHub-release implementation.

## Agreed feature goals

1. Keep the **base GitHub APK small and fully working offline** using Android WebView.
2. Offer **two selectable Cinema browser engines**: Android WebView (default) and
   Firefox GeckoView (download only when chosen). Preserve the existing sites and car UI.
3. Use the **real, Mozilla-signed uBlock Origin** extension for GeckoView; never call
   a domain-only fallback 'uBlock Origin'.
4. Make installation explicitly opted-in, show progress, allow cancellation/retry,
   verify version, ABI, checksum and signing, then activate only after successful install.
5. Always leave a **working WebView fallback** and retain selected sites. Explain that
   WebView and GeckoView logins/cookies may be separate.
6. Ship base and optional split in the **same signed release**, not an unverified
   downloadable code archive. Keep GeckoView code/native libraries out of base.
7. Preserve fullscreen video, back, car keyboard, paused-while-driving safety,
   desktop layouts, Continue, local Jellyfin and native phone/IPTV video behavior.
8. Test install, rollback and upgrades across Android versions and record known
   incompatibilities; upstream F-Droid handling is a follow-on discussion.

## Implementation milestones

- [x] Document product goals and accurate fork/upstream lineage.
- [x] **M1:** Save engine choice, display both options in all nine supported languages,
      select installed engine only, and keep WebView fallback if the split is absent.
- [ ] **M2 (debug packaging proven, release still pending):** Optional arm64 `:cinema_gecko`
      feature with real GeckoView, bundletool-signed master/ABI APKs and the original base APK,
      CI native-library exclusion and compiled-manifest startup checks. The signed
      **debug** split ZIP has been produced by CI; signed **release** delivery, update
      compatibility and on-device split installation still require verification.
- [ ] **M3 (source and debug import committed; phone verification pending):**
      Exact-version release lookup, streamed/atomic SHA-256-checked download, re-hash before
      install, optional file import on debug builds, PackageInstaller split session,
      private callback, permission consent and error/retry UI. Test by choosing the matching
      feature ZIP from the same debug build in Settings > Cinema and installing it on Android.
- [ ] **M4 (implementation committed; unverified):** Optional GeckoView session, Cinema view
      selection, navigation, single-surface new links, fullscreen, input connection for car keyboard,
      car audio pause, official Mozilla-signed uBlock Origin XPI installation and daily update
      checks. **Needs API compile and real video/site tests.**
- [ ] **M5:** Base-only launch/onboarding and Android emulator startup are checked.
      Still validate WebView/Gecko parity, signed feature installation, APK upgrades,
      crashes, video playback and car hardware; submit to SirioNero/MyCar with
      the F-Droid signing caveat.

---

**Status (November 11, 2026):** The **base-only debug APK launches and completes
onboarding on a real phone**, confirmed by Joseph after the manifest crash fix. An Android
34 emulator smoke test also installed, launched and reopened that base APK successfully.
The next test branch has compiled a matching **debug-only signed GeckoView feature ZIP**
containing the master module and ARM64 engine, and the outer ZIP is now compressed to
avoid more than 100 MB of unnecessary download bytes. Debug-only Settings can import
the ZIP from the *same Actions build as the debug APK*. This test installation has
NOT yet been performed on a real device; full Firefox session/uBlock Origin behavior,
screen projection, release-signed downloads, and upgrade/rollback behavior remain unverified.
DO NOT MERGE OR RELEASE until those tests pass.
**Scope:** Cinema websites only. Preserve myCar's existing WebViews (music, dashboard, etc.)
and the native media/IPTV player.

## Product behavior

- **Android WebView (default):** shipped in the base myCar APK. Starts immediately, offline,
  without any additional download. The lightweight domain-blocking implementation from PR #1
  is a separate, explicitly labeled feature, *not* uBlock Origin.
- **Firefox GeckoView + official uBlock Origin (optional):** offered under
  Settings > Cinema > Browser engine. Show **Download Firefox engine** and its estimated
  download/storage size when it is not installed. Do not mark this as usable until both
  the engine and official extension are ready.
- Offer a global Cinema engine selector. Switching is reversible and never uninstalls the
  built-in engine. If GeckoView fails, show an explicit **Use Android WebView** recovery
  action and preserve the chosen site; do not silently downgrade without informing the user.
- The Cinema screen's site tiles, URL/navigation bar, car keyboard, paste/copy, desktop mode,
  fullscreen videos, fullscreen back handling, loading/retry, "Continue" state, and
  parked-only safety cover must work in **both** engines. Keep playback position, history,
  bookmarks, and login/session constraints explicit. Do not promise cookies are shared
  between the two different engines.
- WebView blocking and GeckoView/uBO are separate mechanisms. A Cinema setting must describe
  which one applies; never label the lightweight blocklist "uBlock Origin".

## Delivery design (GitHub releases, no Play Store required)

**Preferred proof of concept:** a separate *on-demand Android feature split APK* built
against the same app base release, not dynamically loading an unverified .dex/.so/.aar.

1. Keep all `org.mozilla.geckoview` dependencies, native libraries, manifests and Firefox
   session implementation **out of :app base** and in an optional module.
2. Build and publish a matching split asset for each release/ABI with explicit version,
   SHA-256 and size, alongside the usual independent `myCar-release.apk` base.
   A third-party tester must be able to install and launch the base APK by itself.
   **Verify Gradle dynamic-feature outputs** before committing to this architecture: the
   existing workflow currently produces a monolithic APK and moves its output with
   `renameApk`. Never ship an APK that unexpectedly includes GeckoView.
3. When requested, download the matching signed split from the project's GitHub release,
   using streamed IO, HTTPS, a size bound, progress/cancel, retry and a manifest containing
   the required base versionCode, ABI, SHA-256 and expected signing identity.
   Do not construct unpinned download URLs or accept a split for another app version.
4. Verify the checksum **and** package identity, exact versionCode, ABI and expected signing
   certificate. Android's package installer enforces same package/version/signer for splits.
   Use `PackageInstaller.SessionParams.MODE_INHERIT_EXISTING` and handle the platform's
   user-confirmation flow and installation result. Do not require root/ADB or Google Play.
5. After Android reports success, confirm the split is present and usable. It may need a
   process restart/reconnection to expose classes/native libraries reliably. Only then
   offer/activate GeckoView. Preserve WebView choice on denial, failure and cancellation.
6. Pin and update GeckoView with each compatible **app release**. For existing split users,
   app upgrades must re-install a matched split as needed; old, incompatible splits are not
   reused. Test updates and rollback, not just first install.
7. Install the **genuine Mozilla-signed uBlock Origin Firefox .xpi** through
   `GeckoRuntime.webExtensionController.install(httpsXpiUrl)`, after GeckoView is available.
   Handle extension permissions and failures. Let uBO manage its own filters; do not rebrand
   custom domain filtering as full uBO. Verify source, add-on identity, license and ongoing
   security updates. Re-enabling GeckoView should not unnecessarily reinstall the extension.
8. Handle data storage, sandbox boundaries and WebView/GeckoView rendering within a
   projected screen, including crash recovery and no-video-while-driving protection.

**Platform limitation:** Google Play's SplitInstallManager isn't an offline/non-Play
download service. Use the Android platform `PackageInstaller` path for GitHub releases.

**Distribution caveat:** F-Droid generally re-signs builds with its own key. An optional
GitHub-signed split cannot be appended to an F-Droid-signed base. Before claiming F-Droid
support, validate a compatible same-signer split distribution or evaluate a separate
companion APK with an IPC bridge. The companion route also needs a demonstrated embedded
surface/input mechanism, so do not present it as equivalent without a prototype.

## November 12: Cinema session retention, split map and Gecko crash investigation

**Reported on Android:** The ARM64 GeckoView optional feature installed, but opening the
browser crashed or exited to Android recents (a blank white page appeared in the preview).
This **has not been proven fixed**; a past-session phone log or Android crash buffer is
needed to distinguish a Gecko native startup fault, missing split resource, browser
embedding/surface issue, or uBlock Origin callback. The source now logs Gecko runtime
startup stages and the full synchronous failure chain, reuses a process-lifetime GeckoRuntime,
and ignores extension callbacks after a browser session closes. A newly installed split
is withheld from the selector until the app process has been restarted. Browser startup
remains a device-test blocker for PR merge.

**New user-requested behavior:**
- [x] **Keep Cinema alive while changing car screens.** Preserve the same Android WebView
      or GeckoSession rather than disposing/recreating on Home, map, or Apps; pause
      media on hide and resume rendering when visible. YouTube's existing seek position
      should survive these transitions within the same car UI process. Explicitly closing
      the website, disconnecting and destroying the car UI, Android killing the process,
      and changes requiring site reload are outside this guarantee.
- [x] **Map + Cinema** toggle from the site header. Show the current interactive navigation
      map in the left ~44% and Cinema in the right ~56%; switch back to full width in-place
      without navigating or creating a fresh browser. Keep only the existing single map
      surface/connection, not a second independent map instance. Existing parked-only cover
      continues to apply to the video area.
- [x] **Fullscreen behavior:** YouTube/fullscreen video temporarily expands the Cinema
      pane to full available width while leaving the car's navigation dock independent.
      Explicit 'Exit fullscreen' control is visible; the prior split choice is restored on exit.
- [ ] **Still unverified on hardware:** Gecko startup, browser persistence under actual
      car projection, map tapping/route controls in split mode, fullscreen video overlay,
      media pause/resume and seek preservation on YouTube, especially with Firefox.
- [ ] **Test plan:** open a YouTube video, seek into it, switch to Map and Apps repeatedly,
      return without reload and check playback is still at the same timestamp. Switch
      Map + Cinema on/off while the map runs. Enter/exit YouTube fullscreen and inspect the
      navigation dock. With parked-only enabled, ensure video imagery is obscured when
      movement is detected, including split mode. Repeat on Firefox only after it launches.

## Acceptance criteria

- [ ] A fresh installation's base APK contains **no GeckoView classes or native engine**.
- [ ] Cinema works using WebView with airplane mode and no optional download.
- [ ] Settings lists both engines, identifies availability and size, and has a reversible
      selection with honest error/retry states.
- [ ] Download is user-initiated; cancellation leaves base browser fully functional.
- [ ] Signed split verification, matching release and Android install/restart pass on
      Android 12+ (including Android 17).
- [ ] Real uBlock Origin installs and actually filters advertising/cosmetic elements.
- [ ] First-party video sites, Jellyfin sign-in, car keyboard, fullscreen and "Continue"
      work with both engines; differences in browser cookies are explained.
- [ ] Upgrades, offline mode, corruption, revoked install permission, missing engine,
      mismatched signature and crashes all recover to a functional WebView.
- [ ] Release workflow publishes independent base and optional feature artifacts,
      with checksums and automated split-install tests.
- [ ] F-Droid/GitHub signature constraints are resolved or documented as a support limit.

## Sources

- Android `PackageInstaller` split requirements:
  https://developer.android.com/reference/android/content/pm/PackageInstaller
- Android `MODE_INHERIT_EXISTING`:
  https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams
- GeckoView integration:
  https://mozilla.github.io/geckoview/consumer/docs/geckoview-quick-start
- GeckoView signed extension installation:
  https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html
- Official uBlock Origin Android add-on:
  https://addons.mozilla.org/en-US/android/addon/ublock-origin/
