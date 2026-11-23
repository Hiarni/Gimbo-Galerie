package de.luma.gallery.ui

import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import de.luma.gallery.model.AppSettings
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AccentContrastTest {
    @Test fun extremeCustomColorsStayReadableOnBothThemesAndButtons() {
        for (background in listOf(0xFF121216.toInt(), 0xFFF8F7FA.toInt())) {
            for (color in listOf(0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF121216, 0xFFF8F7FA)) {
                val accent = readableAccent(color.toInt(), background)
                assertTrue(ColorUtils.calculateContrast(accent, background) >= 4.5)
                assertTrue(ColorUtils.calculateContrast(textOnAccent(accent), accent) >= 4.5)
            }
        }
    }

    @Test fun themeSurfacesAndSelectedControlsStayReadableWithCustomAccents() {
        for(dark in listOf(false,true)) for(color in listOf(0xFF000000,0xFFFFFFFF,0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFCC00)) {
            val scheme=lumaColorScheme(AppSettings(accent=color),dark)
            val pairs=listOf(scheme.onPrimary to scheme.primary,scheme.onSecondaryContainer to scheme.secondaryContainer,
                scheme.onSurfaceVariant to scheme.surfaceContainerHighest,scheme.onSurface to scheme.surfaceContainer,
                scheme.onSurface to scheme.surfaceContainerHigh,scheme.inverseOnSurface to scheme.inverseSurface)
            pairs.forEach {(foreground,background)->assertTrue("$dark / $color / $foreground / $background",
                ColorUtils.calculateContrast(foreground.toArgb(),background.toArgb())>=4.5)}
        }
    }
}
