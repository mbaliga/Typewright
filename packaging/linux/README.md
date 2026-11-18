# packaging/linux

Installs the Linux app-image that `./gradlew :app-desktop:createDistributable` produces. It is the
universal fallback route: a directory copy with a launcher link and a `.desktop` entry, no network
and no package manager.

An app-image is a directory (a launcher plus a bundled runtime), not an `.AppImage` file.

```
packaging/linux/install.sh [--prefix DIR] [APP_IMAGE_DIR]
packaging/linux/install.sh --uninstall [--prefix DIR]
```

The default prefix is `~/.local`. The script has been syntax-checked and dry-run against a fake
app-image in a scratch prefix only; it has not been run against a real build or on a real desktop.
There is no application icon in the repository yet, so the `.desktop` entry has none.
