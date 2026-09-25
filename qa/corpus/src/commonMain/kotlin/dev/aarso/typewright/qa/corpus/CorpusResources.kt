// SPDX-License-Identifier: Apache-2.0

package dev.aarso.typewright.qa.corpus

/**
 * Path, relative to this module's resources root, of the full node-economy pack. Matches the
 * `into("typewright/corpus")` destination `build.gradle.kts`'s `syncCorpusData` task copies
 * `data/node-economy-latin.json` to; `qa/corpus/src/jvmTest/.../CorpusDataResourceTest.kt`
 * pins the same path as a classpath check.
 */
internal const val FULL_PACK_RESOURCE_PATH = "typewright/corpus/node-economy-latin.json"

/** Path, relative to this module's resources root, of the compact node-economy pack. */
internal const val COMPACT_PACK_RESOURCE_PATH = "typewright/corpus/node-economy-latin.compact.json"

/**
 * Path, relative to this module's resources root, of the Devanagari sibling pack
 * (data/node-economy-devanagari.json, built by
 * data/scripts/build_script_node_economy_corpus.py --script devanagari). Same
 * [NodeEconomyPack] shape as [FULL_PACK_RESOURCE_PATH]'s Latin pack, decoded the same way --
 * see [loadNodeEconomyPack] and [NodeEconomyCorpus.loadDevanagari].
 */
internal const val DEVANAGARI_PACK_RESOURCE_PATH = "typewright/corpus/node-economy-devanagari.json"

/**
 * Path, relative to this module's resources root, of the kana sibling pack
 * (data/node-economy-kana.json, built by data/scripts/build_script_node_economy_corpus.py
 * --script kana). Covers both Hiragana and Katakana in one pack -- see that script's module
 * doc for why. Same [NodeEconomyPack] shape as [FULL_PACK_RESOURCE_PATH]'s Latin pack -- see
 * [loadNodeEconomyPack] and [NodeEconomyCorpus.loadKana].
 */
internal const val KANA_PACK_RESOURCE_PATH = "typewright/corpus/node-economy-kana.json"

/**
 * Reads one of this module's embedded corpus resources as UTF-8 text. [resourcePath] is
 * relative to the resources root, e.g. [FULL_PACK_RESOURCE_PATH] or [COMPACT_PACK_RESOURCE_PATH].
 *
 * Kotlin/Wasm has no JVM classpath (docs/OPEN_QUESTIONS.md item 8, "Corpus data on Wasm"), so
 * this is `expect`/`actual` per target rather than one common implementation:
 * - the JVM actual (`CorpusResources.jvm.kt`) reads the resource from the classpath, the way any
 *   JVM library does -- the same resource the existing `CorpusDataResourceTest` already checks;
 * - the Wasm actual (`CorpusResources.wasmJs.kt`) reads the file Kotlin's own Node packaging
 *   colocates next to the compiled module (verified empirically under
 *   `build/wasm/packages/typewright-qa-corpus-test/kotlin/typewright/corpus/`), resolved from
 *   the running script's own `import.meta.url` rather than the Node process's working
 *   directory -- that working directory turned out to be a shared
 *   `<rootProject>/build/wasm/packages/...` path with no fixed relationship to this module's own
 *   `build/`, so a path relative to it would have been fragile.
 *
 * The Wasm actual is written for `wasmJs { nodejs() }`, the only Wasm environment this pure
 * module builds for (`typewright.kmp.pure`, `KmpPureConventionPlugin`) and the one
 * `:qa:corpus:wasmJsNodeTest` exercises. It uses Node's `process` global, so it would not
 * resolve in a browser with no such global; a browser consumer (a future platform module such as
 * `app-web`) needs its own loading strategy for these packs -- most likely fetching them from the
 * app's distribution, per docs/OPEN_QUESTIONS.md item 8 -- which is out of this module's scope.
 */
internal expect fun readCorpusResourceText(resourcePath: String): String
