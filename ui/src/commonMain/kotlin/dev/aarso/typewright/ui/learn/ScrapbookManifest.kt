package dev.aarso.typewright.ui.learn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A scrapbook pin's kind: this task's own data-model instructions name exactly these two --
 * a photo/scan/pattern swatch, or a written note. The finer distinctions
 * `ui/typewright-explorer.html`'s own `#ln-scrap` captions show ("photo", "Devanagari", "scan",
 * "drawn", "saved", ...) are not separate [kind]s; they live in [ScrapbookPin.captionSource], a
 * free label. [kind] only decides how [ScrapbookTab] renders the pin's own image block: a
 * coloured/patterned placeholder for [PHOTO] (never a real photo -- none exists to load, and
 * CLAUDE.md law 4 rules out faking one), or the pin's own [ScrapbookPin.noteText] for [NOTE].
 */
@Serializable
enum class ScrapbookPinKind {
    @SerialName("photo")
    PHOTO,

    @SerialName("note")
    NOTE,
}

/**
 * One pin on the Scrapbook tab (`ui/typewright-explorer.html`'s `#ln-scrap`, one `.pin`):
 * [captionTitle] and [captionSource] are its `.cap b`/`.cap span`; [imagePath] is where a real
 * photo would live on disk, relative to the *project* directory (brief section 11: a project's
 * `scrapbook/` holds "files in the project" plus this manifest) -- always `null` today, since no
 * current-project flow is wired into `ui` yet (see [SampleScrapbook]'s own KDoc) and no pin this
 * build creates has a real image to point at; [noteText] is a [NOTE] pin's own written content,
 * `null` for a [PHOTO] pin; [rotationDegrees] is the small "hand-pinned" tilt
 * [ScrapbookTab] applies to the whole pin card, authored here (a hand-tuned manifest value) or,
 * for a pin [ScrapbookTab] itself creates through "+ photo"/"+ note", produced once by
 * [stablePinRotationDegrees] and then stored, never recomputed; [drivesDesign] is the explorer's
 * own `.pinmark` violet dot -- CLAUDE.md law 8's "selected/driving" meaning for violet, not
 * decoration, so it renders only on pins this flag marks true, matching `#ln-scrap`'s own three
 * `.pinmark` pins against its own "3 driving the design" header count exactly.
 */
@Serializable
data class ScrapbookPin(
    val id: String,
    val kind: ScrapbookPinKind,
    @SerialName("caption_title") val captionTitle: String,
    @SerialName("caption_source") val captionSource: String,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("note_text") val noteText: String? = null,
    @SerialName("rotation_degrees") val rotationDegrees: Double = 0.0,
    @SerialName("drives_design") val drivesDesign: Boolean = false,
)

/**
 * A scrapbook's full contents: plain [pins], newest/most-recently-pinned last (append order --
 * [ScrapbookTab]'s own "+ photo"/"+ note" simply appends). [drivingCount] backs the explorer's own
 * `.lbl` header, `"$n pins · $d driving the design"` (`#ln-scrap`'s own `<span class="lbl">14 pins
 * · 3 driving the design</span>`).
 *
 * **File format (CLAUDE.md law 7, brief section 11).** One JSON file, [ScrapbookManifestCodec]'s
 * own `encode`/`decode`, meant to live at a project's own `scrapbook/manifest.json` -- the same
 * "one small file naming what is in the directory" shape `data/learn-faces/manifest.json` already
 * uses for this app's own fetched-face data (its `faces` map keyed by slug; this file is a flat
 * `pins` array instead, since a pin has no natural stable short key the way a font family does),
 * snake_case field names to match that same file's own convention for a person hand-editing plain
 * JSON. This is deliberately *not* modelled on `core-font`'s `UfoProject`/`writeUfoProject`
 * (a whole directory reduced to a `path -> content` map of many files: `metainfo.plist`,
 * `fontinfo.plist`, one `.glif` per glyph, ...) -- a scrapbook is not a UFO, has no per-item
 * sub-format worth a whole file each (a pin's own image, when a real one exists, is just a plain
 * file under `scrapbook/` that [ScrapbookPin.imagePath] names; the manifest itself is the only
 * generated/parsed artefact), and `ui` has no filesystem-writing job at all yet -- there is no
 * current-project flow to write *to* (see [SampleScrapbook]'s own KDoc for the same gap). One
 * JSON file mirroring `learn-faces`' own manifest shape is the smaller, already-precedented
 * choice; `writeUfoProject`'s "many files as a map" shape is there if a real per-pin sub-format
 * (e.g. a saved annotation overlay per photo, brief section 4 "Annotations") ever needs it.
 */
@Serializable
data class ScrapbookManifest(
    val pins: List<ScrapbookPin> = emptyList(),
) {
    val drivingCount: Int get() = pins.count { it.drivesDesign }
}

private val scrapbookJson = Json { ignoreUnknownKeys = true }
private val scrapbookJsonPretty =
    Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

/** [ScrapbookManifest]'s own JSON codec -- the project's plain `scrapbook/manifest.json` file, exactly as [ScrapbookManifest]'s own KDoc describes. */
object ScrapbookManifestCodec {
    /** `manifest.json`'s own bytes for [manifest], pretty-printed (a person opens and hand-edits this file -- CLAUDE.md law 7). */
    fun encode(manifest: ScrapbookManifest): String = scrapbookJsonPretty.encodeToString(ScrapbookManifest.serializer(), manifest)

