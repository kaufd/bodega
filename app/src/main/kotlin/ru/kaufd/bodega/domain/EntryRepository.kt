package ru.kaufd.bodega.domain

import kotlinx.coroutines.flow.Flow

/** Использованные значения классификации вина для секций фильтра. */
data class WineFacets(
    val colors: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val sweetness: List<String> = emptyList(),
)

interface EntryRepository {
    fun observeEntries(filter: EntryFilter): Flow<List<Entry>>
    fun observeCount(category: Category?): Flow<Int>
    fun observeById(id: Long): Flow<Entry?>
    suspend fun getById(id: Long): Entry?
    /** @return id записи (новой или обновлённой) */
    suspend fun upsert(entry: Entry): Long

    /**
     * Вставляет пачку новых записей одной транзакцией — для импорта бэкапа.
     *
     * Поштучный upsert оставлял бы при отказе на середине половину архива в журнале
     * и половину распакованных снимков без владельца.
     */
    suspend fun insertAll(entries: List<Entry>)
    /**
     * Удаляет строку по id; файл фото НЕ трогает (undo).
     * @return фактически удалённая запись (снимок из БД для undo) или null, если её уже нет.
     */
    suspend fun delete(id: Long): Entry?
    /** Возврат после undo: вставка с исходным id. */
    suspend fun restore(entry: Entry)
    /**
     * Вызвать после истечения undo: удалить осиротевшие файлы фотографий.
     * @return false, если хотя бы один файл остался на диске (сироту подметёт
     * `OrphanPhotoSweeper`).
     */
    suspend fun purgePhotos(paths: List<String>): Boolean

    /** Пути фотографий, на которые ссылаются записи, — для поиска осиротевших файлов. */
    suspend fun usedPhotoPaths(): List<String>

    /** Последние непустые заметки категории — образцы стиля для черновика. */
    suspend fun recentNotes(category: Category, limit: Int): List<String>

    /**
     * Использованные типы, сгруппированные по категориям (для экрана фильтров).
     * Категории идут в порядке объявления [Category]; при заданной категории —
     * только она.
     */
    fun observeUsedTypesGrouped(category: Category?): Flow<Map<Category, List<String>>>
    fun observeUsedCountries(): Flow<List<String>>

    /** Использованные цвет/тип/сладость среди вин (для секций фильтра вина). */
    fun observeWineFacets(): Flow<WineFacets>

    /** Использованные стили среди пива (для секции фильтра пива). */
    fun observeUsedStyles(): Flow<List<String>>
}
