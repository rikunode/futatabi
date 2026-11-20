# futatabi

Morphe patch project targeting **futatter 3.1.1** (`com.Nanasaki.ftutter`, versionCode `65`) only.

## Status

**Scaffold and an offline ADB gate fixture only; no functional patch is published.** The exact target XAPK has not been downloaded and independently SHA-256-verified yet. The mandatory device gate has not run. Do not use this repository as a functional patch yet.

## Planned scope

- Fail-closed banner-ad suppression at the `google_mobile_ads` `loadBannerAd` handler; rewarded advertising remains untouched.
- Read-only local Futaba-compatible bridge with startup ContentProvider, catalog, thread, images, persistent virtual numeric IDs, Shift_JIS rendering, blocked POST and respectful cache/rate-limits.
- Aimoge official API adapter; Animanch parser; Mebuki placeholder disabled in v1.

## Offline ADB reverse gate fixture

Run `python tests/adb_reverse_fixture.py` on Windows. It binds only to `127.0.0.1:18765` and returns a fixed Shift_JIS catalog, fixed thread, and a self-contained PNG thumbnail; all POST requests return HTTP 403. With an Android device attached, use `adb reverse tcp:18765 tcp:18765` and try adding `http://127.0.0.1:18765/aimoge/` as a board in the unmodified futatter app. Verify board admission, listing, existing catalog, thread/image display, and persistence across app restart. These checks **have not yet passed**. No bridge implementation may proceed before all five are verified.

## Board registration (after the bridge is verified)

- Aimoge: `http://127.0.0.1:18765/aimoge/`
- Animanch: `http://127.0.0.1:18765/animanch/`

Futatter is distributed as split XAPK. The Morphe patch target is a **merged APK** of futatter **3.1.1**; direct split-XAPK patching is not supported.

## Upstream / license

This repository is GPL-3.0 licensed. Haiagaru-Morphe by areteruhiro is GPL-3.0 licensed and is referenced for Animanch behavior; no parser implementation has been copied into this scaffold. GPL notices and attribution must be retained for any later code reuse.
Source: https://github.com/areteruhiro/Haiagaru-Morphe

## Release gate

No GitHub Release until explicit final owner approval and successful on-device verification.
