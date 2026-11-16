package ru.kaufd.bodega.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import ru.kaufd.bodega.R

/**
 * Шрифты лежат в ресурсах, а не качаются провайдером Google Play Services.
 * Без сервисов гарнитура не приезжала вовсе — приложение молча рисовалось
 * системным гротеском. Цена — 617 КБ на весь набор.
 *
 * Оба семейства под SIL Open Font License 1.1, см. docs/fonts/.
 */

/**
 * Rubik — один вариативный файл на все начертания вместо четырёх статических
 * (те весили бы вместе больше). Ось `wght` работает с API 26, что совпадает
 * с minSdk, поэтому запасной статики не нужно.
 *
 * FontVariation пока помечен экспериментальным. Если API изменится, замена —
 * четыре статических файла и обычный `Font(resId, weight)`.
 */
@OptIn(ExperimentalTextApi::class)
private fun rubik(weight: FontWeight) = Font(
    R.font.rubik_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Основная гарнитура: заголовки, подписи, лейблы, кнопки, текст. */
val Rubik = FontFamily(
    rubik(FontWeight.Normal),
    rubik(FontWeight.Medium),
    rubik(FontWeight.SemiBold),
    rubik(FontWeight.Bold),
)

/** Моноширинная — только числа: оценка, цена, год, крепость. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)
