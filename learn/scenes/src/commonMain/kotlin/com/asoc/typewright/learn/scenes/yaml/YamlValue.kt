// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.learn.scenes.yaml

/**
 * A minimal, generic YAML-subset value tree. See [parseYaml]'s KDoc for exactly what subset of
 * YAML this parses, and why it exists instead of a library dependency.
 */
public sealed class YamlValue {
    /** A scalar: a string, or `null` for an explicit `~`/`null`/empty value. */
    public data class Scalar(
        val text: String?,
    ) : YamlValue()

    /** An ordered sequence (`- item` block style, or `[a, b]` flow style). */
    public data class Sequence(
        val items: List<YamlValue>,
    ) : YamlValue()

    /** An ordered mapping (`key: value` block style). Order is preserved but not significant. */
    public data class Mapping(
        val entries: List<Pair<String, YamlValue>>,
    ) : YamlValue() {
        /** The value for [key], or null if this mapping has no such key. */
        public operator fun get(key: String): YamlValue? = entries.firstOrNull { it.first == key }?.second

        /** The keys of this mapping, in document order. */
        public val keys: List<String> get() = entries.map { it.first }
    }

    public companion object {
        /** The single shared representation of an explicit YAML null / absent value. */
        public val NULL: Scalar = Scalar(null)
    }
}

/** [YamlValue.Mapping.get] returning the scalar's [YamlValue.Scalar.text], or null if absent. */
public fun YamlValue.Mapping.scalarOrNull(key: String): String? = (this[key] as? YamlValue.Scalar)?.text

/**
 * [scalarOrNull] but required: throws [YamlParseException] naming [key] and this mapping's own
 * known keys if it is missing or not a scalar.
 */
public fun YamlValue.Mapping.requireScalar(key: String): String {
    val value = this[key]
    if (value !is YamlValue.Scalar || value.text == null) {
        throw YamlParseException(
            "expected mapping key '$key' to be a scalar, found ${value?.let { it::class.simpleName } ?: "nothing"} " +
                "(known keys: $keys)",
        )
    }
    return value.text
}

/** [YamlValue.Mapping.get] as a [YamlValue.Mapping], or null if absent or not a mapping. */
public fun YamlValue.Mapping.mappingOrNull(key: String): YamlValue.Mapping? = this[key] as? YamlValue.Mapping

/** [mappingOrNull] but required: throws [YamlParseException] if [key] is missing or not a mapping. */
public fun YamlValue.Mapping.requireMapping(key: String): YamlValue.Mapping =
    mappingOrNull(key)
        ?: throw YamlParseException("expected mapping key '$key' to be a mapping (known keys: $keys)")

/** [YamlValue.Mapping.get] as a [YamlValue.Sequence]'s items, or an empty list if absent. */
public fun YamlValue.Mapping.sequenceOrEmpty(key: String): List<YamlValue> = (this[key] as? YamlValue.Sequence)?.items ?: emptyList()

/** Thrown by [parseYaml] and by the `require*`/typed accessors above on malformed input. */
public class YamlParseException(
    message: String,
) : RuntimeException(message)
