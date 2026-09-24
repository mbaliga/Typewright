# compile

Font compilation behind the `CompileBackend` interface: fontmake and fontTools through system Python on Linux, Chaquopy on Android, and a hosted build endpoint on the web that the UI names every time it is used. No caller may depend on Python being present, and until each backend is built it is a stub that returns an explicit not-implemented result rather than any output.

The web backend (`HostedEndpointBackend`) is real: with an endpoint configured, `compile()` uploads the project over a real `fetch()` POST and decodes a real response; with none configured (the default -- the endpoint's home is still undecided, `docs/DECISIONS.md`) it stays the stub it always was. `docs/HOSTED_BUILD_ENDPOINT.md` documents the wire contract and zero-retention policy in full, and says plainly that no such endpoint is deployed anywhere yet.
