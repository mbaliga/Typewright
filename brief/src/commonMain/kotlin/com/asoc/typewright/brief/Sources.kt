// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.brief

/**
 * How much weight a piece of guidance carries, shown beside it in words (CLAUDE.md law 5: a check
 * with no measurement behind it says so). A person can ignore a convention; they cannot ignore a
 * platform's rule and still ship to that platform.
 */
enum class SourceKind(
    val label: String,
) {
    /** A published requirement: a platform's review rule, a standard, a specification. */
    RULE("Rule"),

    /** A recommendation from a platform or standards body. */
    GUIDELINE("Guideline"),

    /** What a controlled study found. */
    EVIDENCE("Evidence"),

    /** Measured by this app on real fonts (the style atlas). */
    MEASURED("Measured"),

    /** Arithmetic from a sourced figure. */
    DERIVED("Derived"),

    /** Trade practice: what type designers do, not a rule. */
    CONVENTION("Convention"),

    /** This app's own rule of thumb, with nothing measured behind it. */
    OUR_HEURISTIC("Our heuristic"),
}

/** Where a piece of guidance comes from: its [kind] and a short [citation], plus a [url] when there is a public page to read. */
data class Source(
    val kind: SourceKind,
    val citation: String,
    val url: String? = null,
)
