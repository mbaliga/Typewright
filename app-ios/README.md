# app-ios

The iPad app: a static Kotlin framework (`TypewrightKit`, this Gradle module) hosted by a thin SwiftUI shell (`iosApp/`). Everything is unsigned and unverified on a device; read `docs/IOS_PORT.md` first for what is proved and what is not.

- `src/iosMain/.../MainViewController.kt` builds one `ProjectWorkspace` over the sandbox storage in `project/storage` and hands Compose's `ComposeUIViewController` to Swift. `flushWorkspace()` is called when the scene leaves the foreground.
- `iosApp/project.yml` is the XcodeGen spec; `xcodegen generate` there writes the (uncommitted) Xcode project. The bundle id is set in `iosApp/Config/Identity.xcconfig`, the only place.
- `scripts/stage-resources.sh` puts the data packs into the app bundle; `scripts/test-stage-resources.sh` checks it on any host.
- The module exists only with `-Ptypewright.ios=true` and the Android SDK present. Linking the framework needs macOS and Xcode; the `iOS` workflow does it.

## Privacy

Typewright has no telemetry, analytics or network code of its own on iOS. If you install a TestFlight or App Store build, Apple collects crash reports and tester usage on its side, outside Typewright's code; that is the one egress this app accepts, and it is named here and in the app's about text before any such build ships (owner follow-up).
