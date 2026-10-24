# app-desktop

The JVM desktop application shell for Linux, and later Windows and macOS on the same target: a window that hosts `TypewrightApp()`. Run it with `./gradlew :app-desktop:run` and package it with the Compose Desktop distribution tasks (`packageDeb`, `packageRpm`, `packageAppImage`).

"Open a font…" uses `java.awt.FileDialog` for a font file and a Swing folder chooser for a UFO folder or the folder to keep a project in; imported projects are written to real folders. The dialogs have never been shown in the build container (no display), so they are owner-verified only.
