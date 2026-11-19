package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class FinanceSemanticColors(
  val incomeAccent: Color,
  val incomeContainer: Color,
  val onIncomeContainer: Color,
  val expenseAccent: Color,
  val expenseContainer: Color,
  val onExpenseContainer: Color,
  val pendingAccent: Color,
  val pendingContainer: Color,
  val onPendingContainer: Color,
  val periodAccent: Color,
  val periodContainer: Color,
  val onPeriodContainer: Color,
  val heroCardBackground: Color,
  val cardBorder: Color,
  val subtleSurface: Color,
)

private val LightSemanticColors =
  FinanceSemanticColors(
    incomeAccent = EmeraldPrimary,
    incomeContainer = EmeraldContainer,
    onIncomeContainer = OnEmeraldContainer,
    expenseAccent = CrimsonExpense,
    expenseContainer = CrimsonContainer,
    onExpenseContainer = OnCrimsonContainer,
    pendingAccent = AmberPending,
    pendingContainer = AmberContainer,
    onPendingContainer = OnAmberContainer,
    periodAccent = CobaltPeriod,
    periodContainer = CobaltContainer,
    onPeriodContainer = OnCobaltContainer,
    heroCardBackground = EmeraldHeroDark,
    cardBorder = OutlineLight,
    subtleSurface = SurfaceSubtleLight,
  )

private val DarkSemanticColors =
  FinanceSemanticColors(
    incomeAccent = EmeraldPrimaryDark,
    incomeContainer = EmeraldContainerDark,
    onIncomeContainer = OnEmeraldContainerDark,
    expenseAccent = CrimsonExpenseDark,
    expenseContainer = CrimsonContainerDark,
    onExpenseContainer = OnCrimsonContainerDark,
    pendingAccent = AmberPendingDark,
    pendingContainer = AmberContainerDark,
    onPendingContainer = OnAmberContainerDark,
    periodAccent = CobaltPeriodDark,
    periodContainer = CobaltContainerDark,
    onPeriodContainer = OnCobaltContainerDark,
    heroCardBackground = Color(0xFF0A472D),
    cardBorder = OutlineDark,
    subtleSurface = SurfaceSubtleDark,
  )

val LocalFinanceColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CobaltPeriod,
    onSecondary = Color.White,
    secondaryContainer = CobaltContainer,
    onSecondaryContainer = OnCobaltContainer,
    tertiary = AmberPending,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    error = CrimsonExpense,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceWhite,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceSubtleLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = Color(0xFF003821),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = CobaltPeriodDark,
    onSecondary = Color(0xFF0B2559),
    secondaryContainer = CobaltContainerDark,
    onSecondaryContainer = OnCobaltContainerDark,
    tertiary = AmberPendingDark,
    onTertiary = Color(0xFF422006),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    error = CrimsonExpenseDark,
    onError = Color(0xFF4A0A0A),
    errorContainer = CrimsonContainerDark,
    onErrorContainer = OnCrimsonContainerDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceSubtleDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
  )

private val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
  )

@Composable
fun MisFinanzasTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

  CompositionLocalProvider(LocalFinanceColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
