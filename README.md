# futatabi

Morphe patch project for **ふたったー (futatter) 3.1.1**, package `com.Nanasaki.ftutter`, versionCode **65**.

**v0.1.0 scope:** Two default-enabled Morphe patches: **Block banner ads** and **Clone app**. Both passed an unsigned **base-APK-only** application/rebuild test. No signed/merged APK, app functionality, or original/clone coexistence has been verified yet. The board-addition gate failed on the unmodified Android emulator; bridge and adapters are not implemented.

## Target integrity

The official APKPure-distributed XAPK, stored only in ignored `apk-inspection/`, was downloaded on Windows and checked:

| Artifact | SHA-256 | Result |
| --- | --- | --- |
| XAPK (68,916,938 bytes) | `11dae3fa2c2882f1223cacbdc14b62bcf2d6ed04bc4436d9fc988e98d6ea53dc` | Exact match |
| `com.Nanasaki.ftutter.apk` (base APK) | `f8093dd9cae64a48b0e74831800782a747045902e2171855a672195ef5690486` | Exact match |

The application is distributed as a **split XAPK**. End users must use a **merged APK** of the exact 3.1.1 release as input to Morphe, *not* an XAPK or an isolated base APK. A merged APK has not yet been patched or tested.

## Banner patch (stage 3)

`BlockBannerAdsPatch.kt` targets `Lb6/I;->onMethodCall` using exact method signature, method strings, the original manager callback and load-error constructor signatures. It detects **both** `loadBannerAd` and `loadAdManagerBannerAd` and returns a normal `onAdFailedToLoad` plugin event through `Lb6/a;->k` before completing the Flutter method call. It does not change the `loadRewardedAd`, `loadRewardedInterstitialAd`, interstitial, native or app-open branches. Fingerprint or callback mismatch refuses application (fail-closed).

Verified without installation:

- Gradle `:patches:generatePatchesList`: **BUILD SUCCESSFUL**; `patches/build/libs/patches-0.1.0.mpp` generated.
- Morphe Desktop v1.18.1 against the verified **base APK only**, with `--unsigned`: **both** patches applied; PATCHING and REBUILDING succeeded, with no failed patches. `aapt` independently confirmed cloned package `com.Nanasaki.ftutter.morphe` and provider authorities prefixed with that cloned package.
- Decompiled resulting patched DEX with JADX 1.5.7: the new initial guard dispatches only the two named banner commands, sends `new AbstractC1061f.c(0, "FutatabiBannerBlocked", …)` via the existing manager and returns; the remaining original dispatcher is retained.
- **Not yet verified:** signing, merged-APK installation, real banner behavior, rewarded-ad regression behavior on a device.

`apk-inspection/` contains only ignored local test inputs, tools and temporary outputs. Do not commit APKs, keystores or decompiled third-party sources.

## Clone app patch (default ON)

