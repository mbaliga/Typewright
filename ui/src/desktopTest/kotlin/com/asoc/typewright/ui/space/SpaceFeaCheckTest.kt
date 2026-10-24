// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import com.asoc.typewright.compile.FeaCheckResult
import com.asoc.typewright.compile.FeaChecker
import com.asoc.typewright.compile.FeaProblem
import com.asoc.typewright.core.font.ufo.UfoKerning
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the `.fea` editor shows of the parser's answer: the line and column of what it found, marked in the margin and listed in words, which check
 * the result is the result of, and the honest fallback where there is no parser. The parser is a canned one here: the real feaLib is exercised
 * by the compile module's own tests and by the screenshot test when `TYPEWRIGHT_PYTHON` is set.
 */
class SpaceFeaCheckTest {
    private val saved = "# kerning\nfeature kern {\n    pos n q -12;\n} kern;\n"
    private val ufo get() = SpaceTestProjects.hyleDeco(UfoKerning(features = saved))

    /** Answers [answer] to every text, after [gate] if there is one, and keeps what it was asked. */
    private class Canned(
        private val answer: FeaCheckResult,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : FeaChecker {
        val asked = mutableListOf<Pair<String, List<String>>>()

        override suspend fun check(
            text: String,
            glyphNames: List<String>,
        ): FeaCheckResult {
            asked += text to glyphNames
            gate?.await()
            return answer
        }
    }

    private val problem =
        FeaCheckResult.Checked(
            tool = "fontTools 9.9.9 (feaLib)",
            where = "in this computer's Python 3.11",
            built = false,
            problems = listOf(FeaProblem(3, 11, "The following glyph names are referenced but are missing from the glyph set: q")),
        )

    @Test
    fun theParsersFindingIsListedWithItsLineAndColumnAndMarkedInTheMarginAndNamedAsTheParsers() {
        val parser = Canned(problem)
        withSpacePage(ufo, feaChecker = parser) {
            change { open(SpacePanel.FEATURES) }
            awaitVisible("Line 3, column 11")
            assertNotNull(visibleText("fail · Line 3, column 11: The following glyph names are referenced but are missing"))
            assertNotNull(visibleText("● 3"), "the margin marks line 3, in a shape and not only in a colour")
            assertNotNull(
                visibleText("READ BY FONTTOOLS 9.9.9 (FEALIB), WITH THIS FONT'S GLYPH NAMES"),
                "and the report says whose answer it is",
            )
            assertNotNull(visibleText("Its lookups were not built, so the build can still find more."))
            assertNull(visibleText("braces, brackets"), "the quick check is not shown as well")
            val (text, names) = parser.asked.last()
            assertEquals(saved, text, "the parser read the text the editor shows")
            assertTrue(names.containsAll(listOf("n", "o", "H", "O")), "with the font's own glyph names: ${names.take(8)}")
        }
    }

    @Test
    fun whileTheParserHasNotAnsweredThePageSaysItIsReadingAndTheQuickCheckStandsIn() {
        val gate = CompletableDeferred<Unit>()
        withSpacePage(ufo, feaChecker = Canned(problem, gate)) {
            change { open(SpacePanel.FEATURES) }
            assertNotNull(
                visibleText("Until it answers, only this quick check has looked at it."),
                "nothing is claimed checked by the parser yet",
            )
            assertNotNull(visibleText("BRACES, BRACKETS, STRINGS AND BLOCK NAMES"), "the quick check is what is labelled")
            assertNull(visibleText("READ BY"))
            gate.complete(Unit)
            awaitVisible("Line 3, column 11")
            assertNull(visibleText("Until it answers"))
        }
    }

    @Test
    fun whereNoParserWillEverRunThePageNeverSaysOneIsReadingAndNeverWaits() {
        withSpacePage(ufo, feaChecker = NO_PARSER) {
            change { open(SpacePanel.FEATURES) }
            // At once, with no pause and no answer to wait for.
            assertNotNull(visibleText("No parser is available to this test."))
            assertNotNull(visibleText("The language itself is not parsed here"))
            assertNull(visibleText("Asking fontTools"))
            assertNull(visibleText("Until it answers"))
        }
    }

    @Test
    fun withNoParserTheQuickShapeCheckIsShownAsThatWithTheReasonAndTheLimit() {
        val broken = "feature kern {\n    pos n o -12;\n"
        val ufo = SpaceTestProjects.hyleDeco(UfoKerning(features = broken))
        withSpacePage(ufo, feaChecker = Canned(FeaCheckResult.Unavailable("Python 3 was not found on this computer."))) {
            change { open(SpacePanel.FEATURES) }
            awaitVisible("Python 3 was not found on this computer.")
            assertNotNull(visibleText("BRACES, BRACKETS, STRINGS AND BLOCK NAMES"), "labelled for what it looks at, not as a parse")
            assertNotNull(visibleText("fail · Line 1: feature kern is opened here and never closed"))
            assertNotNull(visibleText("● 1"))
            assertNotNull(visibleText("The language itself is not parsed here"))
            assertNull(visibleText("READ BY"))
        }
    }

    @Test
    fun typingAsksTheParserAboutTheNewTextOnceTheTypingPausesAndNotAboutEveryKey() {
        val parser = Canned(FeaCheckResult.Checked("fontTools", "on this computer", built = true, problems = emptyList()))
        withSpacePage(ufo, feaChecker = parser) {
            change { open(SpacePanel.FEATURES) }
            awaitVisible("nothing wrong with what was checked")
            parser.asked.clear()
            val started = System.nanoTime()
            typeInto(0, "feature kern {")
            typeInto(0, "feature kern { pos")
            typeInto(0, "feature kern { pos n o -3; } kern;")
            val typingMillis = (System.nanoTime() - started) / NANOS_PER_MILLI
            // Wait for the answer to the last text, then see what the parser was asked.
            val until = System.nanoTime() + 5_000_000_000L
            while (parser.asked.none { it.first == "feature kern { pos n o -3; } kern;" } && System.nanoTime() < until) Thread.sleep(20)
            assertEquals("feature kern { pos n o -3; } kern;", parser.asked.last().first)
            // Typed inside the pause, the first two texts were never asked about. (On a machine so busy that the typing itself outlasted the
            // pause, the texts typed before it ended may have been; then there is nothing to say.)
            if (typingMillis < PAUSE_MILLIS_WITH_ROOM) {
                assertTrue(
                    parser.asked.none { it.first == "feature kern {" || it.first == "feature kern { pos" },
                    "typed in $typingMillis ms, faster than the pause, yet the parser was asked ${parser.asked.map { it.first }}",
                )
            }
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val PAUSE_MILLIS_WITH_ROOM = 300L
    }
}
