package com.md4a.parser

import com.md4a.ast.MdBlock
import com.md4a.ast.MdBlockQuote
import com.md4a.ast.MdCell
import com.md4a.ast.MdCode
import com.md4a.ast.MdCodeSpan
import com.md4a.ast.MdEmphasis
import com.md4a.ast.MdHardBreak
import com.md4a.ast.MdHeading
import com.md4a.ast.MdHtmlBlock
import com.md4a.ast.MdHtmlInline
import com.md4a.ast.MdImage
import com.md4a.ast.MdInline
import com.md4a.ast.MdLink
import com.md4a.ast.MdList
import com.md4a.ast.MdListItem
import com.md4a.ast.MdParagraph
import com.md4a.ast.MdStrikethrough
import com.md4a.ast.MdTable
import com.md4a.ast.MdText
import com.md4a.ast.MdThematicBreak
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
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
        parent.children().mapNotNull { block(it) }.toList()

    private fun block(node: Node): MdBlock? = when (node) {
        is Heading -> MdHeading(node.level, inlines(node))
        is Paragraph -> MdParagraph(inlines(node))
        is FencedCodeBlock -> MdCode(langOf(node.info), node.literal ?: "")
        is IndentedCodeBlock -> MdCode(null, node.literal ?: "")
        is BlockQuote -> MdBlockQuote(blockChildren(node))
        is ThematicBreak -> MdThematicBreak
        is HtmlBlock -> MdHtmlBlock(node.literal ?: "")
        is BulletList -> listItems(node, ordered = false, start = null)
        is OrderedList -> listItems(node, ordered = true, start = node.startNumber)
        is org.commonmark.ext.gfm.tables.TableBlock -> table(node)
        else -> null // TaskListItemMarker and other extension-only nodes
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
        val out = mutableListOf<MdInline>()
        for (child in parent.children()) {
            inline(child, out)
        }
        return out
    }

    private fun inline(node: Node, out: MutableList<MdInline>) {
        when (node) {
            is Text -> out.add(MdText(node.literal ?: ""))
            is SoftLineBreak -> out.add(MdText(" "))
            is HardLineBreak -> out.add(MdHardBreak)
            is Code -> out.add(MdCodeSpan(node.literal ?: ""))
            is Emphasis -> out.add(MdEmphasis(false, inlineChildren(node, false)))
            is StrongEmphasis -> out.add(MdEmphasis(true, inlineChildren(node, false)))
            is Strikethrough -> out.add(MdStrikethrough(inlineChildren(node, false)))
            is Link -> out.add(MdLink(inlineChildren(node, false), node.destination ?: "", node.title))
            is Image -> out.add(MdImage(node.destination ?: "", altText(node)))
            is HtmlInline -> {
                // Keep the few tags mobile text can actually honor; drop the rest.
                val tag = node.literal?.lowercase() ?: return
                if (tag.startsWith("<br")) out.add(MdHardBreak)
            }
            else -> Unit
        }
    }

    private fun Node.children(): Sequence<Node> = generateSequence(firstChild) { it.next }

    private fun altText(image: Node): String = buildString {
        for (child in image.children()) {
            when (child) {
                is Text -> append(child.literal)
                is Code -> append(child.literal)
            }
        }
    }
}

