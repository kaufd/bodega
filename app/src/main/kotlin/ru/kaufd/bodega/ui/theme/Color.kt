package ru.kaufd.bodega.ui.theme

import androidx.compose.ui.graphics.Color
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.Category

/**
 * Токены макета «airy cellar» (docs/design/result/airy-cellar.html).
 * Значения — из финального блока «editorial refinement»: плоские заливки,
 * стеклянные поверхности разной плотности, единый бирюзовый «выбрано».
 */
object BodegaColors {
    val Paper = Color(0xFFF1F7FA)
    val Surface = Color(0xFFFFFFFF)

    val Ink = Color(0xFF20324A)
    val Muted = Color(0xFF64758A)
    val Placeholder = Color(0xFF95A5B6)
    val Label = Color(0xFF7C8EA1)

    /** Граница поля ввода: rgba(121,151,170,.14) — единственное, что рисует форму поля. */
    val FieldBorder = Color(0x38799AAA)
    /** Разделитель строк внутри карточки: rgba(0,0,0,.07). */
    val Hairline = Color(0x12000000)
    /** Разделитель секций фильтров: rgba(93,133,157,.13). */
    val SectionRule = Color(0x215D859D)

    /** «Выбрано» — сегменты, чипы, пункты списка: rgba(54,143,173,.94). */
    val Teal = Color(0xF0368FAD)
    /** Первичное действие: rgba(222,78,113,.94). */
    val Accent = Color(0xF0DE4E71)
    /** Кнопка сканирования: rgba(31,51,73,.92). */
    val DarkButton = Color(0xEB1F3349)

    /** Разрушающее действие: удаление записи. */
    val Danger = Color(0xFFD93A3A)

    /** Акцент чисел (оценка). */
    val Wine = Color(0xFFE65C78)
    val Rating = Color(0xFFFFB74D)

    val TealBright = Color(0xFF50B8C0)
    val Coral = Color(0xFFFF826A)

    /** Тень стеклянных поверхностей: rgba(61,112,140,…). */
    val Shadow = Color(0xFF3D708C)
    /** Тень акцентных кнопок: rgba(202,55,96,…). */
    val AccentShadow = Color(0xFFCA3760)

    val WineTint = Color(0xFFFFE4EA)
    val BeerTint = Color(0xFFFFEED5)
    val SpiritTint = Color(0xFFE6EEF3)
}

fun categoryColor(category: Category): Color = when (category) {
    Category.WINE -> BodegaColors.Wine
    Category.BEER -> BodegaColors.Rating
    Category.SPIRIT -> BodegaColors.Ink
    Category.COCKTAIL -> BodegaColors.Teal
}

fun categoryTint(category: Category): Color = when (category) {
    Category.WINE -> BodegaColors.WineTint
    Category.BEER -> BodegaColors.BeerTint
    Category.SPIRIT -> BodegaColors.SpiritTint
    Category.COCKTAIL -> BodegaColors.BeerTint
}

fun Category.displayNameRes(): Int = when (this) {
    Category.WINE -> R.string.category_wine
    Category.BEER -> R.string.category_beer
    Category.SPIRIT -> R.string.category_spirit
    Category.COCKTAIL -> R.string.category_cocktail
}
