package dev.aarso.typewright.campaign

/** Anchors [readCampaignResourceText]'s classloader lookup to this module's own class. */
private object JvmCampaignResourceAnchor

internal actual fun readCampaignResourceText(resourcePath: String): String {
    val stream =
        JvmCampaignResourceAnchor.javaClass.getResourceAsStream("/$resourcePath")
            ?: error("Campaign resource not found on the classpath: $resourcePath")
    return stream.use { it.readBytes() }.decodeToString()
}
