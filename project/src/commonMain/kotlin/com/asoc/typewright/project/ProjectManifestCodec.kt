// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant

/** A master exactly as `typewright.json`'s `masters` array names it; the loader reads its `.ufo` separately. */
internal data class DecodedMaster(
    val id: String,
    val path: String,
    val styleName: String,
    val isDefault: Boolean,
)

/** One glyph's lock, as `typewright.json` records it; [approvedPath] is resolved into a [com.asoc.typewright.core.geometry.Glyph] by the loader. */
internal data class DecodedLock(
    val state: LockState,
    val origin: ApprovalOrigin,
    val approvedAt: Instant,
    val approvedPath: String,
    val episodes: List<DecodedEpisode>,
)

/** One unlock episode as recorded; the loader resolves a frozen episode's diff text from disk by its derived path. */
internal data class DecodedEpisode(
    val at: Instant,
    val cause: UnlockCause,
    val reason: String?,
    val relockedAt: Instant?,
)

/** `typewright.json`'s content, before the loader has resolved masters' `.ufo`s or locks' snapshot glyphs. */
internal data class DecodedManifest(
    val manifest: ProjectManifest,
    val masters: List<DecodedMaster>,
    val locks: Map<GlyphRef, DecodedLock>,
    val unknownKeys: JsonObject,
)

/**
 * The JSON codec for `typewright.json` (docs/PROJECT_MODEL.md §3.1): keys in schema order,
 * snake_case, 2-space indent, a trailing newline, byte-identical on every platform (all through
 * [CanonicalJson]). `masters` and `locks` are [FontState]'s, not [ProjectManifest]'s, so [encode]
 * takes the whole [ProjectState] plus the lock file stems [LockPaths.assignStems] computed.
 *
 * A lock's diff paths are never read back from the file: they are re-derived from
 * [LockPaths.diffPaths], so a decoded, then re-encoded, project always names them the same way.
 * [ProjectSession]'s loader uses those derived paths to read a frozen episode's diff text and an
 * approved snapshot's glyph.
 */
internal object ProjectManifestCodec {
    const val FORMAT: String = "typewright-project"
    const val FORMAT_VERSION: Int = 1
    const val PATH: String = "typewright.json"

    private val KNOWN_KEYS =
        setOf(
            ProjectJson.FORMAT_KEY,
            ProjectJson.FORMAT_VERSION_KEY,
            "name",
            "created",
            "brief",
            "scripts",
            "masters",
            "locks",
            "workbook",
            "comparison_fonts",
            "preferences",
        )

    fun encode(
        state: ProjectState,
        lockStems: Map<GlyphRef, String>,
    ): String {
        val manifest = state.meta.manifest
        val obj =
            buildJsonObject {
                put(ProjectJson.FORMAT_KEY, FORMAT)
                put(ProjectJson.FORMAT_VERSION_KEY, FORMAT_VERSION)
                put("name", manifest.name)
                put("created", ProjectTimestamps.format(manifest.created))
                putJsonObject("brief") { writeBrief(manifest.brief) }
                putJsonArray("scripts") { manifest.scripts.forEach { add(it) } }
                putJsonArray("masters") { state.font.masters.forEach { addJsonObject { writeMaster(it) } } }
                putJsonObject("locks") { writeLocks(state.font, lockStems) }
                putJsonObject("workbook") { writeWorkbook(manifest.workbook) }
                putJsonArray("comparison_fonts") { manifest.comparisonFonts.forEach { addJsonObject { writeComparisonFont(it) } } }
                putJsonObject("preferences") { writePreferences(manifest.preferences) }
                for ((key, value) in state.meta.unknownKeys) put(key, value)
            }
        return CanonicalJson.encode(obj)
    }

    fun decode(text: String): DecodedManifest {
        val what = PATH
        val obj = ProjectJson.parseObject(text, what)
        ProjectJson.requireFormat(obj, FORMAT, what)
        ProjectJson.formatVersion(obj, FORMAT_VERSION, what)
        val manifest =
            ProjectManifest(
                name = obj.string("name", what),
                created = ProjectTimestamps.parse(obj.string("created", what)),
                brief = readBrief(obj.jsonObj("brief", what)),
                scripts = obj.jsonArr("scripts", what).map { it.asString("$what.scripts") },
                workbook = readWorkbook(obj.jsonObj("workbook", what)),
                comparisonFonts = obj.jsonArr("comparison_fonts", what).map { readComparisonFont(it.asObject("$what.comparison_fonts")) },
                preferences = readPreferences(obj.jsonObj("preferences", what)),
            )
        val masters = obj.jsonArr("masters", what).map { readMaster(it.asObject("$what.masters")) }
        val locks = readLocks(obj.jsonObj("locks", what))
        val unknown = JsonObject(obj.entries.filterNot { it.key in KNOWN_KEYS }.associate { it.key to it.value })
        return DecodedManifest(manifest, masters, locks, unknown)
    }

