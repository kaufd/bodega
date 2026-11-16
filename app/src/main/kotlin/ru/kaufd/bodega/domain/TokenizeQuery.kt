package ru.kaufd.bodega.domain

/** Разделитель токенов: последовательность не-букв/не-цифр (Unicode-aware). */
private val TOKEN_DELIMITER = Regex("[^\\p{L}\\p{N}]+")

/**
 * Короткий чисто числовой терм (крепость «40», объём «700») — поисковый шум,
 * не несёт смысла. 4-значные числа (винтажные года «2014») сохраняем: иначе
 * теряется ручной поиск по году (`vintageYear` в `searchText` не входит, год
 * ищется только когда он в `name`).
 *
 * Единый источник правила — используется и в [tokenizeQuery] (пер-токен), и в
 * fallback-поиске репозитория (по всему запросу).
 */
internal fun isShortNumericNoise(term: String): Boolean =
    term.toIntOrNull() != null && term.length < 4

/**
 * Токенизация поискового запроса — единая для OCR и ручного ввода.
 *
 * split по не-буквенно-цифровым разделителям (Unicode-aware), lowercase,
 * уникальные, отброс токенов короче 3 символов и коротких числовых
 * ([isShortNumericNoise]).
 *
 * Чистая функция, без зависимостей от Android, JVM-тестируема.
 */
fun tokenizeQuery(text: String): List<String> =
    text.split(TOKEN_DELIMITER)
        .map { it.lowercase() }
        .filter { it.length >= 3 && !isShortNumericNoise(it) }
        .distinct()

/**
 * Разбивает уже нормализованный (нижний регистр) текст на слова тем же
 * разделителем, что и [tokenizeQuery] — без фильтрации. Нужно для пословного
 * fuzzy-сравнения `searchText` в репозитории ([fuzzyTermMatches]).
 */
internal fun splitWords(text: String): List<String> =
    text.split(TOKEN_DELIMITER).filter { it.isNotEmpty() }
