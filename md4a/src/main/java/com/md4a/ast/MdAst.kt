package com.md4a.ast

/**
 * MD4a document model — a mobile-friendly subset of the GFM AST.
 *
 * The [com.md4a.Md4a] parser converts CommonMark/GFM source into this tree;
 * renderers (Compose in `com.md4a.render`) walk it to draw the document.
 */
sealed interface MdBlock

data class MdHeading(val level: Int, val inlines: List<MdInline>) : MdBlock
data class MdParagraph(val inlines: List<MdInline>) : MdBlock
data class MdCode(val language: String?, val code: String) : MdBlock

/** GFM table; every cell is a list of inline nodes. */
data class MdTable(
    val header: List<MdCell>,
    val rows: List<List<MdCell>>,
    val alignments: List<Align>,
) : MdBlock {
    enum class Align { LEFT, CENTER, RIGHT }
}

data class MdCell(val inlines: List<MdInline>)

/** A single `<li>`; `blocks` holds its body (paragraph, nested list, …). */
data class MdListItem(
    val ordered: Boolean,
    val number: Int?,
    val task: Boolean?,
    val checked: Boolean,
    val blocks: List<MdBlock>,
) : MdBlock

data class MdBlockQuote(val blocks: List<MdBlock>) : MdBlock

/** A `<ul>`/`<ol>` holding [MdListItem] entries. */
data class MdList(val items: List<MdListItem>) : MdBlock
data object MdThematicBreak : MdBlock
data class MdHtmlBlock(val raw: String) : MdBlock

sealed interface MdInline

data class MdText(val text: String) : MdInline
data class MdEmphasis(val strong: Boolean, val children: List<MdInline>) : MdInline
data class MdStrikethrough(val children: List<MdInline>) : MdInline
data class MdCodeSpan(val code: String) : MdInline
data class MdLink(val children: List<MdInline>, val url: String, val title: String?) : MdInline
data class MdImage(val url: String, val alt: String, val widthDp: Int? = null, val heightDp: Int? = null) : MdInline
data object MdHardBreak : MdInline
data class MdHtmlInline(val raw: String) : MdInline
