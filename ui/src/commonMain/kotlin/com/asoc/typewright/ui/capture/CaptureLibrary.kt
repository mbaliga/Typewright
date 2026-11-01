// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

import com.asoc.typewright.core.geometry.Contour
import com.asoc.typewright.engine.trace.GrayscaleRaster
import com.asoc.typewright.engine.trace.RasterFrame
import com.asoc.typewright.engine.trace.capture.CaptureFailure
import com.asoc.typewright.engine.trace.capture.CaptureOptions
import com.asoc.typewright.engine.trace.capture.CaptureResult
import com.asoc.typewright.engine.trace.capture.CapturedCell
import com.asoc.typewright.engine.trace.capture.CapturedSheet
import com.asoc.typewright.engine.trace.capture.CellStatus
import com.asoc.typewright.engine.trace.capture.SheetCapture
import com.asoc.typewright.engine.trace.capture.SheetWarp
import com.asoc.typewright.project.EditResult
import com.asoc.typewright.project.MetaChange
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.capture.CaptureKind
import com.asoc.typewright.project.capture.CaptureManifest
import com.asoc.typewright.project.capture.CaptureRecord
import com.asoc.typewright.project.capture.CaptureRelabel
import com.asoc.typewright.scripts.sheets.MmRect
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One cell of a captured sheet as the Cells view and Trace see it: after any relabel, with its drawing as a small outline. */
class CellView(
    val index: Int,
    /** The glyph the template puts in this cell. */
    val templateName: String,
    /** The glyph the cell is taken to be: the template's, unless the person relabelled it. */
    val glyphName: String,
    val cell: CapturedCell,
    val relabelled: Boolean,
    val outline: InkOutline,
) {
    val status: CellStatus get() = cell.status

    /** True when the reader is not sure of this cell and the person has not settled it. */
    val needsYou: Boolean get() = !relabelled && cell.status is CellStatus.Uncertain

    val hasInk: Boolean get() = cell.status !is CellStatus.Empty && cell.raster != null
}

/** Where a stored sheet picture stands. */
sealed interface SheetState {
    data object Reading : SheetState

    class Ready(
        val sheet: CapturedSheet,
        val cells: List<CellView>,
        /** The flattened page, small. */
        val thumbnail: InkOutline,
        val pageAspect: Double,
    ) : SheetState {
        val needYou: Int get() = cells.count { it.needsYou }
        val read: Int get() = cells.count { it.hasInk && !it.needsYou }
        val empty: Int get() = cells.count { !it.hasInk }
        val range: String get() = sheet.spec.range

        /** "Sheet 1 · A–X". */
        val title: String get() = "Sheet ${sheet.identity.index + 1} · $range"

        /** "2 need you · 22 read", then "· 3 empty" where some boxes have nothing in them. */
        val summary: String
            get() =
                buildString {
                    if (needYou > 0) append("$needYou need you · ")
                    append("$read read")
                    if (empty > 0) append(" · $empty empty")
                }
    }

    /** A stored picture that can no longer be read as a sheet (its picture was damaged or the reader's limits changed). */
    data class Unreadable(
        val sentence: String,
    ) : SheetState
}

/** One SVG drawing of a batch: the file's [name], the glyph it is for, and what reading it came to. */
class SvgCell(
    val record: CaptureRecord,
    val reading: SvgReading,
) {
    val glyphName: String? get() = record.glyph
}

/** What Capture lists: one row per sheet photograph or scan, and one per batch of SVG drawings. */
sealed interface CaptureItem {
    val key: String

    class Sheet(
        val record: CaptureRecord,
        val state: SheetState,
    ) : CaptureItem {
        override val key: String get() = record.id

        /** The line under the summary: "Photographed · perspective fixed", or "From file · scan_002.png". */
        val sourceLine: String
            get() =
                when (record.kind) {
                    CaptureKind.PHOTO -> {
                        if (record.qualityOverride) "Photographed · limits overridden" else "Photographed · perspective fixed"
                    }

                    else -> {
                        "From file · ${record.name}"
                    }
                }
    }

    class SvgBatch(
        val batch: String,
        val cells: List<SvgCell>,
    ) : CaptureItem {
        override val key: String get() = batch

        val title: String get() = "SVG drawings · ${cells.size}"
        val usable: Int get() = cells.count { it.reading is SvgReading.Drawing }
        val summary: String get() = "$usable of ${cells.size} ready to fit"
        val sourceLine: String get() = "From files · start at Fit"
    }
}

/** Why a picture was not added, in words, with what can be done about it. */
class AddFailure(
    val name: String,
    val sentence: String,
    val source: CaptureSource?,
)

