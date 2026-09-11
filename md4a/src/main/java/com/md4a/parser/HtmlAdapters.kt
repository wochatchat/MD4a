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
import org.commonmark.node.Node

/**
 * Converts raw HTML (GitHub READMEs lean on it heavily: hero headers,
 * badge rows, `<picture>` logos, `<details>` sections, HTML tables) into
 * MD4a AST nodes instead of dropping it or dumping it as raw text.
 *
 * A tiny tag tokenizer + tree builder is enough — README HTML is
 * machine-generated and regular; we never need a full DOM.
 */
internal object HtmlAdapters {

    private val TAG_RE =
        Regex("""<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9-]*)((?:"[^"]*"|'[^']*'|[^>"'])*)\s*(/?)\s*>""")
    private val ATTR_RE =
        Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
    private val ALIGN_STYLE_RE = Regex("""text-align\s*:\s*(center|right|left)""")

    private val VOID_TAGS = setOf(
        "img", "br", "hr", "input", "meta", "link", "source", "area", "base", "col", "embed", "track", "wbr",
    )

    // ── Public entry points ─────────────────────────────────────────────

    /** Convert one CommonMark HTML block into zero or more MD4a blocks. */
    fun convertBlock(raw: String): List<MdBlock> {
        val cleaned = raw
            .replace(Regex("(?s)<!--.*?-->"), "")
            .replace(Regex("(?is)<(script|style)\\b.*?</\\s*\\1\\s*>"), "")
        if (cleaned.isBlank()) return emptyList()
        val root = parseTree(cleaned) ?: return emptyList()
        val blocks = mapBlocks(root.children)
        if (blocks.isNotEmpty()) return blocks
        val text = collapseSpace(decodeEntities(flattenText(root))).trim()
        return if (text.isEmpty()) emptyList() else listOf(MdParagraph(listOf(MdText(text))))
    }

    // ── Inline-level HTML (inside markdown paragraphs) ──────────────────

    /**
     * Consumes a flat sibling sequence containing [org.commonmark.node.HtmlInline]
     * markers and stitches open/close tags into nested inline nodes.
     */
    internal class InlineSink {

        private enum class Mode { LINK, STRONG, EM, STRIKE, CODE }

        private class Frame(val mode: Mode, val url: String?) {
            val children = mutableListOf<MdInline>()
        }

        private val stack = ArrayDeque<Frame>()
        private val out = mutableListOf<MdInline>()

        fun feed(node: Node) {
            when (node) {
                is org.commonmark.node.HtmlInline -> handleTag(node.literal ?: "")
                is org.commonmark.node.Text -> emit(MdText(decodeEntities(node.literal ?: "")))
                is org.commonmark.node.SoftLineBreak -> emit(MdText(" "))
                is org.commonmark.node.HardLineBreak -> emit(MdHardBreak)
                is org.commonmark.node.Code -> emit(MdCodeSpan(node.literal ?: ""))
                is org.commonmark.node.Emphasis -> emit(MdEmphasis(false, nested(node)))
                is org.commonmark.node.StrongEmphasis -> emit(MdEmphasis(true, nested(node)))
                is org.commonmark.ext.gfm.strikethrough.Strikethrough -> emit(MdStrikethrough(nested(node)))
                is org.commonmark.node.Link -> emit(MdLink(nested(node), node.destination ?: "", node.title))
                is org.commonmark.node.Image -> emit(MdImage(node.destination ?: "", altText(node)))
                else -> Unit
            }
        }

        fun finish(): List<MdInline> {
            // Unclosed tags at end of sequence: unwrap, keep their content.
            while (stack.isNotEmpty()) out.addAll(stack.removeLast().children)
            return out.toList()
        }

        private fun emit(inline: MdInline) {
            (stack.lastOrNull()?.children ?: out).add(inline)
        }

        private fun handleTag(tag: String) {
            val m = TAG_RE.matchEntire(tag.trim()) ?: return
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val attrs = parseAttrs(m.groupValues[3])
            val selfClosed = m.groupValues[4] == "/" || name in VOID_TAGS
            if (closing) {
                if (name in WRAP_TAGS || name == "a") closeFrame()
                return
            }
            when (name) {
                "img" -> {
                    val src = attrs["src"] ?: attrs["srcset"]?.trim()?.substringBefore(' ') ?: return
                    emit(MdImage(decodeEntities(src), decodeEntities(attrs["alt"] ?: ""), pxLen(attrs["width"]), pxLen(attrs["height"])))
                }
                "br" -> emit(MdHardBreak)
                "a", in WRAP_TAGS -> if (!selfClosed) {
                    stack.addLast(when (name) {
                        "a" -> Frame(Mode.LINK, decodeEntities(attrs["href"] ?: ""))
                        "b", "strong" -> Frame(Mode.STRONG, null)
                        "i", "em", "cite", "var" -> Frame(Mode.EM, null)
                        "s", "strike", "del" -> Frame(Mode.STRIKE, null)
                        else -> Frame(Mode.CODE, null) // code, kbd, samp
                    })
                }
                else -> Unit // unknown tag: transparent
            }
        }

