package ru.kaufd.bodega.domain

@JvmInline
value class PhotoSource(val locator: String)

/** Файл в хранилище фотографий: относительный путь и время последней записи. */
data class StoredPhoto(val relativePath: String, val modifiedAt: Long)

interface PhotoStorage {
    /** Содержимое файла как есть. `null`, если файла нет. Закрывает вызывающий. */
    fun open(relativePath: String): java.io.InputStream?

    /**
     * Кладёт готовое изображение в хранилище **без перекодирования** — в отличие от
     * [importImage], который пережимает JPEG. Для снимков из бэкапа: они уже прошли
     * через [importImage] когда-то, и повторное сжатие теряло бы качество на каждом
     * круге экспорт→импорт. Поток не закрывает — он принадлежит вызывающему.
     *
     * @return относительный путь нового файла.
     */
    suspend fun store(input: java.io.InputStream): String

    /**
     * Удаляет файл. @return true, если файла больше нет (в том числе если его и
     * не было); false — удаление не удалось, файл остался на диске.
     */
    suspend fun delete(relativePath: String): Boolean
    /** Абсолютный путь для загрузчика изображений. */
    fun absolutePath(relativePath: String): String
    /** Импорт изображения из opaque-источника. Возвращает относительный путь. */
    suspend fun importImage(source: PhotoSource): String
    /** Все файлы хранилища — для поиска осиротевших (см. `OrphanPhotoSweeper`). */
    suspend fun list(): List<StoredPhoto>
}
