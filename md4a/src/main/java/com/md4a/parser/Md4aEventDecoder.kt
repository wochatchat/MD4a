package com.md4a.parser

import com.md4a.ast.MdBlock
import com.md4a.ast.MdBlockQuote
import com.md4a.ast.MdCell
import com.md4a.ast.MdCode
import com.md4a.ast.MdCodeSpan
import com.md4a.ast.MdEmphasis
import com.md4a.ast.MdHardBreak
import com.md4a.ast.MdHeading
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

/**
 * Decodes the flat event stream produced by the C side (md4a_events.c) into
 * the [MdBlock] AST. Opcodes mirror cpp/md4a_events.h.
 *
 * Inline HTML fragments (`<a href=…>`, `<img …>`, `<kbd>`, …) are stitched
 * into real inline nodes by [NativeInlineSink], mirroring the commonmark-java
 * path's HtmlAdapters.InlineSink.
 */
internal object Md4aEventDecoder {

    private val trace = StringBuilder()

    private const val QUOTE_BEGIN = 0x01
    private const val QUOTE_END = 0x02
    private const val LIST_BEGIN = 0x03
    private const val LIST_END = 0x04
    private const val LI_BEGIN = 0x05
    private const val LI_END = 0x06
    private const val H_BEGIN = 0x07
    private const val H_END = 0x08
    private const val P_BEGIN = 0x09
    private const val P_END = 0x0A
    private const val CODE_BEGIN = 0x0B
    private const val CODE_END = 0x0C
    private const val HTML_BEGIN = 0x0D
    private const val HTML_END = 0x0E
    private const val TABLE_BEGIN = 0x0F
    private const val TABLE_END = 0x10
    private const val THEAD_BEGIN = 0x11
    private const val THEAD_END = 0x12
    private const val TBODY_BEGIN = 0x13
    private const val TBODY_END = 0x14
    private const val TR_BEGIN = 0x15
    private const val TR_END = 0x16
    private const val TD_BEGIN = 0x17
    private const val TD_END = 0x18
    private const val HR = 0x19

    private const val TEXT = 0x20
    private const val ENTITY = 0x21
    private const val SOFTBR = 0x22
    private const val BR = 0x23
    private const val EM_BEGIN = 0x24
    private const val EM_END = 0x25
    private const val DEL_BEGIN = 0x26
    private const val DEL_END = 0x27
    private const val A_BEGIN = 0x28
    private const val A_END = 0x29
    private const val IMG_BEGIN = 0x2A
    private const val IMG_END = 0x2B
    private const val CODESPAN_BEGIN = 0x2C
    private const val CODESPAN_END = 0x2D
    private const val HTML_INLINE = 0x2E

