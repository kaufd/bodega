package ru.kaufd.bodega.platform

import android.content.ContentResolver
import android.graphics.Bitmap
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.PhotoSource
import ru.kaufd.bodega.domain.PhotoStorage
import ru.kaufd.bodega.domain.StoredPhoto
import java.io.File
import java.util.UUID

class AndroidPhotoStorage(
    private val filesDir: File,
    private val contentResolver: ContentResolver,
) : PhotoStorage {

    override fun absolutePath(relativePath: String): String =
        File(filesDir, relativePath).absolutePath

    override fun open(relativePath: String): java.io.InputStream? =
        File(filesDir, relativePath).takeIf { it.isFile }?.inputStream()

    override suspend fun store(input: java.io.InputStream): String = withContext(Dispatchers.IO) {
        val relative = "$PHOTO_DIR/${UUID.randomUUID()}.jpg"
        val target = File(filesDir, relative).apply { parentFile?.mkdirs() }
        try {
            target.outputStream().use { input.copyTo(it) }
        } catch (t: Throwable) {
            // Как и в importImage: недописанный файл на диске не оставляем.
            target.delete()
            throw t
        }
        relative
    }

    override suspend fun delete(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filesDir, relativePath)
        // File.delete() возвращает false и когда файла нет, и когда удалить не вышло;
        // отсутствие файла — это успех, а не отказ.
        !file.exists() || file.delete()
    }

    override suspend fun list(): List<StoredPhoto> = withContext(Dispatchers.IO) {
        File(filesDir, PHOTO_DIR).listFiles().orEmpty()
            .filter { it.isFile }
            .map { StoredPhoto("$PHOTO_DIR/${it.name}", it.lastModified()) }
    }

    /** Потоково декодирует источник, ограничивает размер и сохраняет JPEG. */
    override suspend fun importImage(source: PhotoSource): String = withContext(Dispatchers.IO) {
        val uri = source.locator.toUri()
        val scaled = contentResolver.decodeNormalized(uri)
        val relative = "$PHOTO_DIR/${UUID.randomUUID()}.jpg"
        val target = File(filesDir, relative).apply { parentFile?.mkdirs() }
        try {
            target.outputStream().use {
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) {
                    "Не удалось сохранить изображение"
                }
            }
            // Импорт отменён (пользователь выбрал другое фото) — файл никому не
            // достанется, отдавать его наружу нельзя.
            currentCoroutineContext().ensureActive()
        } catch (t: Throwable) {
            // Частично записанный или осиротевший файл не оставляем на диске.
            target.delete()
            throw t
        } finally {
            scaled.recycle()
        }
        relative
    }

    private companion object {
        const val PHOTO_DIR = "photos"
    }
}
