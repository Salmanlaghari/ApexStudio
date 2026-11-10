package com.apexstudio.app.data.gl

/** Rewrites two-input GLES transition shaders to sample a horizontally packed texture. */
object PackedTransitionShaderSource {
    private val PACKED_DECLARATION = """
uniform sampler2D uPacked;
vec2 packedFrom(vec2 uv) {
    vec2 c = clamp(uv, vec2(0.0), vec2(1.0));
    return vec2(c.x * 0.5, c.y);
}
vec2 packedTo(vec2 uv) {
    vec2 c = clamp(uv, vec2(0.0), vec2(1.0));
    return vec2(0.5 + c.x * 0.5, c.y);
}
""".trimIndent()

    /** Returns a packed-texture shader, or null when the source is not safely rewritable. */
    fun rewrite(source: String): String? {
        val tokens = lex(source)
        if (!hasFunctionDefinition(tokens, "getFromColor") ||
            !hasFunctionDefinition(tokens, "getToColor")
        ) {
            return null
        }

        val declarations = tokens.indices.mapNotNull { index ->
            declarationRange(tokens, index, "uFrom") ?: declarationRange(tokens, index, "uTo")
        }.distinctBy { it.first }
        if (declarations.size < 2) return null

        val replacements = ArrayList<Replacement>()
        declarations.forEachIndexed { index, range ->
            replacements += Replacement(
                range.first,
                range.second,
                if (index == 0) PACKED_DECLARATION else ""
            )
        }

        tokens.indices.forEach { index ->
            if (tokens[index].text != "texture" || index + 1 >= tokens.size || tokens[index + 1].text != "(") {
                return@forEach
            }
            val close = matchingToken(tokens, index + 1, "(", ")") ?: return@forEach
            val comma = topLevelComma(tokens, index + 2, close) ?: return@forEach
            val sampler = tokens[index + 2].text
            val packedFunction = when (sampler) {
                "uFrom" -> "packedFrom"
                "uTo" -> "packedTo"
                else -> return@forEach
            }
            if (comma + 1 >= close) return@forEach
            replacements += Replacement(tokens[index + 2].start, tokens[index + 2].end, "uPacked")
            // Insert after existing comma whitespace, before the first UV token.
            // Token-local edits allow independently rewriting nested texture calls.
            replacements += Replacement(tokens[comma + 1].start, tokens[comma + 1].start, "$packedFunction(")
            replacements += Replacement(tokens[close].start, tokens[close].start, ")")
        }

        val rewritten = applyReplacements(source, replacements)
        val rewrittenTokens = lex(rewritten)
        val remainingSamplerCall = rewrittenTokens.indices.any { index ->
            rewrittenTokens[index].text in setOf("uFrom", "uTo") &&
                index >= 2 && rewrittenTokens[index - 1].text == "(" &&
                rewrittenTokens[index - 2].text in setOf(
                    "texture", "texture2D", "textureProj", "textureLod", "textureOffset", "texelFetch"
                )
        }
        return if (remainingSamplerCall) null else rewritten
    }

    private data class Token(val text: String, val start: Int, val end: Int)
    private data class Replacement(val start: Int, val end: Int, val text: String)

    private fun declarationRange(tokens: List<Token>, index: Int, sampler: String): Pair<Int, Int>? {
        if (index + 3 >= tokens.size) return null
        if (tokens[index].text != "uniform" || tokens[index + 1].text != "sampler2D" ||
            tokens[index + 2].text != sampler || tokens[index + 3].text != ";"
        ) return null
        return tokens[index].start to tokens[index + 3].end
    }

    private fun hasFunctionDefinition(tokens: List<Token>, name: String): Boolean {
        tokens.indices.forEach { index ->
            if (tokens[index].text != name || index + 1 >= tokens.size || tokens[index + 1].text != "(") return@forEach
            val close = matchingToken(tokens, index + 1, "(", ")") ?: return@forEach
            if (close + 1 < tokens.size && tokens[close + 1].text == "{") return true
        }
        return false
    }

    private fun matchingToken(tokens: List<Token>, open: Int, opening: String, closing: String): Int? {
        var depth = 0
        for (index in open until tokens.size) {
            when (tokens[index].text) {
                opening -> depth++
                closing -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun topLevelComma(tokens: List<Token>, start: Int, endExclusive: Int): Int? {
        var parentheses = 0
        var brackets = 0
        var braces = 0
        for (index in start until endExclusive) {
            when (tokens[index].text) {
                "(" -> parentheses++
                ")" -> parentheses--
                "[" -> brackets++
                "]" -> brackets--
                "{" -> braces++
                "}" -> braces--
                "," -> if (parentheses == 0 && brackets == 0 && braces == 0) return index
            }
        }
        return null
    }

    private fun applyReplacements(source: String, replacements: List<Replacement>): String {
        if (replacements.isEmpty()) return source
        val sorted = replacements.sortedWith(compareBy<Replacement> { it.start }.thenByDescending { it.end })
        val result = StringBuilder(source.length)
        var cursor = 0
        sorted.forEach { replacement ->
            if (replacement.start < cursor) return@forEach
            result.append(source, cursor, replacement.start)
            result.append(replacement.text)
            cursor = replacement.end
        }
        result.append(source, cursor, source.length)
        return result.toString()
    }

    private fun lex(source: String): List<Token> {
        val tokens = ArrayList<Token>()
        var index = 0
        while (index < source.length) {
            val character = source[index]
            if (character.isWhitespace()) {
                index++
            } else if (character == '/' && index + 1 < source.length && source[index + 1] == '/') {
                index = source.indexOf('\n', index + 2).let { if (it < 0) source.length else it + 1 }
            } else if (character == '/' && index + 1 < source.length && source[index + 1] == '*') {
                val end = source.indexOf("*/", index + 2)
                index = if (end < 0) source.length else end + 2
            } else if (character.isLetter() || character == '_') {
                val start = index++
                while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                tokens += Token(source.substring(start, index), start, index)
            } else {
                tokens += Token(character.toString(), index, index + 1)
                index++
            }
        }
        return tokens
    }
}
