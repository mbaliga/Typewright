// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.project

import com.asoc.typewright.core.font.ufo.UfoProject
import com.asoc.typewright.core.geometry.GlyphCount
import com.asoc.typewright.core.geometry.count
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.TimeSource

/**
 * What a [ProjectSession] needs from its host (docs/PROJECT_MODEL.md §4): where its coroutines
 * run, where its timestamps come from, and its tuning. [clock] is truncated to whole seconds by
 * every command that stamps one (§3: "Saving never mints a timestamp"). [monotonic] times
 * [HistoryStack]'s coalescing window, independently of wall-clock jumps; a test may inject a
 * [kotlin.time.TestTimeSource] here to control it deterministically. Autosave's own debounce and
 * cap are scheduled with plain `kotlinx.coroutines.delay`, so they follow [scope]'s dispatcher
 * (a `kotlinx-coroutines-test` virtual-time dispatcher in tests) without needing [monotonic].
 */
class SessionEnvironment(
    val scope: CoroutineScope,
    val io: CoroutineDispatcher,
    val clock: Clock = Clock.System,
    val monotonic: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    val autosave: AutosavePolicy = AutosavePolicy(),
    val historyLimit: Int = 500,
    val coalesceWindowMillis: Long = DEFAULT_COALESCE_WINDOW_MS,
) {
    /** [clock]'s current instant, truncated to whole seconds (every project timestamp is one, §3). */
    fun now(): Instant = clock.now().truncatedToProjectSeconds()
}

/** Autosave's timing (docs/PROJECT_MODEL.md §8.1): debounce after the last change, capped from the first unsaved one. */
data class AutosavePolicy(
    val debounceMillis: Long = 1_500,
    val maxDelayMillis: Long = 10_000,
    val enabled: Boolean = true,
)

/** What [ProjectSession.create] needs: the new project's identity and its first master(s). */
data class NewProjectSpec(
    val name: String,
    val brief: Brief,
    val scripts: List<String>,
    val masters: List<NewMaster>,
    val lockGlyphsAs: ApprovalOrigin? = null,
)

/** One master of a [NewProjectSpec]; [ufo] is its starting font data. */
data class NewMaster(
    val id: String = "regular",
    val styleName: String = "Regular",
    val ufo: UfoProject,
)

/** [ProjectSession.open]'s outcome. */
sealed interface OpenResult {
    /** The project opened; [session] is ready to use. */
    data class Opened(
        val session: ProjectSession,
    ) : OpenResult

    /** The store holds UFOs but no `typewright.json`: an import candidate (P12). */
    data class NotAProject(
        val ufoPaths: List<String>,
    ) : OpenResult

    /** [path] could not be read as a project; nothing was written and nothing was auto-fixed. */
    data class Damaged(
        val path: String,
        val message: String,
    ) : OpenResult

    /** `typewright.json`'s `format_version` is newer than this build reads; [session] is read-only. */
    data class NewerFormat(
        val found: Int,
        val session: ProjectSession,
    ) : OpenResult
}

/** [ProjectSession.create]'s outcome. */
sealed interface CreateResult {
    /** The project was created; [session] is ready to use. */
    data class Created(
        val session: ProjectSession,
    ) : CreateResult

    /** The target directory is not empty. */
    data class NotEmpty(
        val entries: List<String>,
    ) : CreateResult

    /** The target could not be written to. */
    data class NotWritable(
        val message: String,
    ) : CreateResult
}

/** What [ProjectSession.open] found while opening, beyond the state itself. */
data class OpenReport(
    val recovery: RecoveryReport,
    val externalEdits: List<GlyphRef>,
    val migratedFrom: Int?,
)

/** The result of [ProjectSession.simulate]: what running [result] would change, for a disabled-row reason or a consequence estimator (INTERACTION_V1 §5). */
data class Simulation(
    val result: EditResult,
    val before: Map<GlyphRef, GlyphCount>,
    val after: Map<GlyphRef, GlyphCount>,
)

/**
 * Every glyph's live on-curve/off-curve counts (docs/PROJECT_MODEL.md §4 "Live economy"),
 * recomputed synchronously inside every state transition. [EconomyCache] is what keeps that
 * cheap: a glyph is recounted only when its [com.asoc.typewright.core.geometry.Glyph] instance changed (`!==`).
 */
data class EconomySnapshot(
    val counts: Map<GlyphRef, GlyphCount>,
) {
    /** [ref]'s count, or null if it names no glyph. */
    fun count(ref: GlyphRef): GlyphCount? = counts[ref]

    companion object {
        /** No glyphs counted yet. */
        val EMPTY: EconomySnapshot = EconomySnapshot(emptyMap())
    }
}

/** Recomputes [EconomySnapshot] from a [FontState], reusing a glyph's previous count while its instance is unchanged. */
internal class EconomyCache {
    private var cache: Map<GlyphRef, Pair<com.asoc.typewright.core.geometry.Glyph, GlyphCount>> = emptyMap()

    fun recompute(font: FontState): EconomySnapshot {
        val next = LinkedHashMap<GlyphRef, Pair<com.asoc.typewright.core.geometry.Glyph, GlyphCount>>()
        for (master in font.masters) {
            for (glyph in master.ufo.glyphs) {
                val ref = GlyphRef(master.id, glyph.name)
                val previous = cache[ref]
                next[ref] = if (previous != null && previous.first === glyph) previous else glyph to glyph.count()
            }
        }
        cache = next
        return EconomySnapshot(next.mapValues { it.value.second })
    }
}
