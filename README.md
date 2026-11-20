# futatabi

Morphe patch project targeting **futatter 3.1.1** (`com.Nanasaki.ftutter`, versionCode `65`) only.

## Status

**Scaffold only; no functional patch is published.** The exact target APK has not yet been available for fingerprint verification. ADB has no connected device, so the mandatory `adb reverse` integration gate has not run. Do not use this repository as a functional patch yet.

## Planned scope

- Fail-closed banner-ad suppression at the `google_mobile_ads` `loadBannerAd` handler; rewarded advertising remains untouched.
- Read-only local Futaba-compatible bridge with startup ContentProvider, catalog, thread, images, persistent virtual numeric IDs, Shift_JIS rendering, blocked POST and respectful cache/rate-limits.
- Aimoge official API adapter; Animanch parser; Mebuki placeholder disabled in v1.

## Board registration (after the bridge is verified)

- Aimoge: `http://127.0.0.1:18765/aimoge/`
- Animanch: `http://127.0.0.1:18765/animanch/`

Futatter is distributed as split XAPK. The Morphe patch target is a **merged APK** of futatter **3.1.1**; direct split-XAPK patching is not supported.

## Upstream / license

This repository is GPL-3.0 licensed. Haiagaru-Morphe by areteruhiro is GPL-3.0 licensed and is referenced for Animanch behavior; no parser implementation has been copied into this scaffold. GPL notices and attribution must be retained for any later code reuse.
Source: https://github.com/areteruhiro/Haiagaru-Morphe

## Release gate

No GitHub Release until explicit final owner approval and successful on-device verification.
