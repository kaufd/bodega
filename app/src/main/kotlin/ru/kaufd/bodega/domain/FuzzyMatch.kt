package ru.kaufd.bodega.domain

import kotlin.math.abs
import kotlin.math.min

/**
 * Порог расстояния Левенштейна в зависимости от длины терма запроса. Короткие
 * слова матчатся строго (0), иначе одна опечатка превращает их в другое слово;
 * чем длиннее терм, тем больше правок допустимо.
 */
private fun fuzzyThreshold(length: Int): Int = when {
    length <= 4 -> 0
    length <= 7 -> 1
    else -> 2
}

/**
 * Толерантное сравнение терма запроса со словом из названия — для поиска по фото,
 * где OCR добавляет опечатки/лишние буквы.
 *
 * Защиты от ложных срабатываний: короткие термы ([fuzzyThreshold] = 0) и чисто
 * числовые термы (годы «2019» ≠ «2018») сравниваются строго.
 *
 * Чистая функция, без зависимостей от Android, JVM-тестируема.
 */
internal fun fuzzyTermMatches(term: String, word: String): Boolean {
    val threshold = fuzzyThreshold(term.length)
    if (threshold == 0 || term.toIntOrNull() != null) return term == word
    return levenshteinWithin(term, word, threshold)
}

/**
 * Расстояние Левенштейна между [a] и [b] ≤ [max]. С ранним выходом по разнице
 * длин и по минимуму строки DP — не считаем полное расстояние, если порог заведомо
 * превышен.
 */
internal fun levenshteinWithin(a: String, b: String, max: Int): Boolean {
    if (abs(a.length - b.length) > max) return false
    if (a == b) return true

    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        var rowMin = curr[0]
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = min(min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost)
            rowMin = min(rowMin, curr[j])
        }
        if (rowMin > max) return false
        val tmp = prev; prev = curr; curr = tmp
    }
    return prev[b.length] <= max
}
