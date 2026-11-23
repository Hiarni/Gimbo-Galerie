package de.luma.gallery.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import de.luma.gallery.model.AppSettings
import de.luma.gallery.model.ThemeMode

@Composable
fun LumaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme = lumaColorScheme(settings, dark)
    val activity = LocalActivity.current
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = scheme.background.luminance() > .5f
                isAppearanceLightNavigationBars = scheme.background.luminance() > .5f
            }
        }
    }
    val type=Typography()
    MaterialTheme(colorScheme=scheme,shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(22.dp),large=RoundedCornerShape(28.dp),extraLarge=RoundedCornerShape(32.dp)),
        typography=type.copy(titleLarge=type.titleLarge.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-.3).sp),titleMedium=type.titleMedium.copy(letterSpacing=(-.2).sp),headlineSmall=type.headlineSmall.copy(fontWeight=FontWeight.SemiBold,letterSpacing=(-.4).sp),labelLarge=type.labelLarge.copy(letterSpacing=.1.sp)),content=content)
}

fun lumaColorScheme(settings: AppSettings, dark: Boolean): androidx.compose.material3.ColorScheme {
    val background = if (dark) 0xFF121216.toInt() else 0xFFF8F7FA.toInt()
    val accentInt = readableAccent(settings.accent.toInt(), background)
    val container = androidx.core.graphics.ColorUtils.blendARGB(background, accentInt, .15f) or 0xFF000000.toInt()
    val accent = Color(accentInt)
    val onAccent = Color(textOnAccent(accentInt))
    val onContainer = Color(readableAccent(accentInt, container))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent, primaryContainer = Color(container), onPrimaryContainer = onContainer,
        secondary = accent, onSecondary = onAccent, secondaryContainer = Color(container), onSecondaryContainer = onContainer,
        tertiary = accent, onTertiary = onAccent, tertiaryContainer = Color(container), onTertiaryContainer = onContainer,
        background = Color(background), onBackground = if (dark) Color(0xFFF4F4FA) else Color(0xFF20202A),
        surface = Color(background), onSurface = if (dark) Color(0xFFF4F4FA) else Color(0xFF20202A),
        surfaceVariant = if (dark) Color(0xFF30303A) else Color(0xFFE2E1EB),
        onSurfaceVariant = if (dark) Color(0xFFBBBAC7) else Color(0xFF616171),
        surfaceDim = if (dark) Color(0xFF0D0D11) else Color(0xFFE0DFE8),
        surfaceBright = if (dark) Color(0xFF36363E) else Color(0xFFFCFCFF),
        surfaceContainerLowest = if (dark) Color(0xFF0D0D11) else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF17171D) else Color(0xFFF3F2F8),
        surfaceContainer = if (dark) Color(0xFF1D1D24) else Color(0xFFEEEEF5),
        surfaceContainerHigh = if (dark) Color(0xFF25252D) else Color(0xFFE8E7EF),
        surfaceContainerHighest = if (dark) Color(0xFF30303A) else Color(0xFFE2E1EB),
        surfaceTint = accent,
        outline = if (dark) Color(0xFF8D8C9B) else Color(0xFF787887),
        outlineVariant = if (dark) Color(0xFF3B3B46) else Color(0xFFD3D2DE),
        inverseSurface = if (dark) Color(0xFFE8E7EF) else Color(0xFF25252D),
        inverseOnSurface = if (dark) Color(0xFF20202A) else Color(0xFFF4F4FA),
        inversePrimary = Color(readableAccent(accentInt, if (dark) 0xFFE8E7EF.toInt() else 0xFF25252D.toInt())),
    )
}
