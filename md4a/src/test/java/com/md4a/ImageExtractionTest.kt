package com.md4a

import com.md4a.ast.*
import org.junit.Assert.*
import org.junit.Test

class ImageExtractionTest {

    private fun img(url: String) = MdImage(url, "alt:$url")

    @Test fun `bare top-level image extracted`() {
        val (rest, imgs) = extractBlockImages(listOf(img("a.png")))
        assertTrue(rest.isEmpty()); assertEquals(listOf("a.png"), imgs.map { it.url })
    }

    @Test fun `paragraph without images untouched`() {
        val src = listOf(MdText("hi "), MdLink(listOf(MdText("x")), "u", null))
        val (rest, imgs) = extractBlockImages(src)
        assertEquals(src, rest); assertTrue(imgs.isEmpty())
    }

    @Test fun `lone link-wrapped image consumed`() {
        val (rest, imgs) = extractBlockImages(listOf(MdLink(listOf(img("a.png")), "href", null)))
        assertTrue(rest.isEmpty()); assertEquals(listOf("a.png"), imgs.map { it.url })
    }

    @Test fun `caption link keeps caption as link`() {
        val link = MdLink(listOf(img("i.png"), MdText("Docs")), "href", null)
        val (rest, imgs) = extractBlockImages(listOf(link))
        assertEquals(1, imgs.size)
        val kept = rest.filterIsInstance<MdLink>().single()
        assertEquals("href", kept.url)
        assertEquals("Docs", (kept.children.single() as MdText).text)
    }

    @Test fun `image inside emphasis unwrapped`() {
        val (rest, imgs) = extractBlockImages(listOf(MdEmphasis(true, listOf(img("a.png")))))
        assertTrue(rest.isEmpty()); assertEquals(listOf("a.png"), imgs.map { it.url })
    }

    @Test fun `emphasis keeps non-image children`() {
        val (rest, imgs) = extractBlockImages(listOf(MdEmphasis(false, listOf(img("a.png"), MdText("b")))))
        assertEquals(1, imgs.size)
        assertEquals("b", (rest.single() as MdEmphasis).children.single().let { (it as MdText).text })
    }

    @Test fun `blank remnants and hard breaks dropped after extraction`() {
        val (rest, imgs) = extractBlockImages(listOf(MdText(" "), img("a.png"), MdHardBreak, MdText("  "), img("b.png")))
        assertTrue(rest.isEmpty()); assertEquals(2, imgs.size)
    }

    @Test fun `table cell mixed text and image`() {
        val (rest, imgs) = extractBlockImages(listOf(MdText("v1.2 "), img("badge.svg")))
        assertEquals("v1.2", (rest.filterIsInstance<MdText>().single()).text.trimEnd()); assertEquals(1, imgs.size)
    }

    @Test fun `deep nesting inside emphasis in link`() {
        val src = listOf(MdLink(listOf(MdEmphasis(true, listOf(img("deep.png"), MdText("x")))), "h", null))
        val (rest, imgs) = extractBlockImages(src)
        assertEquals(1, imgs.size)
        val em = (rest.single() as MdLink).children.single() as MdEmphasis
        assertEquals("x", (em.children.single() as MdText).text)
    }

    @Test fun `document order preserved`() {
        val (rest, imgs) = extractBlockImages(listOf(img("1.png"), MdText("mid"), img("2.png")))
        assertEquals(listOf("1.png", "2.png"), imgs.map { it.url })
        assertEquals("mid", (rest.single() as MdText).text)
    }
}