/** A drawing Trace can start from. */
sealed interface TraceSource {
    val glyphName: String

    /** The script of the template the drawing was made on, which says where its guides are. */
    val script: PrintScript

    /** The code point the glyph is, for a glyph the project does not have yet. */
    val codePoint: Int?

    /** Where it came from, for the header: "sheet 1 · cell 20 of 24". */
    val origin: String

    class Raster(
        override val glyphName: String,
        override val script: PrintScript,
        override val codePoint: Int?,
        override val origin: String,
        val raster: GrayscaleRaster,
        val frame: RasterFrame,
    ) : TraceSource

    /** An SVG drawing: contours already, so Trace starts at Fit. */
    class Vector(
        override val glyphName: String,
        override val script: PrintScript,
        override val codePoint: Int?,
        override val origin: String,
        val contours: List<Contour>,
    ) : TraceSource
}

/**
 * The project's captured sheets and drawings, read and ready for Capture and Trace.
 *
 * What is stored is the source picture (see [CaptureRecord]); this reads each one with the capture engine, keeps the result in
 * memory, and offers it as [items]. Adding goes through the session so it is saved with the project; a picture that cannot be read
 * as a sheet is not stored but reported in [failure], with what to do. Reading runs off the calling thread.
 */
class CaptureLibrary(
    private val session: ProjectSession,
    private val decoder: ImageDecoder,
    private val scope: CoroutineScope,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) {
    private val itemsFlow = MutableStateFlow<List<CaptureItem>>(emptyList())

    /** Every captured thing, in the order added. */
    val items: StateFlow<List<CaptureItem>> = itemsFlow.asStateFlow()

    private val busyFlow = MutableStateFlow(0)

    /** True while a picture is being read. */
    val busy: Boolean get() = busyFlow.value > 0
    val busyState: StateFlow<Int> = busyFlow.asStateFlow()

    private val failureFlow = MutableStateFlow<AddFailure?>(null)

    /** The last picture that could not be added, until it is dismissed or another is added. */
    val failure: StateFlow<AddFailure?> = failureFlow.asStateFlow()

    private val noteFlow = MutableStateFlow<String?>(null)

    /** One line about what the last add did ("Sheet 3 replaced the earlier Sheet 3"), until the next. */
    val note: StateFlow<String?> = noteFlow.asStateFlow()

    private class Parsed(
        val sheet: CapturedSheet? = null,
        val thumbnail: InkOutline = InkOutline.EMPTY,
        val aspect: Double = 1.0,
        val unreadable: String? = null,
        val svg: SvgReading? = null,
        val outlines: Map<Int, InkOutline> = emptyMap(),
    )

    // Read and written from the caller's thread and from the background reader at once, so each is an immutable map swapped
    // atomically rather than a map two threads can both change.
    private val bytesHeld = MutableStateFlow<Map<String, ByteArray>>(emptyMap())
    private val parsedHeld = MutableStateFlow<Map<String, Parsed>>(emptyMap())
    private val lock = Mutex()
    private var observing = false

    /** Starts keeping [items] in step with the project: reads what is stored and every change to it. Idempotent. */
    fun start() {
        if (observing) return
        observing = true
        scope.launch {
            session.state
                .map { it.meta.captures }
                .distinctUntilChanged()
                .collect { rebuild(it) }
        }
    }

    // ---- adding ---------------------------------------------------------------------------------

    /**
     * Reads [files] and stores the ones that read. A sheet photograph or scan must be read as one of the printed sheets (four
     * corner marks and a code); an SVG must be named as one of the pack's templates. A sheet that is already held (the same
     * script and number) is replaced by the new one, which is how a retake works. [lenient] skips the blur, light and angle
     * limits, for the camera's manual override.
     */
    suspend fun add(
        files: List<PickedFile>,
        source: CaptureSource,
        lenient: Boolean = false,
    ) {
        busyFlow.value += 1
        try {
            failureFlow.value = null
            noteFlow.value = null
            val batch = if (files.count { isSvg(it) } > 1) nextBatchId() else null
            for (file in files) {
                if (isSvg(file)) addSvg(file, batch ?: nextBatchId(), source) else addPicture(file, source, lenient)
            }
        } finally {
            busyFlow.value -= 1
        }
    }

    private suspend fun addPicture(
        file: PickedFile,
        source: CaptureSource,
        lenient: Boolean,
    ) {
        val raster = decoder.decodeGray(file.bytes)
        if (raster == null) {
            failureFlow.value =
                AddFailure(
                    file.name,
                    "“${file.name}” could not be read as a picture. Use a JPEG, PNG, GIF or BMP, or an SVG drawing.",
                    source,
                )
            return
        }
        val result = withContext(work) { SheetCapture.read(raster, CaptureOptions(enforceQualityGates = !lenient)) }
        when (result) {
            is CaptureResult.Failure -> {
                failureFlow.value = AddFailure(file.name, result.failure.sentence, source)
            }

            is CaptureResult.Success -> {
                val identity = result.sheet.identity
                val manifest = session.state.value.meta.captures
                val earlier = manifest.records.filter { it.script == identity.script.name && it.sheet == identity.index }
                val extension = extensionOf(file.name)
                val kind = if (source == CaptureSource.CAMERA) CaptureKind.PHOTO else CaptureKind.FILE
                val id = manifest.nextId()
                val record =
                    CaptureRecord(
                        id = id,
                        kind = kind,
                        name = if (kind == CaptureKind.PHOTO) "Photo" else file.name,
                        imagePath = "captures/$id$extension",
                        script = identity.script.name,
                        sheet = identity.index,
                        sheetCount = identity.count,
                        qualityOverride = lenient,
                        // A retake is the same sheet, so the cells the person has already named keep their names.
                        relabels = earlier.lastOrNull()?.relabels.orEmpty(),
                    )
                bytesHeld.update { it + (id to file.bytes) }
                val built = withContext(work) { parse(record, result.sheet, raster) }
                parsedHeld.update { it + (id to built) }
                val applied = session.update(MetaChange.AddCapture(record, file.bytes))
                if (applied !is EditResult.Applied) {
                    forget(id)
                    failureFlow.value = AddFailure(file.name, "The project could not keep this picture: ${describe(applied)}", source)
                    return
                }
                for (old in earlier) session.update(MetaChange.RemoveCapture(old.id))
                if (earlier.isNotEmpty()) {
                    noteFlow.value =
                        "Sheet ${identity.index + 1} replaced the one you had." +
                        if (record.relabels.isNotEmpty()) " The cells you renamed kept their names." else ""
                }
            }
        }
    }

    private suspend fun addSvg(
        file: PickedFile,
        batch: String,
        source: CaptureSource,
    ) {
        val template = TemplatePack.glyphForFileName(file.name)
        if (template == null) {
            failureFlow.value =
                AddFailure(
                    file.name,
                    "“${file.name}” is not named like one of the pack's templates, so Typewright cannot tell which glyph it is. " +
                        "Keep the file names the pack gave them.",
                    source,
                )
            return
        }
        val (script, glyph) = template
        val reading = SvgDrawingReader.read(file.bytes.decodeToString(), TemplatePack.frameOf(script))
        if (reading is SvgReading.Failure) {
            failureFlow.value = AddFailure(file.name, "${file.name}: ${reading.sentence}", source)
            return
        }
        val manifest = session.state.value.meta.captures
        val sameGlyph = manifest.records.filter { it.kind == CaptureKind.SVG && it.glyph == glyph.glyphName }
        val id = manifest.nextId()
        val record =
            CaptureRecord(
                id = id,
                kind = CaptureKind.SVG,
                name = file.name,
                imagePath = "captures/$id.svg",
                batch = batch,
                glyph = glyph.glyphName,
            )
        bytesHeld.update { it + (id to file.bytes) }
        parsedHeld.update { it + (id to Parsed(svg = reading)) }
        val applied = session.update(MetaChange.AddCapture(record, file.bytes))
        if (applied !is EditResult.Applied) {
            forget(id)
            failureFlow.value = AddFailure(file.name, "The project could not keep this drawing: ${describe(applied)}", source)
            return
        }
        for (old in sameGlyph) session.update(MetaChange.RemoveCapture(old.id))
    }

    /** Says that a picker could not give a picture, in the same place a picture that would not read is reported. */
    fun reportFailure(
        message: String,
        source: CaptureSource,
    ) {
        failureFlow.value = AddFailure("File", message, source)
    }

    /** Forgets that the last add failed. */
    fun dismissFailure() {
        failureFlow.value = null
    }

    // ---- changing -------------------------------------------------------------------------------

    /** Removes a sheet, or every drawing of an SVG batch, and their stored files. */
    fun remove(item: CaptureItem) {
        val ids =
            when (item) {
                is CaptureItem.Sheet -> listOf(item.record.id)
                is CaptureItem.SvgBatch -> item.cells.map { it.record.id }
            }
        for (id in ids) {
            session.update(MetaChange.RemoveCapture(id))
            forget(id)
        }
    }

    /** Says that cell [cell] of the sheet [recordId] holds [glyph]. Naming the glyph the template already has clears the relabel. */
    fun relabel(
        recordId: String,
        cell: Int,
        glyph: String,
    ) {
        val record =
            session.state.value.meta.captures.records
                .firstOrNull { it.id == recordId } ?: return
        val template =
            (
                parsedHeld.value[recordId]
                    ?.sheet
                    ?.cells
                    ?.getOrNull(cell)
            )?.glyphName
        val others = record.relabels.filterNot { it.cell == cell }
        val relabels = if (glyph == template) others else (others + CaptureRelabel(cell, glyph)).sortedBy { it.cell }
        session.update(MetaChange.UpdateCapture(record.copy(relabels = relabels)))
    }

    // ---- trace ----------------------------------------------------------------------------------

    /** The drawing to trace for [glyphName], or null if no capture holds one. The newest capture wins. */
    fun sourceFor(glyphName: String): TraceSource? = sources().lastOrNull { it.glyphName == glyphName }

    /** Every drawing Trace can start from, in capture order (a sheet's cells in cell order). */
    fun sources(): List<TraceSource> = sourcesOf(itemsFlow.value)

    // ---- building -------------------------------------------------------------------------------

    private suspend fun rebuild(manifest: CaptureManifest) {
        lock.withLock {
            // Show what is known at once, then read what is not.
            itemsFlow.value = assemble(manifest)
            val missing = manifest.records.filter { it.id !in parsedHeld.value }
            if (missing.isEmpty()) return
            busyFlow.value += 1
            try {
                for (record in missing) {
                    val read = readStored(record)
                    parsedHeld.update { it + (record.id to read) }
                    itemsFlow.value = assemble(manifest)
                }
            } finally {
                busyFlow.value -= 1
            }
        }
    }

    private suspend fun readStored(record: CaptureRecord): Parsed {
        val data =
            bytesHeld.value[record.id]
                ?: session.store.read(record.imagePath)?.also { held -> bytesHeld.update { it + (record.id to held) } }
        if (data == null) return Parsed(unreadable = "The picture for this capture is missing from the project folder.")
        if (record.kind == CaptureKind.SVG) {
            val script = record.glyph?.let { name -> TemplatePack.glyphForFileName(record.name)?.first }
            val reading = SvgDrawingReader.read(data.decodeToString(), script?.let { TemplatePack.frameOf(it) })
            return Parsed(svg = reading)
        }
        val raster = decoder.decodeGray(data) ?: return Parsed(unreadable = "This picture could not be decoded any more.")
        val result = withContext(work) { SheetCapture.read(raster, CaptureOptions(enforceQualityGates = !record.qualityOverride)) }
        return when (result) {
            is CaptureResult.Success -> withContext(work) { parse(record, result.sheet, raster) }
            is CaptureResult.Failure -> Parsed(unreadable = result.failure.sentence)
        }
    }

    private fun parse(
        record: CaptureRecord,
        sheet: CapturedSheet,
        photo: GrayscaleRaster,
    ): Parsed {
        val flat =
            SheetWarp(
                photo,
                sheet.pageToPhoto,
            ).warp(MmRect(0.0, 0.0, sheet.layout.pageWidthMm, sheet.layout.pageHeightMm), THUMB_PX_PER_MM)
        val outlines =
            sheet.cells.mapNotNull { cell -> cell.raster?.let { cell.index to InkOutline.of(it, CELL_OUTLINE_SIDE) } }.toMap()
        return Parsed(
            sheet = sheet,
            thumbnail = InkOutline.of(flat, THUMB_SIDE, thin = true),
            aspect = sheet.layout.pageWidthMm / sheet.layout.pageHeightMm,
            outlines = outlines,
        )
    }

    private fun assemble(manifest: CaptureManifest): List<CaptureItem> {
        // Each SVG batch is one row, placed where its first drawing was added.
        val batches = LinkedHashMap<String, MutableList<SvgCell>>()
        val order = ArrayList<Any>()
        for (record in manifest.records) {
            val p = parsedHeld.value[record.id]
            if (record.kind == CaptureKind.SVG) {
                val key = record.batch ?: record.id
                if (key !in batches) order += key
                batches.getOrPut(key) { ArrayList() } += SvgCell(record, p?.svg ?: SvgReading.Failure("Reading…"))
                continue
            }
            val state: SheetState =
                when {
                    p == null -> SheetState.Reading
                    p.unreadable != null -> SheetState.Unreadable(p.unreadable)
                    p.sheet != null -> SheetState.Ready(p.sheet, cellViews(record, p), p.thumbnail, p.aspect)
                    else -> SheetState.Unreadable("This capture could not be read.")
                }
            order += CaptureItem.Sheet(record, state)
        }
        return order.map { entry ->
            if (entry is CaptureItem) entry else CaptureItem.SvgBatch(entry as String, batches.getValue(entry))
        }
    }

    private fun cellViews(
        record: CaptureRecord,
        p: Parsed,
    ): List<CellView> {
        val sheet = checkNotNull(p.sheet)
        val relabels = record.relabels.associate { it.cell to it.glyph }
        return sheet.cells.map { cell ->
            val relabel = relabels[cell.index]
            CellView(
                index = cell.index,
                templateName = cell.glyphName,
                glyphName = relabel ?: cell.glyphName,
                cell = cell,
                relabelled = relabel != null,
                outline = p.outlines[cell.index] ?: InkOutline.EMPTY,
            )
        }
    }

    private fun forget(id: String) {
        bytesHeld.update { it - id }
        parsedHeld.update { it - id }
    }

    private var batchCounter = 0

    private fun nextBatchId(): String {
        batchCounter += 1
        return "batch-${session.state.value.meta.captures.records.size + 1}-$batchCounter"
    }

    private fun describe(result: EditResult): String =
        when (result) {
            is EditResult.Refused -> result.refusal.message
            else -> "it was not saved."
        }

    companion object {
        private const val THUMB_PX_PER_MM = 0.4
        private const val THUMB_SIDE = 150
        private const val CELL_OUTLINE_SIDE = 64

        private fun isSvg(file: PickedFile): Boolean =
            file.name.lowercase().endsWith(".svg") ||
                file.bytes
                    .take(SNIFF)
                    .toByteArray()
                    .decodeToString()
                    .trimStart()
                    .let { it.startsWith("<svg") || it.startsWith("<?xml") }

        private const val SNIFF = 256

        private fun extensionOf(name: String): String {
            val dot = name.lastIndexOf('.')
            val ext = if (dot >= 0) name.substring(dot).lowercase() else ""
            val normal = if (ext == ".jpeg") ".jpg" else ext
            return if (normal in CaptureManifest.IMAGE_SUFFIXES && normal != ".svg") normal else ".jpg"
        }

        /** The drawings of [items] Trace can start from. */
        fun sourcesOf(items: List<CaptureItem>): List<TraceSource> =
            items.flatMap { item ->
                when (item) {
                    is CaptureItem.Sheet -> {
                        val ready = item.state as? SheetState.Ready ?: return@flatMap emptyList()
                        ready.cells.filter { it.hasInk }.mapNotNull { cell ->
                            val raster = cell.cell.raster ?: return@mapNotNull null
                            val frame = cell.cell.frame ?: return@mapNotNull null
                            TraceSource.Raster(
                                cell.glyphName,
                                ready.sheet.identity.script,
                                TemplatePack.printGlyph(ready.sheet.identity.script, cell.glyphName)?.codePoint,
                                "sheet ${ready.sheet.identity.index + 1} · cell ${cell.index + 1} of ${ready.cells.size}",
                                raster,
                                frame,
                            )
                        }
                    }

                    is CaptureItem.SvgBatch -> {
                        item.cells.mapNotNull { cell ->
                            val drawing = cell.reading as? SvgReading.Drawing ?: return@mapNotNull null
                            val glyph = cell.glyphName ?: return@mapNotNull null
                            val (script, printGlyph) = TemplatePack.glyphForFileName(cell.record.name) ?: return@mapNotNull null
                            TraceSource.Vector(glyph, script, printGlyph.codePoint, "SVG · ${cell.record.name}", drawing.contours)
                        }
                    }
                }
            }

        /** How many different sheets of [script] [items] hold (a sheet taken twice counts once; another script's sheet 1 is not this one). */
        fun sheetsHeld(
            items: List<CaptureItem>,
            script: PrintScript,
        ): Int =
            items
                .filterIsInstance<CaptureItem.Sheet>()
                .filter { it.record.script == script.name }
                .mapNotNull { it.record.sheet }
                .toSet()
                .size

        /** How many sheets a [script] has in all. */
        fun sheetsIn(script: PrintScript): Int = PrintSheets.forScript(script).sheets.size
    }
}

/** The failure of a read as a one-line word for a row: the engine's sentence, shortened to its first sentence. */
fun CaptureFailure.headline(): String = sentence.substringBefore(". ").let { if (it.endsWith(".")) it else "$it." }
