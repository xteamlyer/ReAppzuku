package com.gree1d.reappzuku.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


object AccentId {
    const val SYSTEM    = 0
    const val INDIGO    = 1
    const val CRIMSON   = 2
    const val FOREST    = 3
    const val SLATE     = 4
    const val ROSE      = 5
    const val AMBER     = 6
    const val TEAL      = 7
    const val TERRACOTA = 8
    const val MOCHA     = 9
    const val OLIVE     = 10
    const val STEEL     = 11
    const val APRICOT   = 12
    const val SKY       = 13
    const val PAPAYA    = 14
    const val LAVENDER  = 15
    const val MINT      = 16
    const val PEACH     = 17
    const val POWDER    = 18
    const val FOG       = 19
    const val CUSTOM    = 20
}


private object AccentColors {

    // Indigo (dark accent)
    val IndigoPrimary   = Color(0xFF4B0082)
    val IndigoSecondary = Color(0xFF534BAE)
    val IndigoDark      = Color(0xFF37006E)

    // Crimson
    val CrimsonPrimary   = Color(0xFFFF4040)
    val CrimsonSecondary = Color(0xFFFF6868)
    val CrimsonDark      = Color(0xFFB90000)

    // Forest
    val ForestPrimary   = Color(0xFF0E770E)
    val ForestSecondary = Color(0xFF2C952C)
    val ForestDark      = Color(0xFF004F00)

    // Slate
    val SlatePrimary   = Color(0xFF1414B9)
    val SlateSecondary = Color(0xFF2828CD)
    val SlateDark      = Color(0xFF000069)

    // Rose
    val RosePrimary   = Color(0xFF7B4F5E)
    val RoseSecondary = Color(0xFF9E6E7C)
    val RoseDark      = Color(0xFF4E2F3A)

    // Amber  (dark-on-light accent)
    val AmberPrimary   = Color(0xFFF5B500)
    val AmberSecondary = Color(0xFFA07820)
    val AmberDark      = Color(0xFFAF6F00)

    // Teal
    val TealPrimary   = Color(0xFF1A4F52)
    val TealSecondary = Color(0xFF2E7175)
    val TealDark      = Color(0xFF0A2E30)

    // Terracota
    val TerracotaPrimary   = Color(0xFF7A3D2A)
    val TerracotaSecondary = Color(0xFF9E5A43)
    val TerracotaDark      = Color(0xFF4E2015)

    // Mocha
    val MochaPrimary   = Color(0xFF6B4F3A)
    val MochaSecondary = Color(0xFF8C6B54)
    val MochaDark      = Color(0xFF3E2C1E)

    // Olive
    val OlivePrimary   = Color(0xFF4A5240)
    val OliveSecondary = Color(0xFF687560)
    val OliveDark      = Color(0xFF252B21)

    // Steel
    val SteelPrimary   = Color(0xFF676F74)
    val SteelSecondary = Color(0xFF7B8388)
    val SteelDark      = Color(0xFF495156)

    // Apricot (light accent — onColor = Black)
    val ApricotPrimary   = Color(0xFFFBCEB1)
    val ApricotSecondary = Color(0xFFF4A57A)
    val ApricotDark      = Color(0xFFC97840)

    // Sky (light)
    val SkyPrimary   = Color(0xFF9ACEEB)
    val SkySecondary = Color(0xFF6AB0D8)
    val SkyDark      = Color(0xFF3A7A9C)

    // Papaya (light)
    val PapayaPrimary   = Color(0xFFFFEFD5)
    val PapayaSecondary = Color(0xFFF4C97A)
    val PapayaDark      = Color(0xFFC49040)

    // Lavender (light)
    val LavenderPrimary   = Color(0xFFE6DEFF)
    val LavenderSecondary = Color(0xFFB8A0E8)
    val LavenderDark      = Color(0xFF7A5CB8)

    // Mint (light)
    val MintPrimary   = Color(0xFFC8F0E0)
    val MintSecondary = Color(0xFF80D4B0)
    val MintDark      = Color(0xFF3A9870)

    // Peach (light)
    val PeachPrimary   = Color(0xFFFFD9C0)
    val PeachSecondary = Color(0xFFF4A878)
    val PeachDark      = Color(0xFFC47040)

    // Powder (light)
    val PowderPrimary   = Color(0xFFF5D0E8)
    val PowderSecondary = Color(0xFFE8A0C8)
    val PowderDark      = Color(0xFFB06090)

    // Fog (light)
    val FogPrimary   = Color(0xFFD0E8F5)
    val FogSecondary = Color(0xFF90BCD8)
    val FogDark      = Color(0xFF4080A8)
}


val lightAccents = setOf(
    AccentId.APRICOT, AccentId.SKY, AccentId.PAPAYA, AccentId.LAVENDER,
    AccentId.MINT, AccentId.PEACH, AccentId.POWDER, AccentId.FOG
)


private val AmoledBackground    = Color(0xFF000000)
private val AmoledSurfaceVariant = Color(0xFF0D0D0D)
private val AmoledSurface       = Color(0xFF080808)


