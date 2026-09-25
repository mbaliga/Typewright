// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.campaign

/**
 * Reads one of this module's checked-in campaign resources as UTF-8 text. [resourcePath] is
 * relative to the resources root, e.g. [WorkbookLatinContent.RESOURCE_PATH].
 *
 * This workbook YAML is hand-authored content (like `learn:scenes`' own scene YAML), not
 * generated data, so it lives directly under this module's own `src/commonMain/resources/`
 * rather than being `Sync`'d in from `data/` at build time -- `qa/corpus`'s
 * `syncCorpusData`/`CorpusResources.kt` pattern is for generated packs with their own generator
 * script; this has none, exactly like `learn:scenes`' own scene content
 * ([com.asoc.typewright.learn.scenes.SceneResources]'s own KDoc makes the identical call).
 *
 * The *reading* mechanism follows that same module's `SceneResources.kt` (and, before it,
 * `qa/corpus`'s `CorpusResources.kt`) convention exactly rather than inventing a third one:
 * Kotlin/Wasm has no JVM classpath, so this is `expect`/`actual` per target --
 * `CampaignResources.jvm.kt`'s actual reads the resource from the classpath the way any JVM
 * library does; `CampaignResources.wasmJs.kt`'s actual reads the file Kotlin's own Node packaging
 * colocates next to the compiled module, resolved from the running script's own
 * `import.meta.url`. Written for `wasmJs { nodejs() }` only (`KmpPureConventionPlugin`), not a
 * browser, exactly like both of those.
 */
internal expect fun readCampaignResourceText(resourcePath: String): String
