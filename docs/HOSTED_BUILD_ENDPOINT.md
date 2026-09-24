# The hosted build endpoint

**Status: not deployed.** Nobody has stood this endpoint up anywhere. `docs/DECISIONS.md`
lists the endpoint's home as still open ("Still open: ... the hosted endpoint's home"), so
`CompileLocation.HostedEndpoint.endpoint` (`compile/src/commonMain/.../CompileBackend.kt`) is
`null` by default and `HostedEndpointBackend` stays the honest stub it always was until someone
passes it a real URL. What this document describes is the contract a future deployment of that
endpoint **must** satisfy, and the contract the client code in `compile/src/wasmJsMain/` already
speaks for real, proven against a real local server (see "Tested" below) -- not a service that
exists today. Nothing in this document is a promise that a public endpoint is currently reachable.

## Why this endpoint exists

`CompileBackend` (`compile/src/commonMain/kotlin/dev/aarso/typewright/compile/CompileBackend.kt`)
turns a Typewright project into font binaries, and has three backends (brief §3): fontmake and
fontTools through system Python on Linux desktop, through Chaquopy on Android, and -- on the web,
where neither is available -- fontmake in a container behind this HTTP endpoint. The Ship room
names it every time it is used (CLAUDE.md law 3): `CompileLocation.HostedEndpoint.description`
is the exact sentence the UI shows, and it changes depending on whether an endpoint is configured.
The project is sent **only on an explicit build** -- `HostedEndpointBackend.compile()` is the one
and only place this module makes a network call, and it runs only when a caller invokes it with a
`CompileRequest`. `availability()` and object construction never touch the network.

## Request

```
POST {endpoint}/v1/compile
Content-Type: application/json
```

`{endpoint}` is whatever `HostedEndpointBackend(endpoint = ...)` was constructed with; the path
suffix is the constant `HostedBuildProtocol.COMPILE_PATH`. The body is one JSON object:

```json
{
  "requestVersion": 1,
  "source": { "kind": "ufo", "path": "Font-Regular.ufo" },
  "options": {
    "formats": ["TTF", "WOFF2"],
    "removeOverlaps": true,
    "autohint": true
  },
  "files": [
    { "path": "Font-Regular.ufo/metainfo.plist", "contentBase64": "..." },
    { "path": "Font-Regular.ufo/glyphs/A_.glif", "contentBase64": "..." }
  ]
}
```

- `source.kind` is `"ufo"` (one `.ufo` master, `CompileSource.Ufo`) or `"designspace"` (several
  masters and their axes, `CompileSource.Designspace`); `source.path` is that source's path
  inside the project, exactly as `CompileSource.path` holds it.
- `options.formats` mirrors `OutputFormat`'s names (`TTF`, `OTF`, `VARIABLE_TTF`, `WOFF2`);
  `removeOverlaps` and `autohint` mirror `CompileOptions` exactly (brief: autohint always runs
  `ttfautohint`, since Typewright never hints by hand, brief §10).
- `files` is **every file `ProjectDirectory.listFiles()` returns**, each one's bytes from
  `readBytes()`, base64-encoded whole (no chunking, no compression in v1). This is the entire
  project as the app holds it: every master UFO, any `.designspace`, `typewright.json` -- CLAUDE.md
  law 7 ("a project is a directory of UFO 3 and JSON") is exactly what crosses the wire, nothing
  the client adds and nothing it holds back.
- There is no line for authentication yet: v1 has no user accounts and nothing to authenticate
  with (brief). A real deployment sitting behind a domain the client can be pointed at is the
  whole of what "the endpoint's home" (`docs/DECISIONS.md`) still has to decide.

No size limit is defined yet. A real deployment should reject an oversized body with `413`
(handled the same as any other non-2xx status below) rather than accept it silently.

## Response

**Success** -- fontmake ran and produced binaries. HTTP `200`:

```json
{
  "responseVersion": 1,
  "status": "success",
  "binaries": [
    { "fileName": "Font-Regular.ttf", "format": "TTF", "contentBase64": "..." }
  ],
  "log": [
    { "level": "INFO", "message": "fontmake: wrote Font-Regular.ttf" }
  ]
}
```

**Failure** -- fontmake ran and failed (a bad drawing, not a server problem). Also HTTP `200`,
distinguished by `status`, because the request itself was served correctly:

```json
{
  "responseVersion": 1,
  "status": "failure",
  "reason": "glyph 'A' has an open contour",
  "log": [
    { "level": "ERROR", "message": "fontmake: A_.glif: contour 0 is not closed" }
  ]
}
```