    // ---- brief ------------------------------------------------------------------------------

    private fun JsonObjectBuilder.writeBrief(brief: Brief) {
        put("source", brief.source.jsonName())
        putJsonObject("intent") {
            put("model", brief.model?.jsonName())
            put("use", brief.use?.jsonName())
        }
        putJsonObject("style_class") {
            put("declared", brief.styleClass.declared)
            put("confirmed", brief.styleClass.confirmed)
        }
        put("note", brief.note)
    }

    private fun readBrief(obj: JsonObject): Brief {
        val what = "brief"
        val intent = obj.jsonObj("intent", what)
        val styleClass = obj.jsonObj("style_class", what)
        return Brief(
            source = BriefSource.fromJson(obj.string("source", what)),
            model = intent.stringOrNull("model")?.let(ContrastModel::fromJson),
            use = intent.stringOrNull("use")?.let(Use::fromJson),
            styleClass = StyleClass(declared = styleClass.stringOrNull("declared"), confirmed = styleClass.stringOrNull("confirmed")),
            note = obj.stringOrNull("note"),
        )
    }

    // ---- masters ----------------------------------------------------------------------------

    private fun JsonObjectBuilder.writeMaster(master: Master) {
        put("id", master.id)
        put("path", master.path)
        put("style_name", master.styleName)
        put("default", master.isDefault)
    }

    private fun readMaster(obj: JsonObject): DecodedMaster {
        val what = "masters"
        return DecodedMaster(
            id = obj.string("id", what),
            path = obj.string("path", what),
            styleName = obj.string("style_name", what),
            isDefault = obj.bool("default", what),
        )
    }

    // ---- locks --------------------------------------------------------------------------------

    private fun JsonObjectBuilder.writeLocks(
        font: FontState,
        lockStems: Map<GlyphRef, String>,
    ) {
        for (master in font.masters) {
            val glyphOrder = master.ufo.glyphs.map { it.name }
            val refs =
                font.locks.keys
                    .filter { it.master == master.id }
                    .sortedBy { ref -> glyphOrder.indexOf(ref.glyph).let { if (it < 0) Int.MAX_VALUE else it } }
            if (refs.isEmpty()) continue
            putJsonObject(master.id) {
                for (ref in refs) {
                    val lock = font.locks.getValue(ref)
                    val stem = lockStems.getValue(ref)
                    putJsonObject(ref.glyph) { writeLock(master.id, lock, stem) }
                }
            }
        }
    }

    private fun JsonObjectBuilder.writeLock(
        master: String,
        lock: GlyphLock,
        stem: String,
    ) {
        put("state", lock.state.jsonName())
        put("origin", lock.origin.jsonName())
        put("approved_at", ProjectTimestamps.format(lock.approvedAt))
        put("approved", LockPaths.approvedPath(master, stem))
        val diffPaths = LockPaths.diffPaths(master, stem, lock.episodes)
        putJsonArray("unlocks") {
            for ((episode, diffPath) in lock.episodes.zip(diffPaths)) {
                addJsonObject {
                    put("at", ProjectTimestamps.format(episode.at))
                    put("cause", episode.cause.jsonName())
                    put("reason", episode.reason)
                    put("relocked_at", episode.relockedAt?.let(ProjectTimestamps::format))
                    put("diff", diffPath)
                }
            }
        }
    }

