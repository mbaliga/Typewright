package dev.aarso.typewright.qa.corpus

import kotlinx.serialization.json.Json

/**
 * The [Json] instance this module decodes its data packs with. Unknown keys are ignored so a
 * future field the generator script adds does not break an older build of this loader.
 */
private val corpusJson = Json { ignoreUnknownKeys = true }

/**
 * Loads and decodes the full node-economy pack (data/node-economy-latin.json): every member
 * face's raw counts plus the per-glyph on/off-curve boxes, for all ten style classes (brief
 * 8.1). Re-parses the resource on every call; wrap the result once, or use [NodeEconomyCorpus],
 * if a caller queries it repeatedly.
 */
fun loadNodeEconomyPack(): NodeEconomyPack =
    corpusJson.decodeFromString(NodeEconomyPack.serializer(), readCorpusResourceText(FULL_PACK_RESOURCE_PATH))

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
