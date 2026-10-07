// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

import com.asoc.typewright.qa.corpus.loadStyleAtlas
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Brief's vocabulary against the real style atlas: every name it uses exists there, and the
 * typeface shown for each answer really measures as that answer. A regenerated atlas that moves
 * a band past a cue face fails here, before anyone sees a mislabelled option.
 */
class AtlasConsistencyTest {
    private val atlas = loadStyleAtlas()
    private val maker = TargetMaker.of(atlas)

    @Test
    fun everyAnswersCueFaceMeasuresAsThatAnswer() {
        val wrong =
            Level.entries.mapNotNull { level ->
                val cue = assertNotNull(atlas.pack.bundled[level.cue.face.key], "no measurements for cue ${level.cue.face.key}")
                val misses =
                    level.anchors.filterNot { anchor ->
                        when (anchor) {
                            is Anchor.Value -> {
                                anchor.feature.categoricalValue(cue) == anchor.value
                            }

                            else -> {
                                val v = anchor.feature.numericValue(cue)
                                val r = maker.range(anchor)
                                v != null && r != null && v in r
                            }
                        }
                    }
                if (misses.isEmpty()) null else "${level.name} (${level.cue.face.family}): ${misses.map { it.feature.key }}"
            }
        assertTrue(wrong.isEmpty(), "cue faces outside their own answer: $wrong")
    }

    @Test
    fun everyAnswerHasAMeasuredRange() {
        for (level in Level.entries) {
            for (anchor in level.anchors) {
                if (anchor is Anchor.Value) continue
                assertNotNull(maker.range(anchor), "${level.name} has no range for ${anchor.feature.key}")
            }
        }
    }

    @Test
    fun everyNameTheBriefUsesIsInTheAtlas() {
        for (genre in GENRES) assertTrue(genre.key in atlas.pack.classes, "genre ${genre.key}")
        for (face in CueFace.entries) assertTrue(face.key in atlas.pack.bundled, "cue ${face.key}")
        for (feeling in FEELINGS) assertNotNull(profileOf(feeling, atlas), "feeling ${feeling.key}")
        for (use in USES) for (key in use.suggestedGenres) assertNotNull(genreByKey(key), "${use.id} suggests $key")
        assertTrue(
            CORPUS_GENRE_KEYS ==
                atlas.pack.classes
                    .filterValues { it.corpus }
                    .keys,
        )
    }

    @Test
    fun aGenresOwnCueReadsAsThatGenre() {
        // The face a genre is shown in should read as that genre, or at least put it in its top three.
        val misread =
            GENRES.mapNotNull { genre ->
                val cue = genre.cue ?: return@mapNotNull null
                val face = atlas.pack.bundled.getValue(cue.face.key)
                val reading = assertNotNull(readAs(face.asMeasurement(), atlas))
                val top = reading.genres.take(3).map { it.genre.key }
                if (genre.key in top) null else "${genre.key} (${cue.face.family}) read as $top"
            }
        assertTrue(misread.size <= GENRES.count { it.cue != null } / 3, "too many genre cues misread: $misread")
    }
}
