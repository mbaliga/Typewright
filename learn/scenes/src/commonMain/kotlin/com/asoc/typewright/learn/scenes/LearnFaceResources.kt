// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One entry of `data/learn-faces/manifest.json`'s `faces` map, restricted to the fields this
 * module's own callers actually use. The manifest has many more per-face fields (designer,
 * category, every source URL, licence bookkeeping — see `docs/OPEN_QUESTIONS.md`'s "P6: Learn
 * faces fetch" section for what they mean); [kotlinx.serialization.json.JsonBuilder.ignoreUnknownKeys]
 * is on below precisely so this data class can stay this small without a parse failure the day
 * the fetch script adds another field.
 *
 * @property key the short id a [Scene]'s own [FaceRef.key] and [LearnFaceResources.entry] key by,
 *   e.g. `"garalde"`, `"transitional"` — the same strings `docs/LESSONS_SCAFFOLD.md` section 2's
 *   era table names.
 * @property family the real family name, e.g. `"EB Garamond"`.
 * @property slug the manifest's own per-face directory name under `data/learn-faces/`, e.g.
 *   `"ebgaramond"` — half of this entry's resource path, alongside [file].
 * @property file the font file's own name within that directory, e.g. `"EBGaramond[wght].ttf"`
 *   (several of the seventeen are variable fonts and carry their axis tags in the filename, `[`
 *   and `]` included — see [readLearnFaceResourceBytes]'s own KDoc for why that is safe here).
 * @property fileSha256 the fetch script's own recorded SHA-256 of [file]'s bytes
 *   (`manifest.json`'s `file_sha256`), used by this module's own jvm test (a real
 *   [java.security.MessageDigest] is only conveniently on hand there) to prove the Sync task
 *   copied the exact same bytes through to the classpath, not just a same-named file.
 * @property fileSizeBytes [file]'s own byte count (`manifest.json`'s `file_size_bytes`), used the
 *   same way by this module's own wasmJs test, which has no SHA-256 implementation in scope
 *   (commonMain pulls none in; adding a whole hash library just for one integrity check felt
 *   like the wrong trade against a plain size check that already catches a truncated or
 *   wrong-file copy).
 */
@Serializable
public data class LearnFaceEntry(
    val key: String,
    val family: String,
    val slug: String,
    val file: String,
    val role: String,
    val licence: String? = null,
    @SerialName("file_sha256") val fileSha256: String? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Long? = null,
) {
    /** This entry's own path under this module's `typewright/learn-faces/` resource root. */
    val resourcePath: String get() = "$LEARN_FACE_RESOURCE_DIR/$slug/$file"
}

/** Just the `faces` map — every other top-level `manifest.json` field is fetch-script bookkeeping this module has no use for. */
@Serializable
internal data class LearnFaceManifestFile(
    val faces: Map<String, LearnFaceEntry>,
)

/** The directory `LearnFaceResources.kt` (`build.gradle.kts`'s `syncLearnFaceData` task) copies `data/learn-faces/` into, relative to this module's resources root. */
internal const val LEARN_FACE_RESOURCE_DIR = "typewright/learn-faces"

/** Path, relative to this module's resources root, of the synced `manifest.json`. */
internal const val LEARN_FACE_MANIFEST_RESOURCE_PATH = "$LEARN_FACE_RESOURCE_DIR/manifest.json"

private val manifestJson = Json { ignoreUnknownKeys = true }

/**
 * The real faces `data/learn-faces/manifest.json` names, and the real bytes of their font
 * files — the `.ttf` files under `data/learn-faces/`, seventeen real OFL/Apache faces fetched at build time
 * (`docs/OPEN_QUESTIONS.md`'s "P6: Learn faces fetch" section), synced into this module's own
 * resources by `build.gradle.kts`'s `syncLearnFaceData` task, the exact same Sync-task pattern
 * `qa/corpus/build.gradle.kts`'s `syncCorpusData` already established for
 * `data/node-economy-*.json`.
 *
 * The manifest text itself is read through [readSceneResourceText] ([SceneResources.kt]'s own
 * mechanism, generic over any resource path, not scene-specific) — the same jvm-classpath /
 * wasmJs-Node split applies unchanged. The font *bytes*, unlike scene YAML or the manifest's own
 * JSON, cannot go through that function: it decodes its stream as UTF-8 text, and a `.ttf` is
 * binary — decoding it as text and re-encoding would corrupt every byte sequence that is not
 * valid UTF-8, silently. [readLearnFaceResourceBytes] is this module's own binary equivalent,
 * `expect`/`actual` for the same reason (`SceneResources.kt`'s own KDoc: Kotlin/Wasm has no JVM
 * classpath).
 */
public object LearnFaceResources {
    private val manifest: Map<String, LearnFaceEntry> by lazy {
        manifestJson.decodeFromString<LearnFaceManifestFile>(readSceneResourceText(LEARN_FACE_MANIFEST_RESOURCE_PATH)).faces
    }

    /** [key] looked up in `data/learn-faces/manifest.json`'s `faces` map, or null if unknown. */
    public fun entry(key: String): LearnFaceEntry? = manifest[key]

    /** Every face the manifest names, `lineages-onstage` and `exercise-bank` roles both. */
    public fun allEntries(): Collection<LearnFaceEntry> = manifest.values

    /**
     * [key]'s own real font file, read as bytes. Throws [NoSuchElementException] for a [key] not
     * in the manifest — every caller of this function already has the key from either
     * `data/learn-faces/manifest.json` directly or a [Scene]'s own [FaceRef.key], both of which
     * this module's own tests keep in sync with each other, so an unknown key here is a real bug
     * to surface loudly rather than paper over with a null.
     */
    public fun fontBytes(key: String): ByteArray {
        val face = entry(key) ?: throw NoSuchElementException("Unknown learn-face key: $key (not in data/learn-faces/manifest.json)")
        return readLearnFaceResourceBytes(face.resourcePath)
    }
}

/**
 * Reads [resourcePath] (one of [LearnFaceEntry.resourcePath]) as raw bytes — see
 * [LearnFaceResources]'s own KDoc for why this cannot reuse [readSceneResourceText].
 *
 * A filename like `EBGaramond[wght].ttf` carries a literal `[`/`]` (variable-font axis tags).
 * The jvm actual (classpath [Class.getResourceAsStream]) treats the whole path as a literal
 * string, so brackets are inert there. The wasmJs actual builds a `file://` URL from this path
 * with `new URL(resourcePath, import.meta.url)`, and the WHATWG URL parser percent-encodes `[`
 * and `]` in the path component (they are not valid literal URL characters); Node's own
 * `fs.readFileSync` accepts a `URL` and decodes that percent-encoding back to the real filename
 * before opening it, so the round trip is exact — verified by this module's own
 * `LearnFaceResourcesJvmTest`/wasmJs test, both of which check the read bytes' SHA-256 against
 * [LearnFaceEntry.fileSha256] rather than merely "a file was read".
 */
internal expect fun readLearnFaceResourceBytes(resourcePath: String): ByteArray
