// SPDX-License-Identifier: Apache-2.0

package com.asoc.typewright.core.font.ufo

/**
 * `core-font`'s model of a UFO 3 project's kerning-and-features data: `groups.plist`'s [groups],
 * `kerning.plist`'s [kerning], and `features.fea`'s raw [features] text. One small model carries
 * all three, the way [UfoProject.fontInfo] carries `fontinfo.plist` as one field rather than
 * flattening its keys onto [UfoProject] itself, because the UFO 3 spec treats these three files as
 * one related cluster — `features.fea`'s own spec page (unifiedfontobject.org/versions/ufo3) says
 * so directly: "the features file may contain data that is a duplicate of or in conflict with the
 * data in kerning.plist, groups.plist and fontinfo.plist. Synchronization between the files is not
 * a requirement of this specification." `core-font` does not attempt that synchronization, or
 * resolve such a conflict, or interpret [kerning]/[groups] beyond their plain map shape (no
 * side-membership checks, no group-resolution): it is a faithful carrier, not a kerning or shaping
 * engine — the same "plain files, faithful round-trip, not interpretation" scope [features] itself
 * documents.
 *
 * All three default to "absent" ([groups] and [kerning] empty, [features] `null`), matching a UFO
 * project that has none of `groups.plist`, `kerning.plist` or `features.fea`: [writeUfoProject]
 * omits each file when its data is absent this way, rather than writing an empty or `null`-content
 * file no real UFO project would have.
 */
data class UfoKerning(
    val groups: Map<String, List<String>> = emptyMap(),
    val kerning: Map<String, Map<String, Double>> = emptyMap(),
    val features: String? = null,
)

/**
 * Writes [groups] as `groups.plist`: a `<dict>` of group name -> `<array>` of member glyph name
 * `<string>`s, per the UFO 3 spec (unifiedfontobject.org/versions/ufo3/groups.plist). Kerning-side
 * groups use the spec's `public.kern1.`/`public.kern2.` name prefixes (first-/second-side kerning
 * pair membership, unifiedfontobject.org/versions/ufo3/kerning.plist); a group name with no such
 * prefix is an ordinary (non-kerning) group, which the spec equally allows and this function writes
 * identically — [groups] does not distinguish the two cases, since nothing downstream needs to.
 * Byte-stable in [groups]' own iteration order, like [writePlist] generally.
 */
fun writeGroupsPlist(groups: Map<String, List<String>>): String =
    writePlist(
        PlistValue.PDict(
            groups.entries.map { (name, members) ->
                name to PlistValue.PArray(members.map { PlistValue.PString(it) })
            },
        ),
    )

/** Reads a `groups.plist` document (as [writeGroupsPlist] produces, or any spec-conforming UFO's) into the same `name -> members` map shape. */
fun readGroupsPlist(xml: String): Map<String, List<String>> =
    parsePlistDict(xml).entries.associate { (name, value) ->
        require(value is PlistValue.PArray) { "groups.plist entry '$name' must be an <array>, found <${value.elementName()}>" }
        val members =
            value.items.map {
                (it as? PlistValue.PString)?.value
                    ?: throw IllegalArgumentException("groups.plist group '$name' has a non-<string> member (<${it.elementName()}>)")
            }
        name to members
    }

/**
 * Writes [kerning] as `kerning.plist`: a `<dict>` of first name -> `<dict>` of second name ->
 * kerning value (`<integer>` or `<real>`, via [numericPlistValue]), per the UFO 3 spec
 * (unifiedfontobject.org/versions/ufo3/kerning.plist). A first/second name is either a glyph name
 * or a `public.kern1.`/`public.kern2.`-prefixed group name from a `groups.plist`; this function
 * writes whatever string keys [kerning] holds without checking they resolve against any particular
 * `groups.plist` — that cross-file consistency is a design-tool concern, not this codec's (see
 * [UfoKerning]'s own KDoc).
 */
fun writeKerningPlist(kerning: Map<String, Map<String, Double>>): String =
    writePlist(
        PlistValue.PDict(
            kerning.entries.map { (first, seconds) ->
                first to PlistValue.PDict(seconds.entries.map { (second, value) -> second to numericPlistValue(value) })
            },
        ),
    )

/** Reads a `kerning.plist` document (as [writeKerningPlist] produces, or any spec-conforming UFO's) into the same `first -> second -> value` map shape. */
fun readKerningPlist(xml: String): Map<String, Map<String, Double>> =
    parsePlistDict(xml).entries.associate { (first, secondsValue) ->
        require(secondsValue is PlistValue.PDict) {
            "kerning.plist entry '$first' must be a <dict>, found <${secondsValue.elementName()}>"
        }
        first to
            secondsValue.entries.associate { (second, value) ->
                val number =
                    value.asDoubleOrNull()
                        ?: throw IllegalArgumentException(
                            "kerning.plist pair '$first'/'$second' must be an <integer> or <real>, found <${value.elementName()}>",
                        )
                second to number
            }
    }
