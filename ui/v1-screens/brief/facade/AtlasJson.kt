// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.qa.corpus

/**
 * The style atlas decoded with the browser's own JSON parser, for the Brief's design prototype,
 * which compiles the engine to JavaScript without kotlinx.serialization. It builds the same
 * [StyleAtlasPack] the app decodes; build.py swaps it in for the serialization-based decoder.
 */
fun decodeStyleAtlas(text: String): StyleAtlasPack {
    val root: dynamic = JSON.parse(text)
    return StyleAtlasPack(
        source = root.source as String,
        version = (root.version as? Int) ?: 1,
        numeric = strings(root.numeric),
        categorical = strings(root.categorical),
        classes = entries(root.classes).mapValues { (_, g) -> group(g) },
        feelings = entries(root.feelings).mapValues { (_, f) -> feeling(f) },
        faces = entries(root.faces).mapValues { (_, f) -> face(f) },
        bundled = entries(root.bundled).mapValues { (_, f) -> face(f) },
    )
}

private fun entries(o: dynamic): Map<String, dynamic> {
    if (o == null) return emptyMap()
    val keys: Array<String> = js("Object.keys")(o)
    return keys.associateWith { o[it] }
}

private fun strings(a: dynamic): List<String> {
    if (a == null) return emptyList()
    val array: Array<dynamic> = a
    return array.map { it as String }
}

private fun double(v: dynamic): Double? = if (v == null) null else (v as Number).toDouble()

private fun string(v: dynamic): String? = if (v == null) null else v as String

private fun quartiles(q: dynamic): Quartiles =
    Quartiles(
        min = (q.min as Number).toDouble(),
        q1 = (q.q1 as Number).toDouble(),
        med = (q.med as Number).toDouble(),
        q3 = (q.q3 as Number).toDouble(),
        max = (q.max as Number).toDouble(),
        n = (q.n as Number).toInt(),
    )

private fun shares(s: dynamic): Map<String, Map<String, Int>> =
    entries(s).mapValues { (_, counts) -> entries(counts).mapValues { (_, n) -> (n as Number).toInt() } }

private fun group(g: dynamic): AtlasGroup =
    AtlasGroup(
        corpus = g.corpus == true,
        faces = strings(g.faces),
        n = (g.n as? Number)?.toInt() ?: 0,
        dist = entries(g.dist).mapValues { (_, q) -> quartiles(q) },
        share = shares(g.share),
    )

private fun feeling(f: dynamic): AtlasFeeling {
    val faces: Array<dynamic> = if (f.faces == null) emptyArray() else f.faces
    return AtlasFeeling(
        faces = faces.map { AtlasFeelingFace(family = it.family as String, score = double(it.score)) },
        n = (f.n as? Number)?.toInt() ?: 0,
        dist = entries(f.dist).mapValues { (_, q) -> quartiles(q) },
        share = shares(f.share),
    )
}

private fun face(f: dynamic): AtlasFace =
    AtlasFace(
        family = string(f.family),
        classes = strings(f.classes),
        drawing = double(f.drawing),
        contrast = double(f.contrast),
        stress = double(f.stress),
        bracket = double(f.bracket),
        aperture = double(f.aperture),
        roundness = double(f.roundness),
        xHeight = double(f.xHeight),
        width = double(f.width),
        stem = double(f.stem),
        crossbar = double(f.crossbar),
        serif = string(f.serif),
        aStoreys = string(f.aStoreys),
        gStoreys = string(f.gStoreys),
        terminal = string(f.terminal),
    )
