// SPDX-License-Identifier: FSL-1.1-ALv2

package com.asoc.typewright.ui.capture

/**
 * The part of CSS an SVG drawing uses to say whether a shape is filled: the `fill` of a `style="..."` attribute and of the rules in a
 * `<style>` element. Inkscape writes `style="fill:none;stroke:#000"` on each shape; Illustrator writes `.cls-1{fill:none;...}` in a
 * `<style>` block and gives the shape a class. Only simple selectors are read (`tag`, `.class`, `#id` and combinations of these, in
 * comma lists); a rule with a descendant or child selector is not, and a shape that depends on one reads as filled.
 */
internal class SvgStyles(
    css: String = "",
) {
    private class Rule(
        val tag: String?,
        val classes: List<String>,
        val id: String?,
        val fill: String,
        val order: Int,
    ) {
        val specificity: Int get() = (if (id != null) ID_WEIGHT else 0) + classes.size * CLASS_WEIGHT + (if (tag != null) 1 else 0)
    }

    private val rules: List<Rule> = parse(css)

    /**
     * The `fill` this element is given by the style sheet, lower-case; null when no rule gives it one. The most specific rule wins,
     * and the later of two equally specific ones.
     */
    fun fillOf(
        name: String,
        attributes: Map<String, String>,
    ): String? {
        if (rules.isEmpty()) return null
        val classes = attributes["class"]?.split(' ', '\t', '\n', '\r')?.filter { it.isNotEmpty() }.orEmpty()
        val id = attributes["id"]
        return rules
            .filter { r ->
                (r.tag == null || r.tag == name) &&
                    r.classes.all { it in classes } &&
                    (r.id == null || r.id == id)
            }.maxWithOrNull(compareBy<Rule> { it.specificity }.thenBy { it.order })
            ?.fill
    }

    companion object {
        private const val ID_WEIGHT = 100
        private const val CLASS_WEIGHT = 10
        private val SIMPLE = Regex("^([A-Za-z][A-Za-z0-9_-]*)?((?:\\.[A-Za-z0-9_-]+)*)(#[A-Za-z0-9_-]+)?$")

        /** The `fill` value written in a declaration list such as `fill:none;stroke:#000`, lower-case; null when it has none. */
        fun fillIn(declarations: String): String? {
            var found: String? = null
            for (declaration in declarations.split(';')) {
                val colon = declaration.indexOf(':')
                if (colon < 0) continue
                if (declaration.substring(0, colon).trim().lowercase() != "fill") continue
                found =
                    declaration
                        .substring(colon + 1)
                        .substringBefore("!important")
                        .trim()
                        .lowercase()
            }
            return found?.takeIf { it.isNotEmpty() }
        }

        private fun parse(css: String): List<Rule> {
            val text = StringBuilder()
            var i = 0
            while (i < css.length) {
                if (css.startsWith("/*", i)) {
                    val end = css.indexOf("*/", i + 2)
                    i = if (end < 0) css.length else end + 2
                } else {
                    text.append(css[i])
                    i++
                }
            }
            val out = ArrayList<Rule>()
            var rest = text.toString()
            while (true) {
                val open = rest.indexOf('{')
                if (open < 0) break
                val close = rest.indexOf('}', open)
                if (close < 0) break
                val selectors = rest.substring(0, open)
                val body = rest.substring(open + 1, close)
                rest = rest.substring(close + 1)
                if (selectors.trimStart().startsWith("@")) continue
                val fill = fillIn(body) ?: continue
                for (selector in selectors.split(',')) {
                    val match = SIMPLE.matchEntire(selector.trim()) ?: continue
                    val tag = match.groupValues[1].ifEmpty { null }
                    val classes = match.groupValues[2].split('.').filter { it.isNotEmpty() }
                    val id = match.groupValues[3].removePrefix("#").ifEmpty { null }
                    if (tag == null && classes.isEmpty() && id == null) continue
                    out += Rule(tag, classes, id, fill, out.size)
                }
            }
            return out
        }
    }
}
