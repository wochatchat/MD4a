package com.md4a

import com.md4a.ast.MdBlock
import com.md4a.parser.Md4aParser
import com.md4a.parser.NativeMd4aParser

/**
 * MD4a — a mobile-first GitHub Flavored Markdown parser + renderer for Android.
 *
 * Two entry points:
 *  - [parse]: Markdown source → [MdBlock] tree (framework-free, unit-testable).
 *  - [com.md4a.render.Md4aDocument]: Composable that renders source or blocks
 *    on screen (LazyColumn-backed, images/tables/code/task lists supported).
 */
object Md4a {

    /** Parse engine selection. */
    enum class Engine {
        /** commonmark-java based pure-Kotlin parser (original, always available). */
        KOTLIN,

        /** C (md4c) via JNI — 10~40x faster on large documents. */
        NATIVE,

        /** NATIVE when the .so is loadable, KOTLIN otherwise. */
        AUTO,
    }

    /** Parse GFM markdown into the MD4a block AST with the default (Kotlin) engine. */
    @JvmStatic
    fun parse(markdown: String): List<MdBlock> = Md4aParser.parse(markdown)

    /** Parse with an explicit engine. [Engine.AUTO] falls back to KOTLIN if the native lib is unavailable. */
    @JvmStatic
    fun parse(markdown: String, engine: Engine): List<MdBlock> = when (engine) {
        Engine.KOTLIN -> Md4aParser.parse(markdown)
        Engine.NATIVE -> NativeMd4aParser.parse(markdown)
        Engine.AUTO -> try {
            NativeMd4aParser.parse(markdown)
        } catch (_: UnsatisfiedLinkError) {
            Md4aParser.parse(markdown)
        } catch (_: IllegalStateException) {
            Md4aParser.parse(markdown)
        }
    }
}
