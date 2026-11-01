// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.engine.trace.capture

import com.asoc.typewright.core.geometry.CurveSegment
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.scripts.sheets.PaperSize
import com.asoc.typewright.scripts.sheets.PrintGlyph
import com.asoc.typewright.scripts.sheets.PrintScript
import com.asoc.typewright.scripts.sheets.PrintSheets
import com.asoc.typewright.scripts.sheets.SheetLayout
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The outcome of reading one SVG file of the template pack (`scripts/templates/hyle-all-templates.zip`, `svg/<Script>/NNN_NAME.svg`):
 * a drawing someone made on a template and sent back as a file instead of photographing a sheet. The file is named by its template,
 * and that name is how the drawing is matched to its glyph ("Names are how the font matches each drawing to its character").
 */
sealed interface SvgImport {
    val fileName: String

    /** One plain sentence for the Capture screen's list. */
    val sentence: String

    /**
     * Outlines were found in the file's drawing layer. [outlines] are closed loops in font units, y up, ready for
     * [com.asoc.typewright.engine.trace.traceOutlines]; [notes] say what the reader had to assume (a viewBox moved back, a stroke ignored).
     */
    class Drawing(
        override val fileName: String,
        val glyph: PrintGlyph,
        val outlines: List<List<CurveSegment>>,
        val notes: List<String>,
    ) : SvgImport {
        override val sentence: String
            get() =
                "${glyph.glyphName}: ${outlines.size} outline${if (outlines.size == 1) "" else "s"}" +
                    if (notes.isEmpty()) "." else ". " + notes.joinToString(" ")
    }

    /** The file is a template with nothing drawn in it yet (the guides and a faint reference glyph are not a drawing). */
    class Blank(
        override val fileName: String,
        val glyph: PrintGlyph,
    ) : SvgImport {
        override val sentence: String get() = "${glyph.glyphName}: nothing drawn in this template yet."
    }

    /** The file cannot be used, and [sentence] says why in words that tell the person what to do. */
    class Unusable(
        override val fileName: String,
        val glyph: PrintGlyph?,
        override val sentence: String,
    ) : SvgImport
}

/** Reading the pack's SVG files into outlines, without a browser, a renderer or any platform code. */
object SvgTemplateImport {
    /** The pack's own margin round the guides, in font units: its files are `viewBox="-160 -(top + 120) ..."`. */
    private const val PACK_MARGIN = 160.0
    private const val PACK_VERTICAL_MARGIN = 120.0

    private val templates: List<Pair<PrintScript, PrintGlyph>> by lazy {
        PrintScript.entries.flatMap { script -> PrintSheets.glyphs(script).map { script to it } }
    }

    /** The pack's folder names, including Nastaliq's, which the pack has and this version has no sheet for. */
    private val packFolders: Set<String> by lazy { templates.map { it.second.templateFolder.substringAfterLast('/') }.toSet() + "Nastaliq" }

    /**
     * The glyph (and its script) whose template [fileName] is: a bare file name or a path, matched by folder and name, then by name alone.
     * A file in one of the pack's own folders is looked for in that folder only (Nastaliq's file names repeat Naskh's, so a Nastaliq
     * file must not be taken for a Naskh drawing); a file in any other folder, such as the designer's own, is found by its name.
     */
    fun matchTemplate(fileName: String): Pair<PrintScript, PrintGlyph>? {
        val parts = fileName.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val file = parts.lastOrNull() ?: return null
        val folder = parts.getOrNull(parts.size - 2)
        val pool =
            if (folder != null && packFolders.any { it.equals(folder, ignoreCase = true) }) {
                templates.filter {
                    it.second.templateFolder
                        .substringAfterLast('/')
                        .equals(folder, ignoreCase = true)
                }
            } else {
                templates
            }
        pool.firstOrNull { it.second.templateFileName == file }?.let { return it }
        // A renumbered or copied file keeps its name after the number: the glyph is the one whose template ends the same way.
        val stem = nameWithoutNumber(file)
        return pool.filter { nameWithoutNumber(it.second.templateFileName) == stem }.singleOrNull()
    }

    private fun nameWithoutNumber(file: String): String = file.substringBeforeLast('.').replace(Regex("^\\d+_"), "").lowercase()

