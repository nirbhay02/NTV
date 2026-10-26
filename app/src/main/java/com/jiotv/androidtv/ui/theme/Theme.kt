package com.jiotv.androidtv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private fun tvScheme(c: JtvColors) = darkColorScheme(
    primary = c.acc,
    onPrimary = c.accTx,
    primaryContainer = TvPrimaryContainer,
    onPrimaryContainer = TvOnPrimaryContainer,
    secondary = c.t2,
    onSecondary = c.bg,
    background = c.bg,
    onBackground = c.tx,
    surface = c.s1,
    onSurface = c.tx,
    surfaceVariant = c.s2,
    onSurfaceVariant = c.t2,
    inverseSurface = c.inv,
    inverseOnSurface = c.invTx,
    border = c.line,
    error = c.error,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NtvTheme(
    accent: String? = null,
    content: @Composable () -> Unit
) {
    val colors = remember(accent) { JtvDark.withAccent(accent) }
    val scheme = remember(colors) { tvScheme(colors) }

    CompositionLocalProvider(
        LocalJtvColors provides colors,
        LocalFormFactor provides FormFactor.Tv,
        LocalAccentKey provides accent
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun JioTvTheme(
    accent: String? = null,
    content: @Composable () -> Unit
) = NtvTheme(accent, content)

@Composable
fun JioTVGoTVTheme(
    content: @Composable () -> Unit
) = NtvTheme(content = content)
