package com.md4a.render

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import com.md4a.Md4a
import com.md4a.ast.MdBlock
import com.md4a.ast.MdBlockQuote
import com.md4a.ast.MdCode
import com.md4a.ast.MdCodeSpan
import com.md4a.ast.MdEmphasis
import com.md4a.ast.MdHardBreak
import com.md4a.ast.MdHeading
import com.md4a.ast.MdHtmlBlock
import com.md4a.ast.MdImage
import com.md4a.ast.MdInline
import com.md4a.ast.extractBlockImages
import com.md4a.ast.MdLink
import com.md4a.ast.MdList
import com.md4a.ast.MdListItem
import com.md4a.ast.MdParagraph
import com.md4a.ast.MdStrikethrough
import com.md4a.ast.MdTable
import com.md4a.ast.MdText
import com.md4a.ast.MdThematicBreak
import com.md4a.highlight.CodeHighlighter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Top-level SDK entry: parses [markdown] (off the main thread) and renders it.
 *
 * @param baseUrl base for resolving relative image URLs (e.g. the
 *   `raw.githubusercontent.com/<owner>/<repo>/<branch>/` of the README source).
 */
@Composable
fun Md4aDocument(
    markdown: String,
    modifier: Modifier = Modifier,
    colorScheme: Md4aColorScheme = rememberMd4aColorScheme(),
    typography: Md4aTypography = remember { Md4aTypography.default() },
    baseUrl: String? = null,
    onLinkClick: (String) -> Unit = {},
    engine: Md4a.Engine = Md4a.Engine.KOTLIN,
) {
    val blocks by produceState<List<MdBlock>?>(initialValue = null, markdown, engine) {
        value = withContext(Dispatchers.Default) { Md4a.parse(markdown, engine) }
    }
    when (val result = blocks) {
        null -> Box(modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        else -> Md4aBlocks(result, modifier, colorScheme, typography, baseUrl, onLinkClick)
    }
}

/** Render a pre-parsed block tree (use when you cache parsing yourself). */
@Composable
fun Md4aBlocks(
    blocks: List<MdBlock>,
    modifier: Modifier = Modifier,
    colorScheme: Md4aColorScheme = rememberMd4aColorScheme(),
    typography: Md4aTypography = remember { Md4aTypography.default() },
    baseUrl: String? = null,
    onLinkClick: (String) -> Unit = {},
) {
    val imageLoader = rememberMd4aImageLoader()
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxWidth(),
        userScrollEnabled = true,
    ) {
        items(
            count = blocks.size,
            contentType = { blocks[it]::class.simpleName },
        ) { index ->
            BlockView(
                block = blocks[index],
                colorScheme = colorScheme,
                typography = typography,
                baseUrl = baseUrl,
                imageLoader = imageLoader,
                onLinkClick = onLinkClick,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

// ── Block dispatch ──────────────────────────────────────────────────────

@Composable
private fun BlockView(
    block: MdBlock,
    colorScheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is MdHeading -> HeadingView(block, colorScheme, typography, baseUrl, imageLoader, onLinkClick, modifier)
        is MdParagraph -> ParagraphView(block, colorScheme, typography, baseUrl, imageLoader, onLinkClick, modifier)
        is MdCode -> CodeBlockView(block, colorScheme, typography, modifier)
        is MdTable -> TableView(block, colorScheme, typography, baseUrl, imageLoader, onLinkClick, modifier)
        is MdList -> ListView(block, colorScheme, typography, baseUrl, imageLoader, onLinkClick, modifier)
        is MdBlockQuote -> QuoteView(block, colorScheme, typography, baseUrl, imageLoader, onLinkClick, modifier)
        is MdThematicBreak -> HorizontalDivider(
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            thickness = 1.dp,
            color = colorScheme.rule,
        )
        is MdHtmlBlock -> {
            val raw = block.raw.trim()
            if (!raw.startsWith("<!--")) {
                Text(
                    text = raw,
                    modifier = modifier,
                    style = typography.code,
                    color = colorScheme.textSecondary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        else -> Unit
    }
}

// ── Headings ────────────────────────────────────────────────────────────

@Composable
private fun HeadingView(
    heading: MdHeading,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (textInlines, images) = remember(heading) { extractBlockImages(heading.inlines) }
    Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
        if (textInlines.isNotEmpty()) {
            Md4aInlineText(
                inlines = textInlines,
                colorScheme = scheme,
                typography = typography,
                style = when (heading.level) {
                    1 -> typography.h1; 2 -> typography.h2; 3 -> typography.h3
                    4 -> typography.h4; 5 -> typography.h5; else -> typography.h6
                },
                color = scheme.text,
                onLinkClick = onLinkClick,
            )
        }
        Md4aImageList(images, scheme, baseUrl, imageLoader)
        if (heading.level <= 2) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 5.dp),
                thickness = if (heading.level == 1) 2.dp else 1.dp,
                color = scheme.rule,
            )
        }
    }
}

// ── Paragraphs (+ image extraction for logo/banner patterns) ────────────

@Composable
private fun ParagraphView(
    paragraph: MdParagraph,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (textInlines, images) = remember(paragraph) { splitImages(paragraph.inlines) }
    Column(modifier = modifier.fillMaxWidth()) {
        if (textInlines.isNotEmpty()) {
            Md4aInlineText(
                inlines = textInlines,
                colorScheme = scheme,
                typography = typography,
                onLinkClick = onLinkClick,
            )
        }
        Md4aImageList(images, scheme, baseUrl, imageLoader)
    }
}

// Shared image list for paragraph/heading/table-cell extraction results.
@Composable
private fun Md4aImageList(
    images: List<MdImage>,
    scheme: Md4aColorScheme,
    baseUrl: String?,
    imageLoader: ImageLoader,
) {
    images.forEach { image ->
        Md4aImage(
            url = resolveUrl(image.url, baseUrl),
            alt = image.alt,
            widthDp = image.widthDp,
            heightDp = image.heightDp,
            colorScheme = scheme,
            imageLoader = imageLoader,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun splitImages(inlines: List<MdInline>): Pair<List<MdInline>, List<MdImage>> =
    extractBlockImages(inlines)

@Composable
fun Md4aImage(
    url: String,
    alt: String,
    colorScheme: Md4aColorScheme,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    widthDp: Int? = null,
    heightDp: Int? = null,
) {
    var ratio by androidx.compose.runtime.remember(url) {
        androidx.compose.runtime.mutableStateOf<Float?>(null)
    }
    // Badge services serve tiny wide banners; stretching them to full width
    // would blow a 20px-tall badge up to screen width.
    val isBadge = url.contains("shields.io") || url.contains("/badge/") || url.contains("badgen.net")
    // Size precedence: explicit width/height attr > badge heuristic > intrinsic ratio.
    // Tall/square images (ratio < 1.4) are capped at 260dp height instead of
    // being blown up to full screen width (SVG logos without width attr).
    val r = ratio
    val sizeModifier = when {
        widthDp != null -> Modifier
            .width(widthDp.coerceAtMost(340).dp)
            .then(
                if (r != null) Modifier.height((widthDp / r).dp)
                else Modifier.heightIn(min = 24.dp, max = 260.dp)
            )
        heightDp != null -> Modifier
            .height(heightDp.coerceAtMost(260).dp)
            .then(if (r != null) Modifier.aspectRatio(r) else Modifier.fillMaxWidth())
        isBadge -> Modifier.height(22.dp)
        r != null && r >= 1.4f -> Modifier.fillMaxWidth().aspectRatio(r)
        r != null -> Modifier.height(260.dp).aspectRatio(r)
        else -> Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 260.dp)
    }
    AsyncImage(
        model = url,
        contentDescription = alt.ifEmpty { null },
        imageLoader = imageLoader,
        modifier = modifier
            .then(sizeModifier)
            .clip(RoundedCornerShape(6.dp))
            .background(colorScheme.imagePlaceholder),
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f &&
                !size.width.isInfinite() && !size.height.isInfinite()
            ) {
                ratio = size.width / size.height
            }
        },
    )
}

internal fun resolveUrl(url: String, baseUrl: String?): String {
    if (url.isEmpty() || url.startsWith("http://") || url.startsWith("https://") ||
        url.startsWith("data:") || baseUrl == null
    ) return url
    return if (baseUrl.endsWith("/")) baseUrl + url.removePrefix("/") else "$baseUrl/$url"
}

@Composable
fun rememberMd4aImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                add(GifDecoder.Factory())
            }
            .crossfade(false)
            .build()
    }
}

