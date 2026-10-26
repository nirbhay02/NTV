package com.jiotv.androidtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.jiotv.androidtv.R

/**
 * Fenyx v2 "Everyday" obsidian-dark colour tokens.
 */
@Immutable
data class JtvColors(
    val isDark: Boolean,
    val bg: Color,
    val s1: Color,
    val s2: Color,
    val line: Color,
    val tx: Color,
    val t2: Color,
    val t3: Color,
    val inv: Color,
    val invTx: Color,
    val acc: Color,
    val accTx: Color,
    val plate: Color,
    val error: Color,
)

val JtvDark = JtvColors(
    isDark = true,
    bg = Color(0xFF0D0E12),
    s1 = Color(0xFF14161D),
    s2 = Color(0xFF1E212B),
    line = Color(0xFF282C38),
    tx = Color(0xFFECECEE),
    t2 = Color(0xFFA8A8B0),
    t3 = Color(0xFF8E8E97),
    inv = Color(0xFFECECEE),
    invTx = Color(0xFF0D0E12),
    acc = Color(0xFF00D2FF), // NTV Electric Cyan
    accTx = Color(0xFF031622),
    plate = Color(0xFF1A1D26),
    error = Color(0xFFF2726B),
)

val JtvLight = JtvColors(
    isDark = false,
    bg = Color(0xFFF0F4F8),
    s1 = Color(0xFFFFFFFF),
    s2 = Color(0xFFE2E8F0),
    line = Color(0xFFCBD5E1),
    tx = Color(0xFF0F172A),
    t2 = Color(0xFF475569),
    t3 = Color(0xFF64748B),
    inv = Color(0xFF0F172A),
    invTx = Color(0xFFFFFFFF),
    acc = Color(0xFF0284C7),
    accTx = Color(0xFFFFFFFF),
    plate = Color(0xFFF1F5F9),
    error = Color(0xFFC4332B),
)

val ACCENTS = listOf(
    Triple("cyan", "Electric Cyan", Color(0xFF00D2FF) to Color(0xFF0284C7)),
    Triple("purple", "Neon Purple", Color(0xFFB48CFF) to Color(0xFF7C3AED)),
    Triple("blue", "Sky Blue", Color(0xFF6CA8FF) to Color(0xFF2563EB)),
    Triple("green", "Emerald", Color(0xFF5FCB86) to Color(0xFF15803D)),
    Triple("rose", "Rose Pink", Color(0xFFFF7A90) to Color(0xFFE11D48)),
    Triple("amber", "Amber", Color(0xFFF0A12E) to Color(0xFFE28E0B)),
)

fun JtvColors.withAccent(key: String?): JtvColors {
    val a = ACCENTS.firstOrNull { it.first == key } ?: return this
    val acc = if (isDark) a.third.first else a.third.second
    val lum = 0.2126f * acc.red + 0.7152f * acc.green + 0.0722f * acc.blue
    return copy(acc = acc, accTx = if (lum > 0.45f) Color(0xFF141414) else Color(0xFFFFFFFF))
}

object JtvFonts {
    val text = FontFamily(
        Font(R.font.jtv_anek_regular, FontWeight.Normal),
        Font(R.font.jtv_anek_semibold, FontWeight.SemiBold),
        Font(R.font.jtv_anek_bold, FontWeight.Bold),
    )
    val wide = FontFamily(Font(R.font.jtv_anek_wide_bold, FontWeight.Bold))
}

enum class FormFactor { Tv, Phone, Tablet }

val LocalJtvColors = staticCompositionLocalOf { JtvDark }
val LocalFormFactor = staticCompositionLocalOf { FormFactor.Tv }
val LocalAccentKey = staticCompositionLocalOf<String?> { null }

object Jtv {
    val colors: JtvColors @Composable @ReadOnlyComposable get() = LocalJtvColors.current
    val form: FormFactor @Composable @ReadOnlyComposable get() = LocalFormFactor.current
    val isTv: Boolean @Composable @ReadOnlyComposable get() = LocalFormFactor.current == FormFactor.Tv
}

@Composable
fun JtvDarkOnly(content: @Composable () -> Unit) {
    val accent = LocalAccentKey.current
    CompositionLocalProvider(LocalJtvColors provides JtvDark.withAccent(accent), content = content)
}
