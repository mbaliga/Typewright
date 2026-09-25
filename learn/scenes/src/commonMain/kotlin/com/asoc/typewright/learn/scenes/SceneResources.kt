// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

/**
 * Reads one of this module's checked-in scene resources as UTF-8 text. [resourcePath] is
 * relative to the resources root, e.g. one of [LineagesResources]'s own path constants.
 *
 * These YAML files are hand-authored lesson content (`docs/LESSONS_SCAFFOLD.md` section 2,
 * SCAFFOLD), not generated data, so unlike `qa/corpus`'s node-economy packs they live directly
 * under this module's own `src/commonMain/resources/` rather than being `Sync`'d in from
 * `data/` at build time — unnecessary here since there is no separate generator script for
 * this content and nothing else in the repository needs these files outside this module.
 *
 * The *reading* mechanism, though, follows `qa/corpus`'s own `CorpusResources.kt` convention
 * exactly rather than inventing a different one (this module's own P6 task brief asked for
 * that explicitly), for the same reason: Kotlin/Wasm has no JVM classpath
 * (docs/OPEN_QUESTIONS.md item 8), so this is `expect`/`actual` per target —
 * [SceneResources.jvm.kt]'s actual reads the resource from the classpath, the way any JVM
 * library does; [SceneResources.wasmJs.kt]'s actual reads the file Kotlin's own Node packaging
 * colocates next to the compiled module, resolved from the running script's own
 * `import.meta.url`. See `CorpusResources.kt`'s own KDoc for the fuller explanation of both;
 * it applies here unchanged, including the caveat that this is written for `wasmJs { nodejs() }`
 * only (`KmpPureConventionPlugin`) and not a browser — a browser consumer needs its own loading
 * strategy, out of this module's scope (docs/OPEN_QUESTIONS.md item 33, "wasmJs loading of
 * `data/learn-faces/` is out of this task's scope" raises the identical fork in the road for
 * the fetched font files; this module's own scene YAML has the same open question).
 */
internal expect fun readSceneResourceText(resourcePath: String): String
