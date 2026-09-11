package com.md4a.parser

import com.md4a.ast.MdCodeSpan
import com.md4a.ast.MdEmphasis
import com.md4a.ast.MdHardBreak
import com.md4a.ast.MdImage
import com.md4a.ast.MdInline
import com.md4a.ast.MdLink
import com.md4a.ast.MdStrikethrough
import com.md4a.ast.MdText

/**
 * Event-driven port of [HtmlAdapters.InlineSink] for the native (md4c) path:
 * markdown spans arrive as begin/end events, raw inline HTML as tag chunks —
 * both are stitched into nested [MdInline] nodes with the same behavior
 * (b/strong→strong, i/em→em, s/del→strike, code/kbd/samp→code span,
 * img→[MdImage] with px→dp size hints, br→hard break, unclosed tags unwrap).
 */
internal class NativeInlineSink {

    private enum class Mode { LINK, STRONG, EM, STRIKE, CODE, IMG }

    private class Frame(val mode: Mode, val url: String?) {
        val children = mutableListOf<MdInline>()
    }

    private val stack = ArrayDeque<Frame>()
    private val out = mutableListOf<MdInline>()

    fun text(s: String) = emit(MdText(HtmlAdapters.decodeEntities(s)))

    fun softBreak() = emit(MdText(" "))

    fun hardBreak() = emit(MdHardBreak)

    fun htmlTag(tag: String) {
        val m = HtmlAdapters.TAG_RE.matchEntire(tag.trim()) ?: return
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val attrs = HtmlAdapters.parseAttrs(m.groupValues[3])
        val selfClosed = m.groupValues[4] == "/" || name in HtmlAdapters.VOID_TAGS
        if (closing) {
            if (name in HtmlAdapters.InlineSink.WRAP_TAGS || name == "a") closeFrame()
            return
        }
        when (name) {
            "img" -> {
                val src = attrs["src"] ?: attrs["srcset"]?.trim()?.substringBefore(' ') ?: return
                emit(MdImage(
                    HtmlAdapters.decodeEntities(src),
                    HtmlAdapters.decodeEntities(attrs["alt"] ?: ""),
                    HtmlAdapters.pxLen(attrs["width"]),
                    HtmlAdapters.pxLen(attrs["height"]),
                ))
            }
            "br" -> emit(MdHardBreak)
            "a", in HtmlAdapters.InlineSink.WRAP_TAGS -> if (!selfClosed) {
                stack.addLast(when (name) {
                    "a" -> Frame(Mode.LINK, HtmlAdapters.decodeEntities(attrs["href"] ?: ""))
                    "b", "strong" -> Frame(Mode.STRONG, null)
                    "i", "em", "cite", "var" -> Frame(Mode.EM, null)
                    "s", "strike", "del" -> Frame(Mode.STRIKE, null)
                    else -> Frame(Mode.CODE, null) // code, kbd, samp
                })
            }
            else -> Unit // unknown tag: transparent
        }
    }

    fun spanBegin(kind: Kind, url: String?, title: String?) {
        stack.addLast(when (kind) {
            Kind.EM -> Frame(Mode.EM, null)
            Kind.STRONG -> Frame(Mode.STRONG, null)
            Kind.STRIKE -> Frame(Mode.STRIKE, null)
            Kind.LINK -> Frame(Mode.LINK, url)
            Kind.IMG -> Frame(Mode.IMG, url)
            Kind.CODE -> Frame(Mode.CODE, null)
        })
    }

    fun spanEnd() {
        closeFrame()
    }

    fun finish(): List<MdInline> {
        // Unclosed tags/frames at end of sequence: unwrap, keep their content.
        while (stack.isNotEmpty()) out.addAll(stack.removeLast().children)
        return out.toList()
    }

    enum class Kind { EM, STRONG, STRIKE, LINK, IMG, CODE }

    private fun emit(inline: MdInline) {
        (stack.lastOrNull()?.children ?: out).add(inline)
    }

    private fun closeFrame() {
        val frame = stack.removeLastOrNull() ?: return
        when (frame.mode) {
            Mode.LINK -> emit(MdLink(frame.children, frame.url ?: "", null))
            Mode.STRONG -> emit(MdEmphasis(true, frame.children))
            Mode.EM -> emit(MdEmphasis(false, frame.children))
            Mode.STRIKE -> emit(MdStrikethrough(frame.children))
            Mode.CODE -> emit(MdCodeSpan(frame.children.filterIsInstance<MdText>().joinToString("") { it.text }))
            Mode.IMG -> emit(MdImage(
                frame.url ?: "",
                frame.children.filterIsInstance<MdText>().joinToString("") { it.text },
            ))
        }
    }
}
