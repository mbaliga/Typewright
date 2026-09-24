package dev.aarso.typewright.compile

/** One compiled font file. */
class FontBinary(
    val fileName: String,
    val format: OutputFormat,
    val bytes: ByteArray,
)

/** One line of a build log, as the backend reported it. */
data class CompileLogLine(
    val level: Level,
    val message: String,
) {
    enum class Level { INFO, WARNING, ERROR }
}

/** The outcome of [CompileBackend.compile]. Every outcome carries the log. */
sealed interface CompileResult {
    val log: List<CompileLogLine>

    /** The build ran and produced [binaries]. */
    class Success(
        val binaries: List<FontBinary>,
        override val log: List<CompileLogLine>,
    ) : CompileResult

    /** The build ran and failed; [reason] is one plain sentence for the UI. */
    data class Failure(
        val reason: String,
        override val log: List<CompileLogLine>,
    ) : CompileResult

    /** The backend is a stub: it did nothing, produced nothing, and says what it will do. */
    data class NotImplemented(
        val backend: String,
        val planned: String,
        override val log: List<CompileLogLine>,
    ) : CompileResult
}
