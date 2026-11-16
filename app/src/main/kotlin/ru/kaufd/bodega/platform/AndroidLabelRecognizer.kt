package ru.kaufd.bodega.platform

import android.content.Context
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.LabelRecognizer
import ru.kaufd.bodega.domain.LabelScan
import ru.kaufd.bodega.domain.PhotoSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR этикетки через ML Kit on-device (Latin-распознаватель).
 *
 * Кириллица ML Kit on-device не поддерживается — см. спеку «Ограничение».
 * На кириллических этикетках вернёт пустую строку или мусор.
 */
class AndroidLabelRecognizer(
    private val context: Context,
) : LabelRecognizer {

    override suspend fun recognize(source: PhotoSource): LabelScan {
        // InputImage.fromFilePath — блокирующий I/O (ContentResolver + decode),
        // выносим с главного потока.
        val image = withContext(Dispatchers.IO) {
            InputImage.fromFilePath(context, source.locator.toUri())
        }
        // Распознаватель — Closeable; создаём и закрываем на вызов (сканы редки,
        // так нет удерживаемой в памяти нативной модели на весь жизненный цикл).
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> cont.resume(result.text) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
        } finally {
            recognizer.close()
        }
        // Разбирать текст на поля здесь нечем: OCR отдаёт плоскую простыню.
        return LabelScan(rawText = text)
    }
}
