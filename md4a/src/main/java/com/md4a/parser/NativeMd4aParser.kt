package com.md4a.parser

import com.md4a.ast.MdBlock

/**
 * C (md4c) backed parser. Serializes md4c parse events into a flat byte
 * stream via JNI, then decodes it into the same [MdBlock] AST as
 * [Md4aParser] (commonmark-java). See md4a/src/main/cpp/ for the C side.
 *
 * Use via [com.md4a.Md4a.parse] with [com.md4a.Md4a.Engine.NATIVE].
 * Library availability is probed once: [parse] throws
 * [IllegalStateException] when the .so cannot be loaded (e.g. JVM unit
 * tests, unsupported ABI) so callers can fall back to the Kotlin engine.
 */
internal object NativeMd4aParser {

    private val available: Boolean = try {
        System.loadLibrary("md4a")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun parse(markdown: String): List<MdBlock> {
        check(available) { "md4a native library unavailable" }
        val bytes = nativeParse(markdown.toByteArray(Charsets.UTF_8))
            ?: throw IllegalStateException("md4a native parse failed")
        return Md4aEventDecoder.decode(bytes)
    }

    private external fun nativeParse(markdown: ByteArray): ByteArray?
}