    /** Reads one file: [fileName] names the template, [svg] is the file's text. */
    fun import(
        fileName: String,
        svg: String,
    ): SvgImport {
        val match = matchTemplate(fileName)
        if (match == null && fileName.replace('\\', '/').split('/').any { it.equals("Nastaliq", ignoreCase = true) }) {
            return SvgImport.Unusable(
                fileName,
                null,
                "$fileName is a Nastaliq template, and Nastaliq has no printable sheet or import in this version; Naskh does.",
            )
        }
        if (match == null) {
            return SvgImport.Unusable(
                fileName,
                null,
                "$fileName is not one of the template files, so there is no glyph to give it. " +
                    "Keep the file names the pack came with: they are how a drawing finds its glyph.",
            )
        }
        val (script, glyph) = match
        val parsed =
            try {
                SvgReader(svg, expectedViewBox(script)).read()
            } catch (e: IllegalArgumentException) {
                return SvgImport.Unusable(fileName, glyph, "${glyph.glyphName}: the SVG could not be read (${e.message}).")
            }
        if (parsed.strokesOnly > 0 && parsed.outlines.isEmpty()) {
            return SvgImport.Unusable(
                fileName,
                glyph,
                "${glyph.glyphName}: the drawing is strokes, not filled shapes. Run Outline Stroke in your drawing program and export again.",
            )
        }
        if (parsed.outlines.isEmpty()) return SvgImport.Blank(fileName, glyph)
        val notes = parsed.notes.toMutableList()
        if (parsed.strokesOnly >
            0
        ) {
            notes +=
                "${parsed.strokesOnly} stroked line${if (parsed.strokesOnly == 1) " was" else "s were"} left out: outline them first."
        }
        return SvgImport.Drawing(fileName, glyph, parsed.outlines, notes)
    }

    /** The result of reading a whole pack: [imports] in the order the files were given. */
    class PackResult(
        val imports: List<SvgImport>,
    ) {
        val drawings: List<SvgImport.Drawing> get() = imports.filterIsInstance<SvgImport.Drawing>()
        val blank: Int get() = imports.count { it is SvgImport.Blank }
        val unusable: List<SvgImport.Unusable> get() = imports.filterIsInstance<SvgImport.Unusable>()

        /** "12 drawn · 108 blank · 1 can't be used". */
        val summary: String
            get() = "${drawings.size} drawn · $blank blank" + if (unusable.isEmpty()) "" else " · ${unusable.size} can't be used"
    }

    /**
     * Reads every file of a pack ([files] maps a path or file name to its text). A glyph drawn in two files is kept once, the first,
     * and the second is reported unusable: the person is asked which one they meant instead of one silently replacing the other.
     */
    fun importPack(files: Map<String, String>): PackResult {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<SvgImport>()
        for ((name, text) in files) {
            val read = import(name, text)
            if (read is SvgImport.Drawing && !seen.add(read.glyph.glyphName)) {
                out +=
                    SvgImport.Unusable(
                        name,
                        read.glyph,
                        "${read.glyph.glyphName} is drawn in two files; the first was used. Remove one and import again if the other is the one you meant.",
                    )
            } else {
                out += read
            }
        }
        return PackResult(out)
    }

    /** The viewBox origin the pack gives [script]'s files, `(advanceLeft - 160, -(highest guide + 120))`, which tells an edited file whose viewBox moved where it was. */
    internal fun expectedViewBox(script: PrintScript): Vec2 {
        val guides = SheetLayout.of(PaperSize.A4, script).guides
        return Vec2(guides.advanceLeft - PACK_MARGIN, -(guides.lines.maxOf { it.y } + PACK_VERTICAL_MARGIN))
    }
}

/** What reading an SVG found. */
private class SvgContent(
    val outlines: List<List<CurveSegment>>,
    val strokesOnly: Int,
    val notes: List<String>,
)

private class Element(
    val name: String,
    val attributes: Map<String, String>,
    val selfClosing: Boolean,
    val closing: Boolean,
)

/** Frame of an open group: what its children inherit. */
private class Scope(
    val name: String,
    val transform: Affine,
    val fill: String?,
    val stroke: String?,
    val hidden: Boolean,
    val inDrawLayer: Boolean,
    val inIgnoredLayer: Boolean,
)

