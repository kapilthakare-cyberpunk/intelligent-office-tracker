package com.office.tracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ============================================================================
// Office Tracker — brand Material 3 design system
// Professional-clock identity. Deep blue primary with a teal secondary and a
// warm coral accent for the active/on-site state.
// ============================================================================

// --- Light color scheme (source of truth for the brand) ---
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B4F8A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3C),
    inversePrimary = Color(0xFFA9C7FF),

    secondary = Color(0xFF2B6B4F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCBEEDB),
    onSecondaryContainer = Color(0xFF002110),

    tertiary = Color(0xFF8A4A63),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E4),
    onTertiaryContainer = Color(0xFF3D001F),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = Color(0xFF1B4F8A),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),

    // Brand accent used for "on-site / active" states
    // (surfaceVariant-like warm tone that reads clearly against blue).
)

// --- Dark color scheme ---
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF00396F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    inversePrimary = Color(0xFF1B4F8A),

    secondary = Color(0xFFB0D2BD),
    onSecondary = Color(0xFF1B3829),
    secondaryContainer = Color(0xFF33513F),
    onSecondaryContainer = Color(0xFFCBEEDB),

    tertiary = Color(0xFFF1B7CE),
    onTertiary = Color(0xFF552135),
    tertiaryContainer = Color(0xFF6F374C),
    onTertiaryContainer = Color(0xFFFFD9E4),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF191C20),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF191C20),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceTint = Color(0xFFA9C7FF),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
)

// --- Typography: clean, professional, slightly tighter for data-heavy UIs ---
private val AppTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp
    ),
    headlineMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
)

/** Brand accent coral — the "you are at the office right now" highlight. */
val ActiveAccent: Color
    @Composable get() = MaterialTheme.colorScheme.tertiary

/**
 * Host composable for the app. Use this instead of raw MaterialTheme so the
 * app keeps the Office Tracker brand identity.
 *
 * Note: if you ever want system dynamic colors back, swap the body to
 * `MaterialTheme(colorScheme = dynamicColorScheme()) { ... }`.
 */
@Composable
fun OfficeTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

// Convenience accessor for screens that need the full scheme.
@Composable
fun officeColorScheme(): ColorScheme = MaterialTheme.colorScheme
