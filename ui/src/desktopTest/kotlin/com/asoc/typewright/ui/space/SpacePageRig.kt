// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.asoc.typewright.compile.FeaChecker
import com.asoc.typewright.compile.UnavailableFeaChecker
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.ProjectSession
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.draw.DrawSheetRig
import com.asoc.typewright.ui.draw.workspaceWith
import com.asoc.typewright.ui.project.LocalProjectWorkspace
import com.asoc.typewright.ui.project.ProjectFont
import com.asoc.typewright.ui.puck.PuckPinState
import com.asoc.typewright.ui.puck.PuckUiState
import com.asoc.typewright.ui.shell.GlyphNav
import com.asoc.typewright.ui.shell.LocalGlyphNav
import com.asoc.typewright.ui.shell.LocalSectionNav
import com.asoc.typewright.ui.shell.SectionNav
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import com.asoc.typewright.ui.tokens.toColor
import kotlin.math.abs

/**
 * The Space page alone (no sheet under it) over a real project, driven frame by frame with the pointer and
 * keyboard of the Draw rig. [state] is the page's own state; [session] the open project; [opened] counts how often
 * the page asked for the command palette; [selections] is every glyph the page told the router to make current.
 */
internal class SpacePageRig(
    val rig: DrawSheetRig,
    val state: SpaceRoomState,
    val session: ProjectSession,
    val texture: CanvasTexture,
    val widthDp: Double,
    val heightDp: Double,
    val selections: MutableList<String>,
    val palette: IntArray,
) {
    val master = "regular"

    fun font(): ProjectFont =
        ProjectFont(
            session.state.value.font
                .master(master)!!,
        )

    /** Changes the page's state from the test, and lets the page see the change before the next frame. */
    fun change(block: SpaceRoomState.() -> Unit) {
        state.block()
        Snapshot.sendApplyNotifications()
        rig.frames(8)
    }

    /** The control string as the page sets it now. */
    fun string(text: String = ControlStrings.WORKBOOK.first()): SpaceString = SpaceString.set(font(), text)

    /** The page's content column: the whole width on a phone, at most 720 dp centred on a wide screen, less its gutters on each side. */
    private val columnDp: Double get() = minOf(widthDp, CONTENT_MAX)

    /** Where the row's left edge is and how many dp each font unit takes, as the page lays the string out. */
    fun row(text: String = ControlStrings.WORKBOOK.first()): SpaceRowFit {
        val maxEm = if (widthDp >= 900.0) 96.0 else SpaceRowLayout.MAX_EM_DP
        return SpaceRowLayout.fit(string(text), font().unitsPerEm, columnDp - 2 * GUTTER, maxEm)
    }

    /** The x (dp) of the left edge of the row. */
    fun rowLeft(text: String = ControlStrings.WORKBOOK.first()): Double {
        val inset = if (widthDp >= 900.0) 0.0 else (columnDp - 2 * GUTTER - row(text).widthDp) / 2.0
        return (widthDp - columnDp) / 2.0 + GUTTER + inset
    }

    /** The x (dp) of font-unit position [units] along the row. */
    fun xAt(
        units: Double,
        text: String = ControlStrings.WORKBOOK.first(),
    ): Double = rowLeft(text) + units * row(text).dpPerUnit

    /** The y (dp) of the top of the row's ink block, found by looking down the middle of the first letter for the ink colour. */
    fun rowTop(columnDp: Double): Double {
        val image = rig.snapshot("space-probe")
        val pixels = image.toComposeImageBitmap().toPixelMap()
        val ink = texture.ink.toColor()
        val x = (columnDp * rig.density).toInt()
        // Below the header, whose title letters are ink too.
        for (y in (HEADER_DP * rig.density).toInt() until pixels.height) if (near(pixels[x, y], ink)) return y / rig.density.toDouble()
        error("no ink block found in column $columnDp dp")
    }

    /** The pixel colour at ([x], [y]) dp of a fresh frame. */
    fun colourAt(
        x: Double,
        y: Double,
    ): Color {
        val pixels = rig.snapshot("space-probe").toComposeImageBitmap().toPixelMap()
        return pixels[(x * rig.density).toInt(), (y * rig.density).toInt()]
    }

    /** The pixels between [top] and [bottom] dp of a fresh frame, across the whole width, to compare one frame's row with another's. */
    fun region(
        top: Double,
        bottom: Double,
    ): List<Int> {
        val pixels = rig.snapshot("space-probe").toComposeImageBitmap().toPixelMap()
        val from = (top * rig.density).toInt()
        val to = (bottom * rig.density).toInt().coerceAtMost(pixels.height)
        return buildList {
            for (y in from until to) for (x in 0 until pixels.width) add(pixels.buffer[y * pixels.width + x])
        }
    }

    /** The share (0..1) of the pixels in the rectangle from ([x0], [y0]) to ([x1], [y1]) dp that are ink-coloured, in a fresh frame. */
    fun inkShare(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
    ): Double {
        val pixels = rig.snapshot("space-probe").toComposeImageBitmap().toPixelMap()
        val ink = texture.ink.toColor()
        var total = 0
        var inked = 0
        for (y in (y0 * rig.density).toInt() until (y1 * rig.density).toInt()) {
            for (x in (x0 * rig.density).toInt() until (x1 * rig.density).toInt()) {
                total++
                if (near(pixels[x, y], ink)) inked++
            }
        }
        return if (total == 0) 0.0 else inked.toDouble() / total
    }

    /** A piece of text the page has put in its semantics tree, and where it is on the screen in dp. */
    data class ShownText(
        val text: String,
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double,
    ) {
        /** Whether any of it is inside the window: text scrolled or laid out beyond the edge is in the tree but not seen. */
        fun onScreen(
            widthDp: Double,
            heightDp: Double,
        ): Boolean = right > 0.0 && bottom > 0.0 && left < widthDp && top < heightDp && right > left && bottom > top
    }

    /** Every piece of text on the page now, read from the semantics tree the way a screen reader reads it. */
    fun texts(): List<ShownText> {
        val found = ArrayList<ShownText>()

        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.let { lines ->
                val bounds = node.boundsInRoot
                val d = rig.density.toDouble()
                found += ShownText(lines.joinToString("") { it.text }, bounds.left / d, bounds.top / d, bounds.right / d, bounds.bottom / d)
            }
            node.children.forEach(::walk)
        }
        rig.scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
        return found
    }

    /** The on-screen text that holds [fragment], or null: what a person looking at the page can read. */
    fun visibleText(fragment: String): ShownText? = texts().firstOrNull { fragment in it.text && it.onScreen(widthDp, heightDp) }

    /** Waits, in real time, for [fragment] to be on the screen: something the page waits for (a parser on its own thread) answers when it answers. */
    fun awaitVisible(
        fragment: String,
        millis: Long = AWAIT_MS,
    ) {
        val until = System.nanoTime() + millis * NANOS_PER_MILLI
        while (visibleText(fragment) == null && System.nanoTime() < until) {
            rig.frames(2)
            Thread.sleep(POLL_MS)
        }
        checkNotNull(visibleText(fragment)) { "'$fragment' never appeared. The page shows: ${texts().map { it.text }}" }
        rig.frames(4)
    }

    // ---- driving the page through its semantics, as a screen reader or a switch user would ----------------

    private fun flatten(root: SemanticsNode): List<SemanticsNode> = listOf(root) + root.children.flatMap(::flatten)

    private fun wordsOf(node: SemanticsNode): String =
        (
            (
                node.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.map { it.text }
                    .orEmpty()
            ) +
                (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
        ).joinToString(" ")

    /** The controls that can be tapped, as the merged semantics tree has them (a row of texts that is one button is one node), with what they say. */
    fun controls(): List<Pair<String, SemanticsNode>> =
        rig.scene.semanticsOwners
            .flatMap { flatten(it.rootSemanticsNode) }
            .filter { SemanticsActions.OnClick in it.config }
            .map { wordsOf(it) to it }

    /**
     * Taps the [index]th control whose words satisfy [matches], by performing the click action its semantics offer: what a tap,
     * an Enter key or a screen reader's double tap would do. A control that is disabled is not tapped, and the test fails saying so.
     */
    fun tap(
        index: Int = 0,
        matches: (String) -> Boolean,
    ) {
        val found = controls().filter { (words, _) -> matches(words) }
        val node = found.getOrNull(index)?.second ?: error("nothing to tap at $index among ${controls().map { it.first }}")
        check(SemanticsProperties.Disabled !in node.config) { "'${wordsOf(node)}' is disabled" }
        checkNotNull(node.config[SemanticsActions.OnClick].action) { "no click action" }.invoke()
        Snapshot.sendApplyNotifications()
        rig.frames(8)
    }

    /** Holds the control whose words are exactly [words], as a long press does. */
    fun longPressExactly(words: String) {
        val node =
            controls().firstOrNull { it.first == words }?.second
                ?: error("nothing to hold named $words among ${controls().map { it.first }}")
        checkNotNull(node.config.getOrNull(SemanticsActions.OnLongClick)?.action) { "'$words' has no long press" }.invoke()
        Snapshot.sendApplyNotifications()
        rig.frames(8)
    }

    /** What a screen reader says of each part of the page that has a description (the string's row says its letters and the selection's numbers). */
    fun descriptions(): List<String> {
        val found = ArrayList<String>()

        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { found += it.joinToString(" ") }
            node.children.forEach(::walk)
        }
        rig.scene.semanticsOwners.forEach { walk(it.unmergedRootSemanticsNode) }
        return found
    }

    /** Taps the control whose words are exactly [words]. */
    fun tapExactly(
        words: String,
        index: Int = 0,
    ) = tap(index) { it == words }

    /** Taps the control whose words start with [start]. */
    fun tapStartingWith(
        start: String,
        index: Int = 0,
    ) = tap(index) { it.startsWith(start) }

    private fun fields(): List<SemanticsNode> =
        rig.scene.semanticsOwners
            .flatMap { flatten(it.unmergedRootSemanticsNode) }
            .filter { SemanticsActions.SetText in it.config }

    /** The text in each text field the person can type into, in the order they are on the page. */
    fun fieldValues(): List<String> =
        fields().map {
            it.config
                .getOrNull(SemanticsProperties.EditableText)
                ?.text
                .orEmpty()
        }

    /** Types [text] into the [index]th text field the person can type into, as a screen reader's "set text" does. */
    fun typeInto(
        index: Int,
        text: String,
    ) {
        val field = fields().getOrNull(index) ?: error("no text field at $index; there are ${fields().size}")
        checkNotNull(field.config[SemanticsActions.SetText].action) { "no set-text action" }.invoke(AnnotatedString(text))
        Snapshot.sendApplyNotifications()
        rig.frames(8)
    }

    /** Presses [key] and answers whether the page took it (true) or left it for whatever is behind it (false). */
    @OptIn(InternalComposeUiApi::class)
    fun pressKey(
        key: Key,
        ctrl: Boolean = false,
    ): Boolean {
        val taken = rig.scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown, isCtrlPressed = ctrl))
        rig.frames(1)
        rig.scene.sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp, isCtrlPressed = ctrl))
        rig.frames(1)
        return taken
    }

    fun isInk(colour: Color): Boolean = near(colour, texture.ink.toColor())

    fun isCanvas(colour: Color): Boolean = near(colour, texture.canvas.toColor(), CANVAS_TOLERANCE)

    private fun near(
        a: Color,
        b: Color,
        tolerance: Float = TOLERANCE,
    ): Boolean = abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance

    companion object {
        const val GUTTER = 22.0
        private const val AWAIT_MS = 20_000L
        private const val POLL_MS = 40L
        private const val NANOS_PER_MILLI = 1_000_000L
        private const val HEADER_DP = 64.0
        private const val CONTENT_MAX = 720.0
        private const val TOLERANCE = 0.08f

        // The faint grid under the string is a few per cent of ink over the canvas: still the canvas, to the eye.
        private const val CANVAS_TOLERANCE = 0.12f
    }
}

