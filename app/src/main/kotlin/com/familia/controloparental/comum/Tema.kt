package com.familia.controloparental.comum

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Identidade visual da app — alinhada ao logótipo (escudo azul→verde com
 * cadeado), para que o design pareça desenhado por uma única equipa, do
 * ícone do launcher aos ecrãs internos.
 */

// Azul do escudo (topo) até verde (base), tal como no logótipo.
val AzulProfundo = Color(0xFF0D47A1)
val AzulPrimario = Color(0xFF1565C0)
val AzulClaro = Color(0xFF2E9BF0)
val VerdeEsmeralda = Color(0xFF17A673)
val VerdeClaro = Color(0xFF34D399)
val FundoClaro = Color(0xFFF5F8FC)
val SuperficieClara = Color(0xFFFFFFFF)
val TextoPrimario = Color(0xFF101828)
val TextoSecundario = Color(0xFF5B6679)
val CorErro = Color(0xFFDC2626)
val CorAviso = Color(0xFFF59E0B)

val GradienteMarca = Brush.linearGradient(
    colors = listOf(AzulProfundo, AzulPrimario, VerdeEsmeralda)
)

val GradienteMarcaSuave = Brush.linearGradient(
    colors = listOf(AzulClaro.copy(alpha = 0.12f), VerdeClaro.copy(alpha = 0.10f))
)

private val EsquemaClaro = lightColorScheme(
    primary = AzulPrimario,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = AzulProfundo,
    secondary = VerdeEsmeralda,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F5E8),
    onSecondaryContainer = Color(0xFF065F43),
    background = FundoClaro,
    onBackground = TextoPrimario,
    surface = SuperficieClara,
    onSurface = TextoPrimario,
    surfaceVariant = Color(0xFFEAF1FA),
    onSurfaceVariant = TextoSecundario,
    error = CorErro,
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

private val EsquemaEscuro = darkColorScheme(
    primary = AzulClaro,
    onPrimary = Color(0xFF00264D),
    primaryContainer = AzulProfundo,
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = VerdeClaro,
    onSecondary = Color(0xFF02331F),
    secondaryContainer = Color(0xFF0B4A33),
    onSecondaryContainer = Color(0xFFD4F5E8),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE5EAF2),
    surface = Color(0xFF121A2B),
    onSurface = Color(0xFFE5EAF2),
    surfaceVariant = Color(0xFF1C2638),
    onSurfaceVariant = Color(0xFFA7B2C4),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0A0A),
    outline = Color(0xFF334155)
)

val FormasApp = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val TipografiaApp = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun TemaControloParental(aEscuro: Boolean = androidx.compose.foundation.isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (aEscuro) EsquemaEscuro else EsquemaClaro,
        typography = TipografiaApp,
        shapes = FormasApp,
        content = conteudo
    )
}

/** Fundo com o gradiente de marca, usado no splash e nos ecrãs de autenticação. */
fun Modifier.fundoDeMarca(): Modifier = this.background(GradienteMarca)
