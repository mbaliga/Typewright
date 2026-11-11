# Google Play data-safety form: answers

> **Draft for Madhav's edit.** These are the answers the code supports at 1.0.0-rc1. They are the owner's
> to submit: Play Console is the owner's account, and the form is a legal declaration. Re-check every answer
> against the release build's merged manifest (`app-android/build/intermediates/merged_manifests/release/`)
> and against `docs/release/PRIVACY.md` before submitting. This environment has no Play Console and no device.

Evidence for the answers: the manifest declares one permission, `CAMERA` (`uses-feature camera.any` is
`required=false`). `INTERNET` is not declared, and the one network-state permission a library adds
(`ACCESS_NETWORK_STATE`) is removed in `app-android/src/main/AndroidManifest.xml`. `tools/release/android_smoke.py`
fails the release smoke test if either appears. No analytics, ads, crash-reporting or sign-in SDK is
declared in `THIRD_PARTY.md`.

## Data collection and sharing

| Form question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | Nothing leaves the device; the app has no network permission. |
| Data types collected | none | The camera picture is processed on the device only and never transmitted. |
| Data types shared | none | |
| Is all of the user data collected by your app encrypted in transit? | Not applicable (no data is transmitted). Play shows this only if data is collected. | |
| Do you provide a way for users to request that their data be deleted? | Not applicable: no data is collected. The person's files are on their device and are deleted with the app or by them. | |

## Other declarations

| Item | Answer |
|---|---|
| Privacy policy URL (required) | The published copy of `docs/release/PRIVACY.md` (owner: pick the URL; the repository's file works). |
| Ads | No ads. |
| App access | All functionality is available without an account or a login. |
| Content rating | Owner's questionnaire; the app has no user-generated content shared with others. |
| Target audience | 13+ or general, owner's choice; not designed for children (no Families policy). |
| Permissions declaration | Camera: used only to photograph a printed template sheet (core feature of Capture); no declaration beyond the standard one is needed. |
| Financial features, health, VPN, government | None. |
| Payments | None inside the app. The Play build shows no support link (`-Ptypewright.channel=play`); supporting happens on the website. No Play Billing. |
| Data-safety "app functionality" notes | If the form asks about on-device processing, say: the photograph of a sheet is analysed on the device. |

## If a later version changes this

Adding any of the following changes these answers, and `docs/release/PRIVACY.md` first: the `INTERNET`
permission, a fetch of a Google Fonts family, a GitHub push, a hosted build, any analytics or crash upload. The
smoke test will fail on `INTERNET` until it is changed on purpose.