        private fun closeFrame() {
            val frame = stack.removeLastOrNull() ?: return
            when (frame.mode) {
                Mode.LINK -> emit(MdLink(frame.children, frame.url ?: "", null))
                Mode.STRONG -> emit(MdEmphasis(true, frame.children))
                Mode.EM -> emit(MdEmphasis(false, frame.children))
                Mode.STRIKE -> emit(MdStrikethrough(frame.children))
                Mode.CODE -> emit(MdCodeSpan(frame.children.filterIsInstance<MdText>().joinToString("") { it.text }))
            }
        }

        private fun nested(parent: Node): List<MdInline> {
            val sink = InlineSink()
            var child = parent.firstChild
            while (child != null) { sink.feed(child); child = child.next }
            return sink.finish()
        }

        private fun altText(image: Node): String = buildString {
            var child = image.firstChild
            while (child != null) {
                when (child) {
                    is org.commonmark.node.Text -> append(child.literal)
                    is org.commonmark.node.Code -> append(child.literal)
                }
                child = child.next
            }
        }

        companion object {
            private val WRAP_TAGS = setOf("b", "strong", "i", "em", "cite", "var", "s", "strike", "del", "code", "kbd", "samp")
        }
    }

    // ── Attribute / entity helpers ──────────────────────────────────────