/** Where the router was asked to go while a test ran: the sections the deck's arc sent it to, and how often the Map was opened. */
internal class RouterLog {
    val sections = mutableListOf<DeckSection>()
    var maps = 0
}

/** A parser that is not there, so a page test never depends on which Python the machine running it has. */
internal val NO_PARSER: FeaChecker = UnavailableFeaChecker("No parser is available to this test.")

/** Runs [block] against the Space page over [ufo], with [glyph] in the router's hand. */
internal fun withSpacePage(
    ufo: UfoProject,
    glyph: String = "n",
    width: Int = PHONE_W,
    height: Int = PHONE_H,
    density: Float = PHONE_DENSITY,
    texture: CanvasTexture = CanvasTextures.PAPER,
    feaChecker: FeaChecker = NO_PARSER,
    router: RouterLog = RouterLog(),
    workspace: ProjectWorkspace = workspaceWith(ufo),
    /** False while the router has Space covered by another screen, as when a test leaves and comes back. */
    active: State<Boolean>? = null,
    block: SpacePageRig.() -> Unit,
) {
    val session = checkNotNull(workspace.current.value)
    val selections = mutableListOf<String>()
    val palette = IntArray(1)
    var pageState: SpaceRoomState? = null
    val widthDp = width / density.toDouble()
    val heightDp = height / density.toDouble()
    ImageComposeScene(width = width, height = height, density = Density(density)) {
        CompositionLocalProvider(
            LocalProjectWorkspace provides workspace,
            LocalGlyphNav provides GlyphNav(current = glyph, open = {}, select = { selections += it }),
            LocalSectionNav provides SectionNav(current = DeckSection.SPACE, goTo = { router.sections += it }, openMap = { router.maps++ }),
        ) {
            SpaceRoom(
                active = active?.value ?: true,
                texture = texture,
                onOpenPalette = { palette[0]++ },
                onState = { pageState = it },
                feaChecker = feaChecker,
            )
        }
    }.use { scene ->
        val rig =
            DrawSheetRig(
                scene,
                density,
                PuckUiState(),
                PuckPinState(PuckPinState.defaultFor(Vec2(widthDp, heightDp))),
                selections,
            ) { null }
        rig.frames(8)
        SpacePageRig(
            rig,
            checkNotNull(pageState) {
                "the page did not report its state"
            },
            session,
            texture,
            widthDp,
            heightDp,
            selections,
            palette,
        ).block()
    }
}
