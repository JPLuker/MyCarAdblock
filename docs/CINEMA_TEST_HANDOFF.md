# Cinema test handoff — ARM64 Gecko native startup in Car Preview

**Branch:** `feature/cinema-adblock`  
**PR:** [#1](https://github.com/JPLuker/MyCarAdblock/pull/1) — draft; not ready to merge or release.

## Current state

The latest ARM64 device logs, captured using **myCar's in-app Car Preview** on the phone, show **three identical native crashes** on
November 12, 2026, with the same `libxul.so` program offset `0x3f66680`
and SIGSEGV/SEGV_MAPERR null dereference at `0x8`:

| Time | Ad blocking | Last operation | Result |
|---|---|---|---|
| 11:06:36 | ON | Extension listing requested | Native crash |
| 11:07:38 | OFF | Extension manager bypassed; website navigation | Native crash |
| 11:10:57 | OFF | Extension manager bypassed; website navigation | Native crash |

All three runs constructed GeckoRuntime, GeckoSession and GeckoView before the
Gecko thread died. **The explicit extension-controller call is not required
to trigger the crash.** A previously installed uBlock Origin extension can
still load automatically when Gecko starts, so the add-on itself is not fully
excluded. The root cause is unknown: it may involve Gecko version 159,
split APK integration, or another Gecko startup condition. The native crash
log improvement is confirmed working, including PC addresses and fault type.

Do **not** retest the same build simply to reproduce the crash again.

### Safeguard and controlled comparison

The startup guard now records *every* Gecko startup attempt, including blocking
OFF. It clears only after Gecko reports the first contentful paint, or when
the user explicitly selects Android WebView and then Firefox to retry. This
prevents unintentional crash loops, but it **does not fix the native engine**.
The revised guard is committed and still requires device validation.

The candidate branch `feature/cinema-adblock` remains on GeckoView
`159.0.20261102144711`. A separate
`experiment/gecko-158-startup` branch tests the last GeckoView 158 ARM64
stable release `158.0.20261021001945`. Its CI build and
Car Preview behavior on the ARM64 phone are not yet verified. If 158 behaves differently, that will
isolate a version regression versus an integration problem.

## One-time optional signing setup

Run on your **own trusted computer** with `gh`, `keytool`, `openssl` and `base64` installed,
and with `gh auth login` completed:

```bash
git clone https://github.com/JPLuker/MyCarAdblock.git && cd MyCarAdblock && bash tools/setup-signing.sh
```

The script is already on `main` and stores one private keystore and password outside the
repository. It uploads five encrypted Actions secrets, including the public certificate digest,
so every subsequent debug build and matching Gecko split can use the **same** signer. Keep the
keystore and password backed up privately. Do **not** paste either in a GitHub issue or chat.

If an older myCar Debug APK was signed with a temporary CI key, it cannot be upgraded in place;
uninstall **myCar Debug** once (this erases that debug app's data), then use the new build. Do
not uninstall the separate production myCar app.

## Next test — corrected main branch (Car Preview, one attempt)

**Test `feature/cinema-adblock` first.** The independent review found strong
source-level support for the `-greomni` base-APK versus feature-APK mismatch.
The direct correction is present on this branch; the 158 version comparison is
now a **fallback investigation**, not the immediate test.

1. In [GitHub Actions > Build](https://github.com/JPLuker/MyCarAdblock/actions/workflows/build.yml)
   choose a **successful** run on `feature/cinema-adblock` whose commit includes
   `df0ab4f` (the corrected context and omnijar lookup). The latest source
   HEAD at review time was `92c6a3d`. The most recent build result is **not
   independently verified** by this handoff; source pushes alone are not
   installable APKs.
2. Download **both** `myCar-debug.apk` and
   `myCar-gecko-arm64-v8a.zip` from the **same** run.
   Never mix APK and optional split versions or signers.
3. Open myCar Debug on the phone. Import the matching Gecko ZIP in
   **Settings > Cinema** and restart if prompted. Installing over a
   previously used Debug app can preserve an old Gecko profile; for a
   fully clean profile, uninstall **only myCar Debug** first.
   **Warning: uninstalling erases all its saved Debug data and sign-ins.**
   Do not uninstall the separate production app.
4. In myCar's **in-app Car Preview**, select Firefox GeckoView and turn
   Cinema ad blocking **OFF**. Open a simple website **once**.
5. Inspect the new myCar log for both
   `packaged GeckoView=159.0.20261102144711` and
   `gecko: native omnijar source=split_cinema_gecko.apk`,
   then look for `first contentful paint` and an actually rendered
   website. If it still crashes, do **not** retry the same build.

Only if this unblocked page renders should signed uBlock Origin startup
be tested separately. If the **same** native crash persists after the
corrected omnijar path is logged, inspect the actual Gecko application
context, symbolicate the four `libxul.so` program offsets with the
matching ELF build ID, and examine child-process path propagation.
Consider the Gecko 158 control branch **only after** this is resolved.

## Report only these observations for this milestone

- Did the app launch?
- Did the matching Gecko ZIP install?
- Did the Firefox site open with blocking OFF?
- Did it open with blocking ON? If it crashed, did the next attempt avoid another crash?

Share the relevant **myCar Debug log after restarting the app**, which should include
the native signal and stack frames appended to the crashed session when Android provides
a tombstone. Do not share account tokens, passwords or cookies. A screenshot of a failure
screen is useful but not required.

## Release gates still open

The underlying native fault must be fixed, not merely guarded against. Verify that ON
actually runs the genuine Mozilla-signed uBlock Origin and that OFF actually disables
filtering after an extension is installed. After that: test YouTube navigation, seek position
on app switching, fullscreen exit, Maps/Cinema side-by-side, parked-only cover, car keyboard,
release-signed split installation and upgrades on physical ARM64 hardware.

**Testing boundary:** Car Preview on a real ARM64 phone exercises Gecko's native
runtime, Cinema UI and rendering, so it is fully adequate to reproduce and
investigate this startup SIGSEGV. It does **not** prove car head-unit
projection, real steering wheel/rotary controls, vehicle speed/parked gating,
or vehicle audio routing. Reserve those tests for later, after browser startup
and playback are stable in preview.

**No Gecko startup/playback checks have passed in Car Preview yet; later
head-unit integration checks are also outstanding.**

## Install permission button (November 12)

When Android's **Allow from this source** permission is granted outside the app,
Cinema Settings now refreshes its permission-dependent button state on Activity
resume. The button should switch from **Allow myCar to install** to **Install**
without reopening the Cinema settings screen. The initial download refresh remains
in place. This UI change is committed but not yet verified on device.


## November 12, 11:37–11:39 Car Preview logs

Five additional logs were uploaded. Observations:
- `11:37:51`: process launched; certificate download/TLS self-tests passed;
  there is no Cinema/Gecko startup in this log.
- `11:38:25`: Android reports the prior process ended because its package was
  **updated**, not because it crashed.
- `11:38:36`: the car preview starts Gecko and calls the extension list, then
  `SIGSEGV` at 11:38:51. First native PC is **exactly** the previously observed
  `libxul.so+0x3f66680`; the app restarts at 11:38:53.
- `11:38:53`: the app restores car preview; `cinema: opening youtube.com`
  at 11:39:13; the preview's surface is destroyed at 11:39:20.
  **No new native crash is logged in that session.**
- `11:39:23`: a new session starts Gecko at 11:39:35 with blocking OFF,
  bypasses the extension API and requests the page. Its captured log ends
  immediately afterward, **before success or another crash is established**.

**Do not claim GeckoView 158 passed or failed from these logs.** They do not
record the installed GeckoView dependency version. The same libxul offset
suggests the previous native binary could still be present, but it is not
proof of package identity.

**New diagnostic:** both branch builds now place their single pinned Gecko
dependency version in the feature's generated BuildConfig and write a
`gecko: starting optional runtime; packaged GeckoView=...` line before
initializing the engine. The next phone log can distinguish source 158
from 159. CI must successfully build this change before the marker is available.


## November 12, 11:58 confirmed GeckoView 159

An additional Car Preview log `mycar-log-20261112-115710.txt` explicitly
records at 11:58:38.737:

```text
gecko: starting optional runtime; packaged GeckoView=159.0.20261102144711
```

The runtime, session and view attach successfully. Ad blocking is ON.
`WebExtensionController.list()` is requested at 11:58:38.772.
At 11:58:39 the process again terminates with native
`SIGSEGV (SEGV_MAPERR)`, first frame `libxul.so+0x3f66680`.
This **confirms** both the build-version marker works and that the
installed package on this test remains **GeckoView 159**.

This log is *not* a 158 regression comparison. Do not retest 159 simply
to reconfirm the same fault. Next useful evidence is a successful 158
build bearing `packaged GeckoView=158.0.20261021001945`, installed with
its matching split, ideally against a clean Debug app profile. The
source artifact version was checked against the Mozilla Maven
distribution; an installable Actions artifact and successful runtime
remain unverified.

No successful Firefox page paint is recorded in the latest log.


## November 12, 12:11:49 – confirmed repeat and Gecko resource-path correction

New log `mycar-log-20261112-121138.txt` **explicitly** shows
`packaged GeckoView=159.0.20261102144711`, ad blocking OFF,
native `SIGSEGV` at 12:11:49, with the same
`libxul.so+0x3f66680` first frame. No new Gecko 159 device
reproduction was needed to diagnose this recurring fault.

### Source-level cause to test (NOT yet confirmed)

Mozilla's `GeckoThread.getMainProcessArgs()` explicitly constructs
`-greomni <context.getPackageResourcePath()>` (upstream source:
`mozilla/gecko-dev/mobile/android/geckoview/.../GeckoThread.java`).
Android's ordinary application context returns the **base APK**
path. Our `cinema_gecko` dynamic feature packs
`assets/omni.ja` inside the **optional feature master split**,
not the base. This means Gecko can load `libxul.so` from
the arm64 split but be directed to the wrong APK for its startup
omnijar. That is a concrete source-level mismatch plausibly
causing the repeatable early native fault.

The candidate branch now passes a narrow application ContextWrapper
to `GeckoRuntime.create()` that overrides
`getPackageResourcePath()` to the Android-installed **feature master
split containing `assets/omni.ja`**; all other methods remain
delegated. If no matching split with that asset exists, startup fails
as a normal Java/Kotlin exception and presents the existing WebView
fallback instead of risking another unqualified native launch.
The log line `gecko: native omnijar source=...` confirms which
split the runtime receives.

**This fix is committed, NOT build-verified and NOT device-verified.**
It is a meaningful new native-startup packaging change, not simply
another diagnostic or uBlock retry.

### Next useful Car Preview test

1. After a **successful** `feature/cinema-adblock` Actions run,
   install the base debug APK and Gecko ZIP from **that one run**.
   Avoid reinstalling the known-crashing previous build.
2. In Car Preview, enable GeckoView and set blocking OFF; open
   a simple site once. The new log should show both
   `packaged GeckoView=159...` and
   `native omnijar source=split_cinema_gecko.apk`.
3. Look for `first contentful paint` and actual page rendering.
   If another native SIGSEGV occurs, stop there and share that log.
4. Only after a successful unblocked page render, enable blocking
   and test uBlock startup. Preserve the existing recovery guard.

If this 159 packaging fix does not work, compare the isolated 158
experiment and investigate symbolicated Gecko native stack frames.

## Independent root-cause review (November 12)

A second independent source review strengthened the missing-omnijar
hypothesis and examined Gecko's native ZIP handling. Confirmed from
upstream Gecko sources (not yet checked against the exact Firefox 159 tag):

- Android's `OMNIJAR_NAME` is `assets/omni.ja`.
- `GeckoThread.getMainProcessArgs()` supplies `-greomni` using
  `GeckoAppShell.getApplicationContext().getPackageResourcePath()`.
- `Omnijar::InitOne()` opens that APK ZIP and tries to unwrap
  `assets/omni.ja`. On a valid ZIP *without* that entry it may keep
  the outer ZIP and report success rather than issuing a clear error.
- `GeckoRuntime.create(context)` calls `attachTo(context)`, which
  sets `GeckoAppShell`'s application context from
  `context.getApplicationContext()`. The committed `ContextWrapper`
  deliberately returns itself from that method so Gecko sees the
  overridden feature-master APK path.

These facts make the base-versus-feature resource-path mismatch the
**leading hypothesis**, not a proven resolution. Gecko internal APIs
may change between versions, and child-process omnijar propagation
is still unverified. Recheck the exact 159 upstream source and
native symbols if the corrected version does not render.

Original review references:
- https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/geckoview/src/main/java/org/mozilla/gecko/GeckoThread.java
- https://github.com/mozilla-firefox/firefox/blob/main/mobile/android/geckoview/src/main/java/org/mozilla/geckoview/GeckoRuntime.java
- https://github.com/mozilla-firefox/firefox/blob/main/xpcom/build/Omnijar.cpp


## First Firefox render and split-button UI (November 12)

**Observed in the user's Car Preview screenshot at approximately 12:57 PM:**
YouTube's home/search/navigation UI is visibly rendered inside Cinema. The user
explicitly confirmed that **Firefox GeckoView**, not Android WebView, was
selected. This is our first confirmed successful *visual* page render with
Firefox after the optional-feature `omni.ja` resource-path correction.
It is strong evidence for the corrected resource-path hypothesis, but the
success-session log containing `native omnijar source=` and
`first contentful paint` has not yet been supplied.

**Important testing distinction:** A subsequent phone Settings screenshot
shows **Block ads and trackers ON** and **Firefox GeckoView selected**.
It does not prove that the toggle was ON during the earlier successful
page render, nor does it prove that the signed uBlock Origin extension
installed, activated, or actually filtered a request.

**New UI change:** commit `0bf8bf4` replaces the long
`Map + Cinema` / `Full width` text in the Cinema title bar with a
compact two-panel vector icon. It is interactive and uses the existing
translated labels for accessibility, highlights the active split state,
and is shared by both WebView and GeckoView. The UI commit needs CI
verification and a visual test.

**Next efficient device-test sequence (in Car Preview):**
1. On a newly successful build, confirm the two-panel icon toggles
   Maps/Cinema split view on and off; confirm it remains touch-friendly.
2. With Firefox selected, play one YouTube video and confirm picture
   and sound. Test entering/leaving video fullscreen.
3. While a video is paused or playing, switch to Maps or another screen
   and return. Check for page refresh, playback reset or timestamp loss.
4. Check the log for `packaged GeckoView=159...`,
   `native omnijar source=...`, `first contentful paint`, and
   `gecko: add-on ready` or extension-install failures.
5. Verify actual uBlock Origin installation and filtering **separately**;
   the Settings toggle alone is insufficient evidence.
6. Continue with real-head-unit and release-signing tests only after
   preview behavior is stable.

**Note on the most recently supplied log**
(`mycar-log-20261112-125544.txt`): the 12:55 file contains startup,
certificate/TLS self-tests and weather data only. It ends at
12:56:00, with no `CINEMA`, `gecko:`, or playback events. It cannot
corroborate or refute the later YouTube screenshot.


## November 12 add-on implementation after first successful Firefox render

Substantial source changes have been committed **after** the screenshot showing
YouTube rendering in Firefox GeckoView. These changes have **not** passed
independent CI or phone verification yet:

- Changing ad blocking no longer recreates the Gecko browser/session. The
  real, persistent uBlock Origin is enabled/disabled via GeckoView before
  starting the initial page, and toggled without automatic navigation later.
- Optional SponsorBlock (official Firefox add-on ID
  `sponsorBlocker@ajay.app`, default OFF) has its own setting and daily
  update check. Its install failure while enabling does not block browsing;
  failed disabling is treated as an opt-out failure and prevents further
  browsing under an unknown extension state.
- Signed `.xpi` file import from Android's document picker, Mozilla
  signature verification by GeckoView, explicit per-extension permission
  review, extension list/enable/disable/uninstall, bounded private cache,
  timeout cancellation and reserved curated IDs.
- The shared Gecko permission delegate prevents the two official add-ons
  and user-imported extensions from overwriting each other's prompts.
- Additional runtime/session cleanup guards and Kotlin unit tests; source
  parity of all new strings in the eight translated locales was checked,
  but an actual Android/Gradle build was not available here.

**See `docs/CINEMA_EXTENSIONS.md` for the complete security model,
supported functionality, limits, and one-pass device test sequence.**

Do not interpret the earlier successful Firefox screenshot as proof that
this subsequent add-on batch builds, that SponsorBlock skips segments,
that a user-signed XPI imports, or that uBlock's OFF state is correct.
All of those are **pending**.


## November 12, 15:08–15:09 — fullscreen, split and dock acceptance

**User-supplied Car Preview screenshots** establish that Firefox's YouTube
video is playing (visible frames and subtitles), browser fullscreen displays
a video-only surface with an escape button, and the existing Map + Cinema
mode shows live navigation and the website side by side. The user identified
two specific remaining defects:

- On tapping the **Maps dock icon**, Cinema's return shortcut disappears.
  Root cause in source: Cinema is a built-in `CarScreen.App("cinema")`,
  not an external `RunningSlot`; dock app slots only included media,
  running apps, and pinned apps. On 800x480 there may only be two free
  middle dock slots.
- While a video was fullscreen, `CarUi` forcibly disabled the map/Cinema
  split using `navigator.cinemaSplit && !navigator.cinemaVideoFullscreen`.
  A fullscreen video therefore always expanded over the map instead of
  remaining inside the Cinema pane, and the normal split control was
  hidden with the site toolbar.

**Fixes committed to `feature/cinema-adblock` (build/device unverified):**

1. `CarNavigator` remembers whether Cinema has been opened during this
   car connection. When returning to Maps, a dedicated Cinema dock slot
   appears immediately after Map and before media/pinned shortcuts.
   Tapping it calls `navigator.open("cinema")`, returning to the
   existing composed browser session rather than loading a new page.
   Off-screen turn instructions retain first dock priority.
2. `CarUi` honors `navigator.cinemaSplit` independently of video fullscreen.
   In split mode, fullscreen video remains bounded to the Cinema 56%
   pane, with Maps occupying the left 44%. In single-pane mode, fullscreen
   still uses the available screen beside the persistent left dock.
3. `CinemaExitFullscreen` now also shows a two-pane icon, so the user
   can toggle Map + Cinema **while already fullscreen**. Both GeckoView
   and Android WebView share this overlay.
4. Unit tests in `CarNavigatorCinemaTest` cover retaining the Cinema
   shortcut after Home/Map navigation and preserving the split
   preference across fullscreen transitions.

**Car Preview / parked-car test after successful new build:**

1. Open Firefox/YouTube and a video. Enable Map + Cinema, then press
   YouTube fullscreen. The map must remain visible, with the video
   filling the Cinema pane only. The car's dock should stay visible.
2. While still fullscreen, use the new split icon to switch between
   single-pane and split; the video must continue without restarting,
   and exiting fullscreen must return to the YouTube page.
3. Tap the **Map dock icon** to view Map alone. A dedicated Cinema
   icon must now be visible in the dock. Tap it to return to the
   **same** video and check playback position. No new webpage load.
4. Repeat once with Android WebView to verify the same split/fullscreen
   controls. Check at 800x480 and the actual head-unit viewport.
5. Check parked-only safety: with the setting enabled, video
   content must be concealed whenever the car is moving. Verify only
   when parked or via a safe simulation, never by handling video
   while driving.

**Not yet established:** actual head-unit behavior, uninterrupted
audio, exact player seek position after navigation, extension effects,
and latest CI results. PR stays draft pending confirmation.


## November 12, 15:35 — fullscreen aspect regression and GeckoView workaround

**User screenshots (Car Preview 800x480):**
- Maps + Cinema with Firefox fullscreen leaves the actual image stuck near the
  top of the narrow Cinema pane, while captions are far below in black space.
  Both media and captions continue playing and the fullscreen escape/split
  controls are visible.
- Maps alone can be restored, indicating the earlier return-to-Maps behavior is
  functional; no successful seek-retention assertion was made here.
- Firefox fullscreen in the entire content area can show excess black space.
  Some black space is intrinsic to the video content; do not interpret every
  letterbox pixel as a layout bug.

**Upstream evidence:** Mozilla Bug 1750416
https://bugzilla.mozilla.org/show_bug.cgi?id=1750416 is currently NEW/open.
It documents wrong fullscreen video sizing whenever a GeckoView is smaller
than the host Android window. A reporter's workaround uses
`MediaSession.Delegate.onFullscreen(session, mediaSession, enabled, meta)`,
reads `meta.width`/`meta.height`, and resizes the rendering surface to
match the video aspect. GeckoView's `ContentDelegate.onFullScreen()` is
documented as ordinarily expecting an Activity-wide fullscreen window,
which this car-virtual-display app cannot use without disrupting the phone
and map dock.

**Implemented on `feature/cinema-adblock`, device and CI unverified:**

1. The optional Gecko feature forwards Gecko's
   `MediaSession.ElementMetadata` video aspect to the base car UI through
   an extended `CinemaBrowserFactory` callback, with a 16:9 fallback while
   metadata is pending. It never forwards the media source URL.
2. `CinemaFullscreenSizing.fit()` fits that aspect ratio to Cinema's actual
   available pane size in Compose dp, without cropping or stretching;
   the existing `AndroidView` hosting Firefox is centered in a black stage
   and remeasured, not recreated. For split mode that is the 56% Cinema pane;
   for single-pane mode it uses the entire available content area.
3. GeckoView now explicitly uses MATCH_PARENT layout params instead of
   relying on its default Android View layout behavior.
4. The fullscreen exit and split-view overlay remains anchored to the
   Cinema pane, outside the aspect-fitted video surface.
5. Pure JVM tests verify fitting landscape 16:9, vertical portrait, ultrawide
   and invalid metadata sizes; the tests have been committed but not run.

**Important:** This is a candidate workaround based on matching upstream
geometry symptoms, **not yet proven to fix the phone's Gecko 159 rendering**.
It does not change video decoding, GeckoView's site fullscreen protocol,
Android's car host, or parked-only masking.

**Focused parked-only follow-up after a passing build:**
- Open a YouTube video in single-pane Firefox and tap fullscreen; confirm
  its picture is centered and subtitles remain adjacent to the picture.
- Enable Maps + Cinema while still fullscreen; confirm the same video
  continues playing, remains centered inside the Cinema pane, with Maps
  unchanged on the left. Toggle split back without a new page load.
- Test a portrait video and non-YouTube site if practical, and confirm
  black bars are symmetrical rather than top-heavy.
- Exit fullscreen and return to Maps / Cinema via the dock; check for
  player time reset. Confirm audio and parked-only masking.
- If still badly cropped, collect one post-fix log and note whether
  `gecko: fullscreen video metadata available=true` appears. That tells
  us whether Gecko supplied a real media aspect and whether the native
  surface needs further correction.


## November 12, 15:49 — split fullscreen immediately exits

**Latest user-confirmed observation:** On the same 800×480 phone Car Preview,
YouTube fullscreen works and looks correctly sized with Cinema using the
whole content width. In **Map + Cinema split** mode, tapping YouTube fullscreen
causes a brief attempt and immediate return to the normal YouTube watch page.
The video is not simply letterboxed; the *document fullscreen state is lost*.
The screenshots do not show an actual runtime crash.

**Source-level candidate cause:** Our immediately preceding aspect-ratio
workaround changes the **physical GeckoView AndroidView dimensions** during
`ContentDelegate.onFullScreen(true)`. In a narrow Cinema pane, this shrinks
the live renderer from the whole pane to a 16:9 element size, firing a viewport
resize while Firefox/site fullscreen is in progress. Firefox/YouTube may
respond by exiting fullscreen (this sequence is a hypothesis until logs show
a Gecko `true → false` callback pair). This is distinct from Gecko's older
sub-window fullscreen sizing bug.

**Focused source fix committed (needs CI + Car Preview validation):**

- `CinemaFullscreenSizing.resizeGeckoSurface(fullscreen, split)` returns
  **false in split mode**, regardless of fullscreen state, so the Android
  GeckoView stays `Modifier.fillMaxSize()` within the Cinema pane across a
  fullscreen entry or exit. This prevents an *additional host-initiated
  resize* during the transition.
- The single-pane fullscreen branch retains its previously verified
  aspect-fitted renderer. Neither view/session nor the page is recreated.
- Gecko now logs `gecko: document fullscreen=true/false` for each actual
  content delegate event; this enables a decisive follow-up without guessing
  from a screenshot.
- JVM policy tests verify stable split-mode viewport sizing for both states
  and preserve the working single-pane path.

**Tradeoff/remaining problem:** Stabilizing Gecko's native fullscreen state
is **the immediate objective**. The earlier top-anchored picture/distant
captions issue may return while we keep GeckoView full height in split.
If fullscreen stays active but the picture is still poorly positioned,
the next fix must adjust *rendering/content geometry without remeasuring
the GeckoView during the fullscreen transition*. Do not claim a visual
fullscreen fix until a new Car Preview screenshot confirms it.

**Next parked-car test:** Start with Map + Cinema *already split*; enter
YouTube fullscreen once. Did the video remain in native fullscreen? Does the
existing split/exit overlay remain visible? If it immediately exits again,
return the new myCar log, specifically the sequence
`gecko: document fullscreen=true`, `false`, and
`gecko: fullscreen video metadata available=...`. Then try the
full-width path once as the control. Never test while driving.