    private fun parseAttrs(raw: String): Map<String, String> =
        ATTR_RE.findAll(raw).associate {
            it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3].ifEmpty { it.groupValues[4] } })
        }

    private val NAMED_ENTITIES = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to " ", "copy" to "©", "reg" to "®", "trade" to "™",
        "mdash" to "—", "ndash" to "–", "hellip" to "…", "middot" to "·", "bull" to "•",
        "laquo" to "«", "raquo" to "»", "times" to "×", "plusmn" to "±", "deg" to "°",
        "rarr" to "→", "larr" to "←", "harr" to "↔", "check" to "✓", "star" to "★",
        "eacute" to "é", "egrave" to "è", "agrave" to "à", "ccedil" to "ç", "uuml" to "ü",
        "ouml" to "ö", "auml" to "ä", "szlig" to "ß", "ntilde" to "ñ",
    )

    fun decodeEntities(s: String): String = buildString(s.length) {
        var i = 0
        val n = s.length
        while (i < n) {
            val c = s[i]
            if (c != '&') { append(c); i++; continue }
            val semi = s.indexOf(';', i + 1)
            if (semi < 0 || semi - i > 12) { append(c); i++; continue }
            val body = s.substring(i + 1, semi)
            val decoded = when {
                body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.toChar()?.toString()
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.toChar()?.toString()
                else -> NAMED_ENTITIES[body.lowercase()]
            }
            if (decoded != null) { append(decoded); i = semi + 1 } else { append(c); i++ }
        }
    }

    private fun collapseSpace(s: String) = s.replace(Regex("[ \t\r\n]+"), " ")

    // ── Block-level tree building ───────────────────────────────────────

    private class Element(val name: String, val attrs: Map<String, String>) {
        val children = mutableListOf<Any>() // Element | String(raw text)
    }

    private fun parseTree(html: String): Element? {
        val root = Element("#root", emptyMap())
        val stack = ArrayDeque<Element>().apply { addLast(root) }
        var i = 0
        val n = html.length
        val text = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                stack.last().children.add(text.toString())
                text.clear()
            }
        }
        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0) { text.append(html, i, n); break }
            text.append(html, i, lt)
            val m = TAG_RE.find(html, lt)
            if (m == null) { text.append('<'); i = lt + 1; continue }
            i = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            val name = m.groupValues[2].lowercase()
            val attrs = parseAttrs(m.groupValues[3])
            val selfClosed = m.groupValues[4] == "/" || name in VOID_TAGS
            if (closing) {
                flushText()
                val idx = stack.indexOfLast { it.name == name }
                if (idx > 0) {
                    while (stack.size > idx) {
                        val el = stack.removeLast()
                        stack.last().children.add(el)
                    }
                }
            } else {
                flushText()
                if (selfClosed) {
                    stack.last().children.add(Element(name, attrs))
                } else {
                    stack.addLast(Element(name, attrs))
                }
            }
        }
        flushText()
        while (stack.size > 1) { // auto-close leftovers
            val el = stack.removeLast()
            stack.last().children.add(el)
        }
        return root.takeIf { it.children.isNotEmpty() }
    }

    private val BLOCK_TAGS = setOf(
        "h1", "h2", "h3", "h4", "h5", "h6", "p", "img", "pre", "details", "table",
        "ul", "ol", "blockquote", "hr", "dl", "picture",
    )
    private val TRANSPARENT_BLOCK_TAGS = setOf(
        "div", "center", "figure", "section", "header", "footer", "main", "aside",
        "nav", "article", "font", "span", "a", "video", "audio", "form", "picture",
    )

    private fun mapBlocks(children: List<Any>): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        var inlineBuf = mutableListOf<MdInline>()
        fun flushInline() {
            if (inlineBuf.any { it !is MdText || it.text.isNotBlank() }) {
                blocks.add(MdParagraph(inlineBuf.toList()))
            }
            inlineBuf = mutableListOf()
        }
        for (child in children) {
            when (child) {
                is String -> {
                    val t = collapseSpace(decodeEntities(child))
                    if (t.isNotEmpty()) inlineBuf.add(MdText(t))
                }
                is Element -> when {
                    child.name in BLOCK_TAGS -> { flushInline(); blocks.addAll(mapBlockElement(child)) }
                    child.name in TRANSPARENT_BLOCK_TAGS -> { flushInline(); blocks.addAll(mapBlocks(child.children)) }
                    else -> inlineBuf.addAll(mapInlines(listOf(child)))
                }
            }
        }
        flushInline()
        return blocks
    }

    private fun mapBlockElement(el: Element): List<MdBlock> = when (el.name) {
        "h1", "h2", "h3", "h4", "h5", "h6" ->
            listOf(MdHeading(el.name[1].digitToInt(), mapInlines(el.children)))
        "p" -> listOf(MdParagraph(mapInlines(el.children)))
        "img" -> listOf(MdParagraph(listOf(imageOf(el))))
        "picture" -> {
            val img = el.children.filterIsInstance<Element>().firstOrNull { it.name == "img" }
            if (img != null) mapBlockElement(img) else mapBlocks(el.children)
        }
        "pre" -> listOf(codeFromPre(el))
        "blockquote" -> listOf(MdBlockQuote(mapBlocks(el.children)))
        "hr" -> listOf(MdThematicBreak)
        "details" -> detailsBlocks(el)
        "ul", "ol" -> listOf(listFrom(el))
        "dl" -> dlFrom(el)
        "table" -> {
            val t = tableFrom(el)
            if (t != null) listOf(t) else mapBlocks(el.children)
        }
        else -> mapBlocks(el.children)
    }

    private fun detailsBlocks(el: Element): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        var summarySeen = false
        for (c in el.children) {
            when {
                c is Element && c.name == "summary" && !summarySeen -> {
                    summarySeen = true
                    out.add(MdParagraph(listOf(MdEmphasis(true, mapInlines(c.children)))))
                }
                c is Element -> out.addAll(mapBlockElement(c))
                else -> out.addAll(mapBlocks(listOf(c)))
            }
        }
        return out
    }

    private fun listFrom(el: Element): MdList {
        val ordered = el.name == "ol"
        val start = el.attrs["start"]?.toIntOrNull() ?: 1
        val items = el.children.filterIsInstance<Element>()
            .filter { it.name == "li" }
            .mapIndexed { idx, li ->
                MdListItem(ordered, if (ordered) start + idx else null, null, false, mapBlocks(li.children))
            }
        return MdList(items)
    }

    private fun dlFrom(el: Element): List<MdBlock> {
        val items = mutableListOf<MdListItem>()
        var term: String? = null
        for (c in el.children) {
            if (c !is Element) continue
            when (c.name) {
                "dt" -> term = flattenText(c).trim()
                "dd" -> items.add(
                    MdListItem(
                        false, null, null, false,
                        listOf(MdParagraph(listOf(MdText(((term?.let { "$it — " } ?: "") + flattenText(c).trim()))))),
                    )
                )
            }
        }
        return if (items.isEmpty()) emptyList() else listOf(MdList(items))
    }

    private fun codeFromPre(el: Element): MdCode {
        val codeEl = el.children.filterIsInstance<Element>().firstOrNull { it.name == "code" }
        val lang = codeEl?.attrs?.get("class")
            ?.split(' ', ':', ';')
            ?.firstOrNull { it.startsWith("language-") || it.startsWith("lang-") }
            ?.substringAfter('-')
        val code = flattenText(if (codeEl != null) codeEl else el).removePrefix("\n")
        return MdCode(lang?.takeIf { it.isNotBlank() }, code)
    }

    private fun tableFrom(el: Element): MdTable? {
        val rows = el.children.filterIsInstance<Element>()
            .flatMap { if (it.name in setOf("thead", "tbody", "tfoot")) it.children.filterIsInstance<Element>() else listOf(it) }
            .filter { it.name == "tr" }
        if (rows.isEmpty()) return null
        fun cellsOf(tr: Element): List<MdCell> = tr.children.filterIsInstance<Element>()
            .filter { it.name == "th" || it.name == "td" }
            .map { MdCell(mapInlines(it.children)) }
        val headerRow = rows.firstOrNull { r -> r.children.filterIsInstance<Element>().any { it.name == "th" } }
        val header = headerRow?.let { cellsOf(it) } ?: emptyList()
        val bodyRows = (if (headerRow != null) rows - headerRow else rows).map { cellsOf(it) }
        val aligns = headerRow?.children?.filterIsInstance<Element>()
            ?.filter { it.name == "th" || it.name == "td" }
            ?.map {
                val a = it.attrs["align"]?.lowercase()
                    ?: it.attrs["style"]?.let { s -> ALIGN_STYLE_RE.find(s)?.groupValues?.get(1)?.lowercase() }
                when (a) {
                    "center" -> MdTable.Align.CENTER
                    "right" -> MdTable.Align.RIGHT
                    else -> MdTable.Align.LEFT
                }
            } ?: emptyList()
        val columnCount = maxOf(header.size, bodyRows.maxOfOrNull { it.size } ?: 0)
        return MdTable(header, bodyRows, List(columnCount) { aligns.getOrNull(it) ?: MdTable.Align.LEFT })
    }

    // ── Inline mapping inside HTML elements ─────────────────────────────

    private fun mapInlines(children: List<Any>): List<MdInline> {
        val out = mutableListOf<MdInline>()
        for (child in children) {
            when (child) {
                is String -> {
                    val t = collapseSpace(decodeEntities(child))
                    if (t.isNotEmpty()) out.add(MdText(t))
                }
                is Element -> when (child.name) {
                    "img" -> out.add(imageOf(child))
                    "picture" -> child.children.filterIsInstance<Element>()
                        .firstOrNull { it.name == "img" }?.let { out.add(imageOf(it)) }
                    "br" -> out.add(MdHardBreak)
                    "a" -> {
                        val href = decodeEntities(child.attrs["href"] ?: "")
                        val inner = mapInlines(child.children)
                        when {
                            inner.isEmpty() -> Unit // anchor target, nothing visible
                            href.isBlank() -> out.addAll(inner)
                            else -> out.add(MdLink(inner, href, null))
                        }
                    }
                    "b", "strong" -> out.add(MdEmphasis(true, mapInlines(child.children)))
                    "i", "em", "cite", "var" -> out.add(MdEmphasis(false, mapInlines(child.children)))
                    "s", "strike", "del" -> out.add(MdStrikethrough(mapInlines(child.children)))
                    "code", "kbd", "samp" -> out.add(MdCodeSpan(flattenText(child).trim()))
                    else -> out.addAll(mapInlines(child.children)) // transparent
                }
            }
        }
        return out
    }

    private fun imageOf(el: Element): MdImage {
        val src = el.attrs["src"]
            ?: el.attrs["srcset"]?.trim()?.substringBefore(' ')
            ?: el.attrs["data-src"]
            ?: ""
        return MdImage(decodeEntities(src), decodeEntities(el.attrs["alt"] ?: ""), pxLen(el.attrs["width"]), pxLen(el.attrs["height"]))
    }

    /** HTML px length → dp hint; percentages and junk values are ignored. */
    private fun pxLen(v: String?): Int? =
        v?.trim()?.removeSuffix("px")?.toIntOrNull()?.takeIf { it in 8..2000 }

    private fun flattenText(el: Element): String = buildString {
        for (c in el.children) {
            when (c) {
                is String -> append(c)
                is Element -> append(flattenText(c))
            }
        }
    }
}
