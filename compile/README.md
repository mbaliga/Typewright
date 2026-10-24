# compile

Font compilation behind the `CompileBackend` interface: fontmake and fontTools through the Python on a Linux desktop, and honest stubs on Android and the web that say what they are. No caller may depend on Python being present, and a backend that cannot compile returns an explicit result with a plain sentence rather than any output.

## Desktop: `SystemPythonFontmakeBackend`

Real. It finds a Python (`TYPEWRIGHT_PYTHON`, else `python3`; a relative path is made absolute when it is found), checks that fontmake, ufo2ft and fontTools import, copies the project's UFO into a temporary directory under plain ASCII names (so an accented family builds in a Java run with `LANG=C`), runs `python -m fontmake -u <ufo> -o ttf --output-path <font>`, streams each output line to `onLog` as it arrives, and returns the `.ttf` bytes under the project's own file name. A cancelled or timed-out build kills fontmake and everything it started, and the temporary directory is deleted either way. The project's own files are never written to.

- **Quadratic outlines.** A font opened from a TTF keeps its outlines as quadratic `qcurve` points. fontmake's default overlap remover cannot read them, so the build copy is first rewritten as cubic (`prepareUfoForFontmake`, using the exact degree elevation in `core-geometry`) and the log says how many glyphs were rewritten. The project is not changed.
- **Overlaps and hinting.** With `skia-pathops` installed, overlaps are removed; without it they are kept and the log warns, naming the pip command. `ttfautohint` is used when it is on the PATH; otherwise the font is not hinted and the log says so.
- **Failures** are one sentence a person can act on (`FailureExplanation.kt`): Python missing, a package missing (with the pip command), a glyph or segment fontmake cannot read, a timeout, or fontmake's own last error line.

## Android and web

`ChaquopyBackend` (Android) answers `NotImplemented`, with the sentence "Compiling on this phone is not built in this version (the Android route is an owner decision, D5); build in the desktop app." `HostedEndpointBackend` (web) is real only when an endpoint is configured: it uploads the project over a `fetch()` POST and decodes the response. With none configured, which is the default, it answers `NotImplemented` with "The web preview does not compile (D4); export the UFO and Ship folder and build on the desktop app." `docs/HOSTED_BUILD_ENDPOINT.md` documents the wire contract and the zero-retention policy, and says plainly that no endpoint is deployed anywhere yet. Neither of the two has been run on a device or against a server from this repository (law 4).

## Tests

`./gradlew :compile:desktopTest` runs everything that needs no Python (a fake process runner answers the Python probe, and a small shell script stands in for Python and fontmake wherever a real process is needed). `./gradlew :compile:pythonIntegrationTest` runs fontmake for real and needs `TYPEWRIGHT_PYTHON` to name a Python with the packages in `tools/requirements-golden-path.txt` (it fails, with the pip command, when it does not); CI's golden-path job runs it. `CompileBackendStubTest` pins the Android and web stubs' answer from common code. See `docs/CHECK_AND_COMPILE.md`.
