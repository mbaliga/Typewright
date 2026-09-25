// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlinx.serialization.json.Json

/**
 * The [Json] instance this module decodes its data packs with. Unknown keys are ignored so a
 * future field the generator script adds does not break an older build of this loader.
 */
private val corpusJson = Json { ignoreUnknownKeys = true }

/**
 * Loads and decodes a node-economy pack: by default the full Latin pack
 * (data/node-economy-latin.json, [FULL_PACK_RESOURCE_PATH]) -- every member face's raw counts
 * plus the per-glyph on/off-curve boxes, for all ten style classes (brief 8.1). Pass
 * [DEVANAGARI_PACK_RESOURCE_PATH] or [KANA_PACK_RESOURCE_PATH] to load one of the sibling
 * packs data/scripts/build_script_node_economy_corpus.py builds instead -- same
 * [NodeEconomyPack] shape, a small handful of style classes rather than Latin's ten (see that
 * script's module doc for why). Re-parses the resource on every call; wrap the result once, or
 * use [NodeEconomyCorpus] (its `load`/`loadDevanagari`/`loadKana`), if a caller queries it
 * repeatedly.
 */
fun loadNodeEconomyPack(resourcePath: String = FULL_PACK_RESOURCE_PATH): NodeEconomyPack =
    corpusJson.decodeFromString(NodeEconomyPack.serializer(), readCorpusResourceText(resourcePath))

/**
 * Loads and decodes the compact node-economy pack (data/node-economy-latin.compact.json):
 * on-curve boxes only, derived from the full pack by data/scripts/build_compact_corpus.py.
 * Which pack the app ships is still open (docs/OPEN_QUESTIONS.md item 8); both loaders are
 * exposed so that decision does not have to be made here.
 */
fun loadCompactNodeEconomyPack(): CompactNodeEconomyPack =
    corpusJson.decodeFromString(
        CompactNodeEconomyPack.serializer(),
        readCorpusResourceText(COMPACT_PACK_RESOURCE_PATH),
    )
