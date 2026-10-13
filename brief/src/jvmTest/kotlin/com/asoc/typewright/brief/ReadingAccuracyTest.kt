// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.loadStyleAtlas
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * How well [readAs] names a face's genre: each face in the atlas read against all the others
 * (left out of its own vote). The floors sit a little under what the current atlas measures
 * (0.52 and 0.83 among the ten corpus classes, 0.40 and 0.69 across all genres), so they catch a
 * regression without pinning noise. The numbers are printed for the record.
 */
class ReadingAccuracyTest {
    @Test
    fun readingsPutTheRightGenreNearTheTop() {
        val atlas = loadStyleAtlas()
        val corpusKeys =
            atlas.pack.classes
                .filterValues { it.corpus }
                .keys
        var corpusN = 0
        var corpusTop1 = 0
        var corpusTop3 = 0
        var allN = 0
        var allTop1 = 0
        var allTop3 = 0
        for ((family, face) in atlas.pack.faces) {
            if (face.classes.isEmpty()) continue
            val reading = readAs(face.asMeasurement(), atlas, exclude = family) ?: continue
            val ranked = reading.genres.map { it.genre.key }
            val truth = face.classes.toSet()
            allN++
            if (ranked.firstOrNull() in truth) allTop1++
            if (ranked.take(3).any { it in truth }) allTop3++
            val corpusTruth = truth intersect corpusKeys
            if (corpusTruth.isNotEmpty()) {
                val corpusRanked = ranked.filter { it in corpusKeys }
                corpusN++
                if (corpusRanked.firstOrNull() in corpusTruth) corpusTop1++
                if (corpusRanked.take(3).any { it in corpusTruth }) corpusTop3++
            }
        }
        val c1 = corpusTop1.toDouble() / corpusN
        val c3 = corpusTop3.toDouble() / corpusN
        val a1 = allTop1.toDouble() / allN
        val a3 = allTop3.toDouble() / allN
        println("readAs, left one out: corpus $corpusN faces top1 $c1 top3 $c3; all $allN faces top1 $a1 top3 $a3")
        assertTrue(c1 >= 0.45 && c3 >= 0.78, "corpus top1 $c1, top3 $c3")
        assertTrue(a1 >= 0.34 && a3 >= 0.62, "all genres top1 $a1, top3 $a3")
    }
}