// ── Code blocks ─────────────────────────────────────────────────────────

@Composable
private fun CodeBlockView(
    code: MdCode,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    modifier: Modifier = Modifier,
) {
    SelectionContainer(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.codeBackground)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = highlightedCode(code, scheme),
                style = typography.code,
                color = scheme.codeText,
            )
        }
    }
}

@Composable
private fun highlightedCode(code: MdCode, scheme: Md4aColorScheme): AnnotatedString =
    remember(code.code, code.language) {
        val spans = CodeHighlighter.tokenize(code.code, code.language)
        buildAnnotatedString {
            append(code.code)
            for (span in spans) {
                val color = when (span.type) {
                    CodeHighlighter.TokenType.KEYWORD -> scheme.syntaxKeyword
                    CodeHighlighter.TokenType.STRING -> scheme.syntaxString
                    CodeHighlighter.TokenType.COMMENT -> scheme.syntaxComment
                    CodeHighlighter.TokenType.NUMBER -> scheme.syntaxNumber
                }
                addStyle(SpanStyle(color = color), span.start, span.end)
            }
        }
    }

// ── Tables ──────────────────────────────────────────────────────────────

@Composable
private fun TableView(
    table: MdTable,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(scheme.codeBackground.copy(alpha = 0.4f))
            .horizontalScroll(rememberScrollState()),
    ) {
        // Header
        Row {
            table.header.forEachIndexed { col, cell ->
                TableCell(
                    inlines = cell.inlines,
                    align = table.alignments.getOrElse(col) { MdTable.Align.LEFT },
                    scheme = scheme,
                    typography = typography.copy(
                        tableCell = typography.tableCell.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    ),
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onLinkClick = onLinkClick,
                    background = scheme.tableHeaderBackground,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = scheme.tableBorder)
        table.rows.forEachIndexed { rowIndex, row ->
            Row {
                row.forEachIndexed { col, cell ->
                    TableCell(
                        inlines = cell.inlines,
                        align = table.alignments.getOrElse(col) { MdTable.Align.LEFT },
                        scheme = scheme,
                        typography = typography,
                        baseUrl = baseUrl,
                        imageLoader = imageLoader,
                        onLinkClick = onLinkClick,
                        background = Color.Transparent,
                    )
                }
            }
            if (rowIndex < table.rows.lastIndex) {
                HorizontalDivider(thickness = 0.5.dp, color = scheme.tableBorder.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TableCell(
    inlines: List<MdInline>,
    align: MdTable.Align,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    background: Color,
) {
    Box(
        modifier = Modifier
            .widthIn(min = 96.dp, max = 300.dp)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val (textInlines, images) = remember(inlines) { extractBlockImages(inlines) }
        Column(modifier = Modifier.fillMaxWidth()) {
            if (textInlines.isNotEmpty()) {
                Md4aInlineText(
                    inlines = textInlines,
                    colorScheme = scheme,
                    typography = typography,
                    style = typography.tableCell,
                    modifier = Modifier.fillMaxWidth(),
                    onLinkClick = onLinkClick,
                )
            }
            Md4aImageList(images, scheme, baseUrl, imageLoader)
        }
    }
}

// ── Lists ───────────────────────────────────────────────────────────────

@Composable
private fun ListView(
    list: MdList,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.items.forEach { item ->
            ListItemView(item, scheme, typography, baseUrl, imageLoader, onLinkClick)
        }
    }
}

@Composable
private fun ListItemView(
    item: MdListItem,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(
            text = when {
                item.task == true -> "☑"
                item.task == false -> "☐"
                item.ordered -> "${item.number ?: 1}."
                else -> "•"
            },
            style = typography.body,
            color = if (item.task != null) scheme.link else scheme.textSecondary,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End,
        )
        Column(modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) {
            item.blocks.forEach { child ->
                BlockView(
                    block = child,
                    colorScheme = scheme,
                    typography = typography,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.padding(vertical = 1.dp),
                )
            }
        }
    }
}

// ── Block quotes ────────────────────────────────────────────────────────

@Composable
private fun QuoteView(
    quote: MdBlockQuote,
    scheme: Md4aColorScheme,
    typography: Md4aTypography,
    baseUrl: String?,
    imageLoader: ImageLoader,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp, bottom = 2.dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(scheme.quoteBar, RoundedCornerShape(2.dp)),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            quote.blocks.forEach { child ->
                BlockView(
                    block = child,
                    colorScheme = scheme.copy(text = scheme.quoteText),
                    typography = typography,
                    baseUrl = baseUrl,
                    imageLoader = imageLoader,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