    private fun readLocks(obj: JsonObject): Map<GlyphRef, DecodedLock> {
        val result = LinkedHashMap<GlyphRef, DecodedLock>()
        for ((master, mastersValue) in obj) {
            val glyphsObj = mastersValue.asObject("locks.$master")
            for ((glyph, lockValue) in glyphsObj) {
                val lockObj = lockValue.asObject("locks.$master.$glyph")
                val what = "locks.$master.$glyph"
                val episodes =
                    lockObj.jsonArr("unlocks", what).map { episodeElement ->
                        val episodeObj = episodeElement.asObject("$what.unlocks")
                        DecodedEpisode(
                            at = ProjectTimestamps.parse(episodeObj.string("at", "$what.unlocks")),
                            cause = UnlockCause.fromJson(episodeObj.string("cause", "$what.unlocks")),
                            reason = episodeObj.stringOrNull("reason"),
                            relockedAt = episodeObj.stringOrNull("relocked_at")?.let(ProjectTimestamps::parse),
                        )
                    }
                result[GlyphRef(master, glyph)] =
                    DecodedLock(
                        state = LockState.fromJson(lockObj.string("state", what)),
                        origin = ApprovalOrigin.fromJson(lockObj.string("origin", what)),
                        approvedAt = ProjectTimestamps.parse(lockObj.string("approved_at", what)),
                        approvedPath = lockObj.string("approved", what),
                        episodes = episodes,
                    )
            }
        }
        return result
    }

    // ---- workbook ---------------------------------------------------------------------------

    private fun JsonObjectBuilder.writeWorkbook(workbook: Map<String, WorkbookRecord>) {
        for ((key, record) in workbook) {
            putJsonObject(key) {
                putJsonArray("confirmed_tasks") { record.confirmedTasks.sorted().forEach { add(it) } }
                put("lessons", WorkbookLessonsCodec.path(key))
            }
        }
    }

    private fun readWorkbook(obj: JsonObject): Map<String, WorkbookRecord> {
        val result = LinkedHashMap<String, WorkbookRecord>()
        for ((key, value) in obj) {
            val record = value.asObject("workbook.$key")
            val tasks = record.jsonArr("confirmed_tasks", "workbook.$key").map { it.jsonPrimitiveInt("workbook.$key.confirmed_tasks") }
            result[key] = WorkbookRecord(tasks.toSet())
        }
        return result
    }

    // ---- comparison fonts ---------------------------------------------------------------------

    private fun JsonObjectBuilder.writeComparisonFont(font: ComparisonFont) {
        put("family", font.family)
        put("source", font.source.jsonName())
        put("slug", font.slug)
        put("path", font.path)
    }

    private fun readComparisonFont(obj: JsonObject): ComparisonFont {
        val what = "comparison_fonts"
        return ComparisonFont(
            family = obj.string("family", what),
            source = ComparisonSource.fromJson(obj.string("source", what)),
            slug = obj.stringOrNull("slug"),
            path = obj.stringOrNull("path"),
        )
    }

    // ---- preferences ------------------------------------------------------------------------

    private fun JsonObjectBuilder.writePreferences(preferences: ProjectPreferences) {
        putJsonArray("space_control_strings") { preferences.spaceControlStrings.forEach { add(it) } }
        put("overlay_word", preferences.overlayWord)
        put("specimen_text", preferences.specimenText)
    }

    private fun readPreferences(obj: JsonObject): ProjectPreferences {
        val what = "preferences"
        return ProjectPreferences(
            spaceControlStrings = obj.jsonArr("space_control_strings", what).map { it.asString("$what.space_control_strings") },
            overlayWord = obj.stringOrNull("overlay_word"),
            specimenText = obj.stringOrNull("specimen_text"),
        )
    }

    // ---- small JSON reading helpers -----------------------------------------------------------

    private fun JsonObject.string(
        key: String,
        where: String,
    ): String {
        val value = this[key] as? JsonPrimitive
        require(value != null && value.isString) { "$where needs a string \"$key\"" }
        return value.content
    }

    private fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(
        key: String,
        where: String,
    ): Boolean {
        val value = this[key] as? JsonPrimitive
        require(value != null && !value.isString) { "$where needs a boolean \"$key\"" }
        return value.boolean
    }

    private fun JsonObject.jsonObj(
        key: String,
        where: String,
    ): JsonObject = this[key] as? JsonObject ?: throw IllegalArgumentException("$where needs an object \"$key\"")

    private fun JsonObject.jsonArr(
        key: String,
        where: String,
    ): JsonArray = this[key] as? JsonArray ?: throw IllegalArgumentException("$where needs an array \"$key\"")