    fun decode(bytes: ByteArray): List<MdBlock> {
        val r = Reader(bytes)
        val stack = ArrayDeque<Ctx>()
        val tracing = System.getProperty("md4a.trace") != null
        if (tracing) trace.setLength(0)
        stack.addLast(BlocksCtx()) // document root

        while (r.pos < bytes.size) {
            val op = r.u8()
            // Block-level events close any implicit paragraph of a tight list
            // item (md4c emits no MD_BLOCK_P inside tight lists).
            if (op < 0x20) flushTightPara(stack)
            if (tracing) trace.appendLine("0x%02X %s".format(op, stack.joinToString("+") { it::class.simpleName!! }))
            when (op) {
                P_BEGIN -> stack.addLast(InlineCtx())
                P_END -> popInline(stack).let { (ctx, inlines) ->
                    blocksTop(stack).blocks.add(MdParagraph(inlines))
                }
                H_BEGIN -> stack.addLast(InlineCtx(level = r.u8()))
                H_END -> popInline(stack).let { (ctx, inlines) ->
                    blocksTop(stack).blocks.add(MdHeading(ctx.level, inlines))
                }
                CODE_BEGIN -> stack.addLast(CodeCtx(r.str()))
                CODE_END -> (stack.removeLast() as CodeCtx).let { ctx ->
                    blocksTop(stack).blocks.add(MdCode(ctx.lang.takeIf { it.isNotEmpty() }, ctx.sb.toString()))
                }
                HTML_BEGIN -> stack.addLast(HtmlCtx())
                HTML_END -> (stack.removeLast() as HtmlCtx).let { ctx ->
                    blocksTop(stack).blocks.addAll(HtmlAdapters.convertBlock(ctx.sb.toString()))
                }
                QUOTE_BEGIN -> stack.addLast(BlocksCtx())
                QUOTE_END -> (stack.removeLast() as BlocksCtx).let { ctx ->
                    blocksTop(stack).blocks.add(MdBlockQuote(ctx.blocks))
                }
                LIST_BEGIN -> {
                    val ordered = r.u8() == 1
                    val start = r.u32()
                    stack.addLast(ListCtx(ordered, if (ordered) start else 1))
                }
                LI_BEGIN -> {
                    val isTask = r.u8() == 1
                    val checked = r.u8() == 1
                    val list = stack.last() as ListCtx
                    val number = if (list.ordered) list.next++ else null
                    stack.addLast(LiCtx(number, if (isTask) checked else null, checked))
                }
                LI_END -> (stack.removeLast() as LiCtx).let { ctx ->
                    (stack.last() as ListCtx).items.add(
                        MdListItem((stack.last() as ListCtx).ordered, ctx.number, ctx.task, ctx.checked, ctx.blocks)
                    )
                }
                LIST_END -> (stack.removeLast() as ListCtx).let { ctx ->
                    blocksTop(stack).blocks.add(MdList(ctx.items))
                }
                TABLE_BEGIN -> stack.addLast(TableCtx())
                THEAD_BEGIN -> (stack.last() as TableCtx).inHead = true
                TBODY_BEGIN -> (stack.last() as TableCtx).inHead = false
                THEAD_END, TBODY_END -> {}
                TR_BEGIN -> (stack.last() as TableCtx).row = mutableListOf()
                TR_END -> (stack.last() as TableCtx).let { t ->
                    (if (t.inHead) t.headRows else t.bodyRows).add(t.row ?: mutableListOf())
                    t.row = null
                }
                TD_BEGIN -> {
                    val align = r.u8()
                    val table = stack.last() as TableCtx
                    if (table.inHead) table.aligns.add(alignOf(align))
                    stack.addLast(InlineCtx())
                }
                TD_END -> popInline(stack).let { (_, inlines) ->
                    (stack.last() as TableCtx).row?.add(MdCell(inlines))
                }
                TABLE_END -> (stack.removeLast() as TableCtx).let { t ->
                    val columnCount = maxOf(
                        t.headRows.maxOfOrNull { it.size } ?: 0,
                        t.bodyRows.maxOfOrNull { it.size } ?: 0,
                    )
                    val aligns = List(columnCount) { t.aligns.getOrNull(it) ?: MdTable.Align.LEFT }
                    blocksTop(stack).blocks.add(MdTable(t.headRows.firstOrNull() ?: emptyList(), t.bodyRows, aligns))
                }
                HR -> blocksTop(stack).blocks.add(MdThematicBreak)

                TEXT -> textEvent(stack, r)
                ENTITY -> inlineSink(stack).text(HtmlAdapters.decodeEntities(r.str()))
                SOFTBR -> inlineSink(stack).softBreak()
                BR -> inlineSink(stack).hardBreak()
                HTML_INLINE -> htmlInlineEvent(stack, r)
                EM_BEGIN -> inlineSink(stack).spanBegin(if (r.u8() == 1) NativeInlineSink.Kind.STRONG else NativeInlineSink.Kind.EM, null, null)
                DEL_BEGIN -> inlineSink(stack).spanBegin(NativeInlineSink.Kind.STRIKE, null, null)
                A_BEGIN -> {
                    val url = r.str(); val title = r.optStr()
                    inlineSink(stack).spanBegin(NativeInlineSink.Kind.LINK, url, title)
                }
                IMG_BEGIN -> {
                    val src = r.str(); val title = r.optStr()
                    inlineSink(stack).spanBegin(NativeInlineSink.Kind.IMG, src, title)
                }
                CODESPAN_BEGIN -> inlineSink(stack).spanBegin(NativeInlineSink.Kind.CODE, null, null)
                EM_END, DEL_END, A_END, IMG_END, CODESPAN_END -> inlineSink(stack).spanEnd()

                else -> throw IllegalArgumentException("unknown opcode 0x%02X at %d".format(op, r.pos - 1) + "\n" + trace.toString().takeLast(4000))
            }
        }
        return (stack.removeLast() as BlocksCtx).blocks
    }

