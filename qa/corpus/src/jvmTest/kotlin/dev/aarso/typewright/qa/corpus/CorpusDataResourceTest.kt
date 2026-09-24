package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Proves the data/ -> classpath resource wiring on its own, independent of the P1 loader
 * (CorpusResources.kt / CorpusLoader.kt), which now reads this same path.
 */
class CorpusDataResourceTest {
    @Test
    fun latinPackIsOnTheClasspath() {
        val stream = assertNotNull(javaClass.getResourceAsStream("/typewright/corpus/node-economy-latin.json"))
        stream.use { assertEquals('{'.code, it.read()) }
    }

    @Test
    fun devanagariPackIsOnTheClasspath() {
        val stream = assertNotNull(javaClass.getResourceAsStream("/typewright/corpus/node-economy-devanagari.json"))
        stream.use { assertEquals('{'.code, it.read()) }
    }

    @Test
    fun kanaPackIsOnTheClasspath() {
        val stream = assertNotNull(javaClass.getResourceAsStream("/typewright/corpus/node-economy-kana.json"))
        stream.use { assertEquals('{'.code, it.read()) }
    }
}
