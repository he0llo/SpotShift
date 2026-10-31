package he.spotshift.llo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.android.material.color.utilities.Scheme

private val SpotTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.merge(
            TextStyle(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
        ),
        titleLarge = base.titleLarge.merge(TextStyle(fontWeight = FontWeight.SemiBold)),
        titleMedium = base.titleMedium.merge(TextStyle(fontWeight = FontWeight.SemiBold)),
        labelLarge = base.labelLarge.merge(TextStyle(fontWeight = FontWeight.SemiBold)),
    )
}

@Composable
fun SpotShiftTheme(
    seedColor: Color,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = seedScheme(seedColor, darkTheme),
        typography = SpotTypography,
        content = content,
    )
}

/**
 * Builds a full MD3 scheme from an arbitrary seed using the Material color utilities
 * bundled with the Material Components library (the same algorithm as Material You).
 */
private fun seedScheme(seed: Color, dark: Boolean): androidx.compose.material3.ColorScheme {
    val s = if (dark) Scheme.dark(seed.toArgb()) else Scheme.light(seed.toArgb())
    val surface = Color(s.surface)
    val variant = Color(s.surfaceVariant)
    val containerLowest = if (dark) lerp(surface, Color.Black, 0.4f) else lerp(surface, Color.White, 0.7f)
    val containerLow = lerp(surface, variant, if (dark) 0.16f else 0.22f)
    val container = lerp(surface, variant, if (dark) 0.3f else 0.46f)
    val containerHigh = lerp(surface, variant, if (dark) 0.48f else 0.7f)
    val containerHighest = lerp(surface, variant, if (dark) 0.66f else 0.9f)
    val bright = if (dark) lerp(surface, variant, 0.55f) else lerp(surface, Color.White, 0.55f)
    val dim = if (dark) lerp(surface, Color.Black, 0.35f) else lerp(surface, variant, 0.55f)

    return if (dark) {
        darkColorScheme(
            primary = Color(s.primary),
            onPrimary = Color(s.onPrimary),
            primaryContainer = Color(s.primaryContainer),
            onPrimaryContainer = Color(s.onPrimaryContainer),
            inversePrimary = Color(s.inversePrimary),
            secondary = Color(s.secondary),
            onSecondary = Color(s.onSecondary),
            secondaryContainer = Color(s.secondaryContainer),
            onSecondaryContainer = Color(s.onSecondaryContainer),
            tertiary = Color(s.tertiary),
            onTertiary = Color(s.onTertiary),
            tertiaryContainer = Color(s.tertiaryContainer),
            onTertiaryContainer = Color(s.onTertiaryContainer),
            background = Color(s.background),
            onBackground = Color(s.onBackground),
            surface = surface,
            onSurface = Color(s.onSurface),
            surfaceVariant = variant,
            onSurfaceVariant = Color(s.onSurfaceVariant),
            surfaceTint = Color(s.primary),
            inverseSurface = Color(s.inverseSurface),
            inverseOnSurface = Color(s.inverseOnSurface),
            error = Color(s.error),
            onError = Color(s.onError),
            errorContainer = Color(s.errorContainer),
            onErrorContainer = Color(s.onErrorContainer),
            outline = Color(s.outline),
            outlineVariant = Color(s.outlineVariant),
            scrim = Color(s.scrim),
            surfaceBright = bright,
            surfaceDim = dim,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
        )
    } else {
        lightColorScheme(
            primary = Color(s.primary),
            onPrimary = Color(s.onPrimary),
            primaryContainer = Color(s.primaryContainer),
            onPrimaryContainer = Color(s.onPrimaryContainer),
            inversePrimary = Color(s.inversePrimary),
            secondary = Color(s.secondary),
            onSecondary = Color(s.onSecondary),
            secondaryContainer = Color(s.secondaryContainer),
            onSecondaryContainer = Color(s.onSecondaryContainer),
            tertiary = Color(s.tertiary),
            onTertiary = Color(s.onTertiary),
            tertiaryContainer = Color(s.tertiaryContainer),
            onTertiaryContainer = Color(s.onTertiaryContainer),
            background = Color(s.background),
            onBackground = Color(s.onBackground),
            surface = surface,
            onSurface = Color(s.onSurface),
            surfaceVariant = variant,
            onSurfaceVariant = Color(s.onSurfaceVariant),
            surfaceTint = Color(s.primary),
            inverseSurface = Color(s.inverseSurface),
            inverseOnSurface = Color(s.inverseOnSurface),
            error = Color(s.error),
            onError = Color(s.onError),
            errorContainer = Color(s.errorContainer),
            onErrorContainer = Color(s.onErrorContainer),
            outline = Color(s.outline),
            outlineVariant = Color(s.outlineVariant),
            scrim = Color(s.scrim),
            surfaceBright = bright,
            surfaceDim = dim,
            surfaceContainerLowest = containerLowest,
            surfaceContainerLow = containerLow,
            surfaceContainer = container,
            surfaceContainerHigh = containerHigh,
            surfaceContainerHighest = containerHighest,
        )
    }
}
