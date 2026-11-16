package ru.kaufd.bodega.platform

import android.content.ContentResolver
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.kaufd.bodega.domain.DocumentStorage
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** [DocumentStorage] поверх SAF: локатор — строковый `content://`-URI из системного диалога. */
class AndroidDocumentStorage(
    private val contentResolver: ContentResolver,
) : DocumentStorage {

    override suspend fun read(locator: String): InputStream = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(locator.toUri()) ?: throw IOException("Файл недоступен")
    }

    override suspend fun write(locator: String): OutputStream = withContext(Dispatchers.IO) {
        contentResolver.openOutputStream(locator.toUri()) ?: throw IOException("Файл недоступен")
    }
}
