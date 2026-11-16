package ru.kaufd.bodega.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.RecognitionError
import ru.kaufd.bodega.domain.RecognitionException
import ru.kaufd.bodega.domain.SettingsStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Тонкий клиент OpenRouter (формат OpenAI chat completions).
 *
 * `HttpURLConnection`, а не HTTP-библиотека: ручка одна, а OkHttp в графе
 * зависимостей отсутствует — тащить его ради одного POST не за чем.
 */
@Singleton
class OpenRouterClient @Inject constructor(private val settings: SettingsStore) {

    /**
     * Отправляет запрос и возвращает содержимое ответа модели.
     *
     * Настройки читает сам: гейт «облако настроено» должен быть один на всех
     * вызывающих, иначе ненастроенный ключ у каждого отказывает по-своему.
     */
    internal suspend fun chat(
        parts: List<Part>,
        maxTokens: Int,
        plugins: List<Plugin> = emptyList(),
    ): String = withContext(Dispatchers.IO) {
        val config = settings.recognition.first()
        if (!config.usable) throw RecognitionException(RecognitionError.DISABLED)

        val body = openRouterJson.encodeToString(
            ChatRequest(
                model = config.model,
                messages = listOf(Message(content = parts)),
                maxTokens = maxTokens,
                plugins = plugins,
            )
        ).toByteArray()

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            // Длина известна заранее — иначе HttpURLConnection буферизует всё тело
            // ещё раз, чтобы её вычислить, а с картинкой в base64 это лишний мегабайт.
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        // Блокирующий HttpURLConnection отмену корутины не замечает: без этого уход
        // с экрана оставлял бы поток на сокете до таймаута, а платный запрос всё
        // равно доходил бы до конца и тарифицировался.
        val abort = coroutineContext.job.invokeOnCompletion { cause ->
            if (cause != null) runCatching { connection.disconnect() }
        }

        try {
            writeBody(connection, body)
            val code = connection.responseCode
            if (code !in 200..299) throw RecognitionException(errorFor(code))
            connection.inputStream.bufferedReader().use { extractContent(it.readText()) }
        } catch (e: RecognitionException) {
            throw e
        } catch (_: IOException) {
            throw RecognitionException(RecognitionError.NETWORK)
        } finally {
            abort.dispose()
            connection.disconnect()
        }
    }

    /**
     * Тело в мегабайт сервер может отвергнуть, не дочитав, — тогда падает запись,
     * а не чтение кода ответа. Списать это на сеть значило бы показать «нет
     * соединения» на весь класс отказов ключа и счёта.
     */
    private fun writeBody(connection: HttpURLConnection, body: ByteArray) {
        try {
            connection.outputStream.use { it.write(body) }
        } catch (io: IOException) {
            val code = runCatching { connection.responseCode }.getOrNull()
            if (code == null || code in 200..299) throw io
            throw RecognitionException(errorFor(code))
        }
    }

    private fun errorFor(code: Int): RecognitionError = when (code) {
        401, 403 -> RecognitionError.AUTH
        402 -> RecognitionError.CREDITS
        // Модель — поле свободного ввода: опечатка в имени и модель без поддержки
        // изображений приходят сюда, и «не удалось получить ответ» о них молчало бы.
        400, 404 -> RecognitionError.MODEL
        429, in 500..599 -> RecognitionError.BUSY
        else -> RecognitionError.OTHER
    }

    private companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
        const val CONNECT_TIMEOUT_MS = 15_000

        /**
         * Vision на большой картинке небыстрый, а черновик заметки ещё и ходит
         * в веб-поиск. Ждать минуту у полки никто не станет, но обрывать запрос,
         * который вот-вот ответит, — хуже.
         */
        const val READ_TIMEOUT_MS = 45_000
    }
}
