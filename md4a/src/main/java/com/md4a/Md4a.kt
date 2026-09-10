package com.md4a

import com.md4a.ast.MdBlock
import com.md4a.internal.Md4aParser

/**
 * MD4a — a mobile-first GitHub Flavored Markdown parser + renderer for Android.
 *
 * Two entry points:
 *  - [parse]: Markdown source → [MdBlock] tree (framework-free, unit-testable).
 *  - [com.md4a.render.Md4aDocument]: Composable that renders source or blocks
 *    on screen (LazyColumn-backed, images/tables/code/task lists supported).
 */
object Md4a {

    /** Parse GFM markdown into the MD4a block AST. Cheap enough to call on IO. */
    @JvmStatic
    fun parse(markdown: String): List<MdBlock> = Md4aParser.parse(markdown)
}
