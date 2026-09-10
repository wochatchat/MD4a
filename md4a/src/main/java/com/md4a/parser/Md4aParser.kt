package com.md4a.parser

import com.md4a.ast.MdBlock
import com.md4a.ast.MdBlockQuote
import com.md4a.ast.MdCell
import com.md4a.ast.MdCode
import com.md4a.ast.MdHeading
import com.md4a.ast.MdInline
import com.md4a.ast.MdList
import com.md4a.ast.MdListItem
import com.md4a.ast.MdParagraph
import com.md4a.ast.MdTable
import com.md4a.ast.MdThematicBreak
import org.commonmark.node.BlockQuote
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Markdown → [MdBlock] tree. Wraps commonmark-java configured for GitHub
 * Flavored Markdown (tables, strikethrough, task lists, autolinks).
 */
internal object Md4aParser {

    private val parser: Parser = Parser.builder()
        .extensions(
            listOf(
                TablesExtension.create(),
                org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),
                org.commonmark.ext.task.list.items.TaskListItemsExtension.create(),
                AutolinkExtension.create(),
            )
        )
        .build()

    fun parse(markdown: String): List<MdBlock> {
        val doc = parser.parse(markdown)
        return blockChildren(doc)
    }

    // ── Blocks ──────────────────────────────────────────────────────────

    private fun blockChildren(parent: Node): List<MdBlock> =
        parent.children().flatMap { block(it) }.toList()

    private fun block(node: Node): List<MdBlock> = when (node) {
        is Heading -> listOf(MdHeading(node.level, inlines(node)))
        is Paragraph -> listOf(MdParagraph(inlines(node)))
        is FencedCodeBlock -> listOf(MdCode(langOf(node.info), node.literal ?: ""))
        is IndentedCodeBlock -> listOf(MdCode(null, node.literal ?: ""))
        is BlockQuote -> listOf(MdBlockQuote(blockChildren(node)))
        is ThematicBreak -> listOf(MdThematicBreak)
        // Raw HTML converted into real blocks (headings/tables/images/…);
        // unconvertible junk is dropped rather than dumped as text.
        is HtmlBlock -> HtmlAdapters.convertBlock(node.literal ?: "")
        is BulletList -> listOf(listItems(node, ordered = false, start = null))
        is OrderedList -> listOf(listItems(node, ordered = true, start = node.startNumber))
        is org.commonmark.ext.gfm.tables.TableBlock -> listOf(table(node))
        else -> emptyList() // TaskListItemMarker and other extension-only nodes
    }

    private fun langOf(info: String?): String? =
        info?.trim()?.split(' ', '\t')?.firstOrNull()?.takeIf { it.isNotEmpty() }

    private fun listItems(list: Node, ordered: Boolean, start: Int?): MdBlock {
        var index = start ?: 1
        val items = mutableListOf<MdListItem>()
        for (li in list.children()) {
            li as ListItem
            // GFM task list: the extension emits a block-level
            // TaskListItemMarker as the first child of the ListItem.
            val marker = li.firstChild as? TaskListItemMarker
            val checked = marker?.isChecked ?: false
            items.add(MdListItem(ordered, if (ordered) index else null, marker?.isChecked, checked, blockChildren(li)))
            if (ordered) index++
        }
        return MdList(items)
    }

    private fun table(node: org.commonmark.ext.gfm.tables.TableBlock): MdTable {
        val head = node.children().filterIsInstance<TableHead>().firstOrNull()
        val body = node.children().filterIsInstance<TableBody>().firstOrNull()

        fun rowsOf(parent: Node?): List<List<MdCell>> =
            parent?.children()?.filterIsInstance<TableRow>()?.map { row ->
                row.children().filterIsInstance<TableCell>().map { cell ->
                    MdCell(inlineChildren(cell))
                }.toList()
            }?.toList() ?: emptyList()

        // Alignment is per-column; read it from the header cells when present.
        val aligns = head?.children()?.filterIsInstance<TableRow>()?.firstOrNull()
            ?.children()?.filterIsInstance<TableCell>()
            ?.map { cell ->
                when (cell.alignment) {
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.CENTER -> MdTable.Align.CENTER
                    org.commonmark.ext.gfm.tables.TableCell.Alignment.RIGHT -> MdTable.Align.RIGHT
                    else -> MdTable.Align.LEFT
                }
            }?.toList() ?: emptyList()

        val header = rowsOf(head).firstOrNull() ?: emptyList()
        val rows = rowsOf(body)
        val columnCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
        val normalizedAligns = List(columnCount) { aligns.getOrNull(it) ?: MdTable.Align.LEFT }
        return MdTable(header, rows, normalizedAligns)
    }

    // ── Inlines ─────────────────────────────────────────────────────────

    private fun inlines(parent: Node): List<MdInline> = inlineChildren(parent)

    private fun inlineChildren(parent: Node): List<MdInline> {
        // Inline HTML (<a>, <img>, <kbd>, <br>, …) is stitched into real
        // nodes by InlineSink instead of being dropped.
        val sink = HtmlAdapters.InlineSink()
        for (child in parent.children()) {
            sink.feed(child)
        }
        return sink.finish()
    }

    private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }
}