    private fun JsonElement.asString(where: String): String {
        val value = this as? JsonPrimitive
        require(value != null && value.isString) { "$where holds a non-string value" }
        return value.content
    }

    private fun JsonElement.asObject(where: String): JsonObject =
        this as? JsonObject ?: throw IllegalArgumentException("$where holds a non-object value")

    private fun JsonElement.jsonPrimitiveInt(where: String): Int {
        val value = this as? JsonPrimitive
        val number = if (value != null && !value.isString) value.intOrNull else null
        require(number != null) { "$where must be an integer" }
        return number
    }
}

private fun BriefSource.jsonName(): String =
    when (this) {
        BriefSource.PAPER -> "paper"
        BriefSource.FONT -> "font"
        BriefSource.BLANK -> "blank"
    }

private fun BriefSource.Companion.fromJson(value: String): BriefSource =
    when (value) {
        "paper" -> BriefSource.PAPER
        "font" -> BriefSource.FONT
        "blank" -> BriefSource.BLANK
        else -> throw IllegalArgumentException("brief.source must be \"paper\", \"font\" or \"blank\"; got \"$value\"")
    }

private fun ContrastModel.jsonName(): String =
    when (this) {
        ContrastModel.SERIF -> "serif"
        ContrastModel.SANS -> "sans"
    }

private fun ContrastModel.Companion.fromJson(value: String): ContrastModel =
    when (value) {
        "serif" -> ContrastModel.SERIF
        "sans" -> ContrastModel.SANS
        else -> throw IllegalArgumentException("brief.intent.model must be \"serif\" or \"sans\"; got \"$value\"")
    }

private fun Use.jsonName(): String =
    when (this) {
        Use.TEXT -> "text"
        Use.DISPLAY -> "display"
    }

private fun Use.Companion.fromJson(value: String): Use =
    when (value) {
        "text" -> Use.TEXT
        "display" -> Use.DISPLAY
        else -> throw IllegalArgumentException("brief.intent.use must be \"text\" or \"display\"; got \"$value\"")
    }

private fun LockState.jsonName(): String =
    when (this) {
        LockState.LOCKED -> "locked"
        LockState.UNLOCKED -> "unlocked"
    }

private fun LockState.Companion.fromJson(value: String): LockState =
    when (value) {
        "locked" -> LockState.LOCKED
        "unlocked" -> LockState.UNLOCKED
        else -> throw IllegalArgumentException("locks.*.state must be \"locked\" or \"unlocked\"; got \"$value\"")
    }

private fun ApprovalOrigin.jsonName(): String =
    when (this) {
        ApprovalOrigin.TRACE -> "trace"
        ApprovalOrigin.DRAW -> "draw"
        ApprovalOrigin.IMPORT -> "import"
    }

private fun ApprovalOrigin.Companion.fromJson(value: String): ApprovalOrigin =
    when (value) {
        "trace" -> ApprovalOrigin.TRACE
        "draw" -> ApprovalOrigin.DRAW
        "import" -> ApprovalOrigin.IMPORT
        else -> throw IllegalArgumentException("locks.*.origin must be \"trace\", \"draw\" or \"import\"; got \"$value\"")
    }

private fun UnlockCause.jsonName(): String =
    when (this) {
        UnlockCause.USER -> "user"
        UnlockCause.EXTERNAL_EDIT -> "external-edit"
    }

private fun UnlockCause.Companion.fromJson(value: String): UnlockCause =
    when (value) {
        "user" -> UnlockCause.USER
        "external-edit" -> UnlockCause.EXTERNAL_EDIT
        else -> throw IllegalArgumentException("locks.*.unlocks[].cause must be \"user\" or \"external-edit\"; got \"$value\"")
    }

private fun ComparisonSource.jsonName(): String =
    when (this) {
        ComparisonSource.BUNDLED -> "bundled"
        ComparisonSource.GOOGLE_FONTS -> "google-fonts"
        ComparisonSource.FILE -> "file"
    }

private fun ComparisonSource.Companion.fromJson(value: String): ComparisonSource =
    when (value) {
        "bundled" -> ComparisonSource.BUNDLED

        "google-fonts" -> ComparisonSource.GOOGLE_FONTS

        "file" -> ComparisonSource.FILE

        else -> throw IllegalArgumentException(
            "comparison_fonts[].source must be \"bundled\", \"google-fonts\" or \"file\"; got \"$value\"",
        )
    }
