// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.settings

/**
 * Where a build is distributed from. It decides two things About shows: whether there is a
 * support link, and whether the build is labelled a preview. Google Play's build carries no
 * payment link (docs/LICENSING.md section 3); nothing is sold inside any build and no build
 * contains payment code, so this is a question of one line of text and one link.
 */
enum class DistributionChannel(
    /** The value the build flag takes (`-Ptypewright.channel=` on Android) and BuildConfig carries. */
    val id: String,
    val label: String,
    val showsSupportLink: Boolean,
    val isPreview: Boolean = false,
) {
    /** Hidden until the app says which build it is: the safe state for the support link. */
    UNSPECIFIED("unspecified", "Unspecified build", showsSupportLink = false),
    PLAY("play", "Google Play", showsSupportLink = false),
    GITHUB_RELEASE("github", "GitHub release", showsSupportLink = true),
    DESKTOP("desktop", "Linux desktop", showsSupportLink = true),
    WEB("web", "Web preview", showsSupportLink = true, isPreview = true),
    ;

    companion object {
        /** The channel whose [id] is [id], or [UNSPECIFIED] for anything else, so a typo in a build flag hides the link. */
        fun fromId(id: String?): DistributionChannel = entries.firstOrNull { it.id == id } ?: UNSPECIFIED
    }
}

/**
 * The channel of the running build. Each app shell sets it once at start-up, before the first
 * composition; the default, [DistributionChannel.UNSPECIFIED], shows no support link.
 */
object BuildChannel {
    var current: DistributionChannel = DistributionChannel.UNSPECIFIED
}

/**
 * The support page, as soon as the owner has one (docs/LICENSING.md section 3: a merchant of
 * record's page, linked from About and never built into the app). Blank until then, and About
 * then says the page is not open yet instead of showing a link that goes nowhere.
 */
const val SUPPORT_URL: String = ""

/** The privacy policy as it stands in the repository (docs/release/PRIVACY.md); the app also carries its summary. */
const val PRIVACY_POLICY_URL: String = "https://github.com/mbaliga/Typewright/blob/main/docs/release/PRIVACY.md"

/** The source repository, where the licences, the notices in full and the changelog live. */
const val SOURCE_URL: String = "https://github.com/mbaliga/Typewright"

/** What the web build says it cannot do; shown as a banner above the page and in About. */
const val WEB_PREVIEW_NOTICE: String =
    "Preview. This web version cannot compile a font or run Google Fonts' own checks (layer one); " +
        "it exports your project as UFO and a zip, and the desktop app builds it."

/** One line of About's support section: whether it is shown, what it says and where it goes (null when there is no page yet). */
data class SupportRow(
    val text: String,
    val url: String?,
)

/** The support row for [channel], or null where the build shows none (the Play build, and a build that has not said which it is). */
fun supportRow(
    channel: DistributionChannel,
    supportUrl: String = SUPPORT_URL,
): SupportRow? =
    when {
        !channel.showsSupportLink -> null
        supportUrl.isBlank() -> SupportRow("Support page: not open yet. Supporting will never unlock anything.", null)
        else -> SupportRow("Support Typewright", supportUrl)
    }