private fun buildDarkScheme(
    primary: Color,
    secondary: Color,
    container: Color,
    amoled: Boolean = false
): ColorScheme {
    val bg      = if (amoled) AmoledBackground     else Color(0xFF1C1B1F)
    val surface = if (amoled) AmoledSurface        else Color(0xFF1C1B1F)
    val surfaceVar = if (amoled) AmoledSurfaceVariant else Color(0xFF49454F)
    return darkColorScheme(
        primary          = primary,
        onPrimary        = Color.White,
        primaryContainer = container,
        onPrimaryContainer = Color.White,
        secondary        = secondary,
        onSecondary      = Color.White,
        background       = bg,
        onBackground     = Color.White,
        surface          = surface,
        onSurface        = Color.White,
        surfaceVariant   = surfaceVar,
    )
}

private fun buildLightScheme(
    primary: Color,
    secondary: Color,
    container: Color,
    onPrimary: Color = Color.White
): ColorScheme = lightColorScheme(
    primary            = primary,
    onPrimary          = onPrimary,
    primaryContainer   = container,
    onPrimaryContainer = onPrimary,
    secondary          = secondary,
    onSecondary        = Color.White,
    background         = Color(0xFFFFFBFE),
    onBackground       = Color(0xFF1C1B1F),
    surface            = Color(0xFFFFFBFE),
    onSurface          = Color(0xFF1C1B1F),
)


private fun schemeForAccent(
    accentId: Int,
    dark: Boolean,
    amoled: Boolean
): ColorScheme = with(AccentColors) {
    when (accentId) {

        AccentId.INDIGO -> if (dark || amoled)
            buildDarkScheme(IndigoPrimary, IndigoSecondary, IndigoDark, amoled)
        else
            buildLightScheme(IndigoPrimary, IndigoSecondary, IndigoDark)

        AccentId.CRIMSON -> if (dark || amoled)
            buildDarkScheme(CrimsonPrimary, CrimsonSecondary, CrimsonDark, amoled)
        else
            buildLightScheme(CrimsonPrimary, CrimsonSecondary, CrimsonDark)

        AccentId.FOREST -> if (dark || amoled)
            buildDarkScheme(ForestPrimary, ForestSecondary, ForestDark, amoled)
        else
            buildLightScheme(ForestPrimary, ForestSecondary, ForestDark)

        AccentId.SLATE -> if (dark || amoled)
            buildDarkScheme(SlatePrimary, SlateSecondary, SlateDark, amoled)
        else
            buildLightScheme(SlatePrimary, SlateSecondary, SlateDark)

        AccentId.ROSE -> if (dark || amoled)
            buildDarkScheme(RosePrimary, RoseSecondary, RoseDark, amoled)
        else
            buildLightScheme(RosePrimary, RoseSecondary, RoseDark)

        AccentId.AMBER -> if (dark || amoled)
            buildDarkScheme(AmberPrimary, AmberSecondary, AmberDark, amoled)
        else
            buildLightScheme(AmberPrimary, AmberSecondary, AmberDark, onPrimary = Color.Black)

        AccentId.TEAL -> if (dark || amoled)
            buildDarkScheme(TealPrimary, TealSecondary, TealDark, amoled)
        else
            buildLightScheme(TealPrimary, TealSecondary, TealDark)

        AccentId.TERRACOTA -> if (dark || amoled)
            buildDarkScheme(TerracotaPrimary, TerracotaSecondary, TerracotaDark, amoled)
        else
            buildLightScheme(TerracotaPrimary, TerracotaSecondary, TerracotaDark)

        AccentId.MOCHA -> if (dark || amoled)
            buildDarkScheme(MochaPrimary, MochaSecondary, MochaDark, amoled)
        else
            buildLightScheme(MochaPrimary, MochaSecondary, MochaDark)

        AccentId.OLIVE -> if (dark || amoled)
            buildDarkScheme(OlivePrimary, OliveSecondary, OliveDark, amoled)
        else
            buildLightScheme(OlivePrimary, OliveSecondary, OliveDark)

        AccentId.STEEL -> if (dark || amoled)
            buildDarkScheme(SteelPrimary, SteelSecondary, SteelDark, amoled)
        else
            buildLightScheme(SteelPrimary, SteelSecondary, SteelDark)


        AccentId.APRICOT -> if (dark || amoled)
            buildDarkScheme(ApricotPrimary, ApricotSecondary, ApricotDark, amoled)
        else
            buildLightScheme(ApricotPrimary, ApricotSecondary, ApricotDark, onPrimary = Color.Black)

        AccentId.SKY -> if (dark || amoled)
            buildDarkScheme(SkyPrimary, SkySecondary, SkyDark, amoled)
        else
            buildLightScheme(SkyPrimary, SkySecondary, SkyDark, onPrimary = Color.Black)

        AccentId.PAPAYA -> if (dark || amoled)
            buildDarkScheme(PapayaPrimary, PapayaSecondary, PapayaDark, amoled)
        else
            buildLightScheme(PapayaPrimary, PapayaSecondary, PapayaDark, onPrimary = Color.Black)

        AccentId.LAVENDER -> if (dark || amoled)
            buildDarkScheme(LavenderPrimary, LavenderSecondary, LavenderDark, amoled)
        else
            buildLightScheme(LavenderPrimary, LavenderSecondary, LavenderDark, onPrimary = Color.Black)

        AccentId.MINT -> if (dark || amoled)
            buildDarkScheme(MintPrimary, MintSecondary, MintDark, amoled)
        else
            buildLightScheme(MintPrimary, MintSecondary, MintDark, onPrimary = Color.Black)

        AccentId.PEACH -> if (dark || amoled)
            buildDarkScheme(PeachPrimary, PeachSecondary, PeachDark, amoled)
        else
            buildLightScheme(PeachPrimary, PeachSecondary, PeachDark, onPrimary = Color.Black)

        AccentId.POWDER -> if (dark || amoled)
            buildDarkScheme(PowderPrimary, PowderSecondary, PowderDark, amoled)
        else
            buildLightScheme(PowderPrimary, PowderSecondary, PowderDark, onPrimary = Color.Black)

        AccentId.FOG -> if (dark || amoled)
            buildDarkScheme(FogPrimary, FogSecondary, FogDark, amoled)
        else
            buildLightScheme(FogPrimary, FogSecondary, FogDark, onPrimary = Color.Black)

        else -> if (dark || amoled)
            buildDarkScheme(AccentColors.IndigoPrimary, AccentColors.IndigoSecondary, AccentColors.IndigoDark, amoled)
        else
            buildLightScheme(AccentColors.IndigoPrimary, AccentColors.IndigoSecondary, AccentColors.IndigoDark)
    }
}


