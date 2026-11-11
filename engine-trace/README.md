# engine-trace

The raster-to-vector chain that turns a scanned, photographed or drawn letter into a clean outline, stage by stage (handoff M1, brief §7). Its core is pure Kotlin so the chain is testable off-device; platform capture such as the camera and OpenCV lives outside this module.

The `capture` package reads a photograph of a printed template sheet (corner marks, sheet identity, cells) and an SVG file of the template pack into that chain. Every photograph its tests use is synthetic, and `docs/CAPTURE.md` says what that proves, which thresholds are our heuristic, and what is not claimed.
