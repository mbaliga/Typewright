// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.space

import androidx.compose.runtime.mutableStateOf
import com.asoc.typewright.core.font.ufo.UfoKerning
import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.Vec2
import com.asoc.typewright.project.Brief
import com.asoc.typewright.project.BriefSource
import com.asoc.typewright.project.CreateResult
import com.asoc.typewright.project.NewMaster
import com.asoc.typewright.project.NewProjectSpec
import com.asoc.typewright.project.OpenResult
import com.asoc.typewright.project.ProjectWorkspace
import com.asoc.typewright.ui.deck.DeckGeometry
import com.asoc.typewright.ui.deck.DeckSection
import com.asoc.typewright.ui.deck.DeckToggle
import com.asoc.typewright.ui.draw.DrawSheetRig
import com.asoc.typewright.ui.draw.DrawTestProjects
import com.asoc.typewright.ui.draw.withDrawSheet
import com.asoc.typewright.ui.project.InMemoryProjectFolderPicker
import com.asoc.typewright.ui.project.InMemoryStorageProvider
import com.asoc.typewright.ui.project.createProjectWorkspace
import com.asoc.typewright.ui.tokens.CanvasTexture
import com.asoc.typewright.ui.tokens.CanvasTextures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.min

/** Tests only: Hyle Deco's own outlines (the repository's font file), with the kerning, groups and features a test gives it. */
internal object SpaceTestProjects {
    /** The groups the screenshots use: round letters and straight letters, on both sides. */
    val GROUPS =
        mapOf(
            "public.kern1.round" to listOf("o", "O"),
            "public.kern2.round" to listOf("o", "O"),
            "public.kern1.straight" to listOf("n", "H"),
            "public.kern2.straight" to listOf("n", "H"),
        )

    fun hyleDeco(kerning: UfoKerning = UfoKerning()): UfoProject = DrawTestProjects.hyleDecoQuadratic().copy(kerningInfo = kerning)
}

/**
 * A workspace whose open project refuses every edit, as one that another writer holds does: the project is made, closed, its write
 * lease taken by someone else, and opened again. What the page does with a refusal can then be seen with the project's own.
 */
internal fun readOnlyWorkspaceWith(ufo: UfoProject): ProjectWorkspace =
    runBlocking {
        val storage = InMemoryStorageProvider()
        val workspace = createProjectWorkspace(CoroutineScope(Dispatchers.Default), storage = storage)
        val parent = checkNotNull(InMemoryProjectFolderPicker(workspace).pickNewProjectParent())
        val spec =
            NewProjectSpec(
                name = "Hyle Deco Redraw",
                brief = Brief(source = BriefSource.BLANK),
                scripts = listOf("Latn"),
                masters = listOf(NewMaster(id = "regular", styleName = "Regular", ufo = ufo)),
            )
        val created = workspace.create(parent, spec) as CreateResult.Created
        val store = created.session.store
        val location = storage.createChild(parent, spec.name)
        workspace.closeCurrent()
        checkNotNull(store.acquireWriteLease()) { "the lease was not free to take" }
        check(workspace.open(location) is OpenResult.Opened) { "the project did not open read-only" }
        check(checkNotNull(workspace.current.value).readOnly != null) { "the project is not read-only" }
        workspace
    }

/** A sheet on Space over [ufo], settled; [block] drives it with the pointer and keyboard of the Draw rig. */
internal fun withSpace(
    ufo: UfoProject,
    glyph: String = "n",
    width: Int = PHONE_W,
    height: Int = PHONE_H,
    density: Float = PHONE_DENSITY,
    texture: CanvasTexture = CanvasTextures.PAPER,
    block: DrawSheetRig.() -> Unit,
) = withDrawSheet(ufo, glyph, width, height, density, texture = texture, section = mutableStateOf(DeckSection.SPACE), block = block)

internal const val PHONE_DENSITY = 2f
internal const val PHONE_W = 780
internal const val PHONE_H = 1688
internal const val PHONE_DP_W = 390.0
internal const val PHONE_DP_H = 844.0
internal const val DESKTOP_W = 1280
internal const val DESKTOP_H = 800

/** Where the deck's parts are on a screen [widthDp] by [heightDp], so a test can press them as a person would. */
internal class DeckPoints(
    widthDp: Double,
    heightDp: Double,
) {
    private val k = min(1.0, widthDp / DeckGeometry.WIDTH)
    private val originX = (widthDp - DeckGeometry.WIDTH * k) / 2.0
    private val originY = heightDp - DeckGeometry.HEIGHT + DeckGeometry.HEIGHT * (1 - k)

    private fun at(
        x: Double,
        y: Double,
    ) = Vec2(originX + x * k, originY + y * k)

    /** The left circle (Back or Close). */
    val left: Vec2 get() = at(DeckGeometry.LEFT_CIRCLE_X, DeckGeometry.CIRCLE_Y)

    /** The right circle (⋯). */
    val right: Vec2 get() = at(DeckGeometry.RIGHT_CIRCLE_X, DeckGeometry.CIRCLE_Y)

    /** The seat called [label] among [toggles] on the toggle arc. */
    fun seat(
        label: String,
        toggles: List<DeckToggle>,
    ): Vec2 {
        val labels = DeckGeometry.toggleLabels(toggles)
        val index = toggles.indexOfFirst { it.label == label }
        require(index >= 0) { "no seat called $label" }
        val (x, y) = DeckGeometry.pointAt(DeckGeometry.TOGGLE_RADIUS, DeckGeometry.TOGGLE_HALF_ANGLE, labels[index].centerLength)
        return at(x, y)
    }
}

/** The seats Space shows in Spacing and in Kerning, as the deck draws them. */
internal val SPACING_SEATS = listOf(DeckToggle("Spacing", on = true), DeckToggle("Kerning"))
internal val KERNING_SEATS =
    listOf(DeckToggle("Spacing"), DeckToggle("Kerning", on = true), DeckToggle("Before", groupStart = true), DeckToggle("After", on = true))
