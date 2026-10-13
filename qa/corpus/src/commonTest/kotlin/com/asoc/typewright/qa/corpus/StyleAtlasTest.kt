// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Decodes the real, checked-in style atlas (data/style-atlas-latin.json) through the same
 * resource reader as the node-economy packs, on whichever target this runs under, and checks the
 * queries the Brief builds its ranges from. The sizes are read from the file itself.
 */
class StyleAtlasTest {
    @Test
    fun theAtlasLoadsWithItsDocumentedShape() {
        val atlas = loadStyleAtlas()
        val pack = atlas.pack
        assertTrue(pack.source.startsWith("google/fonts@b5efa9c32e8f9b63005f5cdb1ad5527a77d2cd04"))
        assertEquals(listOf("contrast", "stress", "bracket", "aperture", "roundness", "xHeight", "width", "stem", "crossbar"), pack.numeric)
        assertEquals(listOf("serif", "aStoreys", "gStoreys", "terminal"), pack.categorical)
        assertEquals(23, pack.classes.size)
        assertEquals(10, pack.classes.values.count { it.corpus })
        assertEquals(20, pack.feelings.size)
        assertEquals(22, pack.bundled.size)
        assertEquals(242, atlas.corpusFaces.size)
        assertTrue(pack.faces.size > 600, "faces: ${pack.faces.size}")
    }

    @Test
    fun genreQuartilesAgreeWithTheStoredDistribution() {
        val atlas = loadStyleAtlas()
        val deco = assertNotNull(atlas.pack.classes["display-artdeco"])
        val stored = assertNotNull(deco.dist["crossbar"])
        val computed = assertNotNull(atlas.quartiles("crossbar", deco.faces))
        assertEquals(stored.n, computed.n)
        assertEquals(stored.med, computed.med, 1e-3)
        assertEquals(stored.q1, computed.q1, 1e-3)
        assertEquals(stored.q3, computed.q3, 1e-3)
    }

    @Test
    fun corpusClassesComeFirstInTheGenreKeys() {
        val atlas = loadStyleAtlas()
        val firstTen = atlas.genreKeys.take(10)
        assertTrue(
            firstTen.all {
                atlas.pack.classes
                    .getValue(it)
                    .corpus
            },
            "$firstTen",
        )
    }

    @Test
    fun queriesOverATinyPack() {
        val atlas =
            StyleAtlas(
                decodeStyleAtlas(
                    """
                    {"source":"test","classes":{"a":{"corpus":true,"faces":["X","Y","Z"],"n":3}},
                     "faces":{"X":{"classes":["a"],"contrast":1.0,"serif":"no"},
                              "Y":{"classes":["a"],"contrast":2.0,"serif":"yes"},
                              "Z":{"classes":["a"],"contrast":4.0,"serif":"no","unknownKey":1}}}
                    """.trimIndent(),
                ),
            )
        assertEquals(listOf("X", "Y", "Z"), atlas.corpusFaces)
        assertEquals(listOf(1.0, 2.0, 4.0), atlas.values("contrast", listOf("X", "Y", "Z")))
        assertEquals(mapOf("no" to 2, "yes" to 1), atlas.counts("serif", listOf("X", "Y", "Z")))
        assertEquals(2.0, atlas.quantile("contrast", listOf("X", "Y", "Z"), 0.5))
        assertEquals(1.5, atlas.quantile("contrast", listOf("X", "Y", "Z"), 0.25))
        assertNull(atlas.quartiles("stress", listOf("X", "Y", "Z")))
        assertNull(
            atlas.pack.faces
                .getValue("X")
                .numeric("nonsense"),
        )
    }

    @Test
    fun quantilesInterpolateLinearly() {
        val v = listOf(0.0, 10.0, 20.0, 30.0, 40.0)
        assertEquals(0.0, quantileOfSorted(v, 0.0))
        assertEquals(40.0, quantileOfSorted(v, 1.0))
        assertEquals(20.0, quantileOfSorted(v, 0.5))
        assertEquals(5.0, quantileOfSorted(v, 0.125))
        assertEquals(40.0, quantileOfSorted(v, 2.0))
    }
}
