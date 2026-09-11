package com.md4a.ast

/**
 * Recursively extracts images out of an inline tree so renderers can draw them
 * as block-level [MdImage]s (GitHub renders README images this way even when
 * they sit in table cells, headings, or captioned links).
 *
 * Returns (remaining inline tree, images in document order).
 *
 * Rules (mirrors what the render layer previously did only for paragraph
 * top-levels, extended to nested containers):
 *  - a bare [MdImage] is extracted;
 *  - a [MdLink] whose children contain images keeps its non-image children as
 *    a link (caption links like `[![img](u) text](href)`) and yields the image;
 *    a link whose only child is the image is consumed entirely;
 *  - [MdEmphasis]/[MdStrikethrough] wrapping images are split the same way.
 */
fun extractBlockImages(inlines: List<MdInline>): Pair<List<MdInline>, List<MdImage>> {
    val images = ArrayList<MdImage>()

    fun split(list: List<MdInline>): List<MdInline> =
        list.mapNotNull { inline ->
            when (inline) {
                is MdImage -> { images.add(inline); null }
                is MdLink -> {
                    val inner = split(inline.children)
                    if (inner.isEmpty()) null
                    else inline.copy(children = inner)
                }
                is MdEmphasis -> {
                    val inner = split(inline.children)
                    if (inner.isEmpty()) null else inline.copy(children = inner)
                }
                is MdStrikethrough -> {
                    val inner = split(inline.children)
                    if (inner.isEmpty()) null else inline.copy(children = inner)
                }
                else -> inline
            }
        }

    val text = split(inlines)
    if (images.isEmpty()) return inlines to emptyList()
    // Once images were pulled out, whitespace remnants and dangling breaks
    // between them carry no meaning anymore.
    val cleaned = text.filterNot { it is MdText && it.text.isBlank() || it is MdHardBreak }
    return cleaned to images
}
