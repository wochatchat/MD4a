package com.md4a

import com.md4a.ast.MdCode
import com.md4a.ast.MdHeading
import com.md4a.ast.MdList
import com.md4a.ast.MdListItem
import com.md4a.ast.MdParagraph
import com.md4a.ast.MdTable
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
}
