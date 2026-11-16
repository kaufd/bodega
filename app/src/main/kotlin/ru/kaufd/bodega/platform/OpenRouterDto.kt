package ru.kaufd.bodega.platform

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Тела запросов и ответов OpenRouter (формат OpenAI chat completions).
 * Держим ровно те поля, которые используем: остальное игнорируется при разборе.
 */

internal val openRouterJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int,
    /** Пусто — обычный запрос; для справки о напитке добавляется веб-поиск. */
    val plugins: List<Plugin> = emptyList(),
)

/** Плагин OpenRouter. Нужен ровно один — веб-поиск. */
@Serializable
internal data class Plugin(
    val id: String,
    @SerialName("max_results") val maxResults: Int,
)

@Serializable
internal data class Message(
    val role: String = "user",
    val content: List<Part>,
)

/**
 * Часть сообщения: текст или изображение. Отдельные классы вместо одного с
 * nullable-полями — иначе в запрос уезжают `"image_url": null`, и часть моделей
 * на этом спотыкается.
 */
@Serializable
internal sealed interface Part {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : Part

    @Serializable
    @SerialName("image_url")
    data class Image(@SerialName("image_url") val imageUrl: Url) : Part

    @Serializable
    data class Url(val url: String)
}

@Serializable
private data class ChatResponse(val choices: List<Choice> = emptyList())

@Serializable
private data class Choice(
    val message: ResponseMessage = ResponseMessage(),
    /** «length» — ответ оборвался по лимиту токенов. */
    @SerialName("finish_reason") val finishReason: String? = null,
)

/** У reasoning-моделей `content` приходит `null`, когда лимит кончился на размышлении. */
@Serializable
private data class ResponseMessage(val content: String? = null)

/**
 * Содержимое первого варианта ответа. Пусто, если модель ничего не вернула.
 *
 * Обрыв по лимиту токенов — тоже пусто: обрезанный JSON всё равно не разобрать,
 * а обрезанный черновик заметки прошёл бы проверку на пустоту и заменил бы
 * заметку половиной фразы.
 */
fun extractContent(json: String): String =
    runCatching { openRouterJson.decodeFromString<ChatResponse>(json) }
        .getOrNull()
        ?.choices
        ?.firstOrNull()
        ?.takeIf { it.finishReason != TRUNCATED }
        ?.message
        ?.content
        .orEmpty()

private const val TRUNCATED = "length"

