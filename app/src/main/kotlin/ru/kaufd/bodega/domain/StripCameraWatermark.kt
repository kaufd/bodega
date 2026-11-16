package ru.kaufd.bodega.domain

/**
 * Маркеры водяных знаков камеры производителя («Shot on Galaxy S23 | Triple
 * camera», «50 MP AI CAMERA», «LEICA» и т.п.). Камера впечатывает их в пиксели,
 * поэтому OCR читает их как часть этикетки и засоряет поисковый запрос.
 */
private val WATERMARK_MARKERS = listOf(
    Regex("""shot\s+on\b""", RegexOption.IGNORE_CASE),
    Regex("""\b(triple|dual|quad|ai)\s+camera\b""", RegexOption.IGNORE_CASE),
    Regex("""\b\d+\s?mp\b""", RegexOption.IGNORE_CASE),
    Regex("""\bleica\b""", RegexOption.IGNORE_CASE),
    Regex("""\bhasselblad\b""", RegexOption.IGNORE_CASE),
)

/**
 * Вырезает из распознанного OCR-текста целые строки, содержащие маркер водяного
 * знака камеры ([WATERMARK_MARKERS]).
 *
 * Работаем построчно, а не по отдельным словам: вместе с маркером уходит и модель
 * телефона на той же строке («Shot on Galaxy S23 Ultra»), при этом не задеваем
 * общие слова, которые могут встречаться на настоящей этикетке.
 *
 * Чистая функция, без зависимостей от Android, JVM-тестируема.
 */
fun stripCameraWatermark(text: String): String =
    text.lineSequence()
        .filterNot { line -> WATERMARK_MARKERS.any { it.containsMatchIn(line) } }
        .joinToString("\n")
