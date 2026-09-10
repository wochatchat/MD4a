package com.md4a.highlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeHighlighterTest {

    private fun types(code: String, lang: String? = null) =
        CodeHighlighter.tokenize(code, lang).map { it.type }

    @Test
    fun `kotlin keywords strings comments numbers`() {
        val t = types(
            "// comment\nfun main() { val s = \"hi\"; val n = 42 }",
            "kotlin",
        )
        assertTrue(t.contains(CodeHighlighter.TokenType.COMMENT))
        assertTrue(t.contains(CodeHighlighter.TokenType.KEYWORD))
        assertTrue(t.contains(CodeHighlighter.TokenType.STRING))
        assertTrue(t.contains(CodeHighlighter.TokenType.NUMBER))
    }

    @Test
    fun `hex colors in css are numbers not comments`() {
        val t = types("color: #1A2B3C;", "css")
        assertTrue(CodeHighlighter.TokenType.NUMBER in t)
    }

    @Test
    fun `spans are ordered and non-overlapping`() {
        val code = "val a = \"x\" + 1 // c"
        val spans = CodeHighlighter.tokenize(code, "kotlin")
        var last = 0
        for (s in spans) {
            assertTrue(s.start >= last)
            assertEquals(true, s.end > s.start)
            last = s.end
        }
    }

    @Test
    fun `unknown language still highlights strings`() {
        val t = types("\"only string\"", "nope")
        assertEquals(listOf(CodeHighlighter.TokenType.STRING), t)
    }

    @Test
    fun `spans reference correct source ranges`() {
        val code = "x = \"abc\""
        val span = CodeHighlighter.tokenize(code, "python").single()
        assertEquals("\"abc\"", code.substring(span.start, span.end))
    }
}