    /** Parses a `manifest.json`'s text back into [ScrapbookManifest]. Unknown keys are ignored, the same forward-compatibility [scrapbookJson] shares with `data/learn-faces/manifest.json`'s own loader. */
    fun decode(text: String): ScrapbookManifest = scrapbookJson.decodeFromString(ScrapbookManifest.serializer(), text)
}

/** The small range [stablePinRotationDegrees] draws from, degrees either side of upright -- enough to read as "hand-pinned", not so much a caption becomes hard to read. */
const val PIN_ROTATION_RANGE_DEGREES: Double = 4.0

/**
 * A small, stable "hand-pinned" tilt for a pin identified by [id]: deterministic, so the exact
 * same [id] always yields the exact same degrees -- across recompositions, across a re-open of
 * the same manifest, on every platform (no floating hash-order or locale dependency: [stableHashCode]
 * is plain arithmetic over UTF-16 code units). Not [kotlin.random.Random]: a real RNG advances or
 * reseeds across calls, which is exactly the "re-randomized every recomposition" failure mode
 * this task's own instructions rule out for the pinned look. [ScrapbookTab] calls this once, when
 * a "+ photo"/"+ note" pin is first created, and stores the result in that pin's own
 * [ScrapbookPin.rotationDegrees] from then on -- an authored manifest pin instead carries whatever
 * rotation its own file says, which need not equal this function's output for that pin's [id].
 */
fun stablePinRotationDegrees(id: String): Double {
    val fraction = (stableHashCode(id) % 1000) / 1000.0 // 0.0 until 1.0, stable for this id
    return (fraction * 2.0 - 1.0) * PIN_ROTATION_RANGE_DEGREES
}

/** A small, portable, non-negative string hash (plain polynomial rolling hash, base 31) -- used wherever this file needs a deterministic-per-id number ([stablePinRotationDegrees]; [ScrapbookTab]'s own pattern-variant pick). Not [String.hashCode]: that is documented to be JVM-specific in general, and this value must agree across every KMP target. */
internal fun stableHashCode(id: String): Int {
    var hash = 0
    for (unit in id) hash = (hash * 31 + unit.code) and 0x7fffffff
    return hash
}

/**
 * A small, honestly-labelled **sample** scrapbook -- not a real project's own scrapbook. There is
 * no current-project flow wired into `ui` yet (the same gap the sibling Anatomy Lens task already
 * disclosed for its own "project" stand-in, `HyleDecoProjectFontBytes`'s own KDoc), so
 * [ScrapbookTab] renders this fixed four-pin manifest rather than reading anything real. Reuses
 * `ui/typewright-explorer.html`'s own `#ln-scrap` worked-example captions, kinds and
 * `.pinmark`-vs-not pattern (CLAUDE.md law 6's spirit -- this is data, not look, so law 6 does not
 * strictly bind it, but matching it keeps this sample recognisably "the explorer's own board").
 * The explorer's own board has 14 pins and 3 pinmarks; this sample keeps 4 of its actual entries
 * (three photo pins that carry the explorer's own `.pinmark`, one note pin that does not) rather
 * than inventing ten more just to hit "14" -- so the header reads `"4 pins · 3 driving the
 * design"`, the same real proportion, honestly sized down to a sample. The three photo pins' own
 * ids are chosen (among otherwise-equivalent readable spellings) so [patternVariantForPinId]
 * lands on a different [PinPatternVariant] for each -- a real screenshot showing all three the
 * *same* pattern, purely by hash coincidence on the first spelling tried, is what prompted
 * picking these; nothing about [stablePinRotationDegrees] or [patternVariantForPinId] themselves
 * changed to make this true (see `docs/OPEN_QUESTIONS.md`'s Scrapbook entry).
 */
object SampleScrapbook {
    val MANIFEST: ScrapbookManifest =
        ScrapbookManifest(
            pins =
                listOf(
                    ScrapbookPin(
                        id = "signage-charminar",
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "Signage · Charminar",
                        captionSource = "photo",
                        drivesDesign = true,
                        rotationDegrees = stablePinRotationDegrees("signage-charminar"),
                    ),
                    ScrapbookPin(
                        id = "primer-1912-scan",
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "1912 primer · scan",
                        captionSource = "Devanagari",
                        drivesDesign = true,
                        rotationDegrees = stablePinRotationDegrees("primer-1912-scan"),
                    ),
                    ScrapbookPin(
                        id = "controls-sketch",
                        kind = ScrapbookPinKind.PHOTO,
                        captionTitle = "Sketch · controls",
                        captionSource = "drawn",
                        drivesDesign = true,
                        rotationDegrees = stablePinRotationDegrees("controls-sketch"),
                    ),
                    ScrapbookPin(
                        id = "reflection-task-3",
                        kind = ScrapbookPinKind.NOTE,
                        captionTitle = "Reflection · Task 3",
                        captionSource = "note",
                        noteText = "Round ends everywhere, or nowhere. Decide before the s.",
                        drivesDesign = false,
                        rotationDegrees = stablePinRotationDegrees("reflection-task-3"),
                    ),
                ),
        )
}
