package com.md4a

import com.md4a.ast.MdCode
import com.md4a.ast.MdCodeSpan
import com.md4a.ast.MdEmphasis
import com.md4a.ast.MdHeading
import com.md4a.ast.MdImage
import com.md4a.ast.MdLink
import com.md4a.ast.MdList
import com.md4a.ast.MdListItem
import com.md4a.ast.MdParagraph
import com.md4a.ast.MdTable
import com.md4a.ast.MdText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Md4aParserTest {

    private fun parse(md: String) = Md4a.parse(md)

    @Test
    fun `heading levels`() {
        val blocks = parse("# a\n\n## b\n\n### c")
        assertEquals(listOf(1, 2, 3), blocks.filterIsInstance<MdHeading>().map { it.level })
    }

    @Test
    fun `fenced code keeps language and literal`() {
        val block = parse("```kotlin\nval x = 1\n```").single() as MdCode
        assertEquals("kotlin", block.language)
        assertEquals("val x = 1\n", block.code)
    }

    @Test
    fun `gfm table with alignment`() {
        val table = parse("| a | b | c |\n|:--|:-:|--:|\n| 1 | 2 | 3 |").single() as MdTable
        assertEquals(3, table.header.size)
        assertEquals(1, table.rows.size)
        assertEquals(
            listOf(MdTable.Align.LEFT, MdTable.Align.CENTER, MdTable.Align.RIGHT),
            table.alignments,
        )
    }

    @Test
    fun `task list items`() {
        val list = parse("- [x] done\n- [ ] todo").single() as MdList
        assertEquals(listOf(true, false), list.items.map { it.task })
        assertEquals(listOf(true, false), list.items.map { it.checked })
    }

    @Test
    fun `nested list`() {
        val md = "- a\n  - b\n  - c\n- d"
        val list = parse(md).single() as MdList
        assertEquals(2, list.items.size)
        assertEquals(1, list.items[0].blocks.count { it is MdList })
    }

    @Test
    fun `ordered list numbering`() {
        val list = parse("3. x\n4. y").single() as MdList
        assertEquals(listOf(3, 4), list.items.map { it.number })
    }

    @Test
    fun `autolink turns bare url into link`() {
        val para = parse("see https://example.com now").single() as MdParagraph
        val rendered = para.inlines.map { it::class.simpleName }
        assertTrue(rendered.contains("MdLink"))
    }

    @Test
    fun `empty input yields empty document`() {
        assertTrue(parse("").isEmpty())
    }

    // ── HTML adaptation ─────────────────────────────────────────────────

    @Test
    fun `html hero header becomes image link paragraph`() {
        val blocks = parse(
            "<p align=\"center\">\n  <a href=\"#\">\n    <img alt=\"Ionic\" src=\"https://x/logo.png\" width=\"60\" />\n  </a>\n</p>\n"
        )
        val para = blocks.single() as MdParagraph
        val link = para.inlines.filterIsInstance<MdLink>().single()
        assertEquals("#", link.url)
        val img = link.children.filterIsInstance<MdImage>().single()
        assertEquals("https://x/logo.png", img.url)
        assertEquals("Ionic", img.alt)
    }

    @Test
    fun `html heading is converted`() {
        val h = parse("<h1 align=\"center\">\n  Ionic\n</h1>").single() as MdHeading
        assertEquals(1, h.level)
        assertEquals(listOf("Ionic"), h.inlines.filterIsInstance<MdText>().map { it.text.trim() }.filter { it.isNotEmpty() })
    }

    @Test
    fun `html table with th align becomes md table`() {
        val md = "<table>\n  <tr><th align=\"left\">A</th><th align=\"center\">B</th></tr>\n" +
            "  <tr><td>1</td><td><img src=\"https://img.shields.io/badge/x-y\" alt=\"badge\" /></td></tr>\n</table>"
        val table = parse(md).single() as MdTable
        assertEquals(listOf(MdTable.Align.LEFT, MdTable.Align.CENTER), table.alignments)
        assertEquals(1, table.rows.size)
        assertTrue(table.rows[0][1].inlines.any { it is MdImage })
    }

    @Test
    fun `details summary renders as emphasized lead`() {
        val blocks = parse(
            "<details>\n<summary>Click to expand</summary>\n<p>hidden body</p>\n</details>"
        )
        assertEquals(2, blocks.size)
        val lead = blocks[0] as MdParagraph
        assertTrue((lead.inlines.single() as MdEmphasis).strong)
        assertEquals("hidden body", (blocks[1] as MdParagraph).inlines.filterIsInstance<MdText>().joinToString("") { it.text }.trim())
    }

    @Test
    fun `pre code with language class becomes code block`() {
        val code = parse("<pre><code class=\"language-js language-javascript\">const x = 1;\n</code></pre>").single() as MdCode
        assertEquals("js", code.language)
        assertTrue(code.code.startsWith("const x = 1;"))
    }

    @Test
    fun `inline html tags map to inline nodes`() {
        val md = "press <kbd>Ctrl</kbd> <b>bold</b> <i>italic</i><br>next <a href=\"https://e.com\">link</a> <img src=\"https://x/b.svg\" alt=\"b\" /> end"
        val para = parse(md).single() as MdParagraph
        val types = para.inlines.map { it::class.simpleName }
        assertTrue("kbd->code span", types.contains("MdCodeSpan"))
        assertTrue("br->hardbreak", types.contains("MdHardBreak"))
        assertTrue("img", types.contains("MdImage"))
        val link = para.inlines.filterIsInstance<MdLink>().single()
        assertEquals("https://e.com", link.url)
        assertEquals(listOf("bold"), para.inlines.filterIsInstance<MdEmphasis>().filter { it.strong }.flatMap { it.children }.filterIsInstance<MdText>().map { it.text })
    }

    @Test
    fun `entities are decoded`() {
        val para = parse("<p>a &amp; b &lt;c&gt; &#10084;</p>").single() as MdParagraph
        val text = para.inlines.filterIsInstance<MdText>().joinToString("") { it.text }
        assertTrue(text.contains("a & b <c>"))
        assertTrue(text.contains("❤"))
    }

    @Test
    fun `script and comments are dropped`() {
        val blocks = parse("<script>evil()</script>\n<!-- hidden -->\n<p>visible</p>")
        assertEquals(1, blocks.size)
    }

    @Test
    fun `unconvertible html degrades to plain text not raw tags`() {
        val blocks = parse("<div align=\"center\">plain words here</div>")
        val para = blocks.filterIsInstance<MdParagraph>().single()
        assertEquals("plain words here", para.inlines.filterIsInstance<MdText>().joinToString("") { it.text }.trim())
    }

    @Test
    fun `badge image unwraps from link at block level`() {
        val blocks = parse("<p align=\"center\"><a href=\"https://license\"><img src=\"https://img.shields.io/badge/license-MIT-blue\" alt=\"License\" /></a></p>")
        val para = blocks.single() as MdParagraph
        val link = para.inlines.filterIsInstance<MdLink>().single()
        assertTrue(link.children.single() is MdImage)
    }
}
