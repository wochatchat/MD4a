package com.md4a.render

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Visual specification for rendered markdown. Defaults adapt to system
 * dark/light and are tuned for phone screens (slightly denser than GitHub web).
 */
@Immutable
data class Md4aColorScheme(
    val text: Color,
    val textSecondary: Color,
    val link: Color,
    val codeBackground: Color,
    val codeText: Color,
    val quoteBar: Color,
    val quoteText: Color,
    val rule: Color,
    val tableBorder: Color,
    val tableHeaderBackground: Color,
    val imagePlaceholder: Color,
    // Code highlighting palette
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxNumber: Color,
) {
    companion object {
        fun light(): Md4aColorScheme = Md4aColorScheme(
            text = Color(0xFF1F2328),
            textSecondary = Color(0xFF59636E),
            link = Color(0xFF0969DA),
            codeBackground = Color(0xFFF6F8FA),
            codeText = Color(0xFF1F2328),
            quoteBar = Color(0xFFD1D9E0),
            quoteText = Color(0xFF59636E),
            rule = Color(0xFFD1D9E0),
            tableBorder = Color(0xFFD1D9E0),
            tableHeaderBackground = Color(0xFFF6F8FA),
            imagePlaceholder = Color(0xFFEFF2F5),
            syntaxKeyword = Color(0xFFCF222E),
            syntaxString = Color(0xFF0A3069),
            syntaxComment = Color(0xFF59636E),
            syntaxNumber = Color(0xFF0550AE),
        )

        fun dark(): Md4aColorScheme = Md4aColorScheme(
            text = Color(0xFFE6EDF3),
            textSecondary = Color(0xFF9198A1),
            link = Color(0xFF4493F8),
            codeBackground = Color(0xFF151B23),
            codeText = Color(0xFFE6EDF3),
            quoteBar = Color(0xFF3D444D),
            quoteText = Color(0xFF9198A1),
            rule = Color(0xFF3D444D),
            tableBorder = Color(0xFF3D444D),
            tableHeaderBackground = Color(0xFF151B23),
            imagePlaceholder = Color(0xFF151B23),
            syntaxKeyword = Color(0xFFFF7B72),
            syntaxString = Color(0xFFA5D6FF),
            syntaxComment = Color(0xFF9198A1),
            syntaxNumber = Color(0xFF79C0FF),
        )
    }
}

/** Typography scale optimized for phone reading of READMEs. */
@Immutable
data class Md4aTypography(
    val h1: TextStyle,
    val h2: TextStyle,
    val h3: TextStyle,
    val h4: TextStyle,
    val h5: TextStyle,
    val h6: TextStyle,
    val body: TextStyle,
    val code: TextStyle,
    val tableCell: TextStyle,
) {
    companion object {
        fun default(): Md4aTypography {
            val base = FontFamily.SansSerif
            val mono = FontFamily.Monospace
            return Md4aTypography(
                h1 = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp),
                h2 = TextStyle(fontFamily = base, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 27.sp),
                h3 = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
                h4 = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
                h5 = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
                h6 = TextStyle(fontFamily = base, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 19.sp),
                body = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
                code = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 19.sp),
                tableCell = TextStyle(fontFamily = base, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
            )
        }
    }
}

@Composable
fun rememberMd4aColorScheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    custom: Md4aColorScheme? = null,
): Md4aColorScheme = custom ?: if (darkTheme) Md4aColorScheme.dark() else Md4aColorScheme.light()
