# packaging/linux/flatpak

A Flatpak manifest that wraps the app-image directory from `./gradlew :app-desktop:createDistributable`.
`desktop-linux.yml` builds it with `flatpak-builder` into a `.flatpak` bundle (unsigned, not uploaded).

Evidence: the manifest parses with `flatpak-builder --show-manifest` and the `.desktop` and metainfo pass
`desktop-file-validate` and `appstreamcli validate` (see `../test-metadata.sh`). No Flatpak has been built
or run. The runtime download did not fit in the authoring container, and the app has never started in a
sandbox.

Decisions that are the owner's:

- **App id** `com.asoc.Typewright` is a placeholder. It needs a NAMES.md row first (plan rule R11); then rename
  the three `com.asoc.Typewright.*` files and the ids inside them together.
- **Filesystem grant.** `--filesystem=home` lets the Swing folder chooser browse the home folder. The
  picker already skips `zenity`/`kdialog` inside a sandbox (`LinuxSandbox`), because the runtime has neither.
  A portal-aware picker would let the grant shrink; AWT is not portal-aware, so that is new code.
- **Fonts tools.** `fontc`, `fontmake` and Fontspector are not in the runtime. The V1 compile backend is
  still a stub, so nothing is missing today; once it is real, they must be bundled here.
- **Flathub.** Not submitted. FSL-1.1-ALv2 is source-available, not OSI-approved; Flathub's view is unknown.
