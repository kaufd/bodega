package ru.kaufd.bodega.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Результат распознавания этикетки: ровно то, из чего собирается поиск по фото
 * (см. [searchQueryOf]). Поля названы как в [Entry] — когда появится
 * предзаполнение записи, остальные добавятся строкой на поле и в промпт.
 *
 * On-device распознаватель заполняет только [rawText]; облачная модель — остальное.
 */
@Serializable
data class LabelScan(
    val rawText: String = "",
    val name: String? = null,
    val wineType: String? = null,
    /** Стиль пива: «IPA», «Стаут». */
    val style: String? = null,
    /** Вид крепкого: «Виски», «Ром». */
    val type: String? = null,
) {
    /**
     * Распознать не удалось: ни текста, ни названия. Пустая строка считается
     * отсутствием: модель просят вернуть `null`, но она нередко отдаёт `""`.
     */
    val isEmpty: Boolean get() = rawText.isBlank() && name.isNullOrBlank()
}

/**
 * Ответ модели разбираем терпимо: схему в запросе просим, но соблюдение не гарантировано.
 * Лишние поля игнорируем, `null` в поле с дефолтом заменяем дефолтом.
 */
private val labelJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * Строка поиска по распознанной этикетке: название и тип, без остального.
 *
 * Разобранные поля точнее простыни OCR: в ней ещё объём, штрихкод и «продукт
 * сертифицирован». Год сюда не идёт намеренно — «2019» отсекало бы ту же бутылку
 * другого урожая, а искать по фото обычно нужно именно «пробовал ли я это вообще».
 */
fun searchQueryOf(scan: LabelScan): String {
    // Пустая строка — не разобранное поле, а её отсутствие (см. [LabelScan.isEmpty]):
    // иначе `"name": ""` при годном rawText давал бы пустой запрос.
    val name = scan.name?.takeIf { it.isNotBlank() }
    val kind = listOf(scan.wineType, scan.style, scan.type).firstOrNull { !it.isNullOrBlank() }
    // Тип без названия («Стаут») в запрос не идёт: с queryFuzzy он выдал бы всё пиво
    // этого стиля как найденную бутылку. ML Kit полей не даёт вовсе — для него, как
    // и для такого случая, остаётся текст, схлопнутый в одну строку.
    val source = if (name != null) listOfNotNull(name, kind).joinToString(" ") else scan.rawText
    return source.replace(WHITESPACE, " ").trim()
}

private val WHITESPACE = Regex("""\s+""")

/** Разбирает JSON-ответ модели. Непригодный ответ — пустой [LabelScan], без исключения. */
fun parseLabelScan(text: String): LabelScan {
    // Модель оборачивает ответ в ```json … ``` или добавляет фразу до и после него,
    // даже когда схема запрошена. Берём фрагмент от первой скобки до последней.
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end < start) return LabelScan()
    return runCatching { labelJson.decodeFromString<LabelScan>(text.substring(start, end + 1)) }
        .getOrDefault(LabelScan())
}
