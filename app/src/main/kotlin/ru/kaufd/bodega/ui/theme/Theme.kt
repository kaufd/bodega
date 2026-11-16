package ru.kaufd.bodega.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val scheme = lightColorScheme(
    primary = BodegaColors.Teal,
    background = BodegaColors.Paper,
    surface = BodegaColors.Surface,
    onPrimary = BodegaColors.Surface,
    onBackground = BodegaColors.Ink,
    onSurface = BodegaColors.Ink,
    outlineVariant = BodegaColors.Hairline,
)

// Экраны задают стили напрямую константами из BodegaComponents.kt, но три стиля
// материаловской типографики читаются и должны нести Rubik:
//   headlineSmall — заглушка пустого экрана;
//   labelLarge    — кнопки AlertDialog (DialogTokens.ActionLabelTextFont),
//                   у них нет своего стиля: «Удалить»/«Отмена», «Выйти»/«Остаться»;
//   bodyLarge     — то, что MaterialTheme кладёт в LocalTextStyle, то есть шрифт
//                   любого Text без явного style.
// Остальные пять были параллельным набором, который выглядел действующим.
private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    labelLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontSize = 16.sp),
)

@Composable
fun BodegaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