    private fun alignOf(v: Int): MdTable.Align = when (v) {
        1 -> MdTable.Align.CENTER
        2 -> MdTable.Align.RIGHT
        else -> MdTable.Align.LEFT
    }

    /** Code-block text goes to the [CodeCtx] accumulator, other text to the current inline sink. */
    private fun textEvent(stack: ArrayDeque<Ctx>, r: Reader) {
        val s = r.str()
        val top = stack.lastOrNull()
        if (top is CodeCtx) top.sb.append(s) else inlineSink(stack).text(s)
    }

    /** Raw HTML: block-level chunks accumulate into [HtmlCtx], inline chunks go to the tag stitcher. */
    private fun htmlInlineEvent(stack: ArrayDeque<Ctx>, r: Reader) {
        val s = r.str()
        val top = stack.lastOrNull()
        if (top is HtmlCtx) top.sb.append(s) else inlineSink(stack).htmlTag(s)
    }

    private fun blocksTop(stack: ArrayDeque<Ctx>): BlocksCtx {
        for (i in stack.indices.reversed()) {
            val c = stack[i]
            if (c is BlocksCtx) return c
        }
        error("no block container on stack")
    }

    private fun inlineSink(stack: ArrayDeque<Ctx>): NativeInlineSink {
        val top = stack.lastOrNull()
        // Tight list item: no P block — inline events land directly in the LI.
        if (top is LiCtx) {
            val implicit = InlineCtx(tight = true)
            stack.addLast(implicit)
            return implicit.sink
        }
        for (i in stack.indices.reversed()) {
            val c = stack[i]
            if (c is InlineCtx) return c.sink
        }
        error("no inline context on stack; stack=${stack.joinToString() { it::class.simpleName!! }}\n${trace.toString().takeLast(4000)}")
    }

    private fun flushTightPara(stack: ArrayDeque<Ctx>) {
        val top = stack.lastOrNull()
        if (top is InlineCtx && top.tight) {
            stack.removeLast()
            blocksTop(stack).blocks.add(MdParagraph(top.sink.finish()))
        }
    }

    private fun popInline(stack: ArrayDeque<Ctx>): Pair<InlineCtx, List<MdInline>> {
        val ctx = stack.removeLast() as InlineCtx
        return ctx to ctx.sink.finish()
    }

    // ── Contexts ────────────────────────────────────────────────────────

    private open class BlocksCtx : Ctx() { val blocks = mutableListOf<MdBlock>() }

    private class LiCtx(val number: Int?, val task: Boolean?, val checked: Boolean) : BlocksCtx()

    private class ListCtx(val ordered: Boolean, var next: Int) : Ctx() {
        val items = mutableListOf<MdListItem>()
    }

    private class InlineCtx(val level: Int = 0, val tight: Boolean = false) : Ctx() {
        val sink = NativeInlineSink()
    }

    private class CodeCtx(val lang: String) : Ctx() {
        val sb = StringBuilder()
    }

    private class HtmlCtx : Ctx() {
        val sb = StringBuilder()
    }

    private class TableCtx : Ctx() {
        val headRows = mutableListOf<List<MdCell>>()
        val bodyRows = mutableListOf<List<MdCell>>()
        val aligns = mutableListOf<MdTable.Align>()
        var inHead = false
        var row: MutableList<MdCell>? = null
    }

    private open class Ctx

    // ── Byte reader ─────────────────────────────────────────────────────

    private class Reader(val b: ByteArray) {
        var pos = 0

        fun u8(): Int {
            val v = b[pos].toInt() and 0xFF
            pos++
            return v
        }

        fun u32(): Int {
            val v = (b[pos].toInt() and 0xFF) or
                ((b[pos + 1].toInt() and 0xFF) shl 8) or
                ((b[pos + 2].toInt() and 0xFF) shl 16) or
                ((b[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }

        fun str(): String {
            val n = u32()
            val s = String(b, pos, n, Charsets.UTF_8)
            pos += n
            return s
        }

        fun optStr(): String? = if (u8() == 1) str() else null
    }
}