private fun customScheme(
    customColor: Color,
    onCustomColor: Color,
    dark: Boolean,
    amoled: Boolean
): ColorScheme {
    val darkened = darkenColor(customColor, 0.75f)
    return if (dark || amoled)
        buildDarkScheme(customColor, customColor, darkened, amoled).copy(
            onPrimary = onCustomColor
        )
    else
        buildLightScheme(customColor, customColor, darkened, onPrimary = onCustomColor)
}

private fun darkenColor(color: Color, factor: Float): Color = Color(
    red   = (color.red   * factor).coerceIn(0f, 1f),
    green = (color.green * factor).coerceIn(0f, 1f),
    blue  = (color.blue  * factor).coerceIn(0f, 1f),
    alpha = color.alpha
)


data class AppThemeConfig(
    val accentId: Int        = AccentId.SYSTEM,
    val isAmoled: Boolean    = false,
    val nightMode: Int       = -1,
    val customColor: Color   = Color(0xFF4B0082),
    val onColorIsBlack: Boolean = false
)


val LocalOnAccentColor = compositionLocalOf { Color.White }

val LocalIsLightAccent = compositionLocalOf { false }


@Composable
fun AppTheme(
    config: AppThemeConfig,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view    = LocalView.current

    val systemDark = isSystemInDarkTheme()
    val isDark: Boolean = when {
        config.isAmoled                                               -> true
        config.nightMode == 1 /* MODE_NIGHT_NO */                    -> false
        config.nightMode == 2 /* MODE_NIGHT_YES */                   -> true
        else                                                          -> systemDark
    }

    val colorScheme: ColorScheme = remember(config, isDark) {
        when (config.accentId) {
            AccentId.SYSTEM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (isDark) dynamicDarkColorScheme(context)
                    else        dynamicLightColorScheme(context)
                } else {
                    schemeForAccent(AccentId.INDIGO, isDark, config.isAmoled)
                }
            }
            AccentId.CUSTOM -> customScheme(
                customColor    = config.customColor,
                onCustomColor  = if (config.onColorIsBlack) Color.Black else Color.White,
                dark           = isDark,
                amoled         = config.isAmoled
            )
            else -> schemeForAccent(config.accentId, isDark, config.isAmoled)
        }
    }

    val isLightAccent: Boolean = when (config.accentId) {
        AccentId.CUSTOM -> config.onColorIsBlack
        else            -> config.accentId in lightAccents
    }
    val onAccentColor = if (isLightAccent) Color.Black else Color.White

    val lightBars = !isDark
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor  = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars     = lightBars
            ctrl.isAppearanceLightNavigationBars = lightBars
        }
    }

    CompositionLocalProvider(
        LocalOnAccentColor provides onAccentColor,
        LocalIsLightAccent provides isLightAccent,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = AppTypography,
            shapes      = AppShapes,
            content     = content
        )
    }
}


val MaterialTheme.onAccentColor: Color
    @Composable @ReadOnlyComposable get() = LocalOnAccentColor.current

val MaterialTheme.isLightAccent: Boolean
    @Composable @ReadOnlyComposable get() = LocalIsLightAccent.current

private val AppTypography = Typography()

private val AppShapes = Shapes()
