package ru.kaufd.bodega.domain

/** Нарушенный инвариант записи. Одно значение на поле — UI подсвечивает нужный ввод. */
enum class EntryViolation { NAME_BLANK, RATING, ABV }

/**
 * Нарушения границ записи. Набор совпадает с `require` в репозитории: форма
 * редактора держит валидацию в состоянии и не даёт сохранить заведомо плохую
 * запись, а `require` остаётся защитой «этого не должно случиться».
 *
 * Раньше UI позволял ввести крепость 97: `canSave` смотрел только на название,
 * а `require` бросал исключение из корутины уже после нажатия «Сохранить».
 */
fun Entry.violations(): Set<EntryViolation> = buildSet {
    if (name.isBlank()) add(EntryViolation.NAME_BLANK)
    if (rating != null && rating !in EntryLimits.RATING) add(EntryViolation.RATING)
    if (abvPercent != null && abvPercent !in EntryLimits.ABV) add(EntryViolation.ABV)
}
