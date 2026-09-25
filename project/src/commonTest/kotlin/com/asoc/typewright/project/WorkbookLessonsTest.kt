// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.project.scrapbook.ScrapbookManifest
import com.asoc.typewright.project.scrapbook.ScrapbookPin
import com.asoc.typewright.project.scrapbook.ScrapbookPinKind
import com.asoc.typewright.project.scrapbook.stablePinRotationDegrees
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class WorkbookLessonsTest {
    private val at = Instant.fromEpochSeconds(1_790_344_991) // 2026-09-25T14:03:11Z

    private val lessons =
        WorkbookLessons(
            "latn",
            listOf(Reflection("r-4-1", 4, at, "Round ends everywhere, or nowhere. Decide before the s.")),
        )

    @Test
    fun encodesTheDocumentedBytes() {
        val expected =
            """
            {
              "format": "typewright-lessons",
              "format_version": 1,
              "workbook": "latn",
              "reflections": [
                {
                  "id": "r-4-1",
                  "task": 4,
                  "at": "2026-09-25T14:03:11Z",
                  "text": "Round ends everywhere, or nowhere. Decide before the s."
                }
              ]
            }
            """.trimIndent() + "\n"
        assertEquals(expected, WorkbookLessonsCodec.encode(lessons))
        assertEquals(
            "{\n  \"format\": \"typewright-lessons\",\n  \"format_version\": 1,\n  \"workbook\": \"latn\",\n  \"reflections\": []\n}\n",
            WorkbookLessonsCodec.encode(WorkbookLessons.empty("latn")),
        )
    }

    @Test
    fun roundTripsAndReEncodesToTheSameBytes() {
        val withMore =
            lessons
                .withReflection(4, at, "Second thought on task 4.")
                .withReflection(1, at, "Multi-line\ntext with \"quotes\" and a tab\t.")
        val text = WorkbookLessonsCodec.encode(withMore)
        val decoded = WorkbookLessonsCodec.decode(text)
        assertEquals(withMore, decoded)
        assertEquals(text, WorkbookLessonsCodec.encode(decoded))
    }

    @Test
    fun reflectionIdsCountPerTask() {
        assertEquals("r-4-2", lessons.nextReflectionId(4))
        assertEquals("r-1-1", lessons.nextReflectionId(1))
        val more = lessons.withReflection(4, at, "b").withReflection(4, at, "c").withReflection(11, at, "d")
        assertEquals(listOf("r-4-1", "r-4-2", "r-4-3", "r-11-1"), more.reflections.map { it.id })
        assertEquals(3, more.reflectionsFor(4).size)
        // r-41-… belongs to task 41, not task 4.
        val tricky = WorkbookLessons("latn", listOf(Reflection("r-41-9", 41, at, "x")))
        assertEquals("r-4-1", tricky.nextReflectionId(4))
    }

    @Test
    fun refusesBlankReflectionsAndBadTasks() {
        assertFailsWith<IllegalArgumentException> { lessons.withReflection(4, at, "   ") }
        assertFailsWith<IllegalArgumentException> { lessons.withReflection(0, at, "text") }
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.encode(lessons.copy(workbook = "../x")) }
        assertFailsWith<IllegalArgumentException> {
            WorkbookLessonsCodec.encode(lessons.withReflection(2, Instant.fromEpochMilliseconds(1_790_344_991_250), "fractional"))
        }
    }

    @Test
    fun decodeChecksFormatVersionAndFields() {
        val good = WorkbookLessonsCodec.encode(lessons)
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.decode(good.replace("typewright-lessons", "something-else")) }
        val newer =
            assertFailsWith<NewerFormatException> {
                WorkbookLessonsCodec.decode(
                    good.replace("\"format_version\": 1", "\"format_version\": 3"),
                )
            }
        assertEquals(3, newer.found)
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.decode(good.replace("\"format_version\": 1,\n", "")) }
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.decode(good.replace("\"task\": 4", "\"task\": \"4\"")) }
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.decode(good.replace("14:03:11Z", "14:03:11.5Z")) }
        assertFailsWith<IllegalArgumentException> {
            WorkbookLessonsCodec.decode(good.replace("\"workbook\": \"latn\"", "\"workbook\": \"../latn\""))
        }
        val withExtra = good.replace("\"workbook\": \"latn\",", "\"workbook\": \"latn\",\n  \"future\": [1, 2],")
        assertEquals(lessons, WorkbookLessonsCodec.decode(withExtra))
    }

    @Test
    fun theFileLivesUnderLessons() {
        assertEquals("lessons/workbook-latn.json", WorkbookLessonsCodec.path("latn"))
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.path("Latn") }
        assertFailsWith<IllegalArgumentException> { WorkbookLessonsCodec.path("") }
    }

    @Test
    fun reflectionsShowOnTheBoardAsNotePinsAfterTheManifestPins() {
        val photo = ScrapbookPin("pin-1", ScrapbookPinKind.PHOTO, "Signage", "photo", imagePath = "scrapbook/pin-1.jpg")
        val board = scrapbookBoard(ScrapbookManifest(pins = listOf(photo)), listOf(lessons))
        assertEquals(
            listOf(
                photo,
                ScrapbookPin(
                    id = "r-4-1",
                    kind = ScrapbookPinKind.NOTE,
                    captionTitle = "Reflection · Task 4",
                    captionSource = "note",
                    noteText = "Round ends everywhere, or nowhere. Decide before the s.",
                    rotationDegrees = stablePinRotationDegrees("r-4-1"),
                    drivesDesign = false,
                ),
            ),
            board,
        )
    }
}
