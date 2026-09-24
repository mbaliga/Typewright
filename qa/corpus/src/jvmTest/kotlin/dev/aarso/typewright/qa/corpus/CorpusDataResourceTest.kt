package dev.aarso.typewright.qa.corpus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Proves the data/ wiring only; the loader arrives with P1. */
class CorpusDataResourceTest {
    @Test
    fun latinPackIsOnTheClasspath() {
        val stream = assertNotNull(javaClass.getResourceAsStream("/typewright/corpus/node-economy-latin.json"))
        stream.use { assertEquals('{'.code, it.read()) }
    }
}
