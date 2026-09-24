package dev.aarso.typewright.learn.scenes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The wasmJs-Node half of [LearnFaceResourcesJvmTest]: proves
 * [LearnFaceResources.wasmJs.kt][readLearnFaceResourceBytes]'s own base64-over-`fs.readFileSync`
 * path (this file's own KDoc explains why base64, not a typed array) actually round-trips real
 * bytes under Node, not just that it compiles against the wasmJs klib. Checked against
 * [LearnFaceEntry.fileSizeBytes] rather than a SHA-256 (see that property's own KDoc for why);
 * `:learn:scenes:wasmJsNodeTest` is what runs this.
 */
class LearnFaceResourcesWasmJsTest {
    @Test
    fun everyFaceReadsItsFullByteCountUnderNode() {
        for (entry in LearnFaceResources.allEntries()) {
            val bytes = LearnFaceResources.fontBytes(entry.key)
            assertTrue(bytes.isNotEmpty(), "${entry.key} (${entry.resourcePath}) read zero bytes under Node")
            val expectedSize = entry.fileSizeBytes
            assertNotNull(expectedSize, "${entry.key}'s manifest entry has no file_size_bytes to check against")
            assertEquals(
                expectedSize,
                bytes.size.toLong(),
                "${entry.key} (${entry.resourcePath}): Node-read byte count doesn't match manifest.json's recorded file_size_bytes",
            )
        }
    }

    @Test
    fun aBracketedVariableFontFilenameRoundTripsThroughTheUrlPercentEncoding() {
        // "garalde" -> EBGaramond[wght].ttf: this file's own KDoc names exactly this case.
        val entry = assertNotNull(LearnFaceResources.entry("garalde"))
        assertTrue(entry.file.contains('['), "fixture assumption broke: ${entry.file} no longer has brackets")
        val bytes = LearnFaceResources.fontBytes("garalde")
        assertEquals(entry.fileSizeBytes, bytes.size.toLong())
    }
}
