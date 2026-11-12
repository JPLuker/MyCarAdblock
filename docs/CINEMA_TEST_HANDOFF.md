# Cinema test handoff — ARM64 Gecko native startup

**Branch:** `feature/cinema-adblock`  
**PR:** [#1](https://github.com/JPLuker/MyCarAdblock/pull/1) — draft; not ready to merge or release.

## Current state

The base WebView still has a safe fallback. GeckoView runtime/session/view setup was observed on
a real ARM64 phone, followed within a second by a **native null-pointer process crash** near
signed-extension initialization (November 12, 2026). The new November 12 10:44:36 device log from build #145 confirms
the process reaches `WebExtensionController.list()` (last recorded operation at
10:44:51.534), then dies at 10:44:52 with `SIGSEGV (SEGV_MAPERR)`. Frames #0–3
are in Gecko's `libxul.so`, with `libmozglue.so` at frame #4. This is strong
evidence the problem lies in Gecko's native startup/extension-list path, but it
does not yet identify the failing native function. The earlier log did not have
native frames; the updated native-footnote diagnostic now works on the device.
A further commit adds PC offsets to future reports so the frames can be symbolicated.

The crash guard and extension timeout have **not** been confirmed as a successful
fix on a physical device. The native fault remains open.

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

## Minimal parked-device test

1. Open [GitHub Actions > Build](https://github.com/JPLuker/MyCarAdblock/actions/workflows/build.yml),
   select the newest **successful** run for `feature/cinema-adblock`, then download its
   `myCar-debug-<commit>` artifact. It must contain both
   `myCar-debug.apk` and `myCar-gecko-arm64-v8a.zip`. Do not mix runs or versions.
   If no run is successful, stop here; a source commit alone is not an installable build.
2. Install that APK on the phone. Go to phone **myCar Debug > Settings > Cinema**,
   import the matching Gecko ZIP, approve Android installation, and restart the app if
   requested. Leave WebView selected if the split import/install fails.
3. While **parked**, select Firefox GeckoView and turn **Cinema ad blocking OFF**.
   Open a Cinema website and note whether the Gecko page renders without crashing.
   This diagnostic bypass skips add-on startup; it does **not** prove an already-installed
   uBO extension has stopped filtering.
4. Only if the unblocked-startup diagnostic succeeds, turn Cinema ad blocking **ON** and
   reopen that website. If the app crashes, restart and **do not repeatedly retry** the
   crashing configuration. On the next attempt the checkpoint should display a recovery
   message and allow switching to Android WebView.

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

**None of these physical-device checks is marked passed by this handoff.**

## Install permission button (November 12)

When Android's **Allow from this source** permission is granted outside the app,
Cinema Settings now refreshes its permission-dependent button state on Activity
resume. The button should switch from **Allow myCar to install** to **Install**
without reopening the Cinema settings screen. The initial download refresh remains
in place. This UI change is committed but not yet verified on device.