- `binaries[].format` is one of `OutputFormat`'s names; `log[].level` is one of
  `CompileLogLine.Level`'s (`INFO`, `WARNING`, `ERROR`). The client is forward-compatible with
  both in one direction only: an unrecognised `level` is read as `INFO` rather than dropping the
  line, but an unrecognised `format` drops that one binary and adds a `WARNING` log line instead
  of mislabelling bytes with the wrong format (CLAUDE.md law 5 in spirit: a number -- or a format
  tag -- this client shows the user is either right or explicitly not shown).
- `reason` is one plain sentence, shown to the user as-is (`CompileResult.Failure.reason`'s own
  contract); when a real deployment omits it the client supplies a generic one rather than
  showing nothing.
- Any HTTP status outside `200`-`299` -- `4xx`, `5xx`, or `fetch()` rejecting outright (DNS
  failure, connection refused, CORS) -- is a **transport-level** problem, not a build result; the
  client turns it into `CompileResult.Failure` too (naming the endpoint and, for a non-2xx status,
  echoing up to 2000 characters of the response body), since `CompileResult` has only three cases
  (`Success`, `Failure`, `NotImplemented`) and a transport failure is closer to "the build ran and
  failed" than to "not implemented" -- fontmake really was asked to run.

## Zero-retention policy

This is the promise a real deployment of this endpoint must keep, stated as concretely as a
policy can be before the deployment exists to audit:

- **Never persisted.** The uploaded project and the binaries it produces exist only for the
  lifetime of that one request. Nothing is written to a database, an object store, a queue, or
  a log line that outlives the response -- not the UFO source, not the compiled fonts, not their
  file names or glyph contents.
- **A fresh, isolated working directory per build**, deleted the moment the response (success or
  failure) has been sent -- the "container" in "fontmake in a container" (`HostedEndpointBackend`'s
  own KDoc) is not reused between requests and carries nothing forward from one build to the next.
- **The build log is the only record**, and it is only ever fontmake's own stdout/stderr lines,
  already returned to the client in the same response (`log[]` above) -- there is no separate
  server-side log capturing file contents, glyph names, or the project's identity.
- **No analytics, no telemetry, ever** (CLAUDE.md law 3): no request is logged anywhere beyond
  what a stock reverse proxy's access log records (timestamp, IP, status code -- ordinary
  operational logging, not analysis of what anyone drew), and even that is not this endpoint's own
  choice to make without saying so plainly if a real deployment adds it.
- **In transit**, `{endpoint}` must be `https://`; a real deployment serving plain `http://` does
  not meet this contract. (`http://` appears above only in the local-server tests, which prove
  the wire format, not the transport's confidentiality.)

**What the client can verify, and what it must simply trust.** The client cannot cryptographically
prove any of the above from outside the container -- no response header attests "nothing was
kept", and none is defined here, because a header is only as trustworthy as the server sending it.
What the client *can* observe is negative evidence: nothing in this contract gives the endpoint a
way to ask for a repeat visit, a session, or a stored identity, and the response contains only
binaries and log lines, never a token or reference implying something was kept server-side. Real
verification -- that a deployment actually tears its containers down, keeps no logs beyond access
logs, and runs the code it claims to -- can only come from outside this contract: a published
container image and infrastructure-as-code for the deployment, so the policy above is checkable
rather than only promised. Until a deployment exists and does that, this section is the shape the
promise must take, not evidence that it has been kept.

## Tested

- `compile/src/commonTest/kotlin/dev/aarso/typewright/compile/HostedBuildProtocolTest.kt` --
  `HostedBuildProtocol`'s request encoding and response/outcome decoding, as pure Kotlin. Runs on
  desktop/JVM, Android host tests, and `wasmJsNodeTest`: the exact JSON shapes on this page,
  proven on every target this module builds for, including where `fetch()` itself cannot run.
- `compile/src/wasmJsTest/kotlin/dev/aarso/typewright/compile/HostedEndpointBackendRealHttpTest.kt`
  -- `HostedEndpointBackend.compile()` against a real local HTTP server (Node's own `http` module,
  `MockHttpServer.kt`), run by `:compile:wasmJsNodeTest`. This is a real `fetch()` POST over a
  real loopback socket to a real server process, not a Kotlin fake standing in for the network:
  success, failure, a non-2xx status, an unreachable port, and the literal bytes the server
  received are all asserted for real. `:compile:wasmJsBrowserTest` is registered for this module
  but its test task is disabled (`KmpPlatformConventionPlugin`'s own default, `README.md`'s
  "Wasm tests run under Node except where Compose is involved" -- `:compile` has no Compose, so
  Node is where it runs), so this is the most real proof available inside this module; Node's
  global `fetch()` (Node >= 18) is the same Fetch API a browser exposes, so this exercises the
  identical call `HostedEndpointTransport.kt` makes in the shipped app, just over loopback
  instead of the public internet.

None of this proves the *hosted* endpoint works, because it does not exist yet -- it proves the
client speaks the contract on this page correctly, so that whenever a real deployment answers to
it, the client side of the connection is already right.
