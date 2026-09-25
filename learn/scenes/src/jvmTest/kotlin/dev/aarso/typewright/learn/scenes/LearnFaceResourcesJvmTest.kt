// SPDX-License-Identifier: FSL-1.1-ALv2

package dev.aarso.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Proves the `data/learn-faces/` -> classpath wiring [LearnFaceResources] reads back through,
 * independent of any UI consumer — mirrors `qa/corpus`'s own `CorpusDataResourceTest` and this
 * module's own `SceneResourcesJvmTest`. Checks every one of the seventeen real faces the P6 data
 * task fetched (`docs/OPEN_QUESTIONS.md`'s "P6: Learn faces fetch" section, item 32: "the
 * seventeen actual font files"), not a sample, and checks bytes by SHA-256 against the fetch
 * script's own recorded [LearnFaceEntry.fileSha256] rather than merely "some bytes came back" —
 * a wrong-file or truncated-copy bug that happened to still produce readable bytes would pass a
 * weaker check.
 */
class LearnFaceResourcesJvmTest {
    @Test
    fun manifestIsOnTheClasspath() {
        assertNotNull(
            javaClass.getResourceAsStream("/$LEARN_FACE_MANIFEST_RESOURCE_PATH"),
            "missing from the classpath: $LEARN_FACE_MANIFEST_RESOURCE_PATH",
        )
    }

    @Test
    fun manifestNamesSeventeenFaces() {
        assertEquals(17, LearnFaceResources.allEntries().size)
    }

    @Test
    fun everyFaceHasABracketedOrPlainFilenameReadableByItsKey() {
        // Both shapes are exercised for real here: e.g. "garalde" -> "EBGaramond[wght].ttf"
        // (variable font, brackets and all) and "artdeco" -> "Limelight-Regular.ttf" (plain).
        for (entry in LearnFaceResources.allEntries()) {
            val bytes = LearnFaceResources.fontBytes(entry.key)
            assertTrue(bytes.isNotEmpty(), "${entry.key} (${entry.resourcePath}) read zero bytes")
            val expectedSha256 = entry.fileSha256
            assertNotNull(expectedSha256, "${entry.key}'s manifest entry has no file_sha256 to check against")
            assertEquals(
                expectedSha256,
                bytes.sha256Hex(),
                "${entry.key} (${entry.resourcePath}): classpath bytes don't match manifest.json's recorded SHA-256",
            )
        }
    }

    @Test
    fun unknownKeyThrowsRatherThanReturningEmptyBytes() {
        val thrown = runCatching { LearnFaceResources.fontBytes("not-a-real-key") }.exceptionOrNull()
        assertNotNull(thrown, "expected fontBytes(\"not-a-real-key\") to throw")
    }

    private fun ByteArray.sha256Hex(): String {
        val digest =
            java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(this)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
