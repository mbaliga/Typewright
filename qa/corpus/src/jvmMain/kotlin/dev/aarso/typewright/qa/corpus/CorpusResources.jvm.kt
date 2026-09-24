package dev.aarso.typewright.qa.corpus

/** Anchors [readCorpusResourceText]'s classloader lookup to this module's own class. */
private object JvmCorpusResourceAnchor

internal actual fun readCorpusResourceText(resourcePath: String): String {
    val stream =
        JvmCorpusResourceAnchor.javaClass.getResourceAsStream("/$resourcePath")
            ?: error("Corpus resource not found on the classpath: $resourcePath")
    return stream.use { it.readBytes() }.decodeToString()
}
