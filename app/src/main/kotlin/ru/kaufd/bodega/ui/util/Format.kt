package ru.kaufd.bodega.ui.util

import androidx.annotation.StringRes
import ru.kaufd.bodega.R
import ru.kaufd.bodega.domain.Category
import ru.kaufd.bodega.domain.Entry

/**
 * Подзаголовок записи под названием: «Красное · Сухое · Италия». Один на плитку,
 * компактную строку, витрину и шапку карточки — иначе они описывают одну запись
 * по-разному. Значения не переводятся: они хранятся как введены.
 */
fun entrySubtitle(entry: Entry): String = when (entry.category) {
    Category.WINE -> listOfNotNull(entry.color, entry.sweetness, entry.country).distinct().joinToString(" · ")
    Category.BEER -> entry.style.orEmpty()
    Category.SPIRIT -> entry.type.orEmpty()
    Category.COCKTAIL -> entry.ingredientsText.lineSequence().firstOrNull().orEmpty()
}

/** Форматирует цену в рублях с пробелами-разделителями тысяч: 4200 → «4 200». */
fun formatPrice(rub: Int): String =
    rub.toString().reversed().chunked(3).joinToString(" ").reversed()

/** Приветствие по часу суток: ночь 0–4, утро 5–11, день 12–17, вечер 18–23. */
@StringRes
fun greetingRes(hourOfDay: Int): Int = when (hourOfDay) {
    in 5..11 -> R.string.header_greeting_morning
    in 12..17 -> R.string.header_greeting_day
    in 18..23 -> R.string.header_greeting_evening
    else -> R.string.header_greeting_night
}
