# Cinema test handoff — ARM64 Gecko native startup

**Branch:** `feature/cinema-adblock`  
**PR:** [#1](https://github.com/JPLuker/MyCarAdblock/pull/1) — draft; not ready to merge or release.

## Current state

The latest ARM64 device logs capture **three identical native crashes** on
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
stable release `158.0.20261021001945`. Its CI build and physical-device
behavior are not yet verified. If 158 behaves differently, that will
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

## Minimal parked-device test

1. Do not retest Gecko 159 on the existing build: ON and OFF have already
   failed identically and repeat crashes interrupt the car host.
2. For the controlled comparison, open
   [GitHub Actions > Build](https://github.com/JPLuker/MyCarAdblock/actions/workflows/build.yml)
   and select a **successful** run from `experiment/gecko-158-startup`.
   Download both `myCar-debug.apk` and `myCar-gecko-arm64-v8a.zip`
   from **that same run**. No successful run means no new test APK yet.
3. **For a meaningful clean A/B test**, avoid carrying Gecko's previous
   profile or a persisted uBlock Origin installation into the experiment.
   The recommended path is to uninstall **only myCar Debug**, then reinstall
   the experimental APK and matching Gecko ZIP in **Settings > Cinema**.
   **Uninstalling myCar Debug erases its saved sites, settings and sign-ins.**
   Back up anything important first. Do **not** uninstall production myCar.
   If you choose an install-over-install instead, disclose that the old Gecko
   profile may still be present, so the result will not isolate the native
   engine as cleanly. Do not mix 159 and 158 base/split versions.
4. While **parked**, choose Firefox, turn Cinema ad blocking OFF and open
   a site **once**. If it still crashes, the next attempt should display
   the guarded fallback. Switch to Android WebView instead of repeatedly
   triggering the crash.
5. Only if OFF renders correctly, turn ad blocking ON and open once.
   ON must initialize the signed uBlock extension successfully before
   navigation; further actual filtering tests remain separate.

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
