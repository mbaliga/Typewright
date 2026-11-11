# Release checklist: 1.0.0-rc1 and after

> **Draft for Madhav's edit.** "Done here" means done and checked in the build container (no phone, no display, no
> store accounts, no signing secrets). Everything under "Owner" cannot be done there and is not claimed.

## 0. Blocks the rc tag

- [ ] **D2: name clearance for "Typewright"** (Classes 9 and 42, through Shiva, `docs/LICENSING.md` section 5). **The
      `v1.0.0-rc1` tag, the Play listing, the website and the Google Fonts mentions wait for this.** If the name
      changes, change the application id, the app label, the package names that carry it, `TRADEMARKS.md` and every
      file in this folder first; a published Play application id cannot be changed.
- [ ] Lawyer's read of `docs/LICENSING.md` sections 1 and 5, `TRADEMARKS.md` and `PRIVACY.md` (D1).
- [ ] P19 release-candidate audit (`docs/release/RC_AUDIT.md`) says go for each target you ship.
- [ ] The owner's device checklist from P19 is filled in on the RedMagic 11 Pro. Nothing about on-device behaviour
      in `STORE_LISTING.md` is true until it is.

## 1. Before building (done here, repeat in CI)

- [x] `python3 tools/check_licences.py`: every source file has its SPDX line; no Apache module depends on an FSL one.
- [x] `python3 tools/check_third_party.py`, `python3 tools/gen_notices.py --check`: the catalogue is covered and
      About's embedded notices equal `THIRD_PARTY.md`.
- [x] `python3 tools/check_resolved_dependencies.py`: every library Gradle resolves onto the apps is mentioned in
      `THIRD_PARTY.md` (needs Gradle and the Android SDK).
- [x] `python3 tools/sync_version.py --check`: About's version equals `version.properties`.
- [ ] Set the release in `version.properties` (`VERSION_NAME`, and raise `VERSION_CODE` for every store upload), run
      `python3 tools/sync_version.py`, update `CHANGELOG.md` (move Unreleased, put the date).
- [ ] Set `SUPPORT_URL` in `ui/.../settings/BuildChannel.kt` once the merchant-of-record page exists (D2's neighbour,
      `PROMPTS_V1.md` owner step 2). Until then About says the page is not open yet.

## 2. Android

Signing secrets (owner; never committed): create a keystore once (`keytool -genkeypair -v -keystore
typewright-upload.jks -alias typewright -keyalg RSA -keysize 4096 -validity 10000`), keep the file and passwords
in a password manager, and give CI these as secrets: `TYPEWRIGHT_KEYSTORE_FILE` (a path, written from a base64
secret at job start), `TYPEWRIGHT_KEYSTORE_PASSWORD`, `TYPEWRIGHT_KEY_ALIAS`, `TYPEWRIGHT_KEY_PASSWORD`. Enrol in Play
App Signing for the AAB; the key above is then the upload key.

- [x] `./gradlew :app-android:releaseSmokeTest` builds the minified release APK and checks it with
      `tools/release/android_smoke.py`: package, version equals the version file, minSdk 31, targetSdk 36, not
      debuggable, no backup, only the camera permission, no network permission, launcher icon present, R8 ran, size in
      bounds. With no secrets it prints that the APK is UNSIGNED and still passes. Done here.
- [ ] Direct download (GitHub Release): `./gradlew :app-android:assembleRelease` with the secrets set. Output:
      `app-android/build/outputs/apk/release/app-android-release.apk`. `apksigner verify --print-certs` on it.
- [ ] Play: `./gradlew :app-android:bundleRelease -Ptypewright.channel=play`. Output:
      `app-android/build/outputs/bundle/release/app-android-release.aab`. The `play` channel hides the support link in
      About (tested in `BuildChannelTest` and `BuildChannelFlagTest`). Do not ship the default (`github`) channel to
      Play. Keep `app-android/build/outputs/mapping/release/mapping.txt` with the release for reading crash reports.
- [ ] **Owner, on the phone**: install the release APK; open, create a project, take a sheet photo, relaunch.
      Not verified anywhere else.
- [ ] Play Console (owner): store listing from `STORE_LISTING.md`, privacy policy URL, data-safety form from
      `PLAY_DATA_SAFETY.md`, content rating, target audience, app access (none needed), internal testing track first.
- [ ] `targetSdk` stays 36 unless the owner has verified 37 on the device (`OPEN_QUESTIONS.md` item 12). Do not change
      `minSdk` (31).

## 3. Linux

- [x] `./gradlew :app-desktop:packageAppImage` builds the jpackage app image with the JRE and the licence files. Done here.
- [ ] `APPIMAGETOOL=... tools/release/build_appimage.sh` wraps it into the AppImage (`LINUX_PACKAGING.md`). Not run here.
- [ ] **Owner or CI**: start the AppImage once on a desktop with a display; open the sample font; run Check with and
      without `TYPEWRIGHT_PYTHON` set, and see both messages. `sha256sum` the file and put the sum in the release notes.

## 4. Web

- [x] `./gradlew :app-web:wasmJsBrowserDistribution`, `node tools/locale_guard_test.mjs`, and the Wasm tests (CI web job).
- [ ] Deploy `app-web/build/dist/wasmJs/productionExecutable/` to the static host (`WEB_DEPLOY.md`). Owner: domain, host.
- [ ] **Owner, in a browser**: the page is not blank (also with `LANG` unset in Chromium on Linux), the Preview banner
      shows, About shows the notices, "Open a font" works, the zip downloads. Not verified here.

## 5. Docs and legal

- [ ] `docs/release/*` read by the owner and the "Draft" lines removed or kept on purpose.
- [ ] `LICENSE`, `LICENSES/` and `THIRD_PARTY.md` ship in every artefact: AppImage (done), web `legal/` (done), Android
      through About (done). `TRADEMARKS.md` current after D2.
- [ ] README's status paragraph is out of date (it still says P0 to P9); update it with the release.

## 6. Tag and publish (only after section 0)

- [ ] Merge the release PR. `git tag -a v1.0.0-rc1 -m "Typewright 1.0.0-rc1"` and push the tag. **No tag has been
      created by this work.**
- [ ] GitHub Release: the signed APK, the AppImage and its checksum, the changelog entry. Mark it pre-release.
- [ ] After the rc has been through the audit and the device checklist: tag `v1.0.0` (owner step 5 in `PROMPTS_V1.md`).

## 7. Dependency and licence changes after this

Any new dependency: add its row to `THIRD_PARTY.md` (licence read from its POM or licence file), run
`python3 tools/gen_notices.py`, and `python3 tools/check_resolved_dependencies.py`.
