// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.qa.corpus.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleScorerTest {
    @Test
    fun styleClassKeysMatchTheCorpusTaxonomy() {
        assertEquals(10, STYLE_CLASS_KEYS.size)
        assertEquals(STYLE_CLASS_KEYS.toSet().size, STYLE_CLASS_KEYS.size) // no duplicates
    }

    @Test
    fun rankStyleClassesCoversAllTenKeysExactlyOnce() {
        val ranked = rankStyleClasses(FeatureVector(null, null, null, null, Storeys.UNKNOWN, TerminalStyle.UNKNOWN, null, null, null, null))
        assertEquals(STYLE_CLASS_KEYS.toSet(), ranked.map { it.styleKey }.toSet())
    }

    @Test
    fun aTextbookGeometricSansTopsAsGeometric() {
        val f =
            FeatureVector(
                contrastRatio = 1.05,
                stressAngleDegrees = 2.0,
                hasSerif = false,
                bracketScore = null,
                storeys = Storeys.SINGLE,
                terminalStyle = TerminalStyle.FLAT,
                apertureOpenness = 0.30,
                oRoundnessExponent = 2.1,
                xHeightToCapHeightRatio = 0.72,
                widthClass = 0.55,
            )
        val ranked = rankStyleClasses(f)
        assertEquals("sans-geometric", ranked.first().styleKey)
        assertTrue(ranked.first().confidence > 0.6, "confidence was ${ranked.first().confidence}")
        assertTrue(ranked.first().evidence.isNotEmpty())
    }

    @Test
    fun aTextbookDidoneTopsAsDidone() {
        val f =
            FeatureVector(
                contrastRatio = 5.0,
                stressAngleDegrees = 1.0,
                hasSerif = true,
                bracketScore = 0.05,
                storeys = Storeys.DOUBLE,
                terminalStyle = TerminalStyle.ROUND,
                apertureOpenness = 0.30,
                oRoundnessExponent = 2.1,
                xHeightToCapHeightRatio = 0.70,
                widthClass = 0.55,
            )
        val ranked = rankStyleClasses(f)
        assertEquals("serif-didone", ranked.first().styleKey)
    }

    @Test
    fun aTextbookSlabTopsAsSlab() {
        val f =
            FeatureVector(
                contrastRatio = 1.1,
                stressAngleDegrees = 1.0,
                hasSerif = true,
                bracketScore = 0.02,
                storeys = Storeys.DOUBLE,
                terminalStyle = TerminalStyle.FLAT,
                apertureOpenness = 0.30,
                oRoundnessExponent = 2.5,
                xHeightToCapHeightRatio = 0.68,
                widthClass = 0.55,
            )
        val ranked = rankStyleClasses(f)
        assertEquals("slab", ranked.first().styleKey)
    }

    @Test
    fun aTextbookGaraldeTopsAsGaralde() {
        val f =
            FeatureVector(
                contrastRatio = 2.4,
                stressAngleDegrees = 30.0,
                hasSerif = true,
                bracketScore = 0.7,
                storeys = Storeys.DOUBLE,
                terminalStyle = TerminalStyle.ANGLED,
                apertureOpenness = 0.30,
                oRoundnessExponent = 2.1,
                xHeightToCapHeightRatio = 0.60,
                widthClass = 0.55,
            )
        val ranked = rankStyleClasses(f)
        assertEquals("serif-garalde", ranked.first().styleKey)
    }

    @Test
    fun aFeatureVectorWithNothingMeasuredGivesEveryClassZeroConfidence() {
        val f = FeatureVector(null, null, null, null, Storeys.UNKNOWN, TerminalStyle.UNKNOWN, null, null, null, null)
        val ranked = rankStyleClasses(f)
        for (m in ranked) {
            assertEquals(0.0, m.confidence)
        }
    }

    @Test
    fun weakClassesAreCappedBelowOne() {
        val f =
            FeatureVector(
                contrastRatio = 4.0,
                stressAngleDegrees = 0.0,
                hasSerif = false,
                bracketScore = null,
                storeys = Storeys.SINGLE,
                terminalStyle = TerminalStyle.UNKNOWN,
                apertureOpenness = 0.05,
                oRoundnessExponent = 8.0,
                xHeightToCapHeightRatio = 0.7,
                widthClass = 0.9,
            )
        val ranked = rankStyleClasses(f)
        val blackletter = ranked.first { it.styleKey == "blackletter" }
        assertTrue(blackletter.confidence <= 0.7)
    }
}
