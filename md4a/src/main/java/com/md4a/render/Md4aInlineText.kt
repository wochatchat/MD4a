package com.md4a.render

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.md4a.ast.MdCodeSpan
import com.md4a.ast.MdEmphasis
import com.md4a.ast.MdHardBreak
import com.md4a.ast.MdImage
import com.md4a.ast.MdInline
import com.md4a.ast.MdLink
import com.md4a.ast.MdStrikethrough
import com.md4a.ast.MdText

/**
 * Renders a list of inline nodes into a single [Text].
 *
 * One [androidx.compose.ui.text.AnnotatedString] per paragraph keeps the
 * recomposition cost O(1) per block — links are native [LinkAnnotation]s so
 * long-press/click behavior matches platform text.
 */
@Composable
fun Md4aInlineText(
    inlines: List<MdInline>,
    modifier: Modifier = Modifier,
    colorScheme: Md4aColorScheme,
    typography: Md4aTypography,
    style: TextStyle = typography.body,
    color: Color = colorScheme.text,
    onLinkClick: (String) -> Unit = {},
) {
    val annotated = buildAnnotatedString {
        appendInlines(inlines, colorScheme, onLinkClick)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlines(
    inlines: List<MdInline>,
    scheme: Md4aColorScheme,
    onLinkClick: (String) -> Unit,
) {
    for (node in inlines) {
        when (node) {
            is MdText -> append(node.text)
            is MdHardBreak -> append('\n')
            is MdCodeSpan -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = scheme.codeBackground,
                    color = scheme.codeText,
                )
            ) { append(node.code) }
            is MdEmphasis -> withStyle(
                SpanStyle(
                    fontWeight = if (node.strong) FontWeight.Bold else FontWeight.Medium,
                    fontStyle = if (node.strong) null else FontStyle.Italic,
                )
            ) { appendInlines(node.children, scheme, onLinkClick) }
            is MdStrikethrough -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough)
            ) { appendInlines(node.children, scheme, onLinkClick) }
            is MdLink -> {
                val annotation = LinkAnnotation.Url(
                    url = node.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = scheme.link, textDecoration = TextDecoration.Underline)
                    ),
                    linkInteractionListener = { link -> onLinkClick((link as LinkAnnotation.Url).url) },
                )
                val index = pushLink(annotation)
                appendInlines(node.children, scheme, onLinkClick)
                pop(index)
            }
            // Inline images (badges) degrade to alt text; block-level images
            // are rendered as real <Image>s by the paragraph renderer.
            is MdImage -> withStyle(SpanStyle(color = scheme.textSecondary)) { append(node.alt) }
            is com.md4a.ast.MdHtmlInline -> Unit
        }
    }
}
