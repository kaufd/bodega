package ru.kaufd.bodega.ui.util

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.kaufd.bodega.ui.LocalAppScope
import java.io.File

/** Через сколько временный снимок в кэше считается брошенным и подлежит уборке. */
private const val STALE_CAPTURE_MS = 60 * 60 * 1000L

/**
 * Создаёт content-Uri (через FileProvider) для временного снимка камеры в кэше.
 *
 * Используется общим FileProvider-ом приложения (`${packageName}.fileprovider`,
 * `file_paths.xml` с `cache-path camera/`). Переиспользуется в `EditScreen`
 * (импорт фото записи) и `ListScreen` (скан этикетки).
 */
fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Подчищает временные снимки камеры: удачные уже перекодированы в `files/photos/`,
 * отменённые не нужны вовсе, а ОС чистит кэш только под давлением места.
 *
 * Порог по возрасту нужен, чтобы не снести файл начатой съёмки: приложение может
 * быть пересоздано, пока открыта сторонняя камера. Вызывается при старте
 * приложения — раньше уборка происходила только при следующем открытии камеры,
 * и брошенный снимок мог лежать в кэше неограниченно долго.
 */
fun purgeStaleCaptures(context: Context, now: Long = System.currentTimeMillis()) {
    val staleBefore = now - STALE_CAPTURE_MS
    File(context.cacheDir, "camera").listFiles()?.forEach {
        if (it.lastModified() < staleBefore) it.delete()
    }
}

/**
 * Общая оркестрация съёмки: системная камера через FileProvider, колбэк
 * [onCaptured] с content-Uri снимка (только при успехе) и функцией `onConsumed`,
 * которую потребитель вызывает, закончив читать файл. Возвращает функцию-триггер съёмки.
 *
 * Владение временным файлом явное: снимок удаляется сразу после импорта или OCR,
 * а не копится в кэше до следующего запуска (уборка по возрасту осталась
 * подстраховкой на случай смерти процесса посреди импорта).
 *
 * Разрешение `CAMERA` не запрашивается: снимает стороннее приложение камеры под
 * своими правами. Более того, объявленное в манифесте разрешение сделало бы его
 * обязательным и для нас — поэтому его там и нет.
 *
 * Переиспользуется в `EditScreen` (импорт фото записи) и `ListScreen` (скан этикетки).
 */
@Composable
fun rememberCameraCapture(onCaptured: (uri: Uri, onConsumed: () -> Unit) -> Unit): () -> Unit {
    val context = LocalContext.current
    // Область уровня приложения, а не композиции: пользователь может уйти с экрана
    // во время импорта или OCR, и уборка временного файла обязана это пережить.
    val scope = LocalAppScope.current
    // Резолвер приложения, а не Activity: замыкание discard ниже уезжает в корутину
    // ViewModel на всё время импорта или OCR и держало бы Activity после поворота.
    val resolver = remember(context) { context.applicationContext.contentResolver }
    // Uri переживает пересоздание Activity: система может убить процесс, пока
    // открыта камера, и результат вернётся уже в новый экземпляр.
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri?.toUri()
        val discard = {
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
            Unit
        }
        // Съёмку отменили или она не удалась — пустой файл уже создан FileProvider-ом.
        if (success && uri != null) onCaptured(uri, discard) else discard()
        cameraUri = null
    }

    return {
        val uri = createCameraUri(context)
        cameraUri = uri.toString()
        cameraLauncher.launch(uri)
    }
}
