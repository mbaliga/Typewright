package dev.aarso.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleFeatureVocabularyTest {
    @Test
    fun hasExactlyTheNineBrief84Features() {
        assertEquals(9, STYLE_FEATURE_VOCABULARY.size)
    }

    @Test
    fun everyEntryHasATermNameADefinitionAndAtLeastOneExampleGlyph() {
        for (entry in STYLE_FEATURE_VOCABULARY) {
            assertTrue(entry.termName.isNotBlank(), "blank termName")
            assertTrue(entry.plainLanguageDefinition.isNotBlank(), "blank definition for ${entry.termName}")
            assertTrue(entry.exampleGlyphs.isNotEmpty(), "no example glyphs for ${entry.termName}")
        }
    }

    @Test
    fun everyTermNameIsUnique() {
        val names = STYLE_FEATURE_VOCABULARY.map { it.termName }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun definitionsDoNotUseTheScorersOwnJargonUnexplained() {
        // Plain-language check: no entry names the underlying numeric mechanism (superellipse,
        // quartile, fence) without having already explained it in the same sentence -- a light
        // guard, not a full readability test, against the vocabulary drifting back into
        // implementation KDoc.
        for (entry in STYLE_FEATURE_VOCABULARY) {
            assertTrue(
                !entry.plainLanguageDefinition.contains("Quartiles") &&
                    !entry.plainLanguageDefinition.contains("fence"),
                "definition for ${entry.termName} leaked scorer/corpus jargon",
            )
        }
    }
}
