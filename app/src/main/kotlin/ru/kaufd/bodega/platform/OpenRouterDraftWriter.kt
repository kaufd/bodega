package ru.kaufd.bodega.platform

import ru.kaufd.bodega.domain.DraftWriter
import ru.kaufd.bodega.domain.Entry
import ru.kaufd.bodega.domain.RecognitionError
import ru.kaufd.bodega.domain.RecognitionException
import ru.kaufd.bodega.domain.formatAbv
import javax.inject.Inject

/**
 * Черновик заметки в два запроса: сперва поиск характеристик напитка, затем
 * изложение найденного стилем автора.
 *
 * Шаги разделены не для стройности: в одном промпте поиск и подражание стилю мешают
 * друг другу — модель, занятая образцами и разметкой, формировала невнятный поисковый
 * запрос и приносила описания посторонних вин. Разнесённые, оба шага работают,
 * а второму веб-поиск уже не нужен.
 */
class OpenRouterDraftWriter @Inject constructor(
    private val client: OpenRouterClient,
) : DraftWriter {

    /**
     * Найденные характеристики последнего напитка. Экземпляр живёт вместе с
     * редактором (см. привязку DraftWriter в AppModule), поэтому кэш — на сессию
     * редактора: повтор после сбоя второго шага не платит за веб-поиск заново.
     */
    private var cachedFor: String? = null
    private var cachedFacts: String? = null

    override suspend fun draftNote(entry: Entry, examples: List<String>): String {
        val facts = cachedFacts?.takeIf { cachedFor == entry.name } ?: lookupFacts(entry).also {
            cachedFor = entry.name
            cachedFacts = it
        }

        // Шаг 2: изложение найденного стилем автора. Веб-поиск здесь уже не нужен.
        val draft = client.chat(
            parts = listOf(Part.Text(stylePrompt(entry, facts, examples))),
            maxTokens = STYLE_TOKENS,
        ).trim()
        // Пустой ответ — тоже отказ: вызывающему незачем изобретать своё правило
        // для пустой строки (см. KDoc [DraftWriter]).
        if (draft.isBlank()) throw RecognitionException(RecognitionError.OTHER)
        return draft
    }

    /** Шаг 1: поиск характеристик. Запрос намеренно короткий — так он точнее. */
    private suspend fun lookupFacts(entry: Entry): String {
        val facts = client.chat(
            parts = listOf(Part.Text(lookupPrompt(entry))),
            maxTokens = LOOKUP_TOKENS,
            plugins = listOf(Plugin(id = "web", maxResults = WEB_RESULTS)),
        ).trim()
        // Ничего не нашлось — заметку не пишем: выдумка, поданная как найденное,
        // хуже честной пустоты. «Не нашлось» модель говорит маркером; пустой ответ
        // — это отказ самого запроса (лимит токенов ушёл в размышление), и списывать
        // его на отсутствие описаний нельзя.
        if (facts.contains(NO_DATA_MARKER)) throw RecognitionException(RecognitionError.NO_DATA)
        if (facts.isBlank()) throw RecognitionException(RecognitionError.OTHER)
        return facts
    }

    private fun lookupPrompt(entry: Entry): String = """
        Дегустационные характеристики напитка «${entry.name}»: цвет, аромат, вкус, послевкусие.
        Только про этот конкретный напиток — описания других вин того же сорта, региона
        или производителя не подходят.
        Если про него ничего не нашлось, ответь ровно: $NO_DATA_MARKER
        Иначе перечисли характеристики списком, без ссылок и без вступлений.
    """.trimIndent()

    private fun stylePrompt(entry: Entry, facts: String, examples: List<String>): String =
        buildString {
            append(STYLE_PROMPT)
            append("\n\nХарактеристики напитка:\n").append(facts)
            append("\n\nДанные записи:\n").append(describe(entry))
            if (examples.isNotEmpty()) {
                append("\n\nОбразцы прежних заметок автора:")
                examples.forEach { append("\n\n---\n").append(it.trim()) }
                append("\n---")
            }
        }

    /** Только заполненные поля: пустые строки вида «Год: null» сбивают модель. */
    private fun describe(entry: Entry): String = buildList {
        add("Название: ${entry.name}")
        entry.country?.let { add("Страна: $it") }
        entry.vintageYear?.let { add("Урожай: $it") }
        entry.color?.let { add("Цвет: $it") }
        entry.wineType?.let { add("Тип: $it") }
        entry.sweetness?.let { add("Сладость: $it") }
        entry.style?.let { add("Стиль: $it") }
        entry.type?.let { add("Вид: $it") }
        if (entry.grapes.isNotEmpty()) add("Сорта: ${entry.grapes.joinToString(", ")}")
        entry.abvPercent?.let { add("Крепость: ${formatAbv(it)}%") }
        entry.rating?.let { add("Оценка: $it из 10") }
    }.joinToString("\n")

    private companion object {
        const val WEB_RESULTS = 3
        const val NO_DATA_MARKER = "НЕТ ДАННЫХ"

        /** Поиск раздувает контекст, и размышление модели делит тот же лимит. */
        const val LOOKUP_TOKENS = 8192
        const val STYLE_TOKENS = 4096

        val STYLE_PROMPT = """
            Напиши заметку в личный дневник дегустаций вместо его автора, опираясь только
            на характеристики ниже. Ничего не добавляй от себя и не выдумывай наград.
            Стиль, структуру, разметку, эмодзи и характерные обороты повтори по образцам
            как можно точнее. Объём — примерно как в образцах. Ссылки не вставляй.
            Если оценка указана, тон должен ей соответствовать.
            Верни только текст заметки, без заголовков и пояснений.
        """.trimIndent()
    }
}