private class SvgReader(
    private val svg: String,
    private val expected: Vec2,
) {
    private val outlines = mutableListOf<List<CurveSegment>>()
    private var strokesOnly = 0
    private val notes = mutableListOf<String>()

    fun read(): SvgContent {
        val hasDrawLayer = DRAW_LAYER.containsMatchIn(svg)
        var rebase = Vec2(0.0, 0.0)
        val stack = ArrayDeque<Scope>()
        stack.addLast(Scope("#root", Affine(), null, null, hidden = false, inDrawLayer = !hasDrawLayer, inIgnoredLayer = false))
        var sawRoot = false
        for (element in elements()) {
            if (element.closing) {
                if (stack.size > 1) stack.removeLast()
                continue
            }
            val parent = stack.last()
            val style = styleOf(element.attributes)
            val id = element.attributes["id"]
            val transform = parent.transform.after(parseTransform(element.attributes["transform"]))
            if (element.name == "svg" && !sawRoot) {
                sawRoot = true
                rebase = viewBoxShift(element.attributes["viewbox"])
            }
            val ignoredName = element.name in IGNORED_ELEMENTS
            val scope =
                Scope(
                    element.name,
                    transform,
                    style["fill"] ?: parent.fill,
                    style["stroke"] ?: parent.stroke,
                    parent.hidden || style["display"] == "none" || style["visibility"] == "hidden",
                    parent.inDrawLayer || id == "DRAW_HERE",
                    parent.inIgnoredLayer || ignoredName || (id != null && id in IGNORED_LAYERS),
                )
            if (!element.selfClosing) stack.addLast(scope)
            if (scope.hidden || scope.inIgnoredLayer || !scope.inDrawLayer) continue
            if (element.name !in SHAPES) continue
            if ((scope.fill ?: "black") == "none") {
                if (scope.stroke != null && scope.stroke != "none") strokesOnly++
                continue
            }
            outlines += shapeOf(element, scope, rebase) ?: continue
        }
        return SvgContent(outlines, strokesOnly, notes)
    }

    private fun viewBoxShift(viewBox: String?): Vec2 {
        val numbers = viewBox?.let { SVG_NUMBER.findAll(it).map { m -> m.value.toDouble() }.toList() } ?: return Vec2(0.0, 0.0)
        if (numbers.size < 4) return Vec2(0.0, 0.0)
        val shift = Vec2(numbers[0] - expected.x, numbers[1] - expected.y)
        if (abs(shift.x) < 0.5 && abs(shift.y) < 0.5) return Vec2(0.0, 0.0)
        notes += "The file's viewBox starts at ${numbers[0].toInt()}, ${numbers[1].toInt()} where the pack's starts at " +
            "${expected.x.toInt()}, ${expected.y.toInt()}; the drawing was moved back by that difference."
        return shift
    }

    /** Every tag of the document in order; comments, declarations and the text between tags are skipped. */
    private fun elements(): Sequence<Element> =
        TAG.findAll(svg).mapNotNull { match ->
            val inner = match.groupValues[1].trim()
            when {
                inner.startsWith("!") || inner.startsWith("?") -> {
                    null
                }

                inner.startsWith("/") -> {
                    Element(inner.drop(1).trim().lowercase(), emptyMap(), selfClosing = false, closing = true)
                }

                else -> {
                    val selfClosing = inner.endsWith("/")
                    val body = inner.removeSuffix("/").trim()
                    val name = body.takeWhile { !it.isWhitespace() }.lowercase().substringAfter(':')
                    val attributes =
                        ATTRIBUTE
                            .findAll(body.drop(name.length))
                            .associate {
                                it.groupValues[1].lowercase().substringAfter("xlink:") to
                                    (it.groupValues[3].ifEmpty { it.groupValues[4] })
                            }
                    Element(name, attributes, selfClosing, closing = false)
                }
            }
        }

    /** The fill, stroke and display this element sets, from its attributes and then its `style` (which wins). */
    private fun styleOf(attributes: Map<String, String>): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (key in listOf("fill", "stroke", "display", "visibility")) attributes[key]?.let { out[key] = it.trim().lowercase() }
        attributes["style"]?.split(';')?.forEach { declaration ->
            val key = declaration.substringBefore(':', "").trim().lowercase()
            val value = declaration.substringAfter(':', "").trim().lowercase()
            if (key.isNotEmpty() && value.isNotEmpty()) out[key] = value
        }
        return out
    }

    private fun shapeOf(
        element: Element,
        scope: Scope,
        rebase: Vec2,
    ): List<List<CurveSegment>>? {
        val a = element.attributes

        fun number(key: String) = a[key]?.let { SVG_NUMBER.find(it)?.value?.toDoubleOrNull() } ?: 0.0
        val loops =
            when (element.name) {
                "path" -> PathParser(a["d"] ?: return null).loops()
                "polygon", "polyline" -> polygonLoop(a["points"] ?: return null)
                "rect" -> rectLoops(number("x"), number("y"), number("width"), number("height"), number("rx"), number("ry"))
                "circle" -> ellipseLoop(number("cx"), number("cy"), number("r"), number("r"))
                "ellipse" -> ellipseLoop(number("cx"), number("cy"), number("rx"), number("ry"))
                else -> return null
            }
        // The pack's coordinates are font units with y down and the baseline at 0; a font's y goes up. An element's own
        // transforms act first, in the file's own space; then the file's origin is put back where the pack has it.
        val toFont = Affine(1.0, 0.0, 0.0, -1.0, -rebase.x, rebase.y).after(scope.transform)
        return loops.map { loop -> loop.map { transformSegment(it, toFont) } }
    }

    companion object {
        val TAG = Regex("<([^<>]*)>", RegexOption.DOT_MATCHES_ALL)
        val ATTRIBUTE = Regex("([\\w:.-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
        val DRAW_LAYER = Regex("id\\s*=\\s*[\"']DRAW_HERE[\"']")
        val SHAPES = setOf("path", "polygon", "polyline", "rect", "circle", "ellipse")
        val IGNORED_LAYERS = setOf("guides", "reference_delete_me")
        val IGNORED_ELEMENTS =
            setOf("defs", "clippath", "mask", "text", "title", "desc", "metadata", "style", "symbol", "marker", "pattern")
    }
}
