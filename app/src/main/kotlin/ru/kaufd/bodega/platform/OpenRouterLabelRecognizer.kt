package ru.kaufd.bodega.platform

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.LabelRecognizer
import ru.kaufd.bodega.domain.LabelScan
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.parseLabelScan
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject

/**
 * Распознаёт этикетку облачной моделью. В отличие от ML Kit читает кириллицу
 * и возвращает не простыню текста, а разобранные поля.
 */
class OpenRouterLabelRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OpenRouterClient,
) : LabelRecognizer {

    override suspend fun recognize(source: PhotoSource): LabelScan {
        val dataUrl = withContext(Dispatchers.IO) { encodeImage(source) }

        val content = client.chat(
            parts = listOf(
                Part.Text(PROMPT),
                Part.Image(Part.Url(dataUrl)),
            ),
            maxTokens = MAX_TOKENS,
        )
        return parseLabelScan(content)
    }

    /** Уменьшенный JPEG в data-URL: отдельного хранилища для отправки не нужно. */
    private fun encodeImage(source: PhotoSource): String {
        val bitmap = context.contentResolver.decodeNormalized(source.locator.toUri())
        val bytes = try {
            // Ёмкость с запасом от реального размера: с дефолтными 32 байтами буфер
            // переаллоцируется десяток раз, копируя сотни килобайт.
            ByteArrayOutputStream(bitmap.byteCount / 8)
                .also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                .toByteArray()
        } finally {
            bitmap.recycle()
        }
        return "data:image/jpeg;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private companion object {
        /**
         * С запасом: модель — reasoning, и размышление тратит тот же лимит.
         * Не хватило бы — ответ обрывался бы на середине JSON и молча пропадал.
         */
        const val MAX_TOKENS = 4096

        val PROMPT = """
            На фотографии — этикетка алкогольного напитка. Верни только JSON, без пояснений
            и без markdown-ограждения, со следующими полями (неизвестные — null):
            {
              "rawText": "весь текст с этикетки одной строкой",
              "name": "название напитка",
              "wineType": "для вина: Тихое | Игристое | Креплёное",
              "style": "для пива: стиль (IPA, Стаут, Лагер, Пилснер, Пшеничное и т.п.)",
              "type": "для крепкого: вид (Виски, Бурбон, Ром, Текила, Кальвадос, Коньяк и т.п.)"
            }
            Значения — на русском языке. Поля, не относящиеся к напитку, оставь null.
            Тип, стиль и вид определи по напитку, даже если словом на этикетке они не
            названы: тихое вино так и не подписывают, а стиль пива и вид крепкого
            видны по названию и оформлению.
        """.trimIndent()
    }
}
