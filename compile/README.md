# compile

Font compilation behind the `CompileBackend` interface: fontmake and fontTools through system Python on Linux, Chaquopy on Android, and a hosted build endpoint on the web that the UI names every time it is used. No caller may depend on Python being present, and until each backend is built it is a stub that returns an explicit not-implemented result rather than any output.