The **Clone app** patch is derived from [Morphe's official CloneAppPatch.kt](https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/all/misc/clone/CloneAppPatch.kt), adapted for the pinned Morphe Patcher 1.15.1 resource API and scoped to futatter 3.1.1. Original Morphe/ReVanced attribution and the GPL-3.0 Section 7 notice are retained in the source.

- Enabled by default; unchanged default `packageName` produces **`com.Nanasaki.ftutter.morphe`**, which is separate from the original `com.Nanasaki.ftutter`.
- `updatePermissions=true` and `updateProviders=true` by default. The base APK's 4 original provider authorities and declared custom permission were rewritten in the unsigned static test.
- To **disable cloning**, uncheck **Clone app** in the Morphe patch selection before patching, or use the desktop CLI `--disable="Clone app"`. To use a different clone identity, set its `packageName` option.
- Clones are **not guaranteed to work**. The clone uses a different package identity, Android data location, and signing identity; **do not overwrite or uninstall your existing installation expecting its data to be preserved**. Installation of an APK signed with a different key over an existing package may require removal of that clone and will erase that clone's data. Back up any important app data before reinstalling or changing a package name.
- Final tests by the owner: original + clone install side by side; banner hidden in catalog/thread; rewarded-ad option (24-hour hide) still functional. **All remain unverified.**

**Future ContentProvider interaction:** the clone rewrites manifest authorities prefixed by the original package. The planned bridge must build or reference its authority from the actual application ID (e.g. `context.packageName + ".futatabi.bridge"`), rather than hardcode `com.Nanasaki.ftutter...`. There is **no bridge provider in v0.1.0**, so runtime compatibility cannot be tested yet. Reverify during stage 5.

## ADB reverse gate (stage 4)

Offline fixed-response Windows fixture: `tests/adb_reverse_fixture.py`.

```powershell
python tests/adb_reverse_fixture.py --port 18766
adb -s <physical-device-serial> reverse tcp:18766 tcp:18766
```

For the physical-device gate, the fixture binds to **127.0.0.1:18766**, avoiding the unrelated Windows service on 18765. It serves a fixed Shift_JIS Futaba-style catalog, thread and PNG thumbnail. All POST requests return HTTP 403. It never contacts third-party servers. The default fixture port remains 18765 when no argument is provided; the eventual Android bridge still uses **18765**.

Smoke test:

```powershell
python -m unittest discover -s tests -v
```

**Verified: unit smoke test PASS on an ephemeral port (not port 18765).** GET `/aimoge/futaba.php?mode=cat` = 200, Shift_JIS title decoded; GET `/aimoge/res/1001.htm` = 200; GET `/aimoge/thumb/1.png` = 200, valid PNG signature; POST = 403. Additionally, after this change the actual CLI fixture was launched on **127.0.0.1:18766** and queried over HTTP: catalog 200 (355 bytes, Shift_JIS title readable), thread 200 (559 bytes, body readable), image 200 (68-byte PNG signature), POST 403. This is a Windows HTTP verification, **not** a five-point Android gate PASS.

**Port distinction:** the required **18765** is already bound on Windows by another project's `chatgpt-auditor/sync/collector.mjs` (`node.exe`, PID 58092 at the earlier inspection); it must not be stopped or modified. Therefore, a real fixture HTTP bind/test on **18765** has **not** been performed. The approved physical-device gate instead uses **18766**. The owner has approved an emulator for the stage-4 gate, with final physical-device testing deferred until before a future release.

The stage-4 emulator gate requires five separate observations on **unmodified futatter**:

1. `http://127.0.0.1:18766/aimoge/` is accepted as a board.
2. The board appears in the board list.
3. The catalog renders in the existing catalog UI.
4. The thread body and images render in the existing thread UI.
5. The registered board survives an app restart.

**Latest stage-4 result: FAIL.** On `emulator-5554` the unmodified app accepted the URL text in the board-add dialog, but submitting did not create a board; neither board list nor restart showed it. Gate items 1 and 2 FAIL, items 3–5 NOT TESTED. See [gate report and screenshots](docs/gate4-emulator-20261121.md). Stop before ContentProvider bridge/adapters until a later gate test passes. A physical-device gate is not a prerequisite for publishing the owner-approved v0.1.0 patch-only release.

## Planned read-only bridge (not implemented)

Board URLs after the gate:

- Aimoge: `http://127.0.0.1:18765/aimoge/`
- Animanch: `http://127.0.0.1:18765/animanch/`

The proposed Android in-process ContentProvider HTTP bridge, Shift_JIS renderer, persistent numeric virtual ID mappings, image proxy, upstream caching/rate limits, Aimoge client policy, Animanch parser and disabled Mebuki adapter are **future stages only**.

## Upstream and license

GPL-3.0. The bundled Clone app derivative preserves Morphe's copyright notice, original ReVanced attribution and the original file-specific GPL-3.0 Section 7 additional-terms notice. [Haiagaru-Morphe by areteruhiro](https://github.com/areteruhiro/Haiagaru-Morphe) was confirmed GPL-3.0 and may be used as a reference for Animanch handling; no parser implementation is copied. Preserve all applicable copyright and license notices on redistribution.

Release v0.1.0 is explicitly owner-approved for the two patch definitions **only**; no patched third-party APK is distributed.
